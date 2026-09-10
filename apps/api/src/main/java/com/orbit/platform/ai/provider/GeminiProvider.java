package com.orbit.platform.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.orbit.platform.ai.AiProperties;
import com.orbit.platform.ai.model.TokenUsage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Google Gemini via AI Studio (ADR-031 §1) — the primary provider. Native
 * {@code generateContent} / {@code streamGenerateContent} API (not the
 * OpenAI-compat layer, which lags on tool calling). Key as {@code x-goog-api-key}.
 */
@Component
public class GeminiProvider extends HttpAiProviderSupport implements AiProvider {

    private final AiProperties props;

    public GeminiProvider(ObjectMapper json, AiProperties props) {
        super(json, props.gemini().baseUrl(), props.connectTimeoutMs(), props.readTimeoutMs());
        this.props = props;
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public String model() {
        return props.gemini().model();
    }

    @Override
    public boolean isConfigured() {
        return props.geminiConfigured();
    }

    @Override
    public AiCompletion complete(AiRequest request) {
        JsonNode resp = post(
                "/v1beta/models/" + props.gemini().model() + ":generateContent",
                requestBody(request).toString(),
                h -> h.header("x-goog-api-key", props.gemini().apiKey()));
        return parseWhole(resp);
    }

    @Override
    public AiCompletion completeStream(AiRequest request, TokenSink sink) {
        Acc acc = new Acc();
        postStream(
                "/v1beta/models/" + props.gemini().model() + ":streamGenerateContent?alt=sse",
                requestBody(request).toString(),
                h -> h.header("x-goog-api-key", props.gemini().apiKey()),
                payload -> acc.frame(readTreeSafe(payload), sink));
        return acc.toCompletion();
    }

    // ---------------------------------------------------------------------

    private ObjectNode requestBody(AiRequest request) {
        ObjectNode body = json.createObjectNode();
        body.set("systemInstruction", json.createObjectNode()
                .set("parts", json.createArrayNode().add(json.createObjectNode()
                        .put("text", request.systemPrompt()))));
        body.set("contents", contents(request.history()));

        if (request.tools() != null && !request.tools().isEmpty()) {
            ArrayNode decls = json.createArrayNode();
            for (ToolSpec t : request.tools()) {
                decls.add(json.createObjectNode()
                        .put("name", t.name())
                        .put("description", t.description())
                        .set("parameters", readSchema(t.parametersJson())));
            }
            body.set("tools", json.createArrayNode().add(
                    json.createObjectNode().set("functionDeclarations", decls)));
        }

        body.set("generationConfig", json.createObjectNode()
                .put("maxOutputTokens", request.maxOutputTokens())
                .put("temperature", request.temperature()));
        return body;
    }

    private ArrayNode contents(List<ChatItem> history) {
        ArrayNode contents = json.createArrayNode();
        for (ChatItem item : history) {
            switch (item) {
                case ChatItem.UserText u -> contents.add(textContent("user", u.text()));
                case ChatItem.ModelText m -> contents.add(textContent("model", m.text()));
                case ChatItem.ModelToolCalls tc -> {
                    ArrayNode parts = json.createArrayNode();
                    for (ChatItem.ToolCall call : tc.calls()) {
                        parts.add(json.createObjectNode().set("functionCall", json.createObjectNode()
                                .put("name", call.name())
                                .set("args", readTreeSafe(call.argumentsJson()))));
                    }
                    contents.add(json.createObjectNode().put("role", "model").set("parts", parts));
                }
                case ChatItem.ToolResults tr -> {
                    ArrayNode parts = json.createArrayNode();
                    for (ChatItem.ToolResult r : tr.results()) {
                        parts.add(json.createObjectNode().set("functionResponse", json.createObjectNode()
                                .put("name", r.name())
                                .set("response", json.createObjectNode()
                                        .set("result", readTreeSafe(r.contentJson())))));
                    }
                    contents.add(json.createObjectNode().put("role", "user").set("parts", parts));
                }
                default -> { }
            }
        }
        return contents;
    }

    private ObjectNode textContent(String role, String text) {
        return json.createObjectNode().put("role", role).set("parts",
                json.createArrayNode().add(json.createObjectNode().put("text", text)));
    }

    private AiCompletion parseWhole(JsonNode resp) {
        Acc acc = new Acc();
        acc.frame(resp, TokenSink.DISCARD);
        return acc.toCompletion();
    }

    private JsonNode readSchema(String raw) {
        return readTreeSafe(raw == null || raw.isBlank() ? "{\"type\":\"object\"}" : raw);
    }

    /** Accumulates streamed (or whole) Gemini {@code GenerateContentResponse} chunks. */
    private final class Acc {
        private final StringBuilder text = new StringBuilder();
        private final List<ChatItem.ToolCall> calls = new ArrayList<>();
        private int promptTokens;
        private int completionTokens;
        private String finish;

        void frame(JsonNode chunk, TokenSink sink) {
            JsonNode candidates = chunk.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode cand = candidates.get(0);
                if (cand.hasNonNull("finishReason")) {
                    finish = cand.get("finishReason").asText();
                }
                for (JsonNode part : cand.path("content").path("parts")) {
                    if (part.hasNonNull("text")) {
                        String delta = part.get("text").asText();
                        text.append(delta);
                        if (!delta.isEmpty()) {
                            sink.onToken(delta);
                        }
                    } else if (part.has("functionCall")) {
                        JsonNode fc = part.get("functionCall");
                        String fname = fc.path("name").asText();
                        calls.add(new ChatItem.ToolCall(fname + "#" + calls.size(), fname,
                                fc.has("args") ? fc.get("args").toString() : "{}"));
                    }
                }
            }
            JsonNode usage = chunk.path("usageMetadata");
            if (usage.isObject()) {
                promptTokens = usage.path("promptTokenCount").asInt(promptTokens);
                completionTokens = usage.path("candidatesTokenCount").asInt(completionTokens);
            }
        }

        AiCompletion toCompletion() {
            return new AiCompletion(text.toString(), calls,
                    new TokenUsage(promptTokens, completionTokens), finish);
        }
    }
}

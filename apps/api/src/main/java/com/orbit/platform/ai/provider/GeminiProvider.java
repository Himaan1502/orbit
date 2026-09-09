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
 * {@code generateContent} API (not the OpenAI-compat layer, which lags on tool
 * calling). Key is sent as {@code x-goog-api-key}. Its ~1M-token window absorbs
 * the full context bundle every turn (ADR-032 §2).
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
        ObjectNode body = json.createObjectNode();

        body.set("systemInstruction", json.createObjectNode()
                .set("parts", json.createArrayNode().add(json.createObjectNode()
                        .put("text", request.systemPrompt()))));

        body.set("contents", contents(request.history()));

        if (request.tools() != null && !request.tools().isEmpty()) {
            ArrayNode decls = json.createArrayNode();
            for (ToolSpec t : request.tools()) {
                ObjectNode d = json.createObjectNode();
                d.put("name", t.name());
                d.put("description", t.description());
                d.set("parameters", readSchema(t.parametersJson()));
                decls.add(d);
            }
            body.set("tools", json.createArrayNode().add(
                    json.createObjectNode().set("functionDeclarations", decls)));
        }

        ObjectNode gen = json.createObjectNode();
        gen.put("maxOutputTokens", request.maxOutputTokens());
        gen.put("temperature", request.temperature());
        body.set("generationConfig", gen);

        JsonNode resp = post(
                "/v1beta/models/" + props.gemini().model() + ":generateContent",
                body.toString(),
                h -> h.set("x-goog-api-key", props.gemini().apiKey()));

        return parse(resp);
    }

    private ArrayNode contents(List<ChatItem> history) {
        ArrayNode contents = json.createArrayNode();
        for (ChatItem item : history) {
            switch (item) {
                case ChatItem.UserText u -> contents.add(textContent("user", u.text()));
                case ChatItem.ModelText m -> contents.add(textContent("model", m.text()));
                case ChatItem.ModelToolCalls tc -> {
                    ObjectNode c = json.createObjectNode();
                    c.put("role", "model");
                    ArrayNode parts = json.createArrayNode();
                    for (ChatItem.ToolCall call : tc.calls()) {
                        ObjectNode fc = json.createObjectNode();
                        fc.put("name", call.name());
                        fc.set("args", readArgs(call.argumentsJson()));
                        parts.add(json.createObjectNode().set("functionCall", fc));
                    }
                    c.set("parts", parts);
                    contents.add(c);
                }
                case ChatItem.ToolResults tr -> {
                    ObjectNode c = json.createObjectNode();
                    c.put("role", "user");
                    ArrayNode parts = json.createArrayNode();
                    for (ChatItem.ToolResult r : tr.results()) {
                        ObjectNode fr = json.createObjectNode();
                        fr.put("name", r.name());
                        fr.set("response", json.createObjectNode().set("result", readArgs(r.contentJson())));
                        parts.add(json.createObjectNode().set("functionResponse", fr));
                    }
                    c.set("parts", parts);
                    contents.add(c);
                }
                default -> { }
            }
        }
        return contents;
    }

    private ObjectNode textContent(String role, String text) {
        ObjectNode c = json.createObjectNode();
        c.put("role", role);
        c.set("parts", json.createArrayNode().add(json.createObjectNode().put("text", text)));
        return c;
    }

    private AiCompletion parse(JsonNode resp) {
        JsonNode candidates = resp.path("candidates");
        StringBuilder text = new StringBuilder();
        List<ChatItem.ToolCall> calls = new ArrayList<>();
        String finish = null;

        if (candidates.isArray() && !candidates.isEmpty()) {
            JsonNode cand = candidates.get(0);
            finish = cand.path("finishReason").asText(null);
            int idx = 0;
            for (JsonNode part : cand.path("content").path("parts")) {
                if (part.hasNonNull("text")) {
                    text.append(part.get("text").asText());
                } else if (part.has("functionCall")) {
                    JsonNode fc = part.get("functionCall");
                    String fname = fc.path("name").asText();
                    String args = fc.has("args") ? fc.get("args").toString() : "{}";
                    calls.add(new ChatItem.ToolCall(fname + "#" + idx++, fname, args));
                }
            }
        }

        JsonNode usage = resp.path("usageMetadata");
        TokenUsage tokens = new TokenUsage(
                usage.path("promptTokenCount").asInt(0),
                usage.path("candidatesTokenCount").asInt(0));

        return new AiCompletion(text.toString(), calls, tokens, finish);
    }

    private JsonNode readSchema(String schemaJson) {
        try {
            return json.readTree(schemaJson == null || schemaJson.isBlank() ? "{\"type\":\"object\"}" : schemaJson);
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            return json.createObjectNode().put("type", "object");
        }
    }

    private JsonNode readArgs(String argsJson) {
        try {
            return json.readTree(argsJson == null || argsJson.isBlank() ? "{}" : argsJson);
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            return json.createObjectNode().put("_raw", String.valueOf(argsJson));
        }
    }
}

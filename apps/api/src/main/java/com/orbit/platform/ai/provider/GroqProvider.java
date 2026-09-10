package com.orbit.platform.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.orbit.platform.ai.AiProperties;
import com.orbit.platform.ai.model.TokenUsage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groq (ADR-031 §1) — the documented fallback. OpenAI-compatible
 * {@code /chat/completions}; key as {@code Authorization: Bearer}. Reached only
 * when Gemini fails, is rate-limited, or has no key.
 */
@Component
public class GroqProvider extends HttpAiProviderSupport implements AiProvider {

    private final AiProperties props;

    public GroqProvider(ObjectMapper json, AiProperties props) {
        super(json, props.groq().baseUrl(), props.connectTimeoutMs(), props.readTimeoutMs());
        this.props = props;
    }

    @Override
    public String name() {
        return "groq";
    }

    @Override
    public String model() {
        return props.groq().model();
    }

    @Override
    public boolean isConfigured() {
        return props.groqConfigured();
    }

    @Override
    public AiCompletion complete(AiRequest request) {
        JsonNode resp = post("/chat/completions", requestBody(request, false).toString(),
                h -> h.header("authorization", "Bearer " + props.groq().apiKey()));
        Acc acc = new Acc();
        acc.whole(resp);
        return acc.toCompletion();
    }

    @Override
    public AiCompletion completeStream(AiRequest request, TokenSink sink) {
        Acc acc = new Acc();
        postStream("/chat/completions", requestBody(request, true).toString(),
                h -> h.header("authorization", "Bearer " + props.groq().apiKey()),
                payload -> acc.chunk(readTreeSafe(payload), sink));
        return acc.toCompletion();
    }

    // ---------------------------------------------------------------------

    private ObjectNode requestBody(AiRequest request, boolean stream) {
        ObjectNode body = json.createObjectNode();
        body.put("model", props.groq().model());
        body.put("temperature", request.temperature());
        body.put("max_tokens", request.maxOutputTokens());
        body.set("messages", messages(request));
        if (stream) {
            body.put("stream", true);
            body.set("stream_options", json.createObjectNode().put("include_usage", true));
        }
        if (request.tools() != null && !request.tools().isEmpty()) {
            ArrayNode tools = json.createArrayNode();
            for (ToolSpec t : request.tools()) {
                tools.add(json.createObjectNode().put("type", "function").set("function",
                        json.createObjectNode()
                                .put("name", t.name())
                                .put("description", t.description())
                                .set("parameters", readTreeSafe(
                                        t.parametersJson() == null || t.parametersJson().isBlank()
                                                ? "{\"type\":\"object\"}" : t.parametersJson()))));
            }
            body.set("tools", tools);
            body.put("tool_choice", "auto");
        }
        return body;
    }

    private ArrayNode messages(AiRequest request) {
        ArrayNode messages = json.createArrayNode();
        messages.add(json.createObjectNode().put("role", "system").put("content", request.systemPrompt()));
        for (ChatItem item : request.history()) {
            switch (item) {
                case ChatItem.UserText u ->
                        messages.add(json.createObjectNode().put("role", "user").put("content", u.text()));
                case ChatItem.ModelText m ->
                        messages.add(json.createObjectNode().put("role", "assistant").put("content", m.text()));
                case ChatItem.ModelToolCalls tc -> {
                    ObjectNode msg = json.createObjectNode().put("role", "assistant");
                    msg.putNull("content");
                    ArrayNode calls = json.createArrayNode();
                    for (ChatItem.ToolCall call : tc.calls()) {
                        calls.add(json.createObjectNode()
                                .put("id", call.id())
                                .put("type", "function")
                                .set("function", json.createObjectNode()
                                        .put("name", call.name())
                                        .put("arguments", call.argumentsJson() == null ? "{}" : call.argumentsJson())));
                    }
                    msg.set("tool_calls", calls);
                    messages.add(msg);
                }
                case ChatItem.ToolResults tr -> {
                    for (ChatItem.ToolResult r : tr.results()) {
                        messages.add(json.createObjectNode()
                                .put("role", "tool")
                                .put("tool_call_id", r.id())
                                .put("content", r.contentJson() == null ? "{}" : r.contentJson()));
                    }
                }
                default -> { }
            }
        }
        return messages;
    }

    /** Accumulates OpenAI-style chunks (streamed deltas or one whole response). */
    private final class Acc {
        private final StringBuilder text = new StringBuilder();
        private final Map<Integer, ToolCallDraft> toolDrafts = new LinkedHashMap<>();
        private int promptTokens;
        private int completionTokens;
        private String finish;

        void whole(JsonNode resp) {
            JsonNode choices = resp.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode choice = choices.get(0);
                finish = choice.path("finish_reason").asText(null);
                JsonNode message = choice.path("message");
                if (message.hasNonNull("content")) {
                    text.append(message.get("content").asText());
                }
                int i = 0;
                for (JsonNode call : message.path("tool_calls")) {
                    ToolCallDraft d = toolDrafts.computeIfAbsent(i++, k -> new ToolCallDraft());
                    d.id = call.path("id").asText(d.id);
                    d.name = call.path("function").path("name").asText(d.name);
                    d.args.append(call.path("function").path("arguments").asText(""));
                }
            }
            usage(resp.path("usage"));
        }

        void chunk(JsonNode chunk, TokenSink sink) {
            for (JsonNode choice : chunk.path("choices")) {
                if (choice.hasNonNull("finish_reason")) {
                    finish = choice.get("finish_reason").asText();
                }
                JsonNode delta = choice.path("delta");
                if (delta.hasNonNull("content")) {
                    String d = delta.get("content").asText();
                    text.append(d);
                    if (!d.isEmpty()) {
                        sink.onToken(d);
                    }
                }
                for (JsonNode tc : delta.path("tool_calls")) {
                    int idx = tc.path("index").asInt(0);
                    ToolCallDraft d = toolDrafts.computeIfAbsent(idx, k -> new ToolCallDraft());
                    if (tc.hasNonNull("id")) {
                        d.id = tc.get("id").asText();
                    }
                    JsonNode fn = tc.path("function");
                    if (fn.hasNonNull("name")) {
                        d.name = fn.get("name").asText();
                    }
                    if (fn.hasNonNull("arguments")) {
                        d.args.append(fn.get("arguments").asText());
                    }
                }
            }
            usage(chunk.path("usage"));
        }

        private void usage(JsonNode usage) {
            if (usage != null && usage.isObject()) {
                promptTokens = usage.path("prompt_tokens").asInt(promptTokens);
                completionTokens = usage.path("completion_tokens").asInt(completionTokens);
            }
        }

        AiCompletion toCompletion() {
            List<ChatItem.ToolCall> calls = new ArrayList<>();
            int i = 0;
            for (ToolCallDraft d : toolDrafts.values()) {
                String id = d.id != null ? d.id : (d.name == null ? "call#" : d.name) + "#" + i++;
                calls.add(new ChatItem.ToolCall(id, d.name == null ? "" : d.name,
                        d.args.length() == 0 ? "{}" : d.args.toString()));
            }
            return new AiCompletion(text.toString(), calls,
                    new TokenUsage(promptTokens, completionTokens), finish);
        }
    }

    private static final class ToolCallDraft {
        private String id;
        private String name;
        private final StringBuilder args = new StringBuilder();
    }
}

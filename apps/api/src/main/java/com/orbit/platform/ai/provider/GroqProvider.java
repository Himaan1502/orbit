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
        ObjectNode body = json.createObjectNode();
        body.put("model", props.groq().model());
        body.put("temperature", request.temperature());
        body.put("max_tokens", request.maxOutputTokens());
        body.set("messages", messages(request));

        if (request.tools() != null && !request.tools().isEmpty()) {
            ArrayNode tools = json.createArrayNode();
            for (ToolSpec t : request.tools()) {
                ObjectNode fn = json.createObjectNode();
                fn.put("name", t.name());
                fn.put("description", t.description());
                fn.set("parameters", readNode(t.parametersJson(), "{\"type\":\"object\"}"));
                tools.add(json.createObjectNode().put("type", "function").set("function", fn));
            }
            body.set("tools", tools);
            body.put("tool_choice", "auto");
        }

        JsonNode resp = post("/chat/completions", body.toString(),
                h -> h.setBearerAuth(props.groq().apiKey()));

        return parse(resp);
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
                    ObjectNode msg = json.createObjectNode();
                    msg.put("role", "assistant");
                    msg.putNull("content");
                    ArrayNode calls = json.createArrayNode();
                    for (ChatItem.ToolCall call : tc.calls()) {
                        ObjectNode c = json.createObjectNode();
                        c.put("id", call.id());
                        c.put("type", "function");
                        c.set("function", json.createObjectNode()
                                .put("name", call.name())
                                .put("arguments", call.argumentsJson() == null ? "{}" : call.argumentsJson()));
                        calls.add(c);
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

    private AiCompletion parse(JsonNode resp) {
        JsonNode choices = resp.path("choices");
        StringBuilder text = new StringBuilder();
        List<ChatItem.ToolCall> calls = new ArrayList<>();
        String finish = null;

        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode choice = choices.get(0);
            finish = choice.path("finish_reason").asText(null);
            JsonNode message = choice.path("message");
            if (message.hasNonNull("content")) {
                text.append(message.get("content").asText());
            }
            for (JsonNode call : message.path("tool_calls")) {
                JsonNode fn = call.path("function");
                calls.add(new ChatItem.ToolCall(
                        call.path("id").asText(fn.path("name").asText() + "#" + calls.size()),
                        fn.path("name").asText(),
                        fn.path("arguments").asText("{}")));
            }
        }

        JsonNode usage = resp.path("usage");
        TokenUsage tokens = new TokenUsage(
                usage.path("prompt_tokens").asInt(0),
                usage.path("completion_tokens").asInt(0));

        return new AiCompletion(text.toString(), calls, tokens, finish);
    }

    private JsonNode readNode(String raw, String fallback) {
        try {
            return json.readTree(raw == null || raw.isBlank() ? fallback : raw);
        } catch (com.fasterxml.jackson.core.JacksonException e) {
            try {
                return json.readTree(fallback);
            } catch (com.fasterxml.jackson.core.JacksonException ignored) {
                return json.createObjectNode();
            }
        }
    }
}

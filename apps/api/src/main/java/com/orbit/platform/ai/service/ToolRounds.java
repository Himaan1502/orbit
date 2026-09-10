package com.orbit.platform.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.orbit.platform.ai.provider.ChatItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool-round mechanics shared by the blocking and streaming turn loops: split a
 * batch of tool calls into read vs mutation, dispatch the read tools, and strip
 * explicit {@code null} args before a call re-enters the history (some providers
 * strictly re-validate the assistant's prior {@code tool_calls}).
 */
@Component
class ToolRounds {

    record Classified(List<ChatItem.ToolCall> read, List<ChatItem.ToolCall> mutation) {
    }

    private final ObjectMapper json;
    private final ToolRegistry tools;
    private final ProviderGateway gateway;

    ToolRounds(ObjectMapper json, ToolRegistry tools, ProviderGateway gateway) {
        this.json = json;
        this.tools = tools;
        this.gateway = gateway;
    }

    Classified classify(List<ChatItem.ToolCall> calls) {
        List<ChatItem.ToolCall> read = new ArrayList<>();
        List<ChatItem.ToolCall> mutation = new ArrayList<>();
        for (ChatItem.ToolCall call : normalise(calls)) {
            if (tools.isKnown(call.name()) && tools.isMutation(call.name())) {
                mutation.add(call);
            } else {
                read.add(call);   // unknown tools land here → dispatched to an error result (allow-list)
            }
        }
        return new Classified(read, mutation);
    }

    List<ChatItem.ToolResult> executeReads(List<ChatItem.ToolCall> readCalls) {
        List<ChatItem.ToolResult> out = new ArrayList<>();
        for (ChatItem.ToolCall call : readCalls) {
            boolean known = tools.isKnown(call.name());
            String result = known ? tools.dispatchRead(call)
                    : "{\"error\":\"unknown tool '" + call.name() + "'\"}";
            gateway.countTool(call.name(), known ? "read" : "unknown");
            out.add(new ChatItem.ToolResult(call.id(), call.name(), result));
        }
        return out;
    }

    List<ChatItem.ToolCall> normalise(List<ChatItem.ToolCall> calls) {
        List<ChatItem.ToolCall> out = new ArrayList<>();
        for (ChatItem.ToolCall call : calls) {
            String args = call.argumentsJson();
            try {
                JsonNode node = json.readTree(args == null || args.isBlank() ? "{}" : args);
                if (node.isObject()) {
                    ObjectNode obj = (ObjectNode) node;
                    List<String> nullKeys = new ArrayList<>();
                    obj.fieldNames().forEachRemaining(k -> {
                        if (obj.get(k).isNull()) {
                            nullKeys.add(k);
                        }
                    });
                    nullKeys.forEach(obj::remove);
                    args = obj.toString();
                }
            } catch (com.fasterxml.jackson.core.JacksonException ignored) {
                // leave the raw args as-is
            }
            out.add(new ChatItem.ToolCall(call.id(), call.name(), args));
        }
        return out;
    }

    /**
     * A tool-call round rendered as plain-text history rather than a structured
     * {@code functionCall} replay. Provider tool-call formats have proven fragile
     * on replay (Groq strict-null validation, Gemini {@code thought_signature}
     * requirements); a prose transcript works uniformly and the model still reads
     * the results and continues.
     */
    static ChatItem.ModelText callsAsText(List<ChatItem.ToolCall> calls) {
        String s = calls.stream()
                .map(c -> c.name() + "(" + (c.argumentsJson() == null ? "{}" : c.argumentsJson()) + ")")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return new ChatItem.ModelText("[I called: " + s + "]");
    }

    static ChatItem.UserText resultsAsText(List<ChatItem.ToolResult> results) {
        StringBuilder sb = new StringBuilder("Tool results:\n");
        for (ChatItem.ToolResult r : results) {
            sb.append("• ").append(r.name()).append(" → ").append(r.contentJson()).append('\n');
        }
        return new ChatItem.UserText(sb.toString());
    }

    /** The tool-result note fed back to the model after a mutation call. */
    static String mutationNote(String toolName, List<String> rejected) {
        String reject = rejected.stream().filter(s -> s.startsWith(toolName + ":")).findFirst().orElse(null);
        return reject != null
                ? "REJECTED — " + reject
                : "PROPOSED — surfaced to the user as a confirmable action. Do NOT call it again.";
    }
}

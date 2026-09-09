package com.orbit.platform.ai.provider;

import java.util.List;

/**
 * Provider-neutral conversation history passed to an {@link AiProvider}. Each
 * adapter translates this to its own wire format. Within one OSCAR turn the list
 * grows as: prior {@link UserText}/{@link ModelText} pairs, then this turn's
 * {@link UserText}, then (if the model called read tools)
 * {@link ModelToolCalls} + {@link ToolResults} rounds.
 */
public sealed interface ChatItem {

    record UserText(String text) implements ChatItem { }

    record ModelText(String text) implements ChatItem { }

    record ModelToolCalls(List<ToolCall> calls) implements ChatItem { }

    record ToolResults(List<ToolResult> results) implements ChatItem { }

    /** A tool invocation the model asked for. {@code argumentsJson} is a JSON object string. */
    record ToolCall(String id, String name, String argumentsJson) { }

    /** The backend's answer to a {@link ToolCall}. {@code contentJson} is a JSON string. */
    record ToolResult(String id, String name, String contentJson) { }
}

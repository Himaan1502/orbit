package com.orbit.platform.ai.provider;

import com.orbit.platform.ai.model.TokenUsage;

import java.util.List;

/**
 * One {@link AiProvider} response. Either {@code text} is present (a final
 * answer), or {@code toolCalls} is non-empty (the model wants tools run), or
 * both. {@code usage} feeds the per-message {@code token_usage} and the metrics.
 */
public record AiCompletion(
        String text,
        List<ChatItem.ToolCall> toolCalls,
        TokenUsage usage,
        String finishReason) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public boolean hasText() {
        return text != null && !text.isBlank();
    }
}

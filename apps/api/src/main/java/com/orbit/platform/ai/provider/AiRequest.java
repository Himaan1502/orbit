package com.orbit.platform.ai.provider;

import java.util.List;

/**
 * A single call to an {@link AiProvider}. Provider-neutral; each adapter maps it
 * to its own request body (ADR-031 §1).
 *
 * @param systemPrompt    the versioned OSCAR system prompt ({@code oscar-v1})
 * @param history         the conversation so far + this turn's tool rounds
 * @param tools           tool declarations (read + mutation); may be empty
 * @param maxOutputTokens per-turn output budget (ADR-031 §3)
 * @param temperature     low — OSCAR is operational, not creative
 */
public record AiRequest(
        String systemPrompt,
        List<ChatItem> history,
        List<ToolSpec> tools,
        int maxOutputTokens,
        double temperature) {
}

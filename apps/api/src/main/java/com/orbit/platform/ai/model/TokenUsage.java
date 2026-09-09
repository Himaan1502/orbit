package com.orbit.platform.ai.model;

/**
 * Prompt / completion token counts for one assistant message (Blueprint §18
 * {@code token_usage} JSONB). Stored for future cost controls (Blueprint §28);
 * nothing in M7 acts on it (ADR-031 §3).
 */
public record TokenUsage(int promptTokens, int completionTokens) {

    public static final TokenUsage ZERO = new TokenUsage(0, 0);

    public TokenUsage plus(TokenUsage other) {
        return new TokenUsage(promptTokens + other.promptTokens,
                completionTokens + other.completionTokens);
    }

    public int total() {
        return promptTokens + completionTokens;
    }
}

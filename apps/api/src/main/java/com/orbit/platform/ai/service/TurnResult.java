package com.orbit.platform.ai.service;

import com.orbit.platform.ai.model.TokenUsage;

/**
 * The outcome of one {@link AiService#respond(AiTurn)} call. {@code persistable}
 * is {@code false} for a degraded ({@code unavailable}) turn and for a
 * safe-fallback (unparsable) turn — those are shown to the user but not written
 * to {@code ai_messages} (ADR-032 §1/§6).
 */
public record TurnResult(
        OscarResponse response,
        boolean persistable,
        String assistantText,
        String provider,
        String model,
        String promptVersion,
        TokenUsage usage) {

    public static TurnResult notPersisted(OscarResponse response, String promptVersion) {
        return new TurnResult(response, false, response.message(), null, null, promptVersion, TokenUsage.ZERO);
    }
}

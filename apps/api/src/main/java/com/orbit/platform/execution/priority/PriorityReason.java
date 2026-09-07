package com.orbit.platform.execution.priority;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The explainable breakdown behind a task's {@code priority_score}
 * (Blueprint §10 "explainable factors, not an opaque model response";
 * PRD §6 "must be explainable to the user"). Serialised to the
 * {@code tasks.priority_reason} JSONB column and rendered verbatim by the UI's
 * "Why this?" view.
 */
public record PriorityReason(
        double score,
        String engineVersion,
        String weightsFingerprint,
        Instant computedAt,
        Map<String, Factor> factors) {

    /** One weighted input. {@code contribution == value * weight}. */
    public record Factor(double value, double weight, double contribution, String source, String detail) {
    }

    public static PriorityReason of(double score, String engineVersion, String fingerprint,
                                    Instant computedAt, Map<String, Factor> factors) {
        return new PriorityReason(round(score), engineVersion, fingerprint, computedAt,
                new LinkedHashMap<>(factors));
    }

    private static double round(double v) {
        return Math.round(v * 1_000_000d) / 1_000_000d;
    }
}

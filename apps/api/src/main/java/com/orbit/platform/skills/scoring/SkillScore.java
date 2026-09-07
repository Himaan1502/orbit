package com.orbit.platform.skills.scoring;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The output of {@link SkillScoringEngine} — the overall 0–1 capability estimate
 * plus the per-dimension breakdown behind it (UX §21 "The UI must show the
 * dimensions behind the score"; PRD §2.7 "an indicator, not the objective").
 */
public record SkillScore(
        double overall,
        String weightsFingerprint,
        Confidence confidence,
        Map<String, Component> components) {

    /** How trustworthy the inputs are (ADR-017 §4). */
    public enum Confidence {
        SELF_REPORTED,
        PARTIALLY_VERIFIED,
        VERIFIED;

        public String value() {
            return name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        }
    }

    public record Component(double value, double weight, double contribution) {
    }

    static SkillScore of(double overall, String fingerprint, Confidence confidence,
                         Map<String, Component> components) {
        return new SkillScore(round(overall), fingerprint, confidence, new LinkedHashMap<>(components));
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}

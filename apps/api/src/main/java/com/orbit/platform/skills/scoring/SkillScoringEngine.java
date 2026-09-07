package com.orbit.platform.skills.scoring;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Skill Scoring v1 (Blueprint §23, ADR-017). Deterministic weighted sum over the
 * five 0–1 dimensions; {@link #score} is a pure function of its input.
 */
@Component
public class SkillScoringEngine {

    private final SkillScoringWeights weights;

    public SkillScoringEngine(SkillScoringWeights weights) {
        this.weights = weights;
    }

    public SkillScore score(SkillDimensions d, SkillScore.Confidence confidence) {
        Map<String, SkillScore.Component> components = new LinkedHashMap<>();
        add(components, "knowledge", d.knowledge(), weights.getKnowledge());
        add(components, "practical", d.practical(), weights.getPractical());
        add(components, "evidence", d.evidence(), weights.getEvidence());
        add(components, "interview", d.interview(), weights.getInterview());
        add(components, "production", d.production(), weights.getProduction());

        double overall = components.values().stream()
                .mapToDouble(SkillScore.Component::contribution).sum();
        return SkillScore.of(clamp(overall), weights.fingerprint(), confidence, components);
    }

    /** Just the number — used where the breakdown is not needed (readiness aggregation). */
    public double overall(SkillDimensions d) {
        return score(d, SkillScore.Confidence.SELF_REPORTED).overall();
    }

    private static void add(Map<String, SkillScore.Component> map, String name, double value, double weight) {
        double v = clamp(value);
        map.put(name, new SkillScore.Component(round(v), round(weight), round(v * weight)));
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static double round(double v) {
        return Math.round(v * 1_000_000d) / 1_000_000d;
    }
}

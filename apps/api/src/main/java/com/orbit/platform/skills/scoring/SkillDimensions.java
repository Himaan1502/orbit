package com.orbit.platform.skills.scoring;

/**
 * The five 0–1 capability dimensions of a skill assessment (Blueprint §23,
 * PRD §4.5). A pure value object — no I/O.
 */
public record SkillDimensions(
        double knowledge,
        double practical,
        double evidence,
        double interview,
        double production) {

    public SkillDimensions {
        knowledge = clamp(knowledge);
        practical = clamp(practical);
        evidence = clamp(evidence);
        interview = clamp(interview);
        production = clamp(production);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}

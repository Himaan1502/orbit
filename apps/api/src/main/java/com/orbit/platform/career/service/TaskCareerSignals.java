package com.orbit.platform.career.service;

import java.util.UUID;

/**
 * Application port the {@code execution} domain uses to get real values for the
 * Priority Engine's career/skill factors (ADR-014 factor-provider swap,
 * ADR-018 / ADR-019). Each field is {@code null} when it cannot be computed for
 * the task (e.g. no skill attached, no career target) — the engine then uses its
 * existing neutral / proxy default, so the formula and weights never change.
 */
public interface TaskCareerSignals {

    Signals forTask(UUID taskSkillId, UUID objectiveCareerTargetId, UUID userId);

    record Signals(Double careerRelevance, Double skillGap, Double dependencyImpact, Double interviewValue) {
        public static Signals none() {
            return new Signals(null, null, null, null);
        }
    }
}

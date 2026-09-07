package com.orbit.platform.execution.priority;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Everything {@link PriorityEngine} needs to score one task — a pure value
 * object, assembled by the caller. No entities, no repositories.
 *
 * <p>The four {@code *Signal} fields are the real career/skill inputs
 * (ADR-014 factor-provider swap, ADR-018/019). Each is {@code null} when it
 * cannot be computed for this task — the engine then falls back to its
 * Milestone-2 neutral / proxy default, so the formula and weights are unchanged.
 *
 * @param dueAt                    the task's own deadline, or {@code null}
 * @param objectiveTargetDate      the parent objective's target date, or {@code null}
 * @param objectivePriority        the parent objective's manual priority, 1..5 (careerRelevance proxy fallback)
 * @param estimatedMinutes         the task's effort estimate, or {@code null}
 * @param availableDailyMinutes    the user's daily budget
 * @param resolvedTaskCount        the user's total done+skipped tasks
 * @param doneTaskCount            of those, how many are done
 * @param careerRelevanceSignal    real careerRelevance 0..1, or {@code null}
 * @param skillGapSignal           real skillGap 0..1, or {@code null}
 * @param dependencyImpactSignal   real dependencyImpact 0..1, or {@code null}
 * @param interviewValueSignal     real interviewValue 0..1, or {@code null}
 * @param referenceInstant         "now" for urgency — explicit so scoring is reproducible
 */
public record PriorityInput(
        Instant dueAt,
        LocalDate objectiveTargetDate,
        int objectivePriority,
        Integer estimatedMinutes,
        int availableDailyMinutes,
        long resolvedTaskCount,
        long doneTaskCount,
        Double careerRelevanceSignal,
        Double skillGapSignal,
        Double dependencyImpactSignal,
        Double interviewValueSignal,
        Instant referenceInstant) {
}

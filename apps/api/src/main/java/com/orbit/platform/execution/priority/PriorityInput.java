package com.orbit.platform.execution.priority;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Everything {@link PriorityEngine} needs to score one task — a pure value
 * object, assembled by the caller. No entities, no repositories.
 *
 * @param dueAt                  the task's own deadline, or {@code null}
 * @param objectiveTargetDate    the parent objective's target date, or {@code null}
 * @param objectivePriority      the parent objective's manual priority, 1..5 (M2 careerRelevance proxy)
 * @param estimatedMinutes       the task's effort estimate, or {@code null}
 * @param availableDailyMinutes  the user's daily budget (profile; default applied by the caller)
 * @param resolvedTaskCount      the user's total done+skipped tasks (for historicalCompletionProbability)
 * @param doneTaskCount          of those, how many are done
 * @param referenceInstant       "now" for urgency — explicit so scoring is reproducible
 */
public record PriorityInput(
        Instant dueAt,
        LocalDate objectiveTargetDate,
        int objectivePriority,
        Integer estimatedMinutes,
        int availableDailyMinutes,
        long resolvedTaskCount,
        long doneTaskCount,
        Instant referenceInstant) {
}

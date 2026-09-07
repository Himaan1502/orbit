package com.orbit.platform.objectives.service;

import com.orbit.platform.objectives.model.MilestoneStatus;
import com.orbit.platform.objectives.model.ObjectiveStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Pure progress rollup (ADR-016 §3). Takes only status tokens — no entities, no
 * I/O, no cross-domain coupling — so it is trivially unit-tested. Callers pass
 * the statuses of the objective's non-deleted milestones and tasks.
 *
 * <p>{@code taskDone} / {@code taskTerminal} are counts rather than a list of a
 * foreign domain's enum, keeping this class free of an {@code execution} import.
 */
public final class ProgressCalculator {

    private ProgressCalculator() {
    }

    public record ObjectiveRollup(BigDecimal progress, ObjectiveStatus derivedStatus) {
    }

    /**
     * @param milestoneStatuses statuses of the objective's non-deleted milestones
     * @param taskCount          number of the objective's non-deleted tasks
     * @param taskDone           of those, how many are {@code done}
     * @param taskTerminal       of those, how many are {@code done} or {@code skipped}
     * @param currentStatus      the objective's current status (drives auto active/completed)
     */
    public static ObjectiveRollup forObjective(List<MilestoneStatus> milestoneStatuses,
                                               int taskCount, long taskDone, long taskTerminal,
                                               ObjectiveStatus currentStatus) {
        BigDecimal progress = fractionOf(milestoneStatuses, taskCount, taskDone, taskTerminal);

        ObjectiveStatus derived = currentStatus;
        if (currentStatus == ObjectiveStatus.ACTIVE && progress.compareTo(BigDecimal.ONE) >= 0) {
            derived = ObjectiveStatus.COMPLETED;
        } else if (currentStatus == ObjectiveStatus.COMPLETED && progress.compareTo(BigDecimal.ONE) < 0) {
            derived = ObjectiveStatus.ACTIVE;
        }
        return new ObjectiveRollup(progress, derived);
    }

    private static BigDecimal fractionOf(List<MilestoneStatus> milestoneStatuses,
                                         int taskCount, long taskDone, long taskTerminal) {
        if (!milestoneStatuses.isEmpty()) {
            long completed = milestoneStatuses.stream().filter(s -> s == MilestoneStatus.COMPLETED).count();
            return fraction(completed, milestoneStatuses.size());
        }
        if (taskCount > 0) {
            if (taskDone == 0) {
                return BigDecimal.ZERO;                  // an all-skipped objective is not "progressing"
            }
            return fraction(taskTerminal, taskCount);
        }
        return BigDecimal.ZERO;
    }

    /** Milestone progress for the API response (not persisted): done / total. */
    public static BigDecimal milestoneProgress(int taskCount, long taskDone) {
        return taskCount == 0 ? BigDecimal.ZERO : fraction(taskDone, taskCount);
    }

    private static BigDecimal fraction(long numerator, long denominator) {
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 3, RoundingMode.HALF_UP);
    }
}

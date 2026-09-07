package com.orbit.platform.objectives.service;

import com.orbit.platform.objectives.model.MilestoneStatus;
import com.orbit.platform.objectives.model.ObjectiveStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Pure progress rollup (ADR-016 §3). Takes only status counts — no entities, no
 * I/O, no cross-domain coupling — so it is trivially unit-tested.
 *
 * <p>Each milestone is one unit of progress; all of an objective's <em>loose</em>
 * tasks (no milestone) count together as one additional synthetic unit.
 */
public final class ProgressCalculator {

    private ProgressCalculator() {
    }

    public record ObjectiveRollup(BigDecimal progress, ObjectiveStatus derivedStatus) {
    }

    /** A tally over a set of non-deleted tasks. */
    public record TaskCounts(int total, long done, long terminal) {
        public static TaskCounts none() {
            return new TaskCounts(0, 0, 0);
        }
    }

    /**
     * @param milestoneStatuses statuses of the objective's non-deleted milestones
     * @param looseTasks         tally over tasks with no milestone
     * @param currentStatus      the objective's current status (drives auto active/completed)
     */
    public static ObjectiveRollup forObjective(List<MilestoneStatus> milestoneStatuses,
                                               TaskCounts looseTasks,
                                               ObjectiveStatus currentStatus) {
        BigDecimal progress = fraction(milestoneStatuses, looseTasks);

        ObjectiveStatus derived = currentStatus;
        if (currentStatus == ObjectiveStatus.ACTIVE && progress.compareTo(BigDecimal.ONE) >= 0) {
            derived = ObjectiveStatus.COMPLETED;
        } else if (currentStatus == ObjectiveStatus.COMPLETED && progress.compareTo(BigDecimal.ONE) < 0) {
            derived = ObjectiveStatus.ACTIVE;
        }
        return new ObjectiveRollup(progress, derived);
    }

    private static BigDecimal fraction(List<MilestoneStatus> milestoneStatuses, TaskCounts loose) {
        long completedMilestones =
                milestoneStatuses.stream().filter(s -> s == MilestoneStatus.COMPLETED).count();
        int units = milestoneStatuses.size();

        double looseUnit = 0;
        if (loose.total() > 0) {
            units += 1;
            // An all-skipped set is not "progressing"; needs at least one `done`.
            looseUnit = loose.done() == 0 ? 0.0 : (double) loose.terminal() / loose.total();
        }

        if (units == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf((completedMilestones + looseUnit) / units)
                .setScale(3, RoundingMode.HALF_UP);
    }

    /** Milestone progress for the API response (not persisted): done / total. */
    public static BigDecimal milestoneProgress(int taskCount, long taskDone) {
        return taskCount == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(taskDone)
                        .divide(BigDecimal.valueOf(taskCount), 3, RoundingMode.HALF_UP);
    }
}

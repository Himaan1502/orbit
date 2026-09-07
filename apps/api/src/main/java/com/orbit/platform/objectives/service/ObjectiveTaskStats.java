package com.orbit.platform.objectives.service;

import java.util.Map;
import java.util.UUID;

/**
 * Application port the {@code objectives} domain uses to read task-completion
 * counts owned by the {@code execution} domain (Blueprint §5 — cross-domain
 * communication through explicit interfaces, not by reaching into another
 * domain's repositories). Implemented in {@code execution}.
 */
public interface ObjectiveTaskStats {

    Counts countsForObjective(UUID objectiveId);

    /** A {@code total} / {@code done} / {@code terminal} (done or skipped) tally over non-deleted tasks. */
    record Tally(int total, long done, long terminal) {
        public static Tally empty() {
            return new Tally(0, 0, 0);
        }
    }

    /**
     * @param all         every non-deleted task of the objective
     * @param loose       tasks with no milestone (contribute as one synthetic unit — ADR-016 §3)
     * @param byMilestone per-milestone tallies, keyed by {@code milestone_id}
     */
    record Counts(Tally all, Tally loose, Map<UUID, Tally> byMilestone) {
        public static Counts empty() {
            return new Counts(Tally.empty(), Tally.empty(), Map.of());
        }
    }
}

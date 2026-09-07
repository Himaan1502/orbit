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

    /** {@code total} / {@code done} / {@code terminal} (done or skipped) over non-deleted tasks. */
    record Counts(int total, long done, long terminal, Map<UUID, Counts> byMilestone) {
        public static Counts empty() {
            return new Counts(0, 0, 0, Map.of());
        }
    }
}

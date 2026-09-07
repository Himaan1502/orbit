package com.orbit.platform.objectives.service;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only application port the {@code execution} domain uses to obtain the few
 * objective attributes it needs (for priority scoring and plan filtering)
 * without importing the {@code objectives} entities (Blueprint §5).
 */
public interface ObjectiveLookup {

    Optional<Snapshot> findOwned(UUID objectiveId, UUID userId);

    /** True if the milestone exists, is not deleted, and belongs to that objective. */
    boolean milestoneBelongsTo(UUID milestoneId, UUID objectiveId);

    record Snapshot(UUID id, String title, int priority, LocalDate targetDate, boolean archived) {
    }
}

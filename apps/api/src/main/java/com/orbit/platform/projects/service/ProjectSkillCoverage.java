package com.orbit.platform.projects.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Read port other domains use for a user's demonstrated project coverage per
 * skill — Career Readiness {@code projectCoverage} (ADR-026 §2). The value is
 * {@code Σ demonstrated_contribution} across the user's non-deleted projects,
 * capped at 1.0.
 */
public interface ProjectSkillCoverage {

    Map<UUID, Double> demonstratedBySkill(UUID userId, Collection<UUID> skillIds);

    /**
     * As {@link #demonstratedBySkill} but only from projects completed at or
     * before {@code asOf} (ADR-027 §2, readiness trend). Uses the current stored
     * {@code demonstrated_contribution} — an approximation where that derived
     * value has no history of its own.
     */
    Map<UUID, Double> demonstratedBySkillAsOf(UUID userId, Collection<UUID> skillIds, java.time.Instant asOf);
}

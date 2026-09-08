package com.orbit.platform.projects.service;

import java.util.UUID;

/**
 * Read port the {@code projects} domain uses to derive
 * {@code project_skills.demonstrated_contribution} (ADR-026 §3) — the summed
 * {@code contribution_weight} of evidence linked to <em>both</em> a project and a
 * skill. Owned by {@code projects}; implemented in {@code evidence} (the same
 * inversion as {@link ProjectEvidenceCount}), so {@code projects} never imports
 * the evidence domain.
 */
public interface ProjectSkillEvidenceStrength {

    /** Σ {@code contribution_weight} over evidence linked to both, capped at 1.0. */
    double strength(UUID projectId, UUID skillId);
}

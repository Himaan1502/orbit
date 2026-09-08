package com.orbit.platform.evidence.service;

import java.util.UUID;

/**
 * Read port the {@code projects} domain uses to derive
 * {@code project_skills.demonstrated_contribution} (ADR-026 §3) — the summed
 * {@code contribution_weight} of evidence linked to <em>both</em> a project and a
 * skill.
 */
public interface ProjectSkillEvidenceStrength {

    /** Σ {@code contribution_weight} over evidence linked to both, capped at 1.0. */
    double strength(UUID projectId, UUID skillId);
}

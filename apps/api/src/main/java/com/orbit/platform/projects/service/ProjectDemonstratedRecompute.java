package com.orbit.platform.projects.service;

import java.util.UUID;

/**
 * Port the {@code evidence} domain calls after an evidence ↔ project/skill link
 * changes, so {@code project_skills.demonstrated_contribution} stays derived
 * (ADR-026 §3). Implemented in {@code projects}.
 */
public interface ProjectDemonstratedRecompute {

    void recompute(UUID projectId);
}

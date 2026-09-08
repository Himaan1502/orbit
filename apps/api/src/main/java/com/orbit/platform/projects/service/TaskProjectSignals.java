package com.orbit.platform.projects.service;

import java.util.UUID;

/**
 * Read port the {@code execution} domain uses to compute the Priority Engine's
 * {@code projectImpact} factor (ADR-014 swap, ADR-026 §4) without importing the
 * {@code projects} entities (Blueprint §5). Implemented in {@code projects}.
 */
public interface TaskProjectSignals {

    /**
     * {@code projectImpact} 0..1 for a task, or {@code null} when the task has no
     * project (the engine then keeps its neutral 0.5 — unchanged from M2).
     * Rewards <em>working on</em> an active project via
     * {@code expected_contribution}; it does not wait for the project to finish.
     */
    Double projectImpact(UUID taskProjectId, UUID taskSkillId, UUID userId);
}

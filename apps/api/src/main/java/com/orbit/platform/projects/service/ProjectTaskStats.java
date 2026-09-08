package com.orbit.platform.projects.service;

import com.orbit.platform.projects.progress.ProjectProgressCalculator.TaskTally;

import java.util.UUID;

/**
 * Application port the {@code projects} domain uses to read the task-completion
 * tally owned by {@code execution} (Blueprint §5). Implemented in
 * {@code execution}; consumed here for derived project progress (ADR-024 §6).
 */
public interface ProjectTaskStats {

    TaskTally forProject(UUID projectId);
}

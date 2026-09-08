package com.orbit.platform.execution.service;

import com.orbit.platform.execution.model.Task;
import com.orbit.platform.execution.model.TaskStatus;
import com.orbit.platform.execution.repository.TaskRepository;
import com.orbit.platform.projects.progress.ProjectProgressCalculator.TaskTally;
import com.orbit.platform.projects.service.ProjectTaskStats;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * {@code execution}-side implementation of the {@link ProjectTaskStats} port
 * (Blueprint §5, ADR-024 §6). Total / done tally over a project's non-deleted
 * tasks.
 */
@Component
public class ExecutionProjectTaskStats implements ProjectTaskStats {

    private final TaskRepository tasks;

    public ExecutionProjectTaskStats(TaskRepository tasks) {
        this.tasks = tasks;
    }

    @Override
    @Transactional(readOnly = true)
    public TaskTally forProject(UUID projectId) {
        List<Task> all = tasks.findByProjectIdAndDeletedAtIsNull(projectId);
        int done = (int) all.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        return new TaskTally(all.size(), done);
    }
}

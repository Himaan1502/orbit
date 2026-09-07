package com.orbit.platform.execution.service;

import com.orbit.platform.execution.model.Task;
import com.orbit.platform.execution.model.TaskStatus;
import com.orbit.platform.execution.repository.TaskRepository;
import com.orbit.platform.objectives.service.ObjectiveTaskStats;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code execution}-side implementation of the {@link ObjectiveTaskStats} port
 * (Blueprint §5). Read-only aggregation over an objective's non-deleted tasks,
 * bucketed by milestone.
 */
@Component
public class ExecutionObjectiveTaskStats implements ObjectiveTaskStats {

    private final TaskRepository tasks;

    public ExecutionObjectiveTaskStats(TaskRepository tasks) {
        this.tasks = tasks;
    }

    @Override
    @Transactional(readOnly = true)
    public Counts countsForObjective(UUID objectiveId) {
        List<Task> all = tasks.findByObjectiveIdAndDeletedAtIsNullOrderByCreatedAtAsc(objectiveId);
        if (all.isEmpty()) {
            return Counts.empty();
        }

        Map<UUID, int[]> perMilestone = new HashMap<>();  // [total, done, terminal]
        int total = 0;
        long done = 0;
        long terminal = 0;
        for (Task t : all) {
            total++;
            boolean isDone = t.getStatus() == TaskStatus.DONE;
            boolean isTerminal = t.getStatus().isTerminal();
            if (isDone) {
                done++;
            }
            if (isTerminal) {
                terminal++;
            }
            if (t.getMilestoneId() != null) {
                int[] c = perMilestone.computeIfAbsent(t.getMilestoneId(), k -> new int[3]);
                c[0]++;
                if (isDone) {
                    c[1]++;
                }
                if (isTerminal) {
                    c[2]++;
                }
            }
        }

        Map<UUID, Counts> byMilestone = new HashMap<>();
        perMilestone.forEach((mid, c) -> byMilestone.put(mid, new Counts(c[0], c[1], c[2], Map.of())));
        return new Counts(total, done, terminal, byMilestone);
    }
}

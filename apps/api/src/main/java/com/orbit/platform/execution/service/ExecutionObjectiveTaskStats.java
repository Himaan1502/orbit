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
 * split into per-milestone tallies and a "loose" (no milestone) tally.
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

        int[] total = new int[3];                        // [total, done, terminal] — all
        int[] loose = new int[3];                        // [total, done, terminal] — no milestone
        Map<UUID, int[]> perMilestone = new HashMap<>();

        for (Task t : all) {
            boolean isDone = t.getStatus() == TaskStatus.DONE;
            boolean isTerminal = t.getStatus().isTerminal();
            bump(total, isDone, isTerminal);
            if (t.getMilestoneId() == null) {
                bump(loose, isDone, isTerminal);
            } else {
                bump(perMilestone.computeIfAbsent(t.getMilestoneId(), k -> new int[3]), isDone, isTerminal);
            }
        }

        Map<UUID, Tally> byMilestone = new HashMap<>();
        perMilestone.forEach((mid, c) -> byMilestone.put(mid, tally(c)));
        return new Counts(tally(total), tally(loose), byMilestone);
    }

    private static void bump(int[] c, boolean done, boolean terminal) {
        c[0]++;
        if (done) {
            c[1]++;
        }
        if (terminal) {
            c[2]++;
        }
    }

    private static Tally tally(int[] c) {
        return new Tally(c[0], c[1], c[2]);
    }
}

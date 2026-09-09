package com.orbit.platform.objectives.service;

import com.orbit.platform.objectives.model.Milestone;
import com.orbit.platform.objectives.model.MilestoneStatus;
import com.orbit.platform.objectives.model.Objective;
import com.orbit.platform.objectives.model.ObjectiveProgressEvent;
import com.orbit.platform.objectives.model.ObjectiveStatus;
import com.orbit.platform.objectives.repository.MilestoneRepository;
import com.orbit.platform.objectives.repository.ObjectiveProgressEventRepository;
import com.orbit.platform.objectives.repository.ObjectiveRepository;
import com.orbit.platform.objectives.service.ObjectiveTaskStats.Tally;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Recomputes derived milestone status and objective progress/status (ADR-016 §3)
 * — inline, in the caller's transaction. Invoked whenever a task or milestone
 * changes in a way that could move progress. All writes here stay within the
 * {@code objectives} domain; task counts come through {@link ObjectiveTaskStats}.
 */
@Service
public class ObjectiveProgressService {

    private final ObjectiveRepository objectives;
    private final MilestoneRepository milestones;
    private final ObjectiveTaskStats taskStats;
    private final ObjectiveProgressEventRepository progressEvents;

    public ObjectiveProgressService(ObjectiveRepository objectives, MilestoneRepository milestones,
                                    ObjectiveTaskStats taskStats,
                                    ObjectiveProgressEventRepository progressEvents) {
        this.objectives = objectives;
        this.milestones = milestones;
        this.taskStats = taskStats;
        this.progressEvents = progressEvents;
    }

    @Transactional
    public void recompute(UUID objectiveId) {
        if (objectiveId == null) {
            return;
        }
        Objective objective = objectives.findById(objectiveId).orElse(null);
        if (objective == null) {
            return;
        }

        List<Milestone> ms =
                milestones.findByObjectiveIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(objectiveId);
        ObjectiveTaskStats.Counts counts = taskStats.countsForObjective(objectiveId);

        for (Milestone m : ms) {
            MilestoneStatus derived = deriveMilestoneStatus(m.getStatus(), counts.byMilestone().get(m.getId()));
            if (derived != m.getStatus()) {
                m.setStatus(derived);
                milestones.save(m);
            }
        }

        Tally loose = counts.loose();
        var rollup = ProgressCalculator.forObjective(
                ms.stream().map(Milestone::getStatus).toList(),
                new ProgressCalculator.TaskCounts(loose.total(), loose.done(), loose.terminal()),
                objective.getStatus());

        BigDecimal beforeProgress = objective.getProgress();
        ObjectiveStatus beforeStatus = objective.getStatus();
        objective.setProgress(rollup.progress());
        objective.setStatus(rollup.derivedStatus());
        objectives.save(objective);

        boolean moved = beforeProgress == null
                || beforeProgress.compareTo(rollup.progress()) != 0
                || beforeStatus != rollup.derivedStatus();
        if (moved) {
            progressEvents.save(ObjectiveProgressEvent.of(objectiveId, objective.getUserId(),
                    rollup.progress(), rollup.derivedStatus(), Instant.now()));
        }
    }

    /**
     * A milestone with tasks auto-follows them; a milestone with no tasks keeps
     * whatever the user set (manual control).
     */
    private static MilestoneStatus deriveMilestoneStatus(MilestoneStatus current, Tally mc) {
        if (mc == null || mc.total() == 0) {
            return current;
        }
        if (mc.done() >= 1 && mc.terminal() == mc.total()) {
            return MilestoneStatus.COMPLETED;
        }
        if (mc.done() >= 1) {
            return MilestoneStatus.IN_PROGRESS;
        }
        return current;
    }
}

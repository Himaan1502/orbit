package com.orbit.platform.objectives.service;

import com.orbit.platform.objectives.model.Milestone;
import com.orbit.platform.objectives.model.MilestoneStatus;
import com.orbit.platform.objectives.model.Objective;
import com.orbit.platform.objectives.repository.MilestoneRepository;
import com.orbit.platform.objectives.repository.ObjectiveRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public ObjectiveProgressService(ObjectiveRepository objectives, MilestoneRepository milestones,
                                    ObjectiveTaskStats taskStats) {
        this.objectives = objectives;
        this.milestones = milestones;
        this.taskStats = taskStats;
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
            ObjectiveTaskStats.Counts mc = counts.byMilestone().get(m.getId());
            MilestoneStatus derived = deriveMilestoneStatus(m.getStatus(), mc);
            if (derived != m.getStatus()) {
                m.setStatus(derived);
                milestones.save(m);
            }
        }

        var rollup = ProgressCalculator.forObjective(
                ms.stream().map(Milestone::getStatus).toList(),
                counts.total(), counts.done(), counts.terminal(),
                objective.getStatus());

        objective.setProgress(rollup.progress());
        objective.setStatus(rollup.derivedStatus());
        objectives.save(objective);
    }

    /**
     * A milestone with tasks auto-follows them; a milestone with no tasks keeps
     * whatever the user set (manual control).
     */
    private static MilestoneStatus deriveMilestoneStatus(MilestoneStatus current, ObjectiveTaskStats.Counts mc) {
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

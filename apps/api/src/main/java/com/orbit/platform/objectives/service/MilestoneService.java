package com.orbit.platform.objectives.service;

import com.orbit.platform.common.exception.ApiException;
import com.orbit.platform.objectives.model.Milestone;
import com.orbit.platform.objectives.model.MilestoneStatus;
import com.orbit.platform.objectives.repository.MilestoneRepository;
import com.orbit.platform.objectives.web.dto.MilestoneDtos.CreateMilestoneRequest;
import com.orbit.platform.objectives.web.dto.MilestoneDtos.UpdateMilestoneRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Milestone use-cases. Ownership is the parent objective's ownership
 * ({@link ObjectiveService#requireOwned}); a milestone under an objective the
 * caller does not own is answered {@code 404} (ADR-016 §5). Every mutation
 * triggers an inline objective progress recompute.
 */
@Service
public class MilestoneService {

    /** A milestone plus its (non-persisted) task-completion progress. */
    public record MilestoneView(Milestone milestone, BigDecimal progress) {
    }

    private final MilestoneRepository milestones;
    private final ObjectiveService objectives;
    private final ObjectiveProgressService progress;
    private final ObjectiveTaskStats taskStats;

    public MilestoneService(MilestoneRepository milestones, ObjectiveService objectives,
                            ObjectiveProgressService progress, ObjectiveTaskStats taskStats) {
        this.milestones = milestones;
        this.objectives = objectives;
        this.progress = progress;
        this.taskStats = taskStats;
    }

    @Transactional(readOnly = true)
    public List<MilestoneView> list(UUID objectiveId) {
        objectives.requireOwned(objectiveId);
        ObjectiveTaskStats.Counts counts = taskStats.countsForObjective(objectiveId);
        return milestones.findByObjectiveIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(objectiveId)
                .stream().map(m -> view(m, counts)).toList();
    }

    @Transactional(readOnly = true)
    public MilestoneView get(UUID objectiveId, UUID milestoneId) {
        objectives.requireOwned(objectiveId);
        Milestone m = milestones.findByIdAndObjectiveIdAndDeletedAtIsNull(milestoneId, objectiveId)
                .orElseThrow(() -> ApiException.notFound("Milestone not found."));
        return view(m, taskStats.countsForObjective(objectiveId));
    }

    @Transactional
    public MilestoneView create(UUID objectiveId, CreateMilestoneRequest req) {
        objectives.requireOwned(objectiveId);
        int sortOrder = req.sortOrder() != null ? req.sortOrder()
                : (int) milestones.countByObjectiveIdAndDeletedAtIsNull(objectiveId);
        Milestone saved = milestones.save(Milestone.create(
                objectiveId, req.title().strip(), strip(req.description()), req.targetDate(), sortOrder));
        progress.recompute(objectiveId);
        return get(objectiveId, saved.getId());
    }

    @Transactional
    public MilestoneView update(UUID objectiveId, UUID milestoneId, UpdateMilestoneRequest req) {
        Milestone m = get(objectiveId, milestoneId).milestone();
        if (req.title() != null && !req.title().isBlank()) {
            m.setTitle(req.title().strip());
        }
        if (req.description() != null) {
            m.setDescription(strip(req.description()));
        }
        if (req.targetDate() != null) {
            m.setTargetDate(req.targetDate());
        }
        if (req.sortOrder() != null) {
            m.setSortOrder(req.sortOrder());
        }
        if (req.status() != null && !req.status().isBlank()) {
            m.setStatus(MilestoneStatus.fromValue(req.status()));
        }
        milestones.save(m);
        progress.recompute(objectiveId);
        return get(objectiveId, milestoneId);
    }

    @Transactional
    public void delete(UUID objectiveId, UUID milestoneId) {
        Milestone m = get(objectiveId, milestoneId).milestone();
        m.softDelete();
        milestones.save(m);
        progress.recompute(objectiveId);
    }

    private static MilestoneView view(Milestone m, ObjectiveTaskStats.Counts counts) {
        ObjectiveTaskStats.Tally mc = counts.byMilestone().get(m.getId());
        BigDecimal p = mc == null
                ? BigDecimal.ZERO
                : ProgressCalculator.milestoneProgress(mc.total(), mc.done());
        return new MilestoneView(m, p);
    }

    private static String strip(String s) {
        return s == null ? null : s.strip();
    }
}

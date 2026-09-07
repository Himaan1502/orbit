package com.orbit.platform.career.service;

import com.orbit.platform.career.model.CareerTargetSkill;
import com.orbit.platform.career.repository.CareerTargetRepository;
import com.orbit.platform.career.repository.CareerTargetSkillRepository;
import com.orbit.platform.skills.graph.SkillGraph;
import com.orbit.platform.skills.model.SkillAssessment;
import com.orbit.platform.skills.repository.SkillAssessmentRepository;
import com.orbit.platform.skills.service.SkillGraphService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Computes the real career/skill priority factors for a task (ADR-014 swap).
 * Everything degrades to {@code null} → the engine keeps its neutral / proxy
 * default, so a task with no skill and no career target scores exactly as it did
 * in Milestone 2.
 */
@Component
public class CareerTaskSignalsImpl implements TaskCareerSignals {

    /** Assumed gap for a skill that is attached to a task but has never been self-assessed. */
    private static final double UNASSESSED_GAP = 0.7;

    private final CareerTargetRepository targets;
    private final CareerTargetSkillRepository targetSkills;
    private final SkillAssessmentRepository assessments;
    private final SkillGraphService graphService;

    public CareerTaskSignalsImpl(CareerTargetRepository targets, CareerTargetSkillRepository targetSkills,
                                 SkillAssessmentRepository assessments, SkillGraphService graphService) {
        this.targets = targets;
        this.targetSkills = targetSkills;
        this.assessments = assessments;
        this.graphService = graphService;
    }

    @Override
    @Transactional(readOnly = true)
    public Signals forTask(UUID taskSkillId, UUID objectiveCareerTargetId, UUID userId) {
        if (taskSkillId == null && objectiveCareerTargetId == null) {
            return Signals.none();
        }

        Map<UUID, CareerTargetSkill> targetSkillById = Map.of();
        if (objectiveCareerTargetId != null
                && targets.findByIdAndUserIdAndDeletedAtIsNull(objectiveCareerTargetId, userId).isPresent()) {
            targetSkillById = targetSkills.findByCareerTargetId(objectiveCareerTargetId).stream()
                    .collect(Collectors.toMap(CareerTargetSkill::getSkillId, s -> s));
        }
        Set<UUID> targetSkillIds = targetSkillById.keySet();

        Double careerRelevance = null;
        Double interviewValue = null;
        CareerTargetSkill match = taskSkillId == null ? null : targetSkillById.get(taskSkillId);
        if (match != null) {
            careerRelevance = clamp(match.importanceValue());
            interviewValue = clamp(match.interviewWeightValue());
        } else if (!targetSkillById.isEmpty()) {
            // The task has no specific skill but its objective targets a role —
            // use the average importance of that role's skills.
            careerRelevance = clamp(targetSkillById.values().stream()
                    .mapToDouble(CareerTargetSkill::importanceValue).average().orElse(0.5));
        }

        Double skillGap = null;
        if (taskSkillId != null) {
            SkillAssessment latest =
                    assessments.findFirstByUserIdAndSkillIdOrderByAssessedAtDesc(userId, taskSkillId)
                            .orElse(null);
            skillGap = latest != null ? clamp(1 - latest.overall()) : UNASSESSED_GAP;
        }

        Double dependencyImpact = null;
        if (taskSkillId != null && !targetSkillIds.isEmpty()) {
            SkillGraph graph = graphService.graph();
            double di = graph.dependencyImpact(taskSkillId, targetSkillIds);
            if (di >= 0) {
                dependencyImpact = clamp(di);
            }
        }

        return new Signals(careerRelevance, skillGap, dependencyImpact, interviewValue);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}

package com.orbit.platform.career.service;

import com.orbit.platform.career.model.CareerTarget;
import com.orbit.platform.career.model.CareerTargetSkill;
import com.orbit.platform.career.readiness.CareerReadinessCalculator;
import com.orbit.platform.career.readiness.CareerReadinessCalculator.Capability;
import com.orbit.platform.career.readiness.CareerReadinessCalculator.Readiness;
import com.orbit.platform.career.readiness.CareerReadinessCalculator.TargetSkill;
import com.orbit.platform.career.readiness.ReadinessProperties;
import com.orbit.platform.common.exception.ApiException;
import com.orbit.platform.identity.service.CurrentUserService;
import com.orbit.platform.skills.model.Skill;
import com.orbit.platform.skills.model.SkillAssessment;
import com.orbit.platform.skills.repository.SkillRepository;
import com.orbit.platform.skills.service.SkillGraphService;
import com.orbit.platform.skills.service.SkillService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Loads a career target's weighted skill set, the user's latest capability per
 * skill and the skill graph, then defers to the pure
 * {@link CareerReadinessCalculator} (Blueprint §24, ADR-018). Reproducible from
 * stored data (Blueprint §43).
 */
@Service
public class CareerReadinessService {

    private final CareerTargetService targets;
    private final SkillRepository skills;
    private final SkillService skillService;
    private final SkillGraphService graphService;
    private final CurrentUserService currentUser;
    private final ReadinessProperties properties;

    public CareerReadinessService(CareerTargetService targets, SkillRepository skills,
                                  SkillService skillService, SkillGraphService graphService,
                                  CurrentUserService currentUser, ReadinessProperties properties) {
        this.targets = targets;
        this.skills = skills;
        this.skillService = skillService;
        this.graphService = graphService;
        this.currentUser = currentUser;
        this.properties = properties;
    }

    public record NamedReadiness(UUID careerTargetId, String targetName, Readiness readiness) {
    }

    @Transactional(readOnly = true)
    public NamedReadiness readiness(UUID explicitTargetId) {
        UUID userId = currentUser.currentUserId();
        CareerTarget target = explicitTargetId != null
                ? targets.getMine(explicitTargetId).target()
                : targets.primaryOf(userId);
        if (target == null) {
            throw ApiException.notFound("No career target set.");
        }

        List<CareerTargetSkill> cts = targets.getMine(target.getId()).skills();
        Map<UUID, String> names = skills.findAll().stream()
                .collect(Collectors.toMap(Skill::getId, Skill::getCanonicalName));

        List<TargetSkill> targetSkills = cts.stream()
                .map(s -> new TargetSkill(s.getSkillId(), names.getOrDefault(s.getSkillId(), "?"),
                        s.importanceValue(), s.interviewWeightValue()))
                .toList();

        Map<UUID, SkillAssessment> latest = skillService.latestBySkill(userId);
        Map<UUID, Capability> capabilities = latest.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> new Capability(e.getValue().overall(),
                                e.getValue().dimensions().interview())));

        Readiness r = CareerReadinessCalculator.compute(
                targetSkills, capabilities, graphService.graph(), properties);
        return new NamedReadiness(target.getId(), target.getName(), r);
    }
}

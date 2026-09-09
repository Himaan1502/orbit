package com.orbit.platform.career.service;

import com.orbit.platform.career.model.CareerTarget;
import com.orbit.platform.career.model.CareerTargetSkill;
import com.orbit.platform.career.readiness.CareerReadinessCalculator;
import com.orbit.platform.career.readiness.CareerReadinessCalculator.Capability;
import com.orbit.platform.career.readiness.CareerReadinessCalculator.Readiness;
import com.orbit.platform.career.readiness.CareerReadinessCalculator.TargetSkill;
import com.orbit.platform.career.readiness.ReadinessProperties;
import com.orbit.platform.common.exception.ApiException;
import com.orbit.platform.evidence.service.SkillEvidenceSignals;
import com.orbit.platform.identity.service.CurrentUserService;
import com.orbit.platform.projects.service.ProjectSkillCoverage;
import com.orbit.platform.skills.model.Skill;
import com.orbit.platform.skills.model.SkillAssessment;
import com.orbit.platform.skills.repository.SkillRepository;
import com.orbit.platform.skills.service.SkillGraphService;
import com.orbit.platform.skills.service.SkillService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
    private final SkillEvidenceSignals evidenceSignals;
    private final ProjectSkillCoverage projectCoverage;
    private final CurrentUserService currentUser;
    private final ReadinessProperties properties;

    public CareerReadinessService(CareerTargetService targets, SkillRepository skills,
                                  SkillService skillService, SkillGraphService graphService,
                                  SkillEvidenceSignals evidenceSignals, ProjectSkillCoverage projectCoverage,
                                  CurrentUserService currentUser, ReadinessProperties properties) {
        this.targets = targets;
        this.skills = skills;
        this.skillService = skillService;
        this.graphService = graphService;
        this.evidenceSignals = evidenceSignals;
        this.projectCoverage = projectCoverage;
        this.currentUser = currentUser;
        this.properties = properties;
    }

    public record NamedReadiness(UUID careerTargetId, String targetName, Readiness readiness) {
    }

    /** One point on the readiness trend (ADR-027 §2). */
    public record ReadinessPoint(Instant at, double overall, double skillCapability, int assessedSkillCount) {
    }

    public record NamedTrend(UUID careerTargetId, String targetName, List<ReadinessPoint> points) {
    }

    /**
     * The readiness trend for a target over {@code [from, to]} (ADR-027 §2).
     * Sampled at each assessment the user made for one of the target's skills
     * (plus the range endpoints); at each instant the <em>existing</em>
     * {@link CareerReadinessCalculator} is re-run with the as-of capability
     * (exact), evidence strength (by {@code created_at}) and project coverage
     * (by {@code completed_at}). A step function between samples.
     */
    @Transactional(readOnly = true)
    public NamedTrend readinessTrend(UUID explicitTargetId, Instant from, Instant to) {
        return readinessTrendFor(currentUser.currentUserId(), explicitTargetId, from, to);
    }

    /**
     * The readiness trend for an explicit user — the entry point the scheduled
     * Weekly Review job uses (no security context). Ownership: the target must
     * belong to {@code userId}.
     */
    @Transactional(readOnly = true)
    public NamedTrend readinessTrendFor(UUID userId, UUID explicitTargetId, Instant from, Instant to) {
        CareerTarget target = explicitTargetId != null
                ? targets.getMine(explicitTargetId).target()   // ownership-scoped (request path only)
                : targets.primaryOf(userId);
        if (target == null || !target.getUserId().equals(userId)) {
            throw ApiException.notFound("No career target set.");
        }

        List<CareerTargetSkill> cts = targets.skillsOf(target.getId());
        Set<UUID> targetSkillIds = cts.stream().map(CareerTargetSkill::getSkillId).collect(Collectors.toSet());
        Map<UUID, String> names = skills.findAll().stream()
                .collect(Collectors.toMap(Skill::getId, Skill::getCanonicalName));
        List<TargetSkill> targetSkills = cts.stream()
                .map(s -> new TargetSkill(s.getSkillId(), names.getOrDefault(s.getSkillId(), "?"),
                        s.importanceValue(), s.interviewWeightValue()))
                .toList();

        // sample instants: every assessment of a target skill inside the window, plus both endpoints
        TreeSet<Instant> instants = new TreeSet<>();
        instants.add(to);
        for (SkillAssessment a : skillService.assessmentHistory(userId)) {
            if (targetSkillIds.contains(a.getSkillId())
                    && !a.getAssessedAt().isBefore(from) && !a.getAssessedAt().isAfter(to)) {
                instants.add(a.getAssessedAt());
            }
        }
        if (instants.size() == 1) {
            instants.add(from);   // no events in-window — a flat two-point line
        }

        var graph = graphService.graph();
        List<ReadinessPoint> points = new ArrayList<>();
        for (Instant t : instants) {
            Map<UUID, Capability> capabilities = skillService.latestBySkillAsOf(userId, t).entrySet().stream()
                    .filter(e -> targetSkillIds.contains(e.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            e -> new Capability(e.getValue().overall(), e.getValue().dimensions().interview())));
            Map<UUID, Double> evidence = evidenceSignals.strengthBySkillAsOf(userId, targetSkillIds, t);
            Map<UUID, Double> project = projectCoverage.demonstratedBySkillAsOf(userId, targetSkillIds, t);

            Readiness r = CareerReadinessCalculator.compute(
                    targetSkills, capabilities, evidence, project, graph, properties);
            double capComponent = r.componentBreakdown().stream()
                    .filter(c -> "skillCapability".equals(c.component()))
                    .map(c -> c.value() == null ? 0.0 : c.value())
                    .findFirst().orElse(0.0);
            points.add(new ReadinessPoint(t, r.overall(), capComponent, r.assessedSkillCount()));
        }
        return new NamedTrend(target.getId(), target.getName(), points);
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

        Set<UUID> targetSkillIds = cts.stream().map(CareerTargetSkill::getSkillId).collect(Collectors.toSet());
        Map<UUID, Double> evidenceStrength = evidenceSignals.strengthBySkill(userId, targetSkillIds);
        Map<UUID, Double> projectDemonstrated = projectCoverage.demonstratedBySkill(userId, targetSkillIds);

        Readiness r = CareerReadinessCalculator.compute(
                targetSkills, capabilities, evidenceStrength, projectDemonstrated,
                graphService.graph(), properties);
        return new NamedReadiness(target.getId(), target.getName(), r);
    }
}

package com.orbit.platform.skills.service;

import com.orbit.platform.common.exception.ApiException;
import com.orbit.platform.skills.graph.SkillGraph;
import com.orbit.platform.skills.model.Skill;
import com.orbit.platform.skills.model.SkillAssessment;
import com.orbit.platform.skills.repository.SkillAssessmentRepository;
import com.orbit.platform.skills.repository.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read access to the global skill catalog (ADR-020) joined with the current
 * user's latest assessment per skill. The catalog itself is not user-scoped and
 * not editable in Milestone 3.
 */
@Service
public class SkillService {

    private final SkillRepository skills;
    private final SkillAssessmentRepository assessments;
    private final SkillGraphService graphService;

    public SkillService(SkillRepository skills, SkillAssessmentRepository assessments,
                        SkillGraphService graphService) {
        this.skills = skills;
        this.assessments = assessments;
        this.graphService = graphService;
    }

    public record SkillWithCapability(Skill skill, Optional<SkillAssessment> latestAssessment) {
        public Double capability() {
            return latestAssessment.map(SkillAssessment::overall).orElse(null);
        }
    }

    public record SkillNeighbours(List<Neighbour> prerequisites, List<Neighbour> dependents) {
    }

    public record Neighbour(UUID skillId, String name, double strength, Double capability) {
    }

    @Transactional(readOnly = true)
    public List<SkillWithCapability> listForUser(UUID userId) {
        Map<UUID, SkillAssessment> latest = latestBySkill(userId);
        return skills.findByStatusOrderByCategoryAscCanonicalNameAsc("active").stream()
                .map(s -> new SkillWithCapability(s, Optional.ofNullable(latest.get(s.getId()))))
                .toList();
    }

    @Transactional(readOnly = true)
    public SkillWithCapability getForUser(UUID skillId, UUID userId) {
        Skill skill = skills.findById(skillId)
                .orElseThrow(() -> ApiException.notFound("Skill not found."));
        return new SkillWithCapability(skill,
                assessments.findFirstByUserIdAndSkillIdOrderByAssessedAtDesc(userId, skillId));
    }

    @Transactional(readOnly = true)
    public SkillNeighbours neighboursForUser(UUID skillId, UUID userId) {
        if (skills.findById(skillId).isEmpty()) {
            throw ApiException.notFound("Skill not found.");
        }
        SkillGraph graph = graphService.graph();
        Map<UUID, String> names = skills.findAll().stream()
                .collect(Collectors.toMap(Skill::getId, Skill::getCanonicalName));
        Map<UUID, SkillAssessment> latest = latestBySkill(userId);

        Function<SkillGraph.Edge, Neighbour> asPrereq = e -> neighbour(e.prerequisite(), e.strength(), names, latest);
        Function<SkillGraph.Edge, Neighbour> asDependent = e -> neighbour(e.dependent(), e.strength(), names, latest);

        return new SkillNeighbours(
                graph.prerequisites(skillId).stream().map(asPrereq).toList(),
                graph.dependents(skillId).stream().map(asDependent).toList());
    }

    private static Neighbour neighbour(UUID id, double strength, Map<UUID, String> names,
                                       Map<UUID, SkillAssessment> latest) {
        SkillAssessment a = latest.get(id);
        return new Neighbour(id, names.getOrDefault(id, "?"), strength, a == null ? null : a.overall());
    }

    @Transactional(readOnly = true)
    public Map<UUID, SkillAssessment> latestBySkill(UUID userId) {
        Map<UUID, SkillAssessment> latest = new java.util.HashMap<>();
        for (SkillAssessment a : assessments.findByUserIdOrderByAssessedAtDesc(userId)) {
            latest.putIfAbsent(a.getSkillId(), a);   // list is newest-first
        }
        return latest;
    }
}

package com.orbit.platform.roadmaps.service;

import com.orbit.platform.career.service.PrimaryCareerSkillWeights;
import com.orbit.platform.common.exception.ApiException;
import com.orbit.platform.identity.service.CurrentUserService;
import com.orbit.platform.roadmaps.model.Roadmap;
import com.orbit.platform.roadmaps.progress.RoadmapProperties;
import com.orbit.platform.roadmaps.repository.RoadmapRepository;
import com.orbit.platform.roadmaps.repository.RoadmapTopicSkillRepository;
import com.orbit.platform.roadmaps.repository.RoadmapTopicSkillRepository.SkillRoadmapPair;
import com.orbit.platform.skills.graph.SkillGraph;
import com.orbit.platform.skills.model.Skill;
import com.orbit.platform.skills.repository.SkillRepository;
import com.orbit.platform.skills.service.SkillGraphService;
import com.orbit.platform.skills.service.SkillService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Roadmap Intersection (Blueprint §32, PRD §5, ADR-023 §2) — the skills shared
 * across a set of selected roadmaps, ranked so the highest-leverage shared skill
 * is obvious. Signature ORBIT experience.
 */
@Service
public class RoadmapIntersectionService {

    private final RoadmapRepository roadmaps;
    private final RoadmapTopicSkillRepository topicSkills;
    private final SkillRepository skills;
    private final SkillService skillService;
    private final SkillGraphService graphService;
    private final PrimaryCareerSkillWeights careerWeights;
    private final RoadmapAdoptionService adoption;
    private final CurrentUserService currentUser;
    private final RoadmapProperties properties;

    public RoadmapIntersectionService(RoadmapRepository roadmaps, RoadmapTopicSkillRepository topicSkills,
                                      SkillRepository skills, SkillService skillService,
                                      SkillGraphService graphService, PrimaryCareerSkillWeights careerWeights,
                                      RoadmapAdoptionService adoption, CurrentUserService currentUser,
                                      RoadmapProperties properties) {
        this.roadmaps = roadmaps;
        this.topicSkills = topicSkills;
        this.skills = skills;
        this.skillService = skillService;
        this.graphService = graphService;
        this.careerWeights = careerWeights;
        this.adoption = adoption;
        this.currentUser = currentUser;
        this.properties = properties;
    }

    public record RoadmapRef(UUID id, String name, int tier) {
    }

    public record SharedSkill(
            UUID skillId, String name, int roadmapCount, List<RoadmapRef> contributesTo,
            Double capability, Double careerImportance, String careerRelevance,
            double leverage, String recommendedAction) {
    }

    public record Intersection(
            List<RoadmapRef> roadmaps, int sharedSkillCount, List<SharedSkill> sharedSkills) {
    }

    @Transactional(readOnly = true)
    public Intersection compute(List<UUID> requestedRoadmapIds) {
        UUID userId = currentUser.currentUserId();

        List<UUID> ids = (requestedRoadmapIds == null || requestedRoadmapIds.isEmpty())
                ? new ArrayList<>(adoption.adoptedRoadmapIds(userId))
                : requestedRoadmapIds.stream().distinct().toList();

        if (ids.size() < 2) {
            throw ApiException.badRequest(
                    "Select at least two roadmaps to compare (or adopt two and omit the list).");
        }

        Map<UUID, Roadmap> roadmapById = roadmaps.findAllById(ids).stream()
                .collect(Collectors.toMap(Roadmap::getId, r -> r));
        for (UUID id : ids) {
            if (!roadmapById.containsKey(id)) {
                throw ApiException.notFound("Roadmap not found: " + id);
            }
        }

        // skill -> the selected roadmaps that contain it
        Map<UUID, Set<UUID>> roadmapsBySkill = new HashMap<>();
        for (SkillRoadmapPair p : topicSkills.findSkillRoadmapPairs(ids)) {
            roadmapsBySkill.computeIfAbsent(p.getSkillId(), k -> new LinkedHashSet<>()).add(p.getRoadmapId());
        }
        Map<UUID, Set<UUID>> shared = roadmapsBySkill.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        Map<UUID, String> skillNames = skills.findAll().stream()
                .collect(Collectors.toMap(Skill::getId, Skill::getCanonicalName));
        Map<UUID, Double> capability = skillService.latestBySkill(userId).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().overall()));
        Map<UUID, Double> careerImportance = careerWeights.importanceBySkill(userId);
        SkillGraph graph = graphService.graph();

        // leverage inputs over the shared set: importance (career, or a neutral 0.5) and gap (1 - capability)
        Map<UUID, Double> impForLeverage = new HashMap<>();
        Map<UUID, Double> gapForLeverage = new HashMap<>();
        for (UUID s : shared.keySet()) {
            impForLeverage.put(s, careerImportance.getOrDefault(s, 0.5));
            gapForLeverage.put(s, 1 - capability.getOrDefault(s, 0.0));
        }
        double maxLeverage = shared.keySet().stream()
                .mapToDouble(s -> graph.leverage(s, impForLeverage, gapForLeverage)).max().orElse(0);
        double norm = maxLeverage == 0 ? 1 : maxLeverage;

        List<SharedSkill> out = new ArrayList<>();
        for (Map.Entry<UUID, Set<UUID>> e : shared.entrySet()) {
            UUID skillId = e.getKey();
            List<RoadmapRef> contributesTo = e.getValue().stream()
                    .map(roadmapById::get)
                    .sorted(Comparator.comparingInt(Roadmap::getPriorityTier).thenComparing(Roadmap::getName))
                    .map(r -> new RoadmapRef(r.getId(), r.getName(), r.getPriorityTier()))
                    .toList();
            Double cap = capability.get(skillId);
            Double imp = careerImportance.get(skillId);
            out.add(new SharedSkill(
                    skillId, skillNames.getOrDefault(skillId, "?"), e.getValue().size(), contributesTo,
                    cap, imp, relevanceBand(imp),
                    round(graph.leverage(skillId, impForLeverage, gapForLeverage) / norm),
                    recommendedAction(cap)));
        }

        out.sort(Comparator
                .comparingInt(SharedSkill::roadmapCount).reversed()
                .thenComparing(Comparator.comparingDouble(
                        (SharedSkill s) -> s.careerImportance() == null ? -1 : s.careerImportance()).reversed())
                .thenComparing(Comparator.comparingDouble(SharedSkill::leverage).reversed())
                .thenComparing(Comparator.comparingDouble(
                        (SharedSkill s) -> 1 - (s.capability() == null ? 0.0 : s.capability())).reversed()));

        List<RoadmapRef> refs = ids.stream().map(roadmapById::get)
                .sorted(Comparator.comparingInt(Roadmap::getPriorityTier).thenComparing(Roadmap::getName))
                .map(r -> new RoadmapRef(r.getId(), r.getName(), r.getPriorityTier()))
                .toList();

        return new Intersection(refs, out.size(), out);
    }

    private String relevanceBand(Double importance) {
        if (importance == null) {
            return null;
        }
        if (importance >= properties.careerRelevanceHigh()) {
            return "HIGH";
        }
        return importance >= properties.careerRelevanceMedium() ? "MEDIUM" : "LOW";
    }

    private String recommendedAction(Double capability) {
        double c = capability == null ? 0.0 : capability;
        if (c < properties.actionBandBuild()) {
            return "Start with the fundamentals and a first hands-on exercise.";
        }
        if (c < properties.actionBandOperate()) {
            return "Build something non-trivial with it end to end.";
        }
        if (c < properties.actionBandMaintain()) {
            return "Deploy and operate a production-style workload.";
        }
        return "Maintain — you're strong here; keep it warm with real use.";
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}

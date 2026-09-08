package com.orbit.platform.roadmaps.service;

import com.orbit.platform.roadmaps.model.RoadmapTopicSkill;
import com.orbit.platform.roadmaps.progress.RoadmapProgressCalculator;
import com.orbit.platform.roadmaps.progress.RoadmapProgressCalculator.RoadmapProgress;
import com.orbit.platform.roadmaps.progress.RoadmapProgressCalculator.SkillRef;
import com.orbit.platform.roadmaps.progress.RoadmapProgressCalculator.TopicInput;
import com.orbit.platform.roadmaps.progress.RoadmapProperties;
import com.orbit.platform.roadmaps.repository.RoadmapTopicRepository;
import com.orbit.platform.roadmaps.repository.RoadmapTopicSkillRepository;
import com.orbit.platform.skills.model.Skill;
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
 * Loads a roadmap's topics + skill mappings, the user's latest capability per
 * skill and the {@link SkillGraphService} graph, then defers to the pure
 * {@link RoadmapProgressCalculator} (ADR-023 §1). Nothing is stored.
 */
@Service
public class RoadmapProgressService {

    private final RoadmapTopicRepository topics;
    private final RoadmapTopicSkillRepository topicSkills;
    private final SkillRepository skills;
    private final SkillService skillService;
    private final SkillGraphService graphService;
    private final RoadmapProperties properties;

    public RoadmapProgressService(RoadmapTopicRepository topics, RoadmapTopicSkillRepository topicSkills,
                                  SkillRepository skills, SkillService skillService,
                                  SkillGraphService graphService, RoadmapProperties properties) {
        this.topics = topics;
        this.topicSkills = topicSkills;
        this.skills = skills;
        this.skillService = skillService;
        this.graphService = graphService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public RoadmapProgress forRoadmap(UUID roadmapId, UUID userId) {
        Map<UUID, String> skillNames = skills.findAll().stream()
                .collect(Collectors.toMap(Skill::getId, Skill::getCanonicalName));

        Map<UUID, List<RoadmapTopicSkill>> mappingsByTopic = topicSkills.findAllForRoadmap(roadmapId).stream()
                .collect(Collectors.groupingBy(RoadmapTopicSkill::getRoadmapTopicId));

        List<TopicInput> topicInputs = topics.findByRoadmapIdOrderBySortOrderAscNameAsc(roadmapId).stream()
                .map(t -> new TopicInput(t.getId(), t.getName(), t.getSortOrder(),
                        mappingsByTopic.getOrDefault(t.getId(), List.of()).stream()
                                .map(m -> new SkillRef(m.getSkillId(),
                                        skillNames.getOrDefault(m.getSkillId(), "?"), m.relevanceValue()))
                                .toList()))
                .toList();

        Map<UUID, Double> capability = capabilityBySkill(userId);
        return RoadmapProgressCalculator.compute(topicInputs, capability, graphService.graph(), properties);
    }

    Map<UUID, Double> capabilityBySkill(UUID userId) {
        return skillService.latestBySkill(userId).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().overall()));
    }

    /** Coverage only — cheap path for the library list (avoids building the full topic breakdown twice). */
    @Transactional(readOnly = true)
    public double coverageForRoadmap(UUID roadmapId, Map<UUID, Double> capability) {
        var mappings = topicSkills.findAllForRoadmap(roadmapId);
        Map<UUID, Double> relBySkill = new java.util.HashMap<>();
        for (RoadmapTopicSkill m : mappings) {
            relBySkill.merge(m.getSkillId(), m.relevanceValue(), Math::max);
        }
        double wSum = 0;
        double vSum = 0;
        for (Map.Entry<UUID, Double> e : relBySkill.entrySet()) {
            wSum += e.getValue();
            vSum += e.getValue() * capability.getOrDefault(e.getKey(), 0.0);
        }
        return wSum == 0 ? 0 : Math.round(vSum / wSum * 1000) / 1000.0;
    }
}

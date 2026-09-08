package com.orbit.platform.roadmaps.service;

import com.orbit.platform.common.exception.ApiException;
import com.orbit.platform.identity.service.CurrentUserService;
import com.orbit.platform.roadmaps.model.Roadmap;
import com.orbit.platform.roadmaps.model.RoadmapTopic;
import com.orbit.platform.roadmaps.model.RoadmapTopicSkill;
import com.orbit.platform.roadmaps.model.UserRoadmap;
import com.orbit.platform.roadmaps.progress.RoadmapProgressCalculator.RoadmapProgress;
import com.orbit.platform.roadmaps.progress.RoadmapProgressCalculator.TopicProgress;
import com.orbit.platform.roadmaps.repository.RoadmapRepository;
import com.orbit.platform.roadmaps.repository.RoadmapTopicRepository;
import com.orbit.platform.roadmaps.repository.RoadmapTopicSkillRepository;
import com.orbit.platform.roadmaps.repository.UserRoadmapRepository;
import com.orbit.platform.skills.model.Skill;
import com.orbit.platform.skills.repository.SkillRepository;
import com.orbit.platform.skills.service.SkillService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read access to the global roadmap catalog (ADR-021) joined with the caller's
 * adoption state and derived progress (ADR-023). The catalog is not user-scoped
 * and not editable — populated by the V7 seed (ADR-022).
 */
@Service
public class RoadmapService {

    private final RoadmapRepository roadmaps;
    private final RoadmapTopicRepository topics;
    private final RoadmapTopicSkillRepository topicSkills;
    private final UserRoadmapRepository adoptions;
    private final SkillRepository skills;
    private final SkillService skillService;
    private final RoadmapProgressService progressService;
    private final CurrentUserService currentUser;

    public RoadmapService(RoadmapRepository roadmaps, RoadmapTopicRepository topics,
                          RoadmapTopicSkillRepository topicSkills, UserRoadmapRepository adoptions,
                          SkillRepository skills, SkillService skillService,
                          RoadmapProgressService progressService, CurrentUserService currentUser) {
        this.roadmaps = roadmaps;
        this.topics = topics;
        this.topicSkills = topicSkills;
        this.adoptions = adoptions;
        this.skills = skills;
        this.skillService = skillService;
        this.progressService = progressService;
        this.currentUser = currentUser;
    }

    public record RoadmapSummary(Roadmap roadmap, double coverage, boolean adopted, int skillCount) {
    }

    public record SkillLink(UUID skillId, String name, double relevance, Double capability) {
    }

    public record TopicView(
            UUID id, UUID parentTopicId, String name, int sortOrder, String sourceUrl,
            String status, double coverage, List<SkillLink> skills) {
    }

    public record RoadmapDetail(
            Roadmap roadmap, double coverage, Optional<UserRoadmap> adoption,
            List<TopicView> topics, RoadmapProgress progress) {
    }

    @Transactional(readOnly = true)
    public List<RoadmapSummary> listForUser() {
        UUID userId = currentUser.currentUserId();
        Map<UUID, Double> capability = progressService.capabilityBySkill(userId);
        var adopted = adoptions.findByUserIdOrderByStartedAtDesc(userId).stream()
                .map(UserRoadmap::getRoadmapId).collect(Collectors.toSet());

        return roadmaps.findByStatusOrderByPriorityTierAscNameAsc("active").stream()
                .map(r -> {
                    var mappings = topicSkills.findAllForRoadmap(r.getId());
                    long distinctSkills = mappings.stream().map(RoadmapTopicSkill::getSkillId).distinct().count();
                    return new RoadmapSummary(r,
                            progressService.coverageForRoadmap(r.getId(), capability),
                            adopted.contains(r.getId()),
                            (int) distinctSkills);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public RoadmapDetail getForUser(UUID roadmapId) {
        UUID userId = currentUser.currentUserId();
        Roadmap roadmap = roadmaps.findById(roadmapId)
                .orElseThrow(() -> ApiException.notFound("Roadmap not found."));

        RoadmapProgress progress = progressService.forRoadmap(roadmapId, userId);
        Map<UUID, TopicProgress> progressByTopic = progress.topics().stream()
                .collect(Collectors.toMap(TopicProgress::topicId, tp -> tp));

        List<TopicView> topicViews = buildTopicViews(roadmapId, userId, progressByTopic);
        double coverage = progress.coverage();
        Optional<UserRoadmap> adoption = adoptions.findByUserIdAndRoadmapId(userId, roadmapId);

        return new RoadmapDetail(roadmap, coverage, adoption, topicViews, progress);
    }

    @Transactional(readOnly = true)
    public List<TopicView> topicsForRoadmap(UUID roadmapId) {
        UUID userId = currentUser.currentUserId();
        if (roadmaps.findById(roadmapId).isEmpty()) {
            throw ApiException.notFound("Roadmap not found.");
        }
        RoadmapProgress progress = progressService.forRoadmap(roadmapId, userId);
        Map<UUID, TopicProgress> progressByTopic = progress.topics().stream()
                .collect(Collectors.toMap(TopicProgress::topicId, tp -> tp));
        return buildTopicViews(roadmapId, userId, progressByTopic);
    }

    private List<TopicView> buildTopicViews(UUID roadmapId, UUID userId,
                                            Map<UUID, TopicProgress> progressByTopic) {
        Map<UUID, String> skillNames = skills.findAll().stream()
                .collect(Collectors.toMap(Skill::getId, Skill::getCanonicalName));
        Map<UUID, Double> capability = skillService.latestBySkill(userId).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().overall()));
        Map<UUID, List<RoadmapTopicSkill>> mappingsByTopic = topicSkills.findAllForRoadmap(roadmapId).stream()
                .collect(Collectors.groupingBy(RoadmapTopicSkill::getRoadmapTopicId));

        List<RoadmapTopic> ordered = topics.findByRoadmapIdOrderBySortOrderAscNameAsc(roadmapId);
        return ordered.stream().map(t -> {
            List<SkillLink> links = mappingsByTopic.getOrDefault(t.getId(), List.of()).stream()
                    .map(m -> new SkillLink(m.getSkillId(),
                            skillNames.getOrDefault(m.getSkillId(), "?"), m.relevanceValue(),
                            capability.get(m.getSkillId())))
                    .sorted((a, b) -> Double.compare(b.relevance(), a.relevance()))
                    .toList();
            TopicProgress tp = progressByTopic.get(t.getId());
            return new TopicView(t.getId(), t.getParentTopicId(), t.getName(), t.getSortOrder(),
                    t.getSourceUrl(),
                    tp == null ? "not_started" : tp.status(),
                    tp == null ? 0.0 : tp.coverage(),
                    links);
        }).toList();
    }

    /** Roadmaps whose topics map to {@code skillId} — the Skill → roadmaps reverse lookup (Blueprint §11). */
    @Transactional(readOnly = true)
    public List<Roadmap> roadmapsForSkill(UUID skillId) {
        List<UUID> ids = topicSkills.findRoadmapIdsForSkill(skillId);
        return roadmaps.findAllById(ids);
    }
}

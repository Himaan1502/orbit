package com.orbit.platform.roadmaps.web.dto;

import com.orbit.platform.roadmaps.model.Roadmap;
import com.orbit.platform.roadmaps.model.UserRoadmap;
import com.orbit.platform.roadmaps.progress.RoadmapProgressCalculator.NextTopic;
import com.orbit.platform.roadmaps.service.RoadmapIntersectionService.Intersection;
import com.orbit.platform.roadmaps.service.RoadmapIntersectionService.RoadmapRef;
import com.orbit.platform.roadmaps.service.RoadmapIntersectionService.SharedSkill;
import com.orbit.platform.roadmaps.service.RoadmapService.RoadmapDetail;
import com.orbit.platform.roadmaps.service.RoadmapService.RoadmapSummary;
import com.orbit.platform.roadmaps.service.RoadmapService.SkillLink;
import com.orbit.platform.roadmaps.service.RoadmapService.TopicView;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request / response shapes for {@code /api/v1/roadmaps} (Blueprint §20, ADR-021). */
public final class RoadmapDtos {

    private RoadmapDtos() {
    }

    private static String tierLabel(int priorityTier) {
        return "P" + priorityTier;
    }

    public record RoadmapSummaryResponse(
            UUID id, String name, String tier, int priorityTier, String sourceName, String sourceUrl,
            String description, String status, double coverage, boolean adopted, int skillCount) {

        public static RoadmapSummaryResponse from(RoadmapSummary s) {
            Roadmap r = s.roadmap();
            return new RoadmapSummaryResponse(r.getId(), r.getName(), tierLabel(r.getPriorityTier()),
                    r.getPriorityTier(), r.getSourceName(), r.getSourceUrl(), r.getDescription(),
                    r.getStatus(), s.coverage(), s.adopted(), s.skillCount());
        }
    }

    public record SkillLinkResponse(UUID skillId, String name, double relevance, Double capability) {
        static SkillLinkResponse from(SkillLink l) {
            return new SkillLinkResponse(l.skillId(), l.name(), l.relevance(), l.capability());
        }
    }

    public record TopicResponse(
            UUID id, UUID parentTopicId, String name, int sortOrder, String sourceUrl,
            String status, double coverage, List<SkillLinkResponse> skills) {

        public static TopicResponse from(TopicView t) {
            return new TopicResponse(t.id(), t.parentTopicId(), t.name(), t.sortOrder(), t.sourceUrl(),
                    t.status(), t.coverage(), t.skills().stream().map(SkillLinkResponse::from).toList());
        }
    }

    public record NextTopicResponse(
            UUID topicId, String name, double readiness, double weightedGap, String reason) {

        static NextTopicResponse from(NextTopic n) {
            return new NextTopicResponse(n.topicId(), n.name(), n.readiness(), n.weightedGap(), n.reason());
        }
    }

    public record AdoptionResponse(
            UUID roadmapId, String status, Instant startedAt, LocalDate targetDate) {

        public static AdoptionResponse from(UserRoadmap u) {
            return new AdoptionResponse(u.getRoadmapId(), u.getStatus(), u.getStartedAt(), u.getTargetDate());
        }
    }

    public record RoadmapDetailResponse(
            UUID id, String name, String tier, int priorityTier, String sourceName, String sourceUrl,
            String description, Instant lastVerifiedAt, String status, double coverage,
            int topicCount, int coveredTopicCount, AdoptionResponse adoption,
            List<TopicResponse> topics, List<NextTopicResponse> recommendedNext) {

        public static RoadmapDetailResponse from(RoadmapDetail d) {
            Roadmap r = d.roadmap();
            return new RoadmapDetailResponse(
                    r.getId(), r.getName(), tierLabel(r.getPriorityTier()), r.getPriorityTier(),
                    r.getSourceName(), r.getSourceUrl(), r.getDescription(), r.getLastVerifiedAt(),
                    r.getStatus(), d.coverage(),
                    d.progress().topicCount(), d.progress().coveredTopicCount(),
                    d.adoption().map(AdoptionResponse::from).orElse(null),
                    d.topics().stream().map(TopicResponse::from).toList(),
                    d.progress().recommendedNext().stream().map(NextTopicResponse::from).toList());
        }
    }

    public record AdoptRoadmapRequest(
            @Pattern(regexp = "active|paused", message = "status must be 'active' or 'paused'.") String status,
            LocalDate targetDate) {
    }

    // ---- intersection ----

    public record RoadmapRefResponse(UUID id, String name, String tier) {
        static RoadmapRefResponse from(RoadmapRef r) {
            return new RoadmapRefResponse(r.id(), r.name(), "P" + r.tier());
        }
    }

    public record SharedSkillResponse(
            UUID skillId, String name, int roadmapCount, List<RoadmapRefResponse> contributesTo,
            Double capability, Double careerImportance, String careerRelevance,
            double leverage, String recommendedAction) {

        static SharedSkillResponse from(SharedSkill s) {
            return new SharedSkillResponse(s.skillId(), s.name(), s.roadmapCount(),
                    s.contributesTo().stream().map(RoadmapRefResponse::from).toList(),
                    s.capability(), s.careerImportance(), s.careerRelevance(),
                    s.leverage(), s.recommendedAction());
        }
    }

    public record IntersectionResponse(
            List<RoadmapRefResponse> roadmaps, int sharedSkillCount, List<SharedSkillResponse> sharedSkills) {

        public static IntersectionResponse from(Intersection i) {
            return new IntersectionResponse(
                    i.roadmaps().stream().map(RoadmapRefResponse::from).toList(),
                    i.sharedSkillCount(),
                    i.sharedSkills().stream().map(SharedSkillResponse::from).toList());
        }
    }
}

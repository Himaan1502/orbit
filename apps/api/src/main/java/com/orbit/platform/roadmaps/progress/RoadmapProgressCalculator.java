package com.orbit.platform.roadmaps.progress;

import com.orbit.platform.skills.graph.SkillGraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure derived-progress for a roadmap (ADR-023 §1). No I/O — the caller loads the
 * roadmap's topics, each topic's {@code (skillId, relevance)} mappings, the
 * user's latest capability per skill (absent ⇒ 0, an honest gap per ADR-018) and
 * the {@link SkillGraph}. Given the same inputs it returns the same numbers
 * (Blueprint §43 "reproducible").
 */
public final class RoadmapProgressCalculator {

    private static final int TOP_N_NEXT = 3;

    private RoadmapProgressCalculator() {
    }

    /** One skill a topic maps to. */
    public record SkillRef(UUID skillId, String name, double relevance) {
    }

    /** A topic with its skill mappings (tree position is not needed to score progress). */
    public record TopicInput(UUID topicId, String name, int sortOrder, List<SkillRef> skills) {
    }

    public record TopicProgress(
            UUID topicId, String name, String status, double coverage,
            int skillCount, int assessedSkillCount) {
    }

    public record NextTopic(UUID topicId, String name, double readiness, double weightedGap, String reason) {
    }

    public record RoadmapProgress(
            double coverage,
            int topicCount,
            int coveredTopicCount,
            List<TopicProgress> topics,
            List<NextTopic> recommendedNext) {
    }

    public static RoadmapProgress compute(List<TopicInput> topics, Map<UUID, Double> capabilityBySkill,
                                          SkillGraph graph, RoadmapProperties props) {
        double threshold = props.topicCoveredThreshold();

        // Roadmap coverage: relevance-weighted mean capability over the roadmap's DISTINCT skills,
        // taking each skill's strongest association with the roadmap (max relevance).
        Map<UUID, Double> relBySkill = new HashMap<>();
        for (TopicInput t : topics) {
            for (SkillRef s : t.skills()) {
                relBySkill.merge(s.skillId(), s.relevance(), Math::max);
            }
        }
        double wSum = 0;
        double vSum = 0;
        for (Map.Entry<UUID, Double> e : relBySkill.entrySet()) {
            double rel = e.getValue();
            wSum += rel;
            vSum += rel * cap(capabilityBySkill, e.getKey());
        }
        double coverage = wSum == 0 ? 0 : round(vSum / wSum);

        List<TopicProgress> topicProgress = new ArrayList<>();
        List<NextTopic> candidates = new ArrayList<>();
        int coveredCount = 0;

        for (TopicInput t : topics) {
            double tw = 0;
            double tv = 0;
            int assessed = 0;
            for (SkillRef s : t.skills()) {
                double c = cap(capabilityBySkill, s.skillId());
                tw += s.relevance();
                tv += s.relevance() * c;
                if (c > 0) {
                    assessed++;
                }
            }
            double tCoverage = tw == 0 ? 0 : round(tv / tw);
            String status = statusOf(tCoverage, assessed, threshold);
            if ("covered".equals(status)) {
                coveredCount++;
            }
            topicProgress.add(new TopicProgress(
                    t.topicId(), t.name(), status, tCoverage, t.skills().size(), assessed));

            if (!"covered".equals(status) && !t.skills().isEmpty()) {
                double readiness = topicReadiness(t, capabilityBySkill, graph, threshold);
                double weightedGap = 0;
                for (SkillRef s : t.skills()) {
                    weightedGap += s.relevance() * (1 - cap(capabilityBySkill, s.skillId()));
                }
                candidates.add(new NextTopic(t.topicId(), t.name(), round(readiness), round(weightedGap),
                        nextReason(t, readiness)));
            }
        }

        candidates.sort(Comparator
                .comparingDouble(NextTopic::readiness).reversed()
                .thenComparing(Comparator.comparingDouble(NextTopic::weightedGap).reversed())
                .thenComparingInt(n -> sortOrderOf(topics, n.topicId())));
        List<NextTopic> next = candidates.stream().limit(TOP_N_NEXT).toList();

        return new RoadmapProgress(coverage, topics.size(), coveredCount, topicProgress, next);
    }

    private static double cap(Map<UUID, Double> caps, UUID skillId) {
        return caps.getOrDefault(skillId, 0.0);
    }

    private static String statusOf(double coverage, int assessedSkillCount, double threshold) {
        if (coverage >= threshold) {
            return "covered";
        }
        return assessedSkillCount == 0 ? "not_started" : "in_progress";
    }

    /** Fraction of the topic's skills whose direct prerequisites are all already covered. */
    private static double topicReadiness(TopicInput t, Map<UUID, Double> caps, SkillGraph graph,
                                         double threshold) {
        if (t.skills().isEmpty()) {
            return 0;
        }
        int ready = 0;
        for (SkillRef s : t.skills()) {
            boolean allPrereqsCovered = graph.prerequisites(s.skillId()).stream()
                    .allMatch(e -> cap(caps, e.prerequisite()) >= threshold);
            if (allPrereqsCovered) {
                ready++;
            }
        }
        return (double) ready / t.skills().size();
    }

    private static String nextReason(TopicInput t, double readiness) {
        long readySkills = Math.round(readiness * t.skills().size());
        long blocked = t.skills().size() - readySkills;
        if (blocked == 0) {
            return "All " + t.skills().size() + " skills here are ready to start.";
        }
        return readySkills + " of " + t.skills().size() + " skills are ready to start; "
                + blocked + " sit behind prerequisites you have not covered yet.";
    }

    private static int sortOrderOf(List<TopicInput> topics, UUID topicId) {
        return topics.stream().filter(t -> t.topicId().equals(topicId))
                .mapToInt(TopicInput::sortOrder).findFirst().orElse(Integer.MAX_VALUE);
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}

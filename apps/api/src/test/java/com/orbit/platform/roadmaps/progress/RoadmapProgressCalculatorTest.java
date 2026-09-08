package com.orbit.platform.roadmaps.progress;

import com.orbit.platform.roadmaps.progress.RoadmapProgressCalculator.RoadmapProgress;
import com.orbit.platform.roadmaps.progress.RoadmapProgressCalculator.SkillRef;
import com.orbit.platform.roadmaps.progress.RoadmapProgressCalculator.TopicInput;
import com.orbit.platform.skills.graph.SkillGraph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Direct tests of derived roadmap progress (ADR-023 §1) — no Spring, no database.
 * Blueprint §43: progress is reproducible from stored data.
 */
class RoadmapProgressCalculatorTest {

    private final RoadmapProperties props = new RoadmapProperties(0.70, 0.66, 0.40, 0.30, 0.60, 0.85);
    private final SkillGraph emptyGraph = new SkillGraph(List.of());

    private final UUID docker = UUID.randomUUID();
    private final UUID k8s = UUID.randomUUID();
    private final UUID obs = UUID.randomUUID();

    @Test
    void coverageIsTheRelevanceWeightedMeanCapability() {
        var topics = List.of(
                new TopicInput(UUID.randomUUID(), "Containers", 0,
                        List.of(new SkillRef(docker, "Docker", 0.8), new SkillRef(k8s, "Kubernetes", 0.9))),
                new TopicInput(UUID.randomUUID(), "Observability", 1,
                        List.of(new SkillRef(obs, "Observability", 0.6))));
        var caps = Map.of(docker, 0.9, k8s, 0.4, obs, 0.5);
        // distinct skills, max relevance: docker .8*.9 + k8s .9*.4 + obs .6*.5 = .72 + .36 + .30 = 1.38
        //  / (.8 + .9 + .6 = 2.3)  = 0.6
        RoadmapProgress r = RoadmapProgressCalculator.compute(topics, caps, emptyGraph, props);
        assertThat(r.coverage()).isCloseTo(0.6, within(1e-3));
        assertThat(r.topicCount()).isEqualTo(2);
    }

    @Test
    void topicStatusUsesTheCoveredThresholdAndZeroMeansNotStarted() {
        var covered = new TopicInput(UUID.randomUUID(), "Covered", 0,
                List.of(new SkillRef(docker, "Docker", 1.0)));
        var partial = new TopicInput(UUID.randomUUID(), "Partial", 1,
                List.of(new SkillRef(k8s, "Kubernetes", 1.0)));
        var untouched = new TopicInput(UUID.randomUUID(), "Untouched", 2,
                List.of(new SkillRef(obs, "Observability", 1.0)));
        var caps = Map.of(docker, 0.8, k8s, 0.3);   // obs unassessed -> 0

        RoadmapProgress r = RoadmapProgressCalculator.compute(
                List.of(covered, partial, untouched), caps, emptyGraph, props);
        assertThat(status(r, "Covered")).isEqualTo("covered");
        assertThat(status(r, "Partial")).isEqualTo("in_progress");
        assertThat(status(r, "Untouched")).isEqualTo("not_started");
        assertThat(r.coveredTopicCount()).isEqualTo(1);
    }

    @Test
    void recommendedNextPrefersTopicsWhosePrerequisitesAreCovered() {
        UUID linux = UUID.randomUUID();
        // linux -> docker -> k8s ; user has covered linux but not docker
        SkillGraph g = new SkillGraph(List.of(
                new SkillGraph.Edge(linux, docker, 0.8),
                new SkillGraph.Edge(docker, k8s, 0.9)));
        var ready = new TopicInput(UUID.randomUUID(), "Docker", 0,
                List.of(new SkillRef(docker, "Docker", 0.9)));           // prereq linux covered -> ready
        var blocked = new TopicInput(UUID.randomUUID(), "Kubernetes", 1,
                List.of(new SkillRef(k8s, "Kubernetes", 0.9)));          // prereq docker NOT covered -> blocked
        var caps = Map.of(linux, 0.9);

        RoadmapProgress r = RoadmapProgressCalculator.compute(
                List.of(ready, blocked), caps, g, props);
        assertThat(r.recommendedNext().get(0).name()).isEqualTo("Docker");
        assertThat(r.recommendedNext().get(0).readiness()).isEqualTo(1.0);
    }

    @Test
    void anEmptyRoadmapReadsZeroWithNoRecommendations() {
        RoadmapProgress r = RoadmapProgressCalculator.compute(List.of(), Map.of(), emptyGraph, props);
        assertThat(r.coverage()).isEqualTo(0.0);
        assertThat(r.recommendedNext()).isEmpty();
    }

    @Test
    void isReproducible() {
        var topics = List.of(new TopicInput(UUID.randomUUID(), "T", 0,
                List.of(new SkillRef(docker, "Docker", 0.7), new SkillRef(k8s, "Kubernetes", 0.9))));
        var caps = Map.of(docker, 0.55, k8s, 0.2);
        assertThat(RoadmapProgressCalculator.compute(topics, caps, emptyGraph, props))
                .isEqualTo(RoadmapProgressCalculator.compute(topics, caps, emptyGraph, props));
    }

    private static String status(RoadmapProgress r, String topicName) {
        return r.topics().stream().filter(t -> t.name().equals(topicName)).findFirst().orElseThrow().status();
    }
}

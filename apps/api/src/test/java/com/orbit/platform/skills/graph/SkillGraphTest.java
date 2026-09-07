package com.orbit.platform.skills.graph;

import com.orbit.platform.skills.graph.SkillGraph.Edge;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Direct tests of the in-memory skill graph (ADR-019 §3) — no Spring, no database. */
class SkillGraphTest {

    // linux -> docker -> k8s -> k8sProd ;  linux -> networking -> k8s
    private final UUID linux = UUID.randomUUID();
    private final UUID docker = UUID.randomUUID();
    private final UUID networking = UUID.randomUUID();
    private final UUID k8s = UUID.randomUUID();
    private final UUID k8sProd = UUID.randomUUID();
    private final UUID unrelated = UUID.randomUUID();

    private final SkillGraph graph = new SkillGraph(List.of(
            new Edge(linux, docker, 0.7),
            new Edge(linux, networking, 0.4),
            new Edge(docker, k8s, 0.9),
            new Edge(networking, k8s, 0.6),
            new Edge(k8s, k8sProd, 0.9)));

    @Test
    void directNeighboursAreExposed() {
        assertThat(graph.dependents(linux)).extracting(Edge::dependent).containsExactlyInAnyOrder(docker, networking);
        assertThat(graph.prerequisites(k8s)).extracting(Edge::prerequisite).containsExactlyInAnyOrder(docker, networking);
    }

    @Test
    void downstreamIsTheTransitiveClosureOfDependents() {
        assertThat(graph.downstream(linux)).containsExactlyInAnyOrder(docker, networking, k8s, k8sProd);
        assertThat(graph.downstream(k8s)).containsExactly(k8sProd);
        assertThat(graph.downstream(k8sProd)).isEmpty();
    }

    @Test
    void dependencyImpactIsTheFractionOfTargetSkillsUnblocked() {
        Set<UUID> target = Set.of(k8s, k8sProd, unrelated);
        // linux unblocks k8s and k8sProd of the 3 target skills -> 2/3
        assertThat(graph.dependencyImpact(linux, target)).isEqualTo(2.0 / 3.0);
        // k8sProd unblocks nothing downstream in the target
        assertThat(graph.dependencyImpact(k8sProd, target)).isEqualTo(0.0);
        // a skill not in the graph -> -1 (caller uses neutral)
        assertThat(graph.dependencyImpact(UUID.randomUUID(), target)).isEqualTo(-1.0);
        assertThat(graph.dependencyImpact(linux, Set.of())).isEqualTo(-1.0);
    }

    @Test
    void leverageRewardsUnblockingImportantOutstandingWork() {
        Map<UUID, Double> importance = Map.of(k8s, 0.8, k8sProd, 0.9);
        Map<UUID, Double> gap = Map.of(k8s, 0.5, k8sProd, 0.7);
        // linux leverage = 0.8*0.5 + 0.9*0.7 = 0.4 + 0.63 = 1.03
        assertThat(graph.leverage(linux, importance, gap)).isEqualTo(1.03);
        assertThat(graph.leverage(k8sProd, importance, gap)).isEqualTo(0.0);
    }
}

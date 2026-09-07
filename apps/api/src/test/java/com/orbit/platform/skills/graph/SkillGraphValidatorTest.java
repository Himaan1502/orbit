package com.orbit.platform.skills.graph;

import com.orbit.platform.skills.graph.SkillGraph.Edge;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Direct tests of the acyclicity check (Blueprint §12, ADR-019 §2) — no Spring, no database. */
class SkillGraphValidatorTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final UUID d = UUID.randomUUID();

    private Edge e(UUID from, UUID to) {
        return new Edge(from, to, 0.5);
    }

    @Test
    void anAcyclicGraphHasNoCycle() {
        assertThat(SkillGraphValidator.firstCycle(List.of(e(a, b), e(b, c), e(a, c), e(c, d)))).isEmpty();
    }

    @Test
    void aDirectedCycleIsDetected() {
        var cycle = SkillGraphValidator.firstCycle(List.of(e(a, b), e(b, c), e(c, a)));
        assertThat(cycle).isPresent();
        assertThat(cycle.get()).contains(a, b, c);
        assertThat(cycle.get().get(0)).isEqualTo(cycle.get().get(cycle.get().size() - 1)); // closed loop
    }

    @Test
    void aTwoNodeCycleIsDetected() {
        assertThat(SkillGraphValidator.firstCycle(List.of(e(a, b), e(b, a)))).isPresent();
    }

    @Test
    void wouldCreateCycleRejectsAnEdgeThatClosesALoop() {
        List<Edge> existing = List.of(e(a, b), e(b, c));
        assertThat(SkillGraphValidator.wouldCreateCycle(existing, c, a)).isTrue();   // c -> a closes a->b->c
        assertThat(SkillGraphValidator.wouldCreateCycle(existing, c, d)).isFalse();  // c -> d is fine
        assertThat(SkillGraphValidator.wouldCreateCycle(existing, a, a)).isTrue();   // self loop
    }
}

package com.orbit.platform.career.readiness;

import com.orbit.platform.career.readiness.CareerReadinessCalculator.Capability;
import com.orbit.platform.career.readiness.CareerReadinessCalculator.Readiness;
import com.orbit.platform.career.readiness.CareerReadinessCalculator.TargetSkill;
import com.orbit.platform.skills.graph.SkillGraph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Direct tests of career-readiness aggregation (Blueprint §24, ADR-018) — no
 * Spring, no database. Blueprint §43: "readiness is reproducible".
 */
class CareerReadinessCalculatorTest {

    private final ReadinessProperties weights = new ReadinessProperties(0.70, 0.30);
    private final SkillGraph emptyGraph = new SkillGraph(List.of());

    private final UUID k8s = UUID.randomUUID();
    private final UUID obs = UUID.randomUUID();
    private final UUID ds = UUID.randomUUID();

    private Readiness compute(List<TargetSkill> skills, Map<UUID, Capability> caps, SkillGraph g) {
        return CareerReadinessCalculator.compute(skills, caps, g, weights);
    }

    @Test
    void skillCapabilityIsTheImportanceWeightedMean() {
        var skills = List.of(
                new TargetSkill(k8s, "Kubernetes", 0.9, 0.5),
                new TargetSkill(obs, "Observability", 0.6, 0.5));
        var caps = Map.of(
                k8s, new Capability(0.4, 0.4),
                obs, new Capability(0.8, 0.8));
        // skillCapability = (0.9*0.4 + 0.6*0.8) / (0.9 + 0.6) = (0.36 + 0.48) / 1.5 = 0.56
        Readiness r = compute(skills, caps, emptyGraph);
        assertThat(component(r, "skillCapability")).isCloseTo(0.56, within(1e-3));
        assertThat(r.confidence()).isEqualTo("self-reported");
    }

    @Test
    void anUnassessedTargetSkillCountsAsZeroAndBecomesACriticalGap() {
        var skills = List.of(
                new TargetSkill(k8s, "Kubernetes", 0.9, 0.5),
                new TargetSkill(obs, "Observability", 0.8, 0.5));
        var caps = Map.of(k8s, new Capability(0.7, 0.7));   // obs not assessed

        Readiness r = compute(skills, caps, emptyGraph);
        assertThat(r.assessedSkillCount()).isEqualTo(1);
        assertThat(r.targetSkillCount()).isEqualTo(2);
        // obs contributes 0 -> pulls skillCapability down and tops the gap list
        assertThat(r.criticalGaps().get(0).skill()).isEqualTo("Observability");
        assertThat(r.criticalGaps().get(0).gap()).isCloseTo(0.8, within(1e-3)); // importance 0.8 * (1-0)
    }

    @Test
    void evidenceAndProjectComponentsAreExplicitlyNotTracked() {
        Readiness r = compute(
                List.of(new TargetSkill(k8s, "Kubernetes", 1.0, 1.0)),
                Map.of(k8s, new Capability(0.5, 0.5)), emptyGraph);

        var evidence = r.componentBreakdown().stream()
                .filter(c -> c.component().equals("evidenceCoverage")).findFirst().orElseThrow();
        assertThat(evidence.value()).isNull();
        assertThat(evidence.weight()).isEqualTo(0.0);
        assertThat(evidence.status()).isEqualTo("not-tracked-until-evidence-engine");
        assertThat(r.explanation()).contains("not yet tracked");
    }

    @Test
    void overallBlendsSkillCapabilityAndInterviewReadiness() {
        var skills = List.of(new TargetSkill(k8s, "Kubernetes", 1.0, 1.0));
        var caps = Map.of(k8s, new Capability(0.6, 0.2));
        // overall = (0.6*0.70 + 0.2*0.30) / 1.0 = 0.42 + 0.06 = 0.48
        assertThat(compute(skills, caps, emptyGraph).overall()).isCloseTo(0.48, within(1e-3));
    }

    @Test
    void highLeverageSkillsRankByWhatTheyUnblock() {
        SkillGraph g = new SkillGraph(List.of(
                new SkillGraph.Edge(ds, k8s, 0.8),
                new SkillGraph.Edge(ds, obs, 0.6)));   // ds is a prerequisite for both k8s and obs
        var skills = List.of(
                new TargetSkill(ds, "Distributed Systems", 0.7, 0.5),
                new TargetSkill(k8s, "Kubernetes", 0.9, 0.5),
                new TargetSkill(obs, "Observability", 0.8, 0.5));
        var caps = Map.of(ds, new Capability(0.3, 0.3)); // k8s/obs unassessed -> gap 1.0

        Readiness r = compute(skills, caps, g);
        assertThat(r.highLeverageSkills()).isNotEmpty();
        assertThat(r.highLeverageSkills().get(0).skill()).isEqualTo("Distributed Systems");
        assertThat(r.highLeverageSkills().get(0).unlocks()).isEqualTo(2);
    }

    @Test
    void isReproducible() {
        var skills = List.of(new TargetSkill(k8s, "Kubernetes", 0.9, 0.5));
        var caps = Map.of(k8s, new Capability(0.55, 0.4));
        assertThat(compute(skills, caps, emptyGraph)).isEqualTo(compute(skills, caps, emptyGraph));
    }

    @Test
    void anEmptyTargetReadsZeroWithAGuidingExplanation() {
        Readiness r = compute(List.of(), Map.of(), emptyGraph);
        assertThat(r.overall()).isEqualTo(0.0);
        assertThat(r.explanation()).contains("no skills yet");
    }

    private static double component(Readiness r, String name) {
        return r.componentBreakdown().stream()
                .filter(c -> c.component().equals(name)).findFirst().orElseThrow().value();
    }
}

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
 * Direct tests of career-readiness aggregation (Blueprint §24, ADR-018, amended
 * by ADR-026 §2) — no Spring, no database. Blueprint §43: "readiness is
 * reproducible".
 */
class CareerReadinessCalculatorTest {

    // ratified M5 blend
    private final ReadinessProperties weights = new ReadinessProperties(0.45, 0.20, 0.20, 0.15, 0.50);
    private final SkillGraph emptyGraph = new SkillGraph(List.of());

    private final UUID k8s = UUID.randomUUID();
    private final UUID obs = UUID.randomUUID();
    private final UUID ds = UUID.randomUUID();

    private Readiness compute(List<TargetSkill> skills, Map<UUID, Capability> caps,
                              Map<UUID, Double> evidence, Map<UUID, Double> projects, SkillGraph g) {
        return CareerReadinessCalculator.compute(skills, caps, evidence, projects, g, weights);
    }

    private Readiness compute(List<TargetSkill> skills, Map<UUID, Capability> caps, SkillGraph g) {
        return compute(skills, caps, Map.of(), Map.of(), g);
    }

    @Test
    void skillCapabilityIsTheImportanceWeightedMean() {
        var skills = List.of(
                new TargetSkill(k8s, "Kubernetes", 0.9, 0.5),
                new TargetSkill(obs, "Observability", 0.6, 0.5));
        var caps = Map.of(
                k8s, new Capability(0.4, 0.4),
                obs, new Capability(0.8, 0.8));
        // skillCapability = (0.9*0.4 + 0.6*0.8) / (0.9 + 0.6) = 0.56
        Readiness r = compute(skills, caps, emptyGraph);
        assertThat(component(r, "skillCapability")).isCloseTo(0.56, within(1e-3));
    }

    @Test
    void allFourComponentsAreComputedFromM5On() {
        Readiness r = compute(
                List.of(new TargetSkill(k8s, "Kubernetes", 1.0, 1.0)),
                Map.of(k8s, new Capability(0.5, 0.5)),
                Map.of(k8s, 0.6),   // evidence strength
                Map.of(k8s, 0.4),   // project coverage
                emptyGraph);

        var comps = r.componentBreakdown().stream()
                .collect(java.util.stream.Collectors.toMap(
                        CareerReadinessCalculator.Component::component, c -> c));
        assertThat(comps.get("evidenceCoverage").value()).isEqualTo(0.6);
        assertThat(comps.get("evidenceCoverage").status()).isEqualTo("computed");
        assertThat(comps.get("projectCoverage").value()).isEqualTo(0.4);
        assertThat(comps.get("projectCoverage").status()).isEqualTo("computed");
        double weightSum = r.componentBreakdown().stream()
                .mapToDouble(CareerReadinessCalculator.Component::weight).sum();
        assertThat(weightSum).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void overallBlendsAllFourComponents() {
        var skills = List.of(new TargetSkill(k8s, "Kubernetes", 1.0, 1.0));
        var caps = Map.of(k8s, new Capability(0.6, 0.2));
        // overall = 0.6*0.45 + 0.2*0.20 + 0.8*0.20 + 0.4*0.15 = 0.27 + 0.04 + 0.16 + 0.06 = 0.53
        assertThat(compute(skills, caps, Map.of(k8s, 0.8), Map.of(k8s, 0.4), emptyGraph).overall())
                .isCloseTo(0.53, within(1e-3));
    }

    @Test
    void readinessDropsForAnEvidenceFreeUserComparedToTheOldTwoWayBlend() {
        var skills = List.of(new TargetSkill(k8s, "Kubernetes", 1.0, 1.0));
        var caps = Map.of(k8s, new Capability(0.7, 0.5));
        // M5, no evidence/projects: 0.7*0.45 + 0.5*0.20 + 0 + 0 = 0.315 + 0.10 = 0.415
        double m5 = compute(skills, caps, emptyGraph).overall();
        assertThat(m5).isCloseTo(0.415, within(1e-3));
        // the old two-way blend would have been 0.7*0.70 + 0.5*0.30 = 0.64 — M5 is lower by design
        assertThat(m5).isLessThan(0.64);
    }

    @Test
    void anUnassessedTargetSkillCountsAsZeroAndBecomesACriticalGap() {
        var skills = List.of(
                new TargetSkill(k8s, "Kubernetes", 0.9, 0.5),
                new TargetSkill(obs, "Observability", 0.8, 0.5));
        var caps = Map.of(k8s, new Capability(0.7, 0.7));   // obs not assessed

        Readiness r = compute(skills, caps, emptyGraph);
        assertThat(r.assessedSkillCount()).isEqualTo(1);
        assertThat(r.criticalGaps().get(0).skill()).isEqualTo("Observability");
        assertThat(r.criticalGaps().get(0).gap()).isCloseTo(0.8, within(1e-3));
    }

    @Test
    void evidenceAndProjectGapsAreSurfaced() {
        var skills = List.of(
                new TargetSkill(k8s, "Kubernetes", 0.9, 0.5),
                new TargetSkill(obs, "Observability", 0.6, 0.5));
        var caps = Map.of(k8s, new Capability(0.8, 0.6), obs, new Capability(0.2, 0.2));
        // k8s: capable (0.8 >= 0.5) but no evidence -> an evidence gap. obs: not capable -> not an evidence gap.
        Readiness r = compute(skills, caps, Map.of(), Map.of(), emptyGraph);
        assertThat(r.evidenceGaps()).extracting(CareerReadinessCalculator.EvidenceGap::skill)
                .containsExactly("Kubernetes");
        // neither skill has project coverage -> both are project gaps, ordered by importance
        assertThat(r.projectGaps()).extracting(CareerReadinessCalculator.ProjectGap::skill)
                .containsExactly("Kubernetes", "Observability");
    }

    @Test
    void highLeverageSkillsRankByWhatTheyUnblock() {
        SkillGraph g = new SkillGraph(List.of(
                new SkillGraph.Edge(ds, k8s, 0.8),
                new SkillGraph.Edge(ds, obs, 0.6)));
        var skills = List.of(
                new TargetSkill(ds, "Distributed Systems", 0.7, 0.5),
                new TargetSkill(k8s, "Kubernetes", 0.9, 0.5),
                new TargetSkill(obs, "Observability", 0.8, 0.5));
        var caps = Map.of(ds, new Capability(0.3, 0.3));

        Readiness r = compute(skills, caps, g);
        assertThat(r.highLeverageSkills().get(0).skill()).isEqualTo("Distributed Systems");
        assertThat(r.highLeverageSkills().get(0).unlocks()).isEqualTo(2);
    }

    @Test
    void isReproducible() {
        var skills = List.of(new TargetSkill(k8s, "Kubernetes", 0.9, 0.5));
        var caps = Map.of(k8s, new Capability(0.55, 0.4));
        assertThat(compute(skills, caps, Map.of(k8s, 0.3), Map.of(), emptyGraph))
                .isEqualTo(compute(skills, caps, Map.of(k8s, 0.3), Map.of(), emptyGraph));
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

package com.orbit.platform.skills.scoring;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Direct tests of Skill Scoring v1 (Blueprint §23, ADR-017) — no Spring, no database. */
class SkillScoringEngineTest {

    private final SkillScoringEngine engine = new SkillScoringEngine(new SkillScoringWeights());

    @Test
    void overallIsBlueprintSection23WeightedSum() {
        // knowledge .8, practical .6, evidence .4, interview .5, production .3
        // = .20*.8 + .25*.6 + .20*.4 + .15*.5 + .20*.3 = .16 + .15 + .08 + .075 + .06 = 0.525
        SkillScore s = engine.score(
                new SkillDimensions(0.8, 0.6, 0.4, 0.5, 0.3), SkillScore.Confidence.SELF_REPORTED);
        assertThat(s.overall()).isCloseTo(0.525, within(1e-6));

        double contributionSum = s.components().values().stream()
                .mapToDouble(SkillScore.Component::contribution).sum();
        assertThat(s.overall()).isCloseTo(contributionSum, within(1e-6));
        double weightSum = s.components().values().stream()
                .mapToDouble(SkillScore.Component::weight).sum();
        assertThat(weightSum).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void isDeterministic() {
        SkillDimensions d = new SkillDimensions(0.5, 0.5, 0.5, 0.5, 0.5);
        assertThat(engine.overall(d)).isEqualTo(engine.overall(d));
        assertThat(engine.overall(d)).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void dimensionsAreClampedTo01() {
        SkillScore s = engine.score(
                new SkillDimensions(1.5, -0.2, 2.0, 0.5, 0.5), SkillScore.Confidence.SELF_REPORTED);
        assertThat(s.components().get("knowledge").value()).isEqualTo(1.0);
        assertThat(s.components().get("practical").value()).isEqualTo(0.0);
        assertThat(s.components().get("evidence").value()).isEqualTo(1.0);
    }

    @Test
    void confidenceIsPassedThrough() {
        SkillScore self = engine.score(
                new SkillDimensions(0.5, 0.5, 0.5, 0.5, 0.5), SkillScore.Confidence.SELF_REPORTED);
        assertThat(self.confidence().value()).isEqualTo("self-reported");
        assertThat(self.weightsFingerprint()).isNotBlank();
    }

    @Test
    void practicalHasTheLargestWeight() {
        double onlyPractical = engine.overall(new SkillDimensions(0, 1, 0, 0, 0));
        double onlyInterview = engine.overall(new SkillDimensions(0, 0, 0, 1, 0));
        assertThat(onlyPractical).isGreaterThan(onlyInterview);   // 0.25 vs 0.15
    }
}

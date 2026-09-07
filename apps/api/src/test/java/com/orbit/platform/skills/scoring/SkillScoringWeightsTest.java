package com.orbit.platform.skills.scoring;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The five Skill Scoring weights must sum to 1 or the context refuses to start
 * (Blueprint §23, ADR-017 §2).
 */
class SkillScoringWeightsTest {

    @Test
    void defaultsAreBlueprintSection23AndSumToOne() {
        SkillScoringWeights w = new SkillScoringWeights();
        assertThat(w.getKnowledge()).isEqualTo(0.20);
        assertThat(w.getPractical()).isEqualTo(0.25);
        assertThat(w.getEvidence()).isEqualTo(0.20);
        assertThat(w.getInterview()).isEqualTo(0.15);
        assertThat(w.getProduction()).isEqualTo(0.20);
        assertThatCode(w::validate).doesNotThrowAnyException();
    }

    @Test
    void aWeightSetThatDoesNotSumToOneIsRejected() {
        SkillScoringWeights w = new SkillScoringWeights();
        w.setProduction(0.40);
        assertThatThrownBy(w::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must sum to 1.0");
    }

    @Test
    void reweightingThatStillSumsToOneIsAllowed() {
        SkillScoringWeights w = new SkillScoringWeights();
        w.setProduction(0.30);
        w.setEvidence(0.10);   // moved 0.10 from evidence to production (an SRE-style lean)
        assertThatCode(w::validate).doesNotThrowAnyException();
    }
}

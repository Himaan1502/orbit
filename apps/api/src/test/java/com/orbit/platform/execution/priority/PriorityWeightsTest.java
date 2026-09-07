package com.orbit.platform.execution.priority;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Priority Engine weights must sum to 1 or the context refuses to start
 * (Blueprint §22, ADR-014 §2). {@code validate()} is package-private and runs
 * from {@code @PostConstruct}; here it is exercised directly.
 */
class PriorityWeightsTest {

    @Test
    void defaultsAreBlueprintSection22AndSumToOne() {
        PriorityWeights w = new PriorityWeights();
        assertThat(w.getCareerRelevance()).isEqualTo(0.20);
        assertThat(w.getSkillGap()).isEqualTo(0.20);
        assertThat(w.getDependencyImpact()).isEqualTo(0.15);
        assertThat(w.getProjectImpact()).isEqualTo(0.15);
        assertThat(w.getInterviewValue()).isEqualTo(0.10);
        assertThat(w.getUrgency()).isEqualTo(0.10);
        assertThat(w.getAvailableTimeFit()).isEqualTo(0.05);
        assertThat(w.getHistoricalCompletionProbability()).isEqualTo(0.05);
        assertThatCode(w::validate).doesNotThrowAnyException();
    }

    @Test
    void aWeightSetThatDoesNotSumToOneIsRejected() {
        PriorityWeights w = new PriorityWeights();
        w.setUrgency(0.40);   // sum is now 1.30
        assertThatThrownBy(w::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must sum to 1.0");
    }

    @Test
    void aTinyRoundingDeltaIsTolerated() {
        PriorityWeights w = new PriorityWeights();
        w.setUrgency(0.1004);   // sum 1.0004, within the ±0.001 tolerance
        assertThatCode(w::validate).doesNotThrowAnyException();
    }

    @Test
    void reweightingIsAllowedAsLongAsItStillSumsToOne() {
        PriorityWeights w = new PriorityWeights();
        w.setUrgency(0.20);
        w.setSkillGap(0.10);   // moved 0.10 from skillGap to urgency
        assertThatCode(w::validate).doesNotThrowAnyException();
        assertThat(w.fingerprint()).isNotBlank();
    }
}

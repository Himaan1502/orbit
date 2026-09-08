package com.orbit.platform.roadmaps.progress;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The roadmap tunables are validated at startup (ADR-023 §4, RoadmapsConfig). */
class RoadmapPropertiesTest {

    @Test
    void defaultsAreValid() {
        assertThatCode(new RoadmapProperties(0.70, 0.66, 0.40, 0.30, 0.60, 0.85)::validate)
                .doesNotThrowAnyException();
    }

    @Test
    void aFractionOutOfRangeIsRejected() {
        assertThatThrownBy(new RoadmapProperties(1.4, 0.66, 0.40, 0.30, 0.60, 0.85)::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("topicCoveredThreshold");
    }

    @Test
    void nonIncreasingActionBandsAreRejected() {
        assertThatThrownBy(new RoadmapProperties(0.70, 0.66, 0.40, 0.60, 0.60, 0.85)::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("action bands must be strictly increasing");
    }

    @Test
    void relevanceMediumAboveHighIsRejected() {
        assertThatThrownBy(new RoadmapProperties(0.70, 0.40, 0.66, 0.30, 0.60, 0.85)::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("career-relevance-medium");
    }
}

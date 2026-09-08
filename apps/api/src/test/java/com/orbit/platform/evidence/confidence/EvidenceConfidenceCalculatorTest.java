package com.orbit.platform.evidence.confidence;

import com.orbit.platform.evidence.confidence.EvidenceConfidenceCalculator.Link;
import com.orbit.platform.evidence.model.EvidenceType;
import com.orbit.platform.skills.scoring.SkillScore.Confidence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Direct tests of the evidence → confidence rule (ADR-026 §1) — no Spring, no
 * database. Evidence raises confidence, never a score.
 */
class EvidenceConfidenceCalculatorTest {

    private final EvidenceProperties props = new EvidenceProperties(0.40, 1.00, 2);

    @Test
    void noEvidenceIsSelfReported() {
        assertThat(EvidenceConfidenceCalculator.confidence(List.of(), props))
                .isEqualTo(Confidence.SELF_REPORTED);
    }

    @Test
    void belowThePartialThresholdIsStillSelfReported() {
        assertThat(EvidenceConfidenceCalculator.confidence(
                List.of(new Link(0.3, EvidenceType.REPOSITORY)), props))
                .isEqualTo(Confidence.SELF_REPORTED);
    }

    @Test
    void atOrAboveThePartialThresholdIsPartiallyVerified() {
        assertThat(EvidenceConfidenceCalculator.confidence(
                List.of(new Link(0.4, EvidenceType.REPOSITORY)), props))
                .isEqualTo(Confidence.PARTIALLY_VERIFIED);
        // enough weight for "verified" but only one type -> capped at partially-verified
        assertThat(EvidenceConfidenceCalculator.confidence(List.of(
                new Link(0.7, EvidenceType.REPOSITORY), new Link(0.7, EvidenceType.REPOSITORY)), props))
                .isEqualTo(Confidence.PARTIALLY_VERIFIED);
    }

    @Test
    void fullWeightAcrossTwoTypesIsVerified() {
        assertThat(EvidenceConfidenceCalculator.confidence(List.of(
                new Link(0.6, EvidenceType.REPOSITORY), new Link(0.6, EvidenceType.DEPLOYMENT)), props))
                .isEqualTo(Confidence.VERIFIED);
    }

    @Test
    void strengthIsSummedAndCappedAtOne() {
        assertThat(EvidenceConfidenceCalculator.strength(List.of(
                new Link(0.5, EvidenceType.REPOSITORY), new Link(0.4, EvidenceType.BENCHMARK))))
                .isCloseTo(0.9, within(1e-9));
        assertThat(EvidenceConfidenceCalculator.strength(List.of(
                new Link(0.8, EvidenceType.REPOSITORY), new Link(0.8, EvidenceType.DEPLOYMENT))))
                .isEqualTo(1.0);
    }

    @Test
    void thresholdsAreValidatedAtStartup() {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                new EvidenceProperties(0.4, 1.0, 2)::validate);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                new EvidenceProperties(0.9, 0.5, 2)::validate);   // verified < partial
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                new EvidenceProperties(1.4, 1.0, 2)::validate);   // out of range
    }
}

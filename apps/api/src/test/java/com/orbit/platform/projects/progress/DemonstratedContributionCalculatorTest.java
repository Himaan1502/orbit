package com.orbit.platform.projects.progress;

import com.orbit.platform.projects.model.ProjectStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Direct tests of the demonstrated-contribution rule (ADR-026 §3) — no Spring. */
class DemonstratedContributionCalculatorTest {

    @Test
    void anIncompleteProjectDemonstratesNothing() {
        for (ProjectStatus s : new ProjectStatus[] {
                ProjectStatus.PLANNED, ProjectStatus.ACTIVE, ProjectStatus.ARCHIVED}) {
            assertThat(DemonstratedContributionCalculator.demonstrated(s, 0.9, 0.9)).isZero();
        }
    }

    @Test
    void aCompletedProjectDemonstratesTheEvidenceCappedAtWhatWasExpected() {
        // evidence exceeds expectation -> capped at expected
        assertThat(DemonstratedContributionCalculator.demonstrated(ProjectStatus.COMPLETED, 0.6, 0.9))
                .isEqualTo(0.6);
        // evidence below expectation -> the evidence figure
        assertThat(DemonstratedContributionCalculator.demonstrated(ProjectStatus.COMPLETED, 0.8, 0.3))
                .isEqualTo(0.3);
        // completed but no evidence -> still nothing demonstrated
        assertThat(DemonstratedContributionCalculator.demonstrated(ProjectStatus.COMPLETED, 0.8, 0.0))
                .isZero();
    }

    @Test
    void isRoundedToTwoDecimalsForTheNumeric32Column() {
        assertThat(DemonstratedContributionCalculator.demonstrated(ProjectStatus.COMPLETED, 1.0, 0.667))
                .isEqualTo(0.67);
    }
}

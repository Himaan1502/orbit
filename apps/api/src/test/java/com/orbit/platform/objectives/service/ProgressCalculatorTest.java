package com.orbit.platform.objectives.service;

import com.orbit.platform.objectives.model.MilestoneStatus;
import com.orbit.platform.objectives.model.ObjectiveStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Direct tests of the progress rollup (ADR-016 §3) — no Spring, no database. */
class ProgressCalculatorTest {

    @Test
    void milestoneCompletionDrivesProgressWhenMilestonesExist() {
        var r = ProgressCalculator.forObjective(
                List.of(MilestoneStatus.COMPLETED, MilestoneStatus.COMPLETED,
                        MilestoneStatus.IN_PROGRESS, MilestoneStatus.PENDING),
                0, 0, 0, ObjectiveStatus.ACTIVE);
        assertThat(r.progress()).isEqualByComparingTo("0.500");
        assertThat(r.derivedStatus()).isEqualTo(ObjectiveStatus.ACTIVE);
    }

    @Test
    void allMilestonesCompleteAutoCompletesTheObjective() {
        var r = ProgressCalculator.forObjective(
                List.of(MilestoneStatus.COMPLETED, MilestoneStatus.COMPLETED),
                0, 0, 0, ObjectiveStatus.ACTIVE);
        assertThat(r.progress()).isEqualByComparingTo("1.000");
        assertThat(r.derivedStatus()).isEqualTo(ObjectiveStatus.COMPLETED);
    }

    @Test
    void reopeningWorkReturnsACompletedObjectiveToActive() {
        var r = ProgressCalculator.forObjective(
                List.of(MilestoneStatus.COMPLETED, MilestoneStatus.PENDING),
                0, 0, 0, ObjectiveStatus.COMPLETED);
        assertThat(r.derivedStatus()).isEqualTo(ObjectiveStatus.ACTIVE);
    }

    @Test
    void withoutMilestonesProgressComesFromTasks() {
        var r = ProgressCalculator.forObjective(List.of(), 4, 2, 3, ObjectiveStatus.ACTIVE);
        assertThat(r.progress()).isEqualByComparingTo("0.750");   // 3 of 4 resolved, ≥1 done
    }

    @Test
    void anAllSkippedObjectiveIsNotProgressing() {
        var r = ProgressCalculator.forObjective(List.of(), 3, 0, 3, ObjectiveStatus.ACTIVE);
        assertThat(r.progress()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void emptyObjectiveIsZero() {
        var r = ProgressCalculator.forObjective(List.of(), 0, 0, 0, ObjectiveStatus.ACTIVE);
        assertThat(r.progress()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(r.derivedStatus()).isEqualTo(ObjectiveStatus.ACTIVE);
    }

    @Test
    void archivedIsNeverAutoChanged() {
        var r = ProgressCalculator.forObjective(
                List.of(MilestoneStatus.COMPLETED), 0, 0, 0, ObjectiveStatus.ARCHIVED);
        assertThat(r.derivedStatus()).isEqualTo(ObjectiveStatus.ARCHIVED);
    }

    @Test
    void milestoneProgressIsDoneOverTotal() {
        assertThat(ProgressCalculator.milestoneProgress(4, 1)).isEqualByComparingTo("0.250");
        assertThat(ProgressCalculator.milestoneProgress(0, 0)).isEqualByComparingTo(BigDecimal.ZERO);
    }
}

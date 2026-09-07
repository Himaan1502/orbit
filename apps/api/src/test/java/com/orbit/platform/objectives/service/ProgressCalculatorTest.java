package com.orbit.platform.objectives.service;

import com.orbit.platform.objectives.model.MilestoneStatus;
import com.orbit.platform.objectives.model.ObjectiveStatus;
import com.orbit.platform.objectives.service.ProgressCalculator.TaskCounts;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Direct tests of the progress rollup (ADR-016 §3) — no Spring, no database. */
class ProgressCalculatorTest {

    private static final TaskCounts NO_LOOSE = TaskCounts.none();

    @Test
    void milestoneCompletionDrivesProgressWhenOnlyMilestonesExist() {
        var r = ProgressCalculator.forObjective(
                List.of(MilestoneStatus.COMPLETED, MilestoneStatus.COMPLETED,
                        MilestoneStatus.IN_PROGRESS, MilestoneStatus.PENDING),
                NO_LOOSE, ObjectiveStatus.ACTIVE);
        assertThat(r.progress()).isEqualByComparingTo("0.500");
        assertThat(r.derivedStatus()).isEqualTo(ObjectiveStatus.ACTIVE);
    }

    @Test
    void allMilestonesCompleteAutoCompletesTheObjective() {
        var r = ProgressCalculator.forObjective(
                List.of(MilestoneStatus.COMPLETED, MilestoneStatus.COMPLETED),
                NO_LOOSE, ObjectiveStatus.ACTIVE);
        assertThat(r.progress()).isEqualByComparingTo("1.000");
        assertThat(r.derivedStatus()).isEqualTo(ObjectiveStatus.COMPLETED);
    }

    @Test
    void reopeningWorkReturnsACompletedObjectiveToActive() {
        var r = ProgressCalculator.forObjective(
                List.of(MilestoneStatus.COMPLETED, MilestoneStatus.PENDING),
                NO_LOOSE, ObjectiveStatus.COMPLETED);
        assertThat(r.derivedStatus()).isEqualTo(ObjectiveStatus.ACTIVE);
    }

    @Test
    void withoutMilestonesProgressComesFromLooseTasks() {
        var r = ProgressCalculator.forObjective(List.of(), new TaskCounts(4, 2, 3), ObjectiveStatus.ACTIVE);
        assertThat(r.progress()).isEqualByComparingTo("0.750");   // 3 of 4 resolved, ≥1 done
    }

    @Test
    void anAllSkippedTaskSetIsNotProgressing() {
        var r = ProgressCalculator.forObjective(List.of(), new TaskCounts(3, 0, 3), ObjectiveStatus.ACTIVE);
        assertThat(r.progress()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void emptyObjectiveIsZero() {
        var r = ProgressCalculator.forObjective(List.of(), NO_LOOSE, ObjectiveStatus.ACTIVE);
        assertThat(r.progress()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(r.derivedStatus()).isEqualTo(ObjectiveStatus.ACTIVE);
    }

    @Test
    void archivedIsNeverAutoChanged() {
        var r = ProgressCalculator.forObjective(
                List.of(MilestoneStatus.COMPLETED), NO_LOOSE, ObjectiveStatus.ARCHIVED);
        assertThat(r.derivedStatus()).isEqualTo(ObjectiveStatus.ARCHIVED);
    }

    // --- mixed milestone + loose-task objectives (ADR-016 §3, 2026-09-07 amendment) ---

    @Test
    void looseTasksCountAsOneSyntheticUnitAlongsideMilestones() {
        // 1 of 2 milestones complete, plus a fully-done loose set:
        // units = 2 + 1 = 3; (1 completed milestone + 1.0 loose unit) / 3 = 0.667
        var r = ProgressCalculator.forObjective(
                List.of(MilestoneStatus.COMPLETED, MilestoneStatus.PENDING),
                new TaskCounts(2, 2, 2), ObjectiveStatus.ACTIVE);
        assertThat(r.progress()).isEqualByComparingTo("0.667");
    }

    @Test
    void partiallyDoneLooseTasksContributeAFractionalUnit() {
        // 1 of 1 milestone complete, loose set half done:
        // units = 1 + 1 = 2; (1 + 0.5) / 2 = 0.75
        var r = ProgressCalculator.forObjective(
                List.of(MilestoneStatus.COMPLETED),
                new TaskCounts(4, 2, 2), ObjectiveStatus.ACTIVE);
        assertThat(r.progress()).isEqualByComparingTo("0.750");
        assertThat(r.derivedStatus()).isEqualTo(ObjectiveStatus.ACTIVE);
    }

    @Test
    void objectiveWithMilestonesAndUnfinishedLooseTasksIsNotComplete() {
        // every milestone done, but loose work outstanding -> < 1.0, stays active
        var r = ProgressCalculator.forObjective(
                List.of(MilestoneStatus.COMPLETED, MilestoneStatus.COMPLETED),
                new TaskCounts(2, 0, 0), ObjectiveStatus.ACTIVE);
        assertThat(r.progress()).isEqualByComparingTo("0.667");   // (2 + 0) / 3
        assertThat(r.derivedStatus()).isEqualTo(ObjectiveStatus.ACTIVE);
    }

    @Test
    void milestoneProgressIsDoneOverTotal() {
        assertThat(ProgressCalculator.milestoneProgress(4, 1)).isEqualByComparingTo("0.250");
        assertThat(ProgressCalculator.milestoneProgress(0, 0)).isEqualByComparingTo(BigDecimal.ZERO);
    }
}

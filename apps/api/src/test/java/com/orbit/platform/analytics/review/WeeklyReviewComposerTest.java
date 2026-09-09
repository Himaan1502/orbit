package com.orbit.platform.analytics.review;

import com.orbit.platform.analytics.review.WeeklyReviewComposer.ObjectiveMove;
import com.orbit.platform.analytics.review.WeeklyReviewComposer.ReadinessMove;
import com.orbit.platform.analytics.review.WeeklyReviewComposer.SkillMove;
import com.orbit.platform.analytics.review.WeeklyReviewComposer.WeeklyInputs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The rule-based composer is pure and never infers a cause it can't point to (ADR-028 §2). */
class WeeklyReviewComposerTest {

    private WeeklyInputs inputs(List<SkillMove> skills, List<ReadinessMove> readiness,
                                List<ObjectiveMove> objectives, int completed, int planned) {
        return new WeeklyInputs(completed, planned, 1, 0, 0, skills, readiness, objectives,
                List.of(), List.of());
    }

    @Test
    void happenedCountsAndSummaryAreBuiltFromTheInputs() {
        var c = WeeklyReviewComposer.compose(inputs(List.of(), List.of(), List.of(), 7, 11));
        assertThat(c.content().happened().tasksCompleted()).isEqualTo(7);
        assertThat(c.content().happened().tasksPlanned()).isEqualTo(11);
        assertThat(c.summary()).isEqualTo("Completed 7 of 11 planned tasks; logged 1 evidence.");
    }

    @Test
    void aSkillThatRoseBecomesAnImprovementWithAMechanicalBasis() {
        UUID k8s = UUID.randomUUID();
        var c = WeeklyReviewComposer.compose(inputs(
                List.of(new SkillMove(k8s, "Kubernetes", 0.30, 0.78)), List.of(), List.of(), 3, 3));

        assertThat(c.content().improved()).anySatisfy(i -> {
            assertThat(i.kind()).isEqualTo("skill");
            assertThat(i.label()).isEqualTo("Kubernetes");
            assertThat(i.delta()).isEqualTo(0.48);
        });
        assertThat(c.content().why()).anySatisfy(w -> {
            assertThat(w.claim()).contains("Kubernetes capability +48%");
            assertThat(w.basis()).contains("30%").contains("78%");   // points to the actual scores
        });
    }

    @Test
    void aSkillThatDidNotMoveProducesNoImprovementOrWhy() {
        UUID s = UUID.randomUUID();
        var c = WeeklyReviewComposer.compose(inputs(
                List.of(new SkillMove(s, "Flat", 0.5, 0.5)), List.of(), List.of(), 1, 1));
        assertThat(c.content().improved()).isEmpty();
        assertThat(c.content().why()).isEmpty();
    }

    @Test
    void anObjectiveStatusFlipIsRecordedUnderWhatChanged() {
        var c = WeeklyReviewComposer.compose(inputs(List.of(), List.of(),
                List.of(new ObjectiveMove("Ship it", 0.6, 1.0, "active", "completed")), 4, 4));
        assertThat(c.content().changed()).anySatisfy(ch -> {
            assertThat(ch.kind()).isEqualTo("objective");
            assertThat(ch.from()).isEqualTo("active");
            assertThat(ch.to()).isEqualTo("completed");
        });
        assertThat(c.content().improved()).anySatisfy(i -> assertThat(i.kind()).isEqualTo("objective"));
    }

    @Test
    void readinessRiseSurfacesInBothTheSummaryAndImprovements() {
        var c = WeeklyReviewComposer.compose(inputs(List.of(),
                List.of(new ReadinessMove("Platform Engineer", 0.40, 0.52)), List.of(), 2, 5));
        assertThat(c.summary()).contains("Platform Engineer readiness +12%");
        assertThat(c.content().improved()).anySatisfy(i -> assertThat(i.kind()).isEqualTo("readiness"));
    }
}

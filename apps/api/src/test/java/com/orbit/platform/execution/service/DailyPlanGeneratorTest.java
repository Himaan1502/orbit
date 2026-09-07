package com.orbit.platform.execution.service;

import com.orbit.platform.execution.service.DailyPlanGenerator.Candidate;
import com.orbit.platform.execution.service.DailyPlanGenerator.Request;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Direct tests of the daily-plan selection (ADR-015 §2) — no Spring, no database. */
class DailyPlanGeneratorTest {

    private final DailyPlanGenerator generator = new DailyPlanGenerator();
    private final LocalDate date = LocalDate.of(2026, 9, 7);
    private final Instant t0 = Instant.parse("2026-09-01T00:00:00Z");

    private Candidate task(String score, int effort) {
        return new Candidate(UUID.randomUUID(), new BigDecimal(score), null, t0, effort, false);
    }

    @Test
    void picksHighestPriorityTasksThatFitTheBudget() {
        var plan = generator.generate(new Request(date, 90, List.of(
                task("0.9", 45), task("0.8", 30), task("0.4", 60), task("0.3", 15))));

        assertThat(plan.entries()).hasSize(3);
        assertThat(plan.allocatedMinutes()).isEqualTo(90);
        assertThat(plan.entries().get(0).allocatedMinutes()).isEqualTo(45);   // 0.9
        assertThat(plan.entries().get(1).allocatedMinutes()).isEqualTo(30);   // 0.8
        assertThat(plan.entries().get(2).allocatedMinutes()).isEqualTo(15);   // 0.3 (0.4/60 skipped — too big)
    }

    @Test
    void allocatesTheRemainingBudgetToAnOversizeTopTaskThenStops() {
        var plan = generator.generate(new Request(date, 50, List.of(
                task("0.9", 120), task("0.5", 20))));

        assertThat(plan.entries()).hasSize(1);
        assertThat(plan.entries().get(0).allocatedMinutes()).isEqualTo(50);   // partial session
    }

    @Test
    void skipsAnOversizeTaskWhenTheGapIsBelowTheMinimumPartial() {
        var plan = generator.generate(new Request(date, 40, List.of(
                task("0.9", 35), task("0.8", 90), task("0.7", 5))));

        // 35 taken (remaining 5 < 15 min partial floor) -> 0.8/90 skipped -> 0.7/5 fits
        assertThat(plan.entries()).hasSize(2);
        assertThat(plan.allocatedMinutes()).isEqualTo(40);
    }

    @Test
    void neverReturnsAnEmptyPlanWhenThereIsWork() {
        var plan = generator.generate(new Request(date, 10, List.of(task("0.9", 120))));
        assertThat(plan.entries()).hasSize(1);
        assertThat(plan.entries().get(0).allocatedMinutes()).isEqualTo(10);
    }

    @Test
    void completedTasksKeepTheirSlotAndTheirTimeCountsAsSpent() {
        UUID doneId = UUID.randomUUID();
        var plan = generator.generate(new Request(date, 60, List.of(
                new Candidate(doneId, new BigDecimal("0.3"), null, t0, 40, true),
                task("0.9", 30))));

        assertThat(plan.entries().get(0).taskId()).isEqualTo(doneId);
        assertThat(plan.entries().get(0).allocatedMinutes()).isEqualTo(40);
        assertThat(plan.entries().get(1).allocatedMinutes()).isEqualTo(20);   // 60 - 40 = 20 left for 0.9
    }

    @Test
    void isDeterministic() {
        Request r = new Request(date, 75, List.of(task("0.5", 30), task("0.5", 30), task("0.5", 30)));
        assertThat(generator.generate(r)).isEqualTo(generator.generate(r));
    }
}

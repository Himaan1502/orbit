package com.orbit.platform.analytics.review;

import com.orbit.platform.common.jobs.JobResult;
import com.orbit.platform.common.jobs.JobRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;

/**
 * The one scheduled job in Milestone 6 (ADR-029 §2). Every Monday it generates
 * last week's review for each user with activity in it, through {@link JobRunner}
 * (idempotent on the ISO-week key, observable, retried on the next trigger if it
 * failed). The lazy-on-GET and {@code POST /generate} paths share the same
 * {@link WeeklyReviewService} composer — no divergence.
 */
@Component
public class WeeklyReviewGenerationJob {

    static final String JOB_NAME = "weekly-review-generation";
    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    private final JobRunner runner;
    private final WeeklyReviewService reviews;
    private final Clock clock;

    public WeeklyReviewGenerationJob(JobRunner runner, WeeklyReviewService reviews, Clock clock) {
        this.runner = runner;
        this.reviews = reviews;
        this.clock = clock;
    }

    /** Monday 05:00 UTC. The scheduler thread never sees an exception — {@link JobRunner} absorbs it. */
    @Scheduled(cron = "0 0 5 * * MON", zone = "UTC")
    public void weekly() {
        runForWeekEnding(LocalDate.now(clock).minusWeeks(1));
    }

    /** Extracted so tests drive an explicit week without touching the scheduler. */
    public JobRunner.RunReport runForWeekEnding(LocalDate anyDayLastWeek) {
        String runKey = WeeklyReviewService.isoWeekKey(anyDayLastWeek);
        return runner.run(JOB_NAME, runKey, TIMEOUT,
                () -> JobResult.of(reviews.runForWeek(anyDayLastWeek)));
    }
}

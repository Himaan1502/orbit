package com.orbit.platform.common.jobs;

import com.orbit.platform.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** The ADR-029 job-ledger contract: idempotent, retryable, observable. */
class JobRunnerIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired
    JobRunner runner;

    @Autowired
    JobRunRepository ledger;

    private static final Duration T = Duration.ofMinutes(5);

    @BeforeEach
    void clean() {
        ledger.deleteAll();
    }

    @Test
    void succeedsOnceThenSkipsTheSameRunKey() {
        AtomicInteger calls = new AtomicInteger();

        var first = runner.run("test-job", "2026-W10", T, () -> {
            calls.incrementAndGet();
            return JobResult.of(3);
        });
        var second = runner.run("test-job", "2026-W10", T, () -> {
            calls.incrementAndGet();
            return JobResult.of(3);
        });

        assertThat(first.outcome()).isEqualTo(JobRunner.Outcome.SUCCEEDED);
        assertThat(first.rowsAffected()).isEqualTo(3);
        assertThat(second.outcome()).isEqualTo(JobRunner.Outcome.SKIPPED);
        assertThat(calls.get()).isEqualTo(1);

        assertThat(ledger.findByJobNameAndRunKey("test-job", "2026-W10")).get()
                .satisfies(r -> {
                    assertThat(r.statusEnum()).isEqualTo(JobRun.Status.SUCCEEDED);
                    assertThat(r.getFinishedAt()).isNotNull();
                    assertThat(r.getRowsAffected()).isEqualTo(3);
                });
    }

    @Test
    void aDifferentRunKeyRunsAgain() {
        runner.run("test-job", "2026-W10", T, () -> JobResult.of(1));
        var next = runner.run("test-job", "2026-W11", T, () -> JobResult.of(2));

        assertThat(next.outcome()).isEqualTo(JobRunner.Outcome.SUCCEEDED);
        assertThat(ledger.findTop20ByJobNameOrderByStartedAtDesc("test-job")).hasSize(2);
    }

    @Test
    void aFailedRunIsRecordedAndRetriedOnTheNextTrigger() {
        var failed = runner.run("flaky-job", "k1", T, () -> {
            throw new IllegalStateException("boom");
        });
        assertThat(failed.outcome()).isEqualTo(JobRunner.Outcome.FAILED);
        assertThat(ledger.findByJobNameAndRunKey("flaky-job", "k1")).get()
                .satisfies(r -> {
                    assertThat(r.statusEnum()).isEqualTo(JobRun.Status.FAILED);
                    assertThat(r.getError()).contains("boom");
                });

        // the next trigger for the same key re-attempts
        var retry = runner.run("flaky-job", "k1", T, () -> JobResult.of(5));
        assertThat(retry.outcome()).isEqualTo(JobRunner.Outcome.SUCCEEDED);
        assertThat(ledger.findByJobNameAndRunKey("flaky-job", "k1")).get()
                .satisfies(r -> assertThat(r.statusEnum()).isEqualTo(JobRun.Status.SUCCEEDED));
    }

    @Test
    void theJobBodyExceptionDoesNotPropagateToTheCaller() {
        // the runner swallows it — the scheduler thread must never see a throw
        var r = runner.run("q", "k", T, () -> {
            throw new RuntimeException("scheduler must not see this");
        });
        assertThat(r.outcome()).isEqualTo(JobRunner.Outcome.FAILED);
    }
}

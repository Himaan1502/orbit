package com.orbit.platform.common.jobs;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * The single entry point every scheduled job goes through (ADR-029 §1).
 *
 * <ul>
 *   <li><b>Idempotent</b> — one {@link JobRun} per {@code (jobName, runKey)}; a
 *       key whose run already {@code succeeded} is skipped.</li>
 *   <li><b>Retryable</b> — a {@code failed} run, or a {@code running} run older
 *       than {@code timeout} (a hung previous attempt), is taken over on the
 *       next trigger.</li>
 *   <li><b>Observable</b> — every outcome is a ledger row plus
 *       {@code orbit_job_runs_total{job,outcome}} / {@code orbit_job_duration_seconds{job}}
 *       (Blueprint §38).</li>
 *   <li><b>Bounded</b> — {@code timeout} is the staleness threshold that lets a
 *       later trigger reclaim a stuck run. (An in-flight run is not force-killed;
 *       single-instance, the next Monday trigger reclaims it.)</li>
 *   <li><b>Dup-safe</b> — single instance + the {@code UNIQUE} constraint.
 *       ShedLock is the M10 multi-instance add-on.</li>
 * </ul>
 *
 * The {@code work} supplier owns its own transaction(s); the ledger is written
 * in separate transactions so a job-body rollback never loses the {@code failed}
 * record.
 */
@Component
public class JobRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

    public enum Outcome { SUCCEEDED, FAILED, SKIPPED }

    public record RunReport(Outcome outcome, int rowsAffected, String note) {
    }

    private final JobRunRepository ledger;
    private final MeterRegistry meters;
    private final Clock clock;
    private final TransactionTemplate tx;

    public JobRunner(JobRunRepository ledger, MeterRegistry meters, Clock clock,
                     PlatformTransactionManager txManager) {
        this.ledger = ledger;
        this.meters = meters;
        this.clock = clock;
        this.tx = new TransactionTemplate(txManager);
    }

    /** Run {@code work} once for {@code (jobName, runKey)}, recording the outcome. */
    public RunReport run(String jobName, String runKey, Duration timeout, Supplier<JobResult> work) {
        Instant now = clock.instant();
        JobRun claimed = claim(jobName, runKey, timeout, now);
        if (claimed == null) {
            log.info("event=job.skipped job={} run_key={}", jobName, runKey);
            meters.counter("orbit_job_runs_total", "job", jobName, "outcome", "skipped").increment();
            return new RunReport(Outcome.SKIPPED, 0, "already completed or in flight");
        }

        Timer.Sample sample = Timer.start(meters);
        try {
            JobResult result = work.get();
            long ms = finish(sample, jobName, "succeeded");
            tx.executeWithoutResult(s -> {
                JobRun r = ledger.findById(claimed.getId()).orElseThrow();
                r.succeeded(result.rowsAffected(), clock.instant());
                ledger.save(r);
            });
            log.info("event=job.succeeded job={} run_key={} rows={} duration_ms={} note={}",
                    jobName, runKey, result.rowsAffected(), ms, result.note());
            return new RunReport(Outcome.SUCCEEDED, result.rowsAffected(), result.note());
        } catch (RuntimeException ex) {
            long ms = finish(sample, jobName, "failed");
            tx.executeWithoutResult(s -> {
                JobRun r = ledger.findById(claimed.getId()).orElseThrow();
                r.failed(ex.getClass().getSimpleName() + ": " + ex.getMessage(), clock.instant());
                ledger.save(r);
            });
            log.error("event=job.failed job={} run_key={} duration_ms={}", jobName, runKey, ms, ex);
            return new RunReport(Outcome.FAILED, 0, ex.getMessage());
        }
    }

    /** List the most recent runs of a job — drives an ops view / a health signal. */
    public java.util.List<JobRun> recentRuns(String jobName) {
        return ledger.findTop20ByJobNameOrderByStartedAtDesc(jobName);
    }

    /**
     * Insert (or reclaim) the {@code running} ledger row. Returns {@code null}
     * when the run should be skipped (already {@code succeeded}, or a fresh
     * {@code running} row from a concurrent trigger).
     */
    private JobRun claim(String jobName, String runKey, Duration timeout, Instant now) {
        return tx.execute(s -> {
            JobRun existing = ledger.findByJobNameAndRunKey(jobName, runKey).orElse(null);
            if (existing != null) {
                JobRun.Status st = existing.statusEnum();
                if (st == JobRun.Status.SUCCEEDED) {
                    return null;
                }
                if (st == JobRun.Status.RUNNING && !existing.isStale(now, timeout)) {
                    return null;
                }
                existing.restart(now);
                return ledger.save(existing);
            }
            try {
                return ledger.saveAndFlush(JobRun.start(jobName, runKey, now));
            } catch (DataIntegrityViolationException race) {
                // A concurrent trigger inserted it first — re-read and defer to it.
                JobRun other = ledger.findByJobNameAndRunKey(jobName, runKey).orElse(null);
                if (other != null && other.statusEnum() != JobRun.Status.SUCCEEDED
                        && other.isStale(now, timeout)) {
                    other.restart(now);
                    return ledger.save(other);
                }
                return null;
            }
        });
    }

    private long finish(Timer.Sample sample, String jobName, String outcome) {
        long nanos = sample.stop(meters.timer("orbit_job_duration_seconds", "job", jobName));
        meters.counter("orbit_job_runs_total", "job", jobName, "outcome", outcome).increment();
        return nanos / 1_000_000;
    }
}

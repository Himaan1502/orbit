package com.orbit.platform.common.jobs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One execution of a scheduled job (ADR-029 §1). One row per
 * {@code (jobName, runKey)} — the {@code UNIQUE} constraint is the idempotency
 * guard; {@code status} + timings give retry and observability.
 */
@Entity
@Table(name = "job_runs", schema = "orbit")
@Getter
@NoArgsConstructor
public class JobRun {

    public enum Status { RUNNING, SUCCEEDED, FAILED }

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "job_name", nullable = false, updatable = false, length = 80)
    private String jobName;

    @Column(name = "run_key", nullable = false, updatable = false, length = 120)
    private String runKey;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "rows_affected")
    private Integer rowsAffected;

    @Column(name = "error", columnDefinition = "text")
    private String error;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    static JobRun start(String jobName, String runKey, Instant now) {
        JobRun r = new JobRun();
        r.id = UUID.randomUUID();
        r.jobName = jobName;
        r.runKey = runKey;
        r.status = Status.RUNNING.name().toLowerCase();
        r.startedAt = now;
        r.createdAt = now;
        return r;
    }

    Status statusEnum() {
        return Status.valueOf(status.toUpperCase());
    }

    void succeeded(int rows, Instant now) {
        this.status = Status.SUCCEEDED.name().toLowerCase();
        this.rowsAffected = rows;
        this.finishedAt = now;
        this.error = null;
    }

    void failed(String message, Instant now) {
        this.status = Status.FAILED.name().toLowerCase();
        this.finishedAt = now;
        this.error = message == null ? "(no message)" : message.substring(0, Math.min(message.length(), 4000));
    }

    void restart(Instant now) {
        this.status = Status.RUNNING.name().toLowerCase();
        this.startedAt = now;
        this.finishedAt = null;
        this.error = null;
        this.rowsAffected = null;
    }

    boolean isStale(Instant now, java.time.Duration timeout) {
        return statusEnum() == Status.RUNNING && startedAt.plus(timeout).isBefore(now);
    }
}

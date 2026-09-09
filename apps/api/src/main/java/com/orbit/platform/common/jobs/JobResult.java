package com.orbit.platform.common.jobs;

/**
 * What a job body reports back to {@link JobRunner} — recorded on the ledger row
 * and surfaced as the {@code rows_affected} column.
 */
public record JobResult(int rowsAffected, String note) {

    public static JobResult of(int rowsAffected) {
        return new JobResult(rowsAffected, null);
    }

    public static JobResult nothing() {
        return new JobResult(0, "nothing to do");
    }
}

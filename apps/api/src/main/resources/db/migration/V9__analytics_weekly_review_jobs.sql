-- V9: Milestone 6 — Analytics & Weekly Review.
--   Job infrastructure ....... ADR-029  (job_runs)
--   Analytics ................ ADR-027  (objective_progress_events, tasks.blocked_since,
--                                        metrics, metric_entries — schema only)
--   Weekly Review ............ ADR-028  (weekly_reviews)
--
-- All M6 tables are PER-USER. No seed data. Analytics stores NO derived series
-- (ADR-027) — the only new history tables are objective_progress_events (an
-- event log fed by the existing inline progress recompute) and job_runs.

-- ---------------------------------------------------------------------------
-- orbit.job_runs — the durable / idempotent / observable job ledger (ADR-029 §1).
-- One row per (job_name, run_key). UNIQUE gives dedup; status + timings give
-- retry + observability. ShedLock (multi-instance dup-safety) is an M10 add-on.
-- ---------------------------------------------------------------------------
CREATE TABLE orbit.job_runs (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name      VARCHAR(80)  NOT NULL,
    run_key       VARCHAR(120) NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    started_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at   TIMESTAMPTZ,
    rows_affected INTEGER,
    error         TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_job_runs_name_key UNIQUE (job_name, run_key),
    CONSTRAINT ck_job_runs_status CHECK (status IN ('running', 'succeeded', 'failed'))
);

CREATE INDEX idx_job_runs_name ON orbit.job_runs (job_name, started_at DESC);

COMMENT ON TABLE orbit.job_runs IS
    'ADR-029 — @Scheduled job ledger. run_key is the per-run idempotency key (e.g. an ISO week).';

-- ---------------------------------------------------------------------------
-- orbit.objective_progress_events — append-only progress/status history
-- (ADR-027 §4). Written inside the EXISTING inline ProgressCalculator recompute,
-- only when progress or status actually changes. Powers the objective-health
-- trend. No background job.
-- ---------------------------------------------------------------------------
CREATE TABLE orbit.objective_progress_events (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    objective_id UUID         NOT NULL REFERENCES orbit.objectives (id) ON DELETE CASCADE,
    user_id      UUID         NOT NULL REFERENCES orbit.users (id)      ON DELETE CASCADE,
    progress     NUMERIC(4,3) NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    recorded_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_obj_progress_events_progress CHECK (progress BETWEEN 0 AND 1)
);

CREATE INDEX idx_obj_progress_events_obj  ON orbit.objective_progress_events (objective_id, recorded_at);
CREATE INDEX idx_obj_progress_events_user ON orbit.objective_progress_events (user_id, recorded_at);

-- ---------------------------------------------------------------------------
-- orbit.tasks.blocked_since — set when a task enters 'blocked', cleared when it
-- leaves (ADR-027 §5). Enough for "recurring blockers" / "time spent blocked"
-- without a full task_status_events audit table (a deferred item).
-- ---------------------------------------------------------------------------
ALTER TABLE orbit.tasks ADD COLUMN blocked_since TIMESTAMPTZ;

-- ---------------------------------------------------------------------------
-- orbit.metrics / orbit.metric_entries — Blueprint §15 verbatim. SCHEMA ONLY in
-- M6 (ADR-027 §3): the user-defined / life-module / integration-sourced metric
-- substrate (PRD Phase 5). The M6 career/execution/skill analytics derive from
-- domain data and DO NOT read or write these tables. No M6 API or UI.
-- ---------------------------------------------------------------------------
CREATE TABLE orbit.metrics (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES orbit.users (id) ON DELETE CASCADE,
    key          VARCHAR(80)  NOT NULL,
    name         VARCHAR(120) NOT NULL,
    unit         VARCHAR(32),
    direction    VARCHAR(16),
    target_value NUMERIC,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_metrics_key_nonempty CHECK (key <> ''),
    CONSTRAINT ck_metrics_direction CHECK (direction IS NULL OR direction IN ('up', 'down', 'neutral')),
    CONSTRAINT uq_metrics_user_key UNIQUE (user_id, key)
);

CREATE TABLE orbit.metric_entries (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    metric_id   UUID        NOT NULL REFERENCES orbit.metrics (id) ON DELETE CASCADE,
    value       NUMERIC     NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    source      VARCHAR(32)
);

CREATE INDEX idx_metric_entries_metric ON orbit.metric_entries (metric_id, recorded_at);

COMMENT ON TABLE orbit.metrics IS
    'Blueprint §15 — user-defined / life-module / integration metrics. Schema only in M6 (ADR-027 §3).';

-- ---------------------------------------------------------------------------
-- orbit.weekly_reviews — Blueprint §16 + a content JSONB holding the six PRD §8
-- sections as STRUCTURED DATA (references + deltas, not a copy of the metrics —
-- "keep generated summaries separate from raw metrics"). Rule-based generation
-- in M6 (ADR-028); generated_by reserves 'oscar' for M7+.
-- ---------------------------------------------------------------------------
CREATE TABLE orbit.weekly_reviews (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES orbit.users (id) ON DELETE CASCADE,
    week_start   DATE        NOT NULL,
    week_end     DATE        NOT NULL,
    summary      TEXT,
    content      JSONB       NOT NULL DEFAULT '{}'::jsonb,
    user_notes   TEXT,
    generated_by VARCHAR(16) NOT NULL DEFAULT 'system',
    status       VARCHAR(16) NOT NULL DEFAULT 'generated',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_weekly_reviews_user_week UNIQUE (user_id, week_start),
    CONSTRAINT ck_weekly_reviews_generated_by CHECK (generated_by IN ('system', 'user', 'oscar')),
    CONSTRAINT ck_weekly_reviews_status CHECK (status IN ('generated', 'reviewed')),
    CONSTRAINT ck_weekly_reviews_week CHECK (week_end >= week_start)
);

CREATE INDEX idx_weekly_reviews_user ON orbit.weekly_reviews (user_id, week_start DESC);

COMMENT ON COLUMN orbit.weekly_reviews.user_notes IS
    'The user''s own reflection — never generated, never overwritten by regeneration (ADR-028 §3).';

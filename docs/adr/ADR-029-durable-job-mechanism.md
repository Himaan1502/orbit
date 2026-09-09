# ADR-029: Durable Job Mechanism — `@Scheduled` + a `job_runs` Ledger

## Status
Proposed — Milestone 6. Resolves Blueprint §55 open decision *"durable job
implementation"*, deferred since M0.

## Date
2026-09-09

## Context

Blueprint §55 lists **"durable job implementation"** as an open decision
requiring an explicit ADR *before the relevant milestone*. Tech Arch §11
("Background Jobs") wants "a durable job mechanism appropriate to the Spring
ecosystem" and names the initial jobs:

> roadmap processing · **analytics aggregation** · **weekly review generation** ·
> scheduled reminders · GitHub synchronization · AI insight generation

with the requirements: *idempotent · retryable · observable · bounded by
timeouts · safe against duplicate execution.*

The decision has been deferred three times, each time correctly:
- **M2** — daily-plan generation runs inline; written as a pure service a future
  runner can call directly ([ADR-015](ADR-015-daily-plan-generation.md) §1).
- **M3** — skill scoring recompute, inline.
- **M5** — `project_skills.demonstrated_contribution` recompute, inline
  ([ADR-026](ADR-026-evidence-backed-confidence-and-readiness.md) §3).

Each deferral note said the runner arrives "when analytics aggregation or
scheduled work needs it." **Milestone 6 is that milestone** — it delivers
Analytics and the Weekly Review, and the Weekly Review is a *scheduled,
persisted artifact* (Blueprint §16). This ADR resolves the decision rather than
deferring a fourth time.

### What actually needs a job (and what does not)

Investigation of M6's requirements against the data ORBIT already persists:

| M6 output | Recoverable from persisted data? | Needs a job? |
|---|---|---|
| Execution trend (planned vs completed, throughput, blockers) | `tasks.completed_at` / `status` / `daily_plan_tasks` — all timestamped | **No** — read-time `GROUP BY` |
| Skill progression | `skill_assessments` — append-only since M3 | **No** |
| Career readiness history | recompute the pure `CareerReadinessCalculator` at each past assessment/evidence/project event | **No** — read-time recompute |
| Evidence / project velocity | `evidence.created_at`, `projects.completed_at` | **No** |
| Objective progress over time | `objectives.progress` is **current-only**, overwritten each recompute | **No job** — append `objective_progress_events` inside the *existing* inline recompute ([ADR-027](ADR-027-analytics-domain.md) §4) |
| **Weekly Review** | a *persisted summary* that should be *waiting for the user* each Monday | **Yes** — a scheduled generation job |

So M6 needs **exactly one** scheduled job: weekly-review generation. Everything
else stays derived-at-read-time (the "inline still works" case, made explicit).

## Decision

### 1. The mechanism: Spring `@Scheduled` + an `orbit.job_runs` ledger

**No Quartz, no Spring Batch, no message queue, no Redis job store.** ORBIT is a
single-instance modular monolith through M9. The mechanism is:

**`@EnableScheduling`** (Spring context, zero new dependencies) for the trigger.

**`orbit.job_runs`** — the durability, idempotency and observability substrate:

```text
orbit.job_runs
- id            UUID PK
- job_name      VARCHAR(80)  NOT NULL          -- e.g. 'weekly-review-generation'
- run_key       VARCHAR(120) NOT NULL          -- the idempotency key, e.g. '2026-W36'
- status        VARCHAR(16)  NOT NULL          -- running | succeeded | failed
- started_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
- finished_at   TIMESTAMPTZ
- rows_affected INTEGER
- error         TEXT
- created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
UNIQUE (job_name, run_key)
```

**`JobRunner`** (`common/jobs/`) — the single entry point every job goes through:

```java
JobResult run(String jobName, String runKey, Duration timeout, Supplier<JobResult> work)
```

- Inserts a `running` row for `(job_name, run_key)`. If the unique constraint
  rejects it **and** the existing row is `succeeded`, returns immediately
  (**idempotent** — dup-safe). If the existing row is `failed` or a stale
  `running` (older than `timeout`), it is re-attempted (**retryable**).
- Runs `work` under a wrapper that abandons it past `timeout` (**bounded**).
- On success → `succeeded`, `rows_affected`, `finished_at`. On throw → `failed`,
  `error` (truncated), `finished_at`; the exception is logged, not rethrown to
  the scheduler thread.
- Emits Micrometer `orbit_job_runs_total{job, outcome}` and
  `orbit_job_duration_seconds{job}` — the Blueprint §38 `job_success_total` /
  `job_failure_total` metrics. Extends [ADR-010](ADR-010-observability-stack.md)'s
  metric list.

### 2. The one scheduled job: `WeeklyReviewGenerationJob`

- `@Scheduled(cron = "0 0 5 * * MON", zone = "UTC")` — Monday 05:00 UTC.
- One run per ISO week: `run_key = "<isoYear>-W<isoWeek>"` for the **just-ended**
  week.
- Processes every user with activity in that week (a completed task, a new
  assessment, logged evidence, or a project state change). For each, calls the
  pure `WeeklyReviewComposer` ([ADR-028](ADR-028-weekly-review.md)) and upserts
  one `weekly_reviews` row. Per-user failure is logged and counted; it does not
  fail the whole run (best-effort; a later run or `POST /weekly-reviews/generate`
  recovers a missed user).
- The **same** `WeeklyReviewComposer` is the on-demand path
  (`POST /api/v1/weekly-reviews/generate`) — no separate code path, no
  double-generation (the endpoint also goes through `JobRunner` with the same
  `run_key`, or writes directly with an on-conflict upsert).

### 3. Testability

- The `@Scheduled` method delegates immediately to a plain
  `WeeklyReviewGenerationService.runForWeek(IsoWeek)` — tests call that directly
  with an injected week, no clock manipulation of the scheduler.
- `JobRunner` takes the existing `Clock` bean (`ExecutionConfig.clock()`).
- Testcontainers: run the service twice for the same week → assert one
  `weekly_reviews` row and two `job_runs` rows (second `succeeded` via the
  short-circuit, or one row if the endpoint upserts) — the idempotency contract.

### 4. Multi-instance upgrade path (documented, not built)

When ORBIT runs more than one replica (Blueprint M10 "Production Platform"),
duplicate execution across instances is prevented by adding **ShedLock**:
`net.javacrumbs.shedlock:shedlock-spring` + `shedlock-provider-jdbc-template`,
one `shedlock` table, and `@SchedulerLock(name = "weekly-review-generation")` on
the scheduled method. That is the entire change — ~1 dependency, 1 table, 1
annotation. The `job_runs` ledger and `JobRunner` are unchanged. This upgrade is
a tracked item for M10, not M6.

## Alternatives Considered

1. **Quartz (JDBC job store).** Rejected for M6. Solves dynamic runtime
   scheduling, misfire policies, and clustered execution — none of which ORBIT
   has. Adds a dependency, ~11 tables, and a scheduler lifecycle to operate. The
   `@Scheduled` + ledger approach meets every Tech Arch §11 requirement with far
   less surface; Quartz can replace `@Scheduled` later without touching job
   bodies if genuinely needed.
2. **Spring Batch.** Rejected. Built for chunk-oriented processing of large
   datasets with restart/skip semantics. ORBIT's jobs process a handful of rows
   per user. Wrong tool.
3. **Redis-backed queue (e.g. a Redis list + worker).** Rejected — and
   explicitly disallowed by [ADR-005](ADR-005-redis-usage-boundaries.md): *"Redis
   is NOT used for message queuing (use database-backed jobs instead)."* This ADR
   *is* the database-backed jobs.
4. **Lazy generation only — no scheduler at all.** Considered seriously. The
   Weekly Review for a completed week is deterministic; generating it on first
   view works for one attentive user. Rejected as the primary mechanism because
   (a) the review should be *waiting* Monday morning, not conjured on demand —
   that is the product intent (PRD §8, Journey E); (b) it does not scale past one
   user; (c) a skipped week would leave a permanent hole in the history unless
   backfilled. **Lazy generation is kept as a fallback** (GET on a missing past
   week generates it) but the schedule is the primary path.
5. **`metric_entries` snapshot job for objective progress.** Rejected in favour
   of event-appending inside the existing inline recompute
   ([ADR-027](ADR-027-analytics-domain.md) §4) — no new scheduled machinery, and
   the history is exact rather than daily-sampled.

## Consequences

- **Positive.** Blueprint §55's open decision is **closed**. Jobs are idempotent,
  retryable, observable (`job_runs` + Prometheus), bounded, and dup-safe —
  Tech Arch §11 satisfied. Zero new runtime dependencies for M6. The pure-service
  seam from ADR-015 §1 is finally used as designed. The multi-instance story is
  written down and cheap.
- **Negative.** `@Scheduled` is in-process — if the app is down at 05:00 Monday,
  that week's run is missed until the next trigger or a manual generate. Mitigated
  by the lazy-on-GET fallback and `POST /generate` backfill. A single long-hung
  job could block the scheduler's thread pool — mitigated by the `JobRunner`
  timeout and a dedicated `ThreadPoolTaskScheduler` (pool size 2).
- **Neutral.** `job_runs` grows one row per week per job — negligible; a
  retention trim is a future non-issue.

## Sign-off items

1. **`@Scheduled` + `job_runs` ledger as ORBIT's durable job mechanism** for the
   single-instance phase (not Quartz / Batch / Redis-queue), with ShedLock as the
   named M10 multi-instance upgrade.
2. **One scheduled job in M6** — `WeeklyReviewGenerationJob`, Monday 05:00 UTC —
   with lazy-on-GET + `POST /generate` as the fallback/backfill paths.
3. **Everything else in M6 stays derived-at-read-time** (no analytics rollup
   storage, no snapshot job).

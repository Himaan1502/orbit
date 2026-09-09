# ADR-027: Analytics Domain — Derived Trends, the `metrics` Schema, and Objective Progress History

## Status
Accepted — derived-at-read-time analytics (no rollup storage), readiness trend =
the existing calculator re-run at each user event, `metrics`/`metric_entries`
schema-only in M6, `objective_progress_events` + `tasks.blocked_since` (no full
audit table): all ratified by the product owner 2026-09-09. Milestone 6 (our
numbering; Blueprint "Milestone 8 — Analytics").

## Date
2026-09-09

## Context

Blueprint M8 acceptance: *"analytics derive from persisted events/data; time
ranges are correct; **no fabricated metrics**."* UX §24: *"Analytics should
answer questions, not merely display charts,"* with three core views —
**Execution**, **Career**, **Skills**. Blueprint §15 defines a `metrics` /
`metric_entries` schema. PRD §9: *"Track trends for career velocity, learning
velocity, skill growth, execution consistency, goal completion, project
velocity."* Success criterion: *"demonstrate measurable progress over time."*

What ORBIT already persists that analytics can derive from:
- `skill_assessments` — **append-only** since M3 ([ADR-018](ADR-018-career-readiness.md)).
- `tasks` — `status`, `completed_at`, `created_at`, `due_at`.
- `daily_plans` + `daily_plan_tasks` — `allocated_minutes`, membership per date.
- `evidence` — `occurred_at`, `created_at`; `skill_evidence`, `project_evidence`.
- `projects` — `status`, `started_at`, `completed_at`.
- `milestones` — `status`, timestamps.
- `career_targets` + `career_target_skills`.

What is **not** recoverable: `objectives.progress` is current-only (overwritten
on each inline `ProgressCalculator` recompute — [ADR-016](ADR-016-objective-status-and-progress.md)).

Frozen and untouched: the Priority Engine formula/weights
([ADR-014](ADR-014-priority-engine-v1.md)), the Skill Scoring formula/weights
([ADR-017](ADR-017-skill-scoring-v1.md)), the Career Readiness blend
([ADR-018](ADR-018-career-readiness.md) / [ADR-026](ADR-026-evidence-backed-confidence-and-readiness.md)).
Analytics is **read-only** — it aggregates and re-runs existing calculators over
historical inputs; it never writes a score or changes a formula.

## Decision

### 1. `analytics` domain — read-only, no analytics-fact storage

A new `com.orbit.platform.analytics` package, a leaf consumer at the top of the
dependency graph (like `dashboard`). It reads history through thin **query ports
the `analytics` domain owns and each source domain implements** (the M5
consumer-owned-port pattern): `TaskExecutionHistory` (execution),
`AssessmentHistory` (skills), `EvidenceHistory` (evidence), `ProjectHistory`
(projects), `ObjectiveProgressHistory` (objectives). Each port returns already
time-bucketed or timestamped rows; the aggregation SQL lives in the domain that
owns the table.

Every series is **computed on request**. Nothing is stored. Given the same
underlying rows and time range, an endpoint returns the same numbers
(reproducible, Blueprint §43).

### 2. The three analytics endpoints

All take `from` / `to` (default: last 12 weeks) and bucket by ISO week unless
noted. All are ownership-scoped to the caller.

**`GET /api/v1/analytics/execution`**
- planned vs completed per week (from `daily_plan_tasks` ∪ `tasks.completed_at`)
- completion rate + rolling throughput (tasks done / week)
- tasks by current status (snapshot bar)
- recurring blockers — tasks that entered `blocked` (needs a lightweight signal:
  see §5)
- focused minutes per week — `Σ daily_plan_tasks.allocated_minutes` for tasks
  completed that week

**`GET /api/v1/analytics/career?careerTargetId=`**
- **readiness trend** — resolves the `ReadinessResponse.trend: null` stub
  ([ADR-018](ADR-018-career-readiness.md)). Sampled at each event that could
  change readiness for the target (a new assessment / evidence link / project
  completion touching a target skill). At each sample instant *T*, re-run the
  **existing** `CareerReadinessCalculator` with the as-of state:
  - capability: latest `skill_assessments` row per skill with `assessed_at ≤ T`
  - evidence coverage: `skill_evidence` whose evidence `created_at ≤ T`
  - project coverage: `demonstrated_contribution` of projects with
    `completed_at ≤ T` (demonstrated is only non-zero once completed — ADR-026
    §3 — so completion date is the correct reconstruction point)
  The line is a **step function** between samples. Same formula, same weights,
  same `fingerprint`.
- skill-gap closure — `Σ importance·(1−capability)` for the target over time
- evidence growth — count of target-skill-linked evidence per week
- target progression — % of target skills at capability ≥ 0.7 over time

**`GET /api/v1/analytics/skills`**
- per-skill progression — `overall_score` from `skill_assessments` over time
  (the assessed skills only)
- neglected skills — target/roadmap skills with no assessment in ≥ N days
- high-leverage skills — reuse the M3 `SkillGraph.leverage`
- dependency bottlenecks — unassessed prerequisites blocking assessed dependents

**Empty is explicit.** A metric with no underlying data returns
`{ points: [], status: "no-data-yet" }` — never a zero-filled line. "No
fabricated metrics" (Blueprint M8).

### 3. `metrics` / `metric_entries` — schema only, no M6 API

V9 creates Blueprint §15's tables **verbatim** (`metrics`, `metric_entries`).
They are the substrate for **user-defined / life-module / integration-sourced
metrics** (Blueprint §15 "other selected life metrics"; PRD Phase 5). The M6
career/execution/skill analytics **do not read or write them** — those derive
from domain data per the M8 acceptance. No CRUD endpoint and no UI for
`metrics` in M6; that lands with Balance / integrations. Creating the tables now
keeps the schema complete and gives OSCAR (M7+) a defined shape to read.

### 4. Objective progress history — `objective_progress_events`, event-appended (no job)

V9 adds:
```text
orbit.objective_progress_events
- id           UUID PK
- objective_id UUID NOT NULL REFERENCES orbit.objectives(id) ON DELETE CASCADE
- user_id      UUID NOT NULL REFERENCES orbit.users(id)      ON DELETE CASCADE
- progress     NUMERIC(4,3) NOT NULL
- status       VARCHAR(20)  NOT NULL
- recorded_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
```
Appended **inside the existing inline `ProgressCalculator` recompute** in
`ObjectiveService` / the task-completion path — one row whenever an objective's
progress or status actually changes (skip no-op recomputes). No background job
(this is the [ADR-029](ADR-029-durable-job-mechanism.md) §Alternatives choice).
Powers the objective-health trend on Analytics and Command Center.

### 5. The "blocked" signal

`tasks` has no history of status transitions. Rather than a full audit table,
M6 adds a nullable `tasks.blocked_since TIMESTAMPTZ` set when a task moves to
`blocked` and cleared when it leaves — enough for "recurring blockers" and
"time spent blocked". A full `task_status_events` table is a deferred item if
richer execution analytics are needed.

### 6. Caching

If a 12-week range query is measurably slow, results are cached in Redis with a
short TTL keyed by `(user, endpoint, from, to)` and evicted on any write to the
underlying domain — [ADR-005](ADR-005-redis-usage-boundaries.md) bullet 2
("caching frequently-read, expensive-to-compute aggregations"), the first real
use of Redis in `apps/api`. Measure first; do not cache pre-emptively.

## Alternatives Considered

1. **Write derived analytics into `metric_entries` via a nightly job.** Rejected
   — the M8 acceptance says analytics *derive from* persisted data; a nightly
   snapshot would be a lossy copy that can drift from source and needs the job
   machinery ADR-029 argues against for M6. Read-time derivation over
   append-only source rows is exact.
2. **A generic `analytics_facts` star schema.** Rejected as premature — one user,
   modest data volume, and the source tables are already well-indexed and
   timestamped. Revisit if query cost becomes real.
3. **Full `task_status_events` audit table now.** Deferred — `blocked_since` +
   `completed_at` cover the M6 execution views; a general transition log is only
   worth it when analytics need it.
4. **Interpolated readiness line instead of a step function.** Rejected —
   interpolation invents values between events. A step function is the truthful
   representation of "the number was X until you did Y."

## Consequences

- **Positive.** Blueprint M8 acceptance met with zero fabricated metrics and no
  new storage for derived series. The `trend: null` stub is resolved. Objective
  progress history is exact (event-sourced) and free (piggybacks the existing
  recompute). `metrics`/`metric_entries` schema is in place for later.
- **Negative.** Readiness-trend reconstruction is only as granular as the user's
  own events and approximates the evidence/project components to their
  create/complete dates — documented, not hidden. A wide time range with many
  assessments does more compute per request (bounded by the cache).
- **Neutral.** `objective_progress_events` and `job_runs` grow slowly; retention
  is a non-issue for the foreseeable term.

## Sign-off items

1. **Derived-at-read-time analytics, no rollup storage** — the three endpoints,
   time-bucketed, empty-is-explicit.
2. **Readiness trend = the existing calculator re-run at each user event**
   (step function; evidence/project components reconstructed to their
   create/complete dates). Formula/weights/`fingerprint` unchanged.
3. **`metrics` / `metric_entries` — V9 schema only, no M6 API/UI.**
4. **`objective_progress_events`** appended inside the existing inline recompute;
   **`tasks.blocked_since`** for the blocked signal (no full audit table in M6).

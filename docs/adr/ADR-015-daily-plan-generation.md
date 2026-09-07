# ADR-015: Daily Plan Generation — Inline, Pure Service

## Status
Accepted

## Date
2026-09-07

## Context
Blueprint §10 defines `daily_plans` + `daily_plan_tasks`. PRD §4.8: "Generate
daily plans from objectives, deadlines, skill gaps, dependencies, project state,
historical execution and available time." Blueprint §20 API:
`GET /api/v1/daily-plans/{date}`, `POST /api/v1/daily-plans/generate`,
`POST /api/v1/daily-plans/{id}/regenerate`. Blueprint §33 lists
**"background-job implementation"** as an open decision. Blueprint §11 (Tech Arch
§11) wants "a durable job mechanism appropriate to the Spring ecosystem" for
jobs like "analytics aggregation".

The Milestone 2 brief: generation runs **inline (synchronous)** for M2, written
as a **pure service method a future durable job runner could invoke directly**,
not embedded in a request handler.

## Decision

### 1. Generation is a pure application service, invoked inline
`DailyPlanGenerator.generate(GenerationRequest)` → `GeneratedPlan` is a pure
function of its inputs (the user's open tasks, their priority scores, the
available-minutes budget, the plan date, a reference instant). It performs **no
I/O** — it does not read repositories or the security context. Callers assemble
the `GenerationRequest` and persist the `GeneratedPlan`.

`DailyPlanService` (transactional, ownership-aware) is the only inline caller in
M2: it loads the current user's candidate tasks + profile, calls the generator,
and upserts `daily_plans` / `daily_plan_tasks`. A future
`DailyPlanRefreshJob` (durable runner) would call the **same**
`DailyPlanGenerator.generate(...)` with a `GenerationRequest` it assembled for a
given user id — no extraction, no refactor.

### 2. The M2 selection algorithm (deterministic)
Given candidate tasks (status `todo` or `in_progress`, not deleted, belonging to
a non-archived objective) sorted by `priority_score` desc, then `due_at` asc
nulls last, then `created_at` asc:

1. Walk the sorted list, greedily adding a task while
   `Σ effort ≤ availableMinutes`. `effort = estimated_minutes` (default 30 when
   unset).
2. A task whose effort alone exceeds the remaining budget is **allocated the
   remaining budget** (a partial session) if at least 15 minutes remain, then
   selection stops; otherwise it is skipped and selection continues with smaller
   tasks (one look-ahead).
3. `position` follows selection order. `allocated_minutes` is the effort, capped
   at the remaining budget for the last task.
4. At least one task is always included if any candidate exists (the top task,
   even if over budget) — the plan is never empty when there is work to do.

`availableMinutes` = `user_profiles.available_daily_minutes`, default **120** when
unset (documented default, overridable via the request body on `generate`).

### 3. Idempotency & regenerate
- One plan per `(user_id, plan_date)` — DB unique constraint.
- `POST /generate` with a date that already has a plan returns the existing plan
  unchanged (`200`, not a duplicate) unless `?force=true`.
- `POST /{id}/regenerate` recomputes task priority scores first, then rebuilds
  `daily_plan_tasks` from scratch. Tasks already marked `done` in the current
  plan keep their slot (their `allocated_minutes` still counts as "spent") so
  regenerating mid-day does not drop completed work.
- `generated_by` = `system` for engine output; a manually curated plan (future)
  would set `user`.

### 4. Task completion updates progress immediately (UX §33, Blueprint §33)
`POST /api/v1/tasks/{id}/complete` sets `status=done`, `completed_at=now`, and
**inline** recomputes the parent milestone's status (all sibling tasks done →
milestone `completed`) and the parent objective's `progress` (fraction of
non-deleted milestones completed; if an objective has no milestones, fraction of
its non-deleted tasks done). This rollup is a pure `ProgressCalculator` given the
milestone/task sets — see [ADR-016](ADR-016-objective-status-and-progress.md).

### 5. Durable job mechanism — deferred
No Quartz / Spring Batch / DB-polling runner in M2. Deferred until analytics
aggregation (Blueprint §9, §15) needs scheduled background work — that milestone
picks the mechanism in its own ADR. Until then every "generation" is a user
action through the API and completes within the request. Recorded in
`deferred-items.md`.

## Alternatives Considered
1. **Generate inside the controller method** — rejected by the brief; would need
   extraction when a job runner arrives, and couples HTTP concerns to planning
   logic.
2. **Knapsack / optimal packing** for the budget — rejected for v1: greedy by
   priority is explainable ("we picked the highest-priority tasks that fit"),
   deterministic, and O(n log n). Optimal packing is a v2 refinement if users
   complain the plan wastes time.
3. **Store the plan as a denormalized JSON blob** — rejected; `daily_plan_tasks`
   is in Blueprint §10 and the relational form lets completion/reordering update
   rows without rewriting the plan.
4. **Async generation with a "generating…" status** — rejected for M2 (adds the
   job runner this ADR defers, and inline generation is fast for one user).

## Consequences
- **Positive:** the generation algorithm is unit-testable in isolation (no DB, no
  Spring context); the API contract matches Blueprint §20; moving to a durable
  runner later is additive.
- **Positive:** completion → progress is immediate, matching UX §33.
- **Negative:** a very large task backlog makes `generate` a slightly heavier
  request (priority recompute is O(tasks)). Acceptable for a single user;
  becomes the trigger to introduce the deferred job runner.
- **Negative:** greedy selection can leave a small unfillable gap at the end of
  the budget. Accepted for v1 (see alternative 2).

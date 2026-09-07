# ADR-016: Objective / Milestone / Task Status Model & Progress Rollup

## Status
Accepted

## Date
2026-09-07

## Context
Blueprint §9 gives `objectives.status VARCHAR`, `objectives.priority INTEGER`,
`objectives.progress NUMERIC`, and `milestones.status VARCHAR` with **no
enumerated values**. Blueprint §10 gives `tasks.status VARCHAR`. UX §17 lists
task states as "Not started, In progress, Blocked, Completed, Skipped". UX §18
shows an objective with "progress" and "milestone health"; UX §33 / Blueprint §33
require task completion to "immediately update relevant objective … progress".

Milestone 1 already shipped `orbit.objectives.status CHECK IN
('active','completed','archived')`. That must not be second-guessed.

This ADR fills the enumerated-value gap and defines the progress rollup.

## Decision

### 1. Status vocabularies

| Entity | Values | Notes |
|--------|--------|-------|
| **objective** | `active` · `completed` · `archived` | Unchanged from M1. `completed` is set manually or auto when progress reaches 1.0 (§3). `archived` = soft-hidden, excluded from planning and the dashboard. |
| **milestone** | `pending` · `in_progress` · `completed` | `in_progress` is set manually or auto when a first child task is completed; `completed` auto when all non-deleted child tasks are `done`/`skipped` **and at least one is `done`**, or set manually. |
| **task** | `todo` · `in_progress` · `blocked` · `done` · `skipped` | Maps UX §17: "Not started"→`todo`, "Completed"→`done`. `done` and `skipped` are terminal (via `POST /complete` and a `PATCH status=skipped`). |

Persisted lower-case snake/flat tokens with DB `CHECK` constraints. The API
accepts and returns exactly these tokens. Java enums back them
(`@Enumerated(EnumType.STRING)`).

### 2. Objective `priority` scale
`INTEGER 1..5` where **5 = highest**, default **3**. DB `CHECK (priority BETWEEN
1 AND 5)`. Chosen over 0-based so the UI can show "P1..P5" naturally and the
Priority Engine's proxy maps `1→0.2 … 5→1.0` linearly
([ADR-014](ADR-014-priority-engine-v1.md) §3).

### 3. Progress rollup — `ProgressCalculator` (pure)
`objective.progress ∈ [0, 1]`, `NUMERIC(4,3)`, **derived, never set directly via
the API**:

- If the objective has **≥ 1 non-deleted milestone**:
  `progress = completedMilestones / totalNonDeletedMilestones`.
- Else if it has **≥ 1 non-deleted task**:
  `progress = (done + skipped) tasks / totalNonDeletedTasks` — but `skipped`
  contributes only if at least one task is `done` (an all-skipped objective is
  not "progressing").
- Else `progress = 0`.

Recomputed inline (in the same transaction) whenever a milestone's status
changes, a task is completed/skipped/reopened, or a milestone/task is
created/deleted. When `progress` reaches `1.0` and the objective is `active`, it
auto-transitions to `completed`; if it later drops below `1.0` (a task
reopened), it returns to `active`.

`ProgressCalculator.forObjective(List<Milestone>, List<Task>)` takes the loaded
collections and returns `{ progress, derivedObjectiveStatus }` — no I/O, unit
tested directly.

### 4. Milestone progress
Milestones expose a computed `progress` in their API response
(`doneTasks / totalNonDeletedTasks`, `0` when none) but do **not** persist it —
it is cheap to compute and there is no planning input that needs it stored.

### 5. Ownership
Milestones and tasks have no `user_id` of their own is **not** the choice —
`tasks` carries `user_id` (Blueprint §10). `milestones` do **not** (Blueprint §9);
a milestone's owner is its objective's owner. Every milestone/task lookup is
therefore scoped by joining to the owning objective / by `tasks.user_id`, and a
cross-user request returns **404** (the M1 pattern — existence-disclosure
hardening, Tech Arch §9), consistently for objectives, milestones, tasks and
daily plans.

## Alternatives Considered
1. **Persist milestone progress** — rejected; recompute is trivial and one less
   field to keep consistent.
2. **Store `objective.progress` as 0–100** — rejected; `NUMERIC` 0–1 matches
   Blueprint §9's `progress NUMERIC` idiom and the Priority Engine's 0–1 factors.
3. **Free-text status** — rejected; `CHECK` constraints + enums prevent typos and
   make planning queries (`status = 'active'`) safe.
4. **Give milestones their own `user_id`** — rejected; redundant with the
   objective FK and a denormalization risk. The join is indexed.

## Consequences
- **Positive:** deterministic, testable progress; task completion visibly moves
  objective progress (UX §33); statuses are constrained at the DB.
- **Negative:** completing a task touches up to three rows (task, milestone,
  objective) in one transaction. Fine at single-user scale; the rollup is
  O(milestones + tasks of one objective).
- **Negative:** auto status transitions (`active ↔ completed`) can surprise a
  user who manually set `completed`. Mitigated: a manual `archived` is never
  auto-changed, and re-opening a task on a `completed` objective is a deliberate
  act.

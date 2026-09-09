# ADR-028: Weekly Review — Schema, Rule-Based Composition (pre-OSCAR)

## Status
Proposed — Milestone 6.

## Date
2026-09-09

## Context

PRD §8 — the Weekly Review generates:

> What happened? · What improved? · What was missed? · Why? · What changed? ·
> What should be prioritized next?

Blueprint §16 gives the schema (`weekly_reviews`: `week_start`, `week_end`,
`summary`, `generated_by`, `status`) and one rule: *"Keep generated summaries
separate from raw metrics."* UX Journey E: `Analytics → Weekly Review →
completed vs planned → skill progress → objective health → blockers → OSCAR
recommendations → next-week plan`. UX §14 Screen Inventory lists **Analytics**
as an MVP screen but **not** Weekly Review as a separate screen — it is reached
*from within* Analytics (UX §7.1 nav is unchanged; [ADR-008](ADR-008-navigation-structure.md)).

**OSCAR is Milestone 7+.** Journey E's "OSCAR recommendations" step cannot exist
in M6. This ADR defines the review as a **rule-based, templated** artifact built
from the M6 analytics aggregates, with the OSCAR-authored narrative as a later,
additive layer.

## Decision

### 1. `orbit.weekly_reviews` — Blueprint §16 + a structured body

```text
orbit.weekly_reviews
- id           UUID PK
- user_id      UUID NOT NULL REFERENCES orbit.users(id) ON DELETE CASCADE
- week_start   DATE NOT NULL                 -- ISO Monday, in the user's timezone
- week_end     DATE NOT NULL                 -- the Sunday
- summary      TEXT                          -- Blueprint §16: a short headline line
- content      JSONB NOT NULL DEFAULT '{}'   -- the six structured sections (below)
- user_notes   TEXT                          -- the user's own reflection, never generated
- generated_by VARCHAR(16) NOT NULL DEFAULT 'system'   -- CHECK IN ('system','user','oscar')
- status       VARCHAR(16) NOT NULL DEFAULT 'generated' -- CHECK IN ('generated','reviewed')
- created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
- updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
UNIQUE (user_id, week_start)
```

`content` shape (stable keys, each a small typed object — *data*, not prose):
```
happened   : { tasksCompleted, tasksPlanned, evidenceLogged, projectsAdvanced, milestonesHit }
improved   : [ { kind: 'skill'|'readiness'|'objective', label, delta, ... } ]
missed     : [ { taskId, title, objectiveTitle, dueAt } ]      -- planned-not-completed, overdue
why        : [ { claim, basis } ]   -- e.g. "readiness +0.04" / "Kubernetes and Docker assessed"
changed    : [ { kind, label, from, to } ]  -- status flips, new targets, new adoptions
next       : [ { taskId, title, priorityScore, reason } ]  -- top of the current priority order
```
`summary` is one generated sentence ("Completed 7 of 11 planned tasks; readiness
+4 pts; Kubernetes moved to 78%.") — string-built from `content`, no LLM.

### 2. `WeeklyReviewComposer` — pure, no I/O

```java
WeeklyReviewContent compose(WeekWindow week, WeeklyInputs inputs)
```
`WeeklyInputs` is assembled by the caller from the M6 analytics ports for that
one week: the week's task events, assessment deltas, evidence rows, project
state changes, objective progress events, and the current priority-ordered task
list. The composer is deterministic and does **no** repository or security-context
access — the [ADR-015](ADR-015-daily-plan-generation.md) §1 pattern. Unit-tested
directly.

- **What happened** — counts from the week's events.
- **What improved** — positive deltas: skills whose `overall` rose, readiness
  rise for each target, objectives whose progress rose.
- **What was missed** — tasks that were in a daily plan during the week (or due
  in the week) and are still not `done`.
- **Why** — mechanical attribution, not inference: each `improved` entry gets a
  `basis` naming the events that caused it ("readiness +0.04 — 2 new assessments
  (Kubernetes, Docker)"). No causal language beyond what the deltas support.
- **What changed** — objective/target/project status transitions, new roadmap
  adoptions, new career targets in the week.
- **What to prioritize next** — the top N of the *current* Priority Engine
  ordering (unchanged formula), with each task's existing `priority_reason`
  headline. This is where OSCAR will later add narrative; for M6 it is the
  engine's own output.

### 3. Endpoints

| Route | Behaviour |
|---|---|
| `GET /api/v1/weekly-reviews` | the caller's reviews, newest first |
| `GET /api/v1/weekly-reviews/{weekStart}` | return it; if absent **and the week is over**, generate-then-return (lazy fallback); a future/current week → `404` unless already generated |
| `POST /api/v1/weekly-reviews/generate` | generate/regenerate for `{ weekStart }` (default: the just-ended week) — backfill + the current partial week |
| `PATCH /api/v1/weekly-reviews/{id}` | set `user_notes`, flip `status` to `reviewed` |

Ownership-scoped; cross-user access → `404` (the M1 pattern). Generation is
idempotent on `(user_id, week_start)` — upsert; regenerate replaces `content` /
`summary` but **preserves `user_notes` and `status`**.

### 4. Scheduled generation

`WeeklyReviewGenerationJob` — the single M6 scheduled job
([ADR-029](ADR-029-durable-job-mechanism.md) §2). Monday 05:00 UTC, through
`JobRunner` with `run_key = "<isoYear>-W<isoWeek>"`, generates the just-ended
week for every user with activity in it. The lazy `GET` path and `POST /generate`
share the **same composer** — no divergence.

### 5. Week boundaries

ISO week, **Monday–Sunday**, computed in `user_profiles.timezone` (default
`UTC`). The `@Scheduled` trigger fires once at 05:00 UTC Monday; the composer
uses each user's timezone to decide which calendar days belong to the week. A
per-user-timezone *trigger* is a multi-user refinement, not M6.

### 6. `generated_by = 'oscar'` is reserved

When OSCAR lands (M7+), a review can be (re)composed with an LLM-authored
narrative over the same `content` — `generated_by` flips to `oscar`, `content`
keys are unchanged, `summary`/section prose gets richer. No schema change
required. M6 never writes `'oscar'`.

## Alternatives Considered

1. **Wait for OSCAR and ship Weekly Review in M7.** Rejected — the PRD lists it
   as a core loop (§8), the schema is defined (§16), and the rule-based version
   is genuinely useful (a truthful "you completed 7/11, here's what moved").
   OSCAR then *enhances* rather than *enables* it.
2. **LLM-generated review in M6 via a direct provider call.** Rejected — pulls
   OSCAR's provider abstraction, prompt versioning, cost controls and
   confirmation model (Blueprint §12, PRD §10) forward into M6. Out of scope and
   against "avoid silently changing goals / distinguish facts from inference."
3. **Store only `summary` TEXT (Blueprint §16 literal).** Rejected — a flat blob
   can't drive the UX Journey E sections or be re-rendered when OSCAR arrives.
   `content` JSONB keeps "summaries separate from raw metrics" (Blueprint §16) —
   it holds *references and deltas*, not a copy of the metrics.
4. **Generate on every request (no persistence).** Rejected — Blueprint §16
   persists reviews, the user adds notes to them, and a past week's review
   should be stable even as new data arrives.

## Consequences

- **Positive.** A working weekly loop from M6, truthful and deterministic. Clean
  seam for OSCAR (`content` unchanged, `generated_by` flips). User reflection
  (`user_notes`) is first-class and never overwritten.
- **Negative.** The rule-based "why" is shallow next to what OSCAR will produce —
  acceptable, and clearly framed in the UI as the engine's own read.
- **Neutral.** One row per user per week.

## Sign-off items

1. **Rule-based / templated review in M6** (no LLM); `generated_by` vocab
   `('system','user','oscar')` with `'oscar'` reserved.
2. **`content` JSONB** with the six PRD §8 sections as structured data +
   a generated one-line `summary`.
3. **Lazy-on-GET (past weeks) + scheduled Monday job + `POST /generate`
   backfill**, all through one pure composer; idempotent on
   `(user_id, week_start)`; regenerate preserves `user_notes` + `status`.
4. **Reached from Analytics, not a new nav item** (UX §7.1 / ADR-008 unchanged).

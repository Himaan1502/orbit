# ADR-019: Skill Dependency Graph — Storage, Cycle Validation, Leverage

## Status
Accepted

## Date
2026-09-07

## Context
Blueprint §12 defines `skill_dependencies` (`prerequisite_skill_id`,
`dependent_skill_id`, `dependency_strength`, PK on the pair) and states: **"Do
not create cycles unless explicitly justified. The system should validate the
graph during imports and administrative edits."** Blueprint §43 Milestone-4
acceptance: **"dependency graph is navigable"**. UX §21 skill detail shows
*dependencies* and *dependents*. The Priority Engine's `dependencyImpact` factor
([ADR-014](ADR-014-priority-engine-v1.md)) and Career Readiness's
"high-leverage skills" ([ADR-018](ADR-018-career-readiness.md) §3) both derive
from this graph.

## Decision

### 1. Storage — exactly Blueprint §12
`orbit.skill_dependencies(prerequisite_skill_id, dependent_skill_id,
dependency_strength NUMERIC, PRIMARY KEY (prerequisite_skill_id,
dependent_skill_id))`. `dependency_strength ∈ [0, 1]`. Both columns FK to
`orbit.skills(id)`. A `CHECK (prerequisite_skill_id <> dependent_skill_id)`
rejects self-loops at the database.

Edges are part of the **global skill catalog** (like `skills` themselves) — not
per-user. M3 populates them only via the seed migration
([ADR-020](ADR-020-skill-catalog-seed.md)); a user-facing or admin edit API is
deferred.

### 2. Cycle rejection — `SkillGraphValidator` (pure)
Before any edge is inserted (seed load, or a future admin edit), the full edge
set + the candidate edge is checked for a directed cycle by DFS. A cycle
**aborts the whole operation** with a clear error naming the offending path.
There is no "explicitly justified cycle" escape hatch in M3 — Blueprint §12
allows one but nothing needs it; add it (a `justification` column + an override
flag) only when a real case appears.

`SkillGraphValidator.wouldCreateCycle(existingEdges, from, to)` and
`.firstCycle(edges)` are pure and unit-tested directly with hand-built graphs.

### 3. Derived signals (pure, from the loaded edge set)
`SkillGraph` is an in-memory adjacency structure built per request from
`skill_dependencies` (the catalog is small — tens to low hundreds of skills).

- **`dependents(skillId)`** / **`prerequisites(skillId)`** — direct neighbours,
  for the skill-detail view (Blueprint §12, UX §21).
- **`downstream(skillId)`** — the transitive closure of dependents (skills you
  unblock by learning this one). Memoised per `SkillGraph` instance.
- **`leverage(skillId, targetSkills)`** for high-leverage ranking
  ([ADR-018](ADR-018-career-readiness.md)): `Σ over d ∈ downstream(skillId) ∩
  targetSkills of (importance_d × gap_d × pathStrength)`, where `pathStrength` is
  the product of `dependency_strength` along the shortest prerequisite path
  (≤ 1), normalised to `[0, 1]` across the target's skills.
- **`dependencyImpact(skillId, targetSkills)`** for the Priority Engine: the
  normalised count of `downstream(skillId) ∩ targetSkills` — "this task's skill
  is a prerequisite for N of your target skills". `0.5` (neutral) when the task
  has no skill or the skill is not in the graph.

### 4. Navigability (Blueprint §43 acceptance)
`GET /api/v1/skills/{id}/dependencies` returns
`{ prerequisites: [...], dependents: [...] }` (each: skill id, name,
`dependency_strength`, and the current user's `capability` if assessed) so the
web Skill Graph / Skill detail can walk the graph one hop at a time.

## Alternatives Considered
1. **Allow cycles with justification now** — rejected; nothing needs it and it
   complicates every traversal. Deferred until a real case.
2. **Materialise the transitive closure in a table** — rejected; the catalog is
   small enough to close transitively in memory per request, and a materialised
   closure would need careful invalidation on every edge change.
3. **Store edges per user** — rejected; skill *dependencies* are a property of
   the skills, not the user (Blueprint §12). Only *assessments* are per-user.
4. **Recursive CTE in SQL for `downstream`** — viable, but the pure in-memory
   `SkillGraph` is directly unit-testable and reused by three consumers; the
   query cost of loading all edges once is trivial at this scale.

## Consequences
- **Positive:** the graph is a small, pure, well-tested value object; three
  consumers (skill detail, readiness leverage, priority `dependencyImpact`)
  share one implementation; cycles are impossible by construction.
- **Positive:** `dependency graph is navigable` (Blueprint §43) via one hop per
  API call.
- **Negative:** loading all edges per request. Fine for a catalog of this size;
  revisit (a cache or CTE) only if the catalog grows past a few hundred skills.
- **Negative:** no cycle-justification path. Acceptable — re-add when needed.

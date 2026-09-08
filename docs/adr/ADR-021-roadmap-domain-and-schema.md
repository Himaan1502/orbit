# ADR-021: Roadmap Domain — Schema, Priority Tier, Topic Dependencies

## Status
Accepted — priority-tier semantics and the `user_roadmaps` adoption table ratified by the product owner 2026-09-08

## Date
2026-09-08

## Context
Milestone 4 adds Roadmaps. Blueprint §11 gives the schema
(`roadmaps`, `roadmap_topics`, `roadmap_topic_skills`); §20 the read API; §25 the
ingestion sources and the legal boundary; §32 / UX §20 / PRD §5 the Roadmap
Intersection. The Blueprint's own milestone plan calls this its "Milestone 5";
our sequence calls it M4 — the numbering differs, the content is the same.

The product owner supplied the concrete dataset — **13 curated roadmap.sh
roadmaps across three priority tiers** — and a suggested schema shape for the
tier concept (`priority_tier`, `status`, `progress_percentage`, `started_at`,
`target_date`, `last_activity_at`), explicitly "to finalize, not a mandate —
reconcile with Blueprint §11".

Two things Blueprint §11 does **not** settle:
1. A **priority tier** on a roadmap (P1/P2/P3) — not in §11's column list.
2. **Topic-level dependencies** — §25 says store "dependencies" and the M5
   acceptance says "dependencies are validated", but §11 defines no
   `roadmap_topic_dependencies` table.

This ADR fills both gaps and pins the physical schema.

## Decision

### 1. `orbit.roadmaps` — the global roadmap catalog

Mirrors `orbit.skills` (ADR-020): a plain table, **not** user-scoped, **not**
soft-deletable, populated by a seed migration, read-only to end users.

```text
orbit.roadmaps
- id                  UUID PK
- name                VARCHAR(120) NOT NULL          -- "MLOps"
- external_identifier VARCHAR(80)  NOT NULL UNIQUE   -- roadmap.sh slug: "mlops"
- source_name         VARCHAR(80)  NOT NULL          -- "roadmap.sh"
- source_url          TEXT         NOT NULL          -- "https://roadmap.sh/mlops"
- description          TEXT                           -- one-line factual summary WE author
- priority_tier       INTEGER      NOT NULL          -- 1 | 2 | 3   CHECK (priority_tier IN (1,2,3))
- status              VARCHAR(20)  NOT NULL DEFAULT 'active'  -- CHECK IN ('active','archived')
- last_verified_at    TIMESTAMPTZ                     -- when the source was last checked
- created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
- updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
```

Deviations from Blueprint §11, all tightening not widening:
- `external_identifier` is **`NOT NULL UNIQUE`** (§11 has it nullable, no
  constraint). It is the seed's `ON CONFLICT` key and the roadmap's stable
  public identifier — every curated roadmap has one.
- `priority_tier INTEGER (1|2|3)` added (the dataset). Stored `1/2/3`, rendered
  `P1/P2/P3`.

### 2. `orbit.roadmap_topics` — Blueprint §11 verbatim

```text
orbit.roadmap_topics
- id                  UUID PK
- roadmap_id          UUID NOT NULL REFERENCES orbit.roadmaps(id) ON DELETE CASCADE
- parent_topic_id     UUID REFERENCES orbit.roadmap_topics(id) ON DELETE CASCADE   -- self-referential tree
- name                VARCHAR(120) NOT NULL          -- normalized short label WE author
- external_identifier VARCHAR(120)                    -- roadmap.sh node id, when known
- source_url          TEXT                            -- deep link to the source node
- sort_order          INTEGER NOT NULL DEFAULT 0
- created_at, updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
- CONSTRAINT ck_roadmap_topic_not_own_parent CHECK (parent_topic_id IS NULL OR parent_topic_id <> id)
- CONSTRAINT uq_roadmap_topic_ext UNIQUE (roadmap_id, external_identifier)
```
Indexes: `(roadmap_id, sort_order)`, `(parent_topic_id)`.

A topic tree is **≤ 2 levels** in practice (section → topic). `sort_order` +
tree position give the recommended reading order within a roadmap.

### 3. `orbit.roadmap_topic_skills` — Blueprint §11 verbatim

```text
orbit.roadmap_topic_skills
- roadmap_topic_id UUID NOT NULL REFERENCES orbit.roadmap_topics(id) ON DELETE CASCADE
- skill_id         UUID NOT NULL REFERENCES orbit.skills(id) ON DELETE CASCADE
- relevance        NUMERIC(3,2) NOT NULL DEFAULT 0.5   -- CHECK (relevance BETWEEN 0 AND 1)
- PRIMARY KEY (roadmap_topic_id, skill_id)
```
Index: **`(skill_id)`** — the intersection query and the `Skill → roadmaps`
reverse lookup (Blueprint domain model `SkillRoadmap`) both start from a skill.
One canonical skill can map to many topics across many roadmaps (§11's stated
goal, M5 acceptance "one skill can belong to multiple roadmaps").

### 4. No `roadmap_topic_dependencies` table in M4

Topic ordering is `sort_order` + tree depth (§11). The **real** prerequisite
signal is `orbit.skill_dependencies` — the M3 catalog graph (ADR-019), which
roadmap ingestion **augments** (ADR-022). "Dependencies are validated" (M5
acceptance) is satisfied by running `SkillGraphValidator` over every edge the
roadmap seed contributes, at authoring time and again at startup
(`SkillsConfig` runner, already in place).

roadmap.sh's inter-topic arrows are a visual layout, not a rigorous DAG;
duplicating them as a second dependency graph would need its own cycle
validation, its own traversal code, and would largely restate what
`skill_dependencies` already says once topics map to skills. A dedicated table
is deferred until a concrete need appears (an ADR when it does).

### 5. `orbit.user_roadmaps` — minimal per-user adoption

The proposal deferred per-user roadmap state; the owner's suggested schema
(`started_at`, `target_date`, …) points at it and the Roadmap Intersection
needs a natural "my selected roadmaps" default. So M4 ships a **lean** adoption
table — and progress stays **derived**, never stored (ADR-023, consistent with
M2 objective progress and M3 readiness).

```text
orbit.user_roadmaps
- user_id     UUID NOT NULL REFERENCES orbit.users(id) ON DELETE CASCADE
- roadmap_id  UUID NOT NULL REFERENCES orbit.roadmaps(id) ON DELETE CASCADE
- status      VARCHAR(20) NOT NULL DEFAULT 'active'   -- CHECK IN ('active','paused')
- started_at  TIMESTAMPTZ NOT NULL DEFAULT now()
- target_date DATE
- created_at, updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
- PRIMARY KEY (user_id, roadmap_id)
```
Index: `(roadmap_id)`.

Dropped from the suggested shape:
- `progress_percentage` — derived (ADR-023), never a stored column.
- `last_activity_at` — only meaningful with an activity hook (a task completed /
  a skill re-assessed for a skill in the roadmap). Deferred to when that hook
  exists; noted in `deferred-items.md`.

Adoption is the **only** user-scoped roadmap surface. `PUT
/api/v1/roadmaps/{id}/adoption` upserts (idempotent); `DELETE` removes it; a
`DELETE` for a roadmap the caller has not adopted answers **404** (the M1
ownership pattern). The pair PK makes cross-user access structurally impossible.

### 6. Priority tier — surfacing only, **not** a Priority Engine input

Per the owner's explicit instruction, on the record:

> P1/P2/P3 inform **surfacing and ordering in the UI** (Priority 1 roadmaps
> shown first, more prominently). They are **not** a Priority Engine override.
> An urgent P2/P3 task must still be able to outscore a less-urgent P1 task —
> consistent with ADR-014's frozen formula and the caution that tier "should
> not be the entire score". Roadmap tier is a **future candidate** Priority
> Engine signal at most; M4 does not wire it in.

M4 therefore: `priority_tier` lives on `roadmaps`, drives web ordering/grouping
(§Obj6), and is returned by the read API. Nothing in `PriorityEngine` /
`PriorityInput` / `TaskScoringService` reads it. This is **flagged for
sign-off**: the tier values themselves (which roadmap is P1 vs P2 vs P3) and the
confirmation that "surfacing only, future-candidate-signal only" is the intended
ceiling for M4.

### 7. Domain package

`com.orbit.platform.roadmaps` — `model/`, `repository/`, `service/`, `web/`,
`progress/` (pure). Cross-domain: `roadmaps → skills` (catalog + `SkillGraph`)
and `roadmaps → career` (a new read port `PrimaryCareerSkillWeights` in
`career`, for the intersection's "career relevance"). Both one-way; no cycle
(`skills` and `career` do not depend on `roadmaps`).

## Alternatives Considered
1. **A `roadmap_topic_dependencies` table now** — rejected (§4): not in §11,
   redundant with `skill_dependencies`, adds a second graph to validate.
2. **`priority_tier` as a lookup table (`roadmap_tiers`)** — rejected; three
   fixed values, an `INTEGER` + `CHECK` is simpler and needs no join.
3. **No `user_roadmaps` — intersection always takes an explicit id list** —
   rejected; the owner's schema and UX §20 ("completion/progress", onboarding
   "relevant roadmaps") point at a persistent selection, and it is the ownership
   surface M4 should exercise. Kept minimal.
4. **Store `progress_percentage` on `user_roadmaps`** — rejected; progress is a
   pure function of assessments × relevance (ADR-023). A stored copy would drift
   and need invalidation on every assessment, exactly the mistake ADR-016 and
   ADR-018 avoided.
5. **`roadmaps` extends `BaseEntity` (soft-delete)** — rejected; the catalog is
   curated like `skills`, `status = archived` covers retirement, and a roadmap
   with adoptions/mappings should never be hard-deleted anyway.

## Consequences
- **Positive:** schema is Blueprint §11 plus two well-scoped additions; the
  intersection is one indexed `GROUP BY`; ingestion augments the existing skill
  graph rather than starting a parallel one.
- **Positive:** progress and tier-vs-priority stay honest — derived progress,
  tier never touches the frozen Priority Engine.
- **Negative:** roadmap topic ordering carries no explicit prerequisite edges;
  "learn this before that" within a roadmap is inferred from `sort_order` + the
  skill graph. Acceptable — revisit if a roadmap needs a hard intra-topic gate.
- **Negative:** `user_roadmaps` is a small expansion beyond the proposal's
  "deferred". Called out for sign-off; kept to one table + two endpoints.

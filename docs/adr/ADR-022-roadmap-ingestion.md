# ADR-022: Roadmap Ingestion — Mechanism, Normalization, Attribution, Catalog Augmentation

## Status
Accepted — the 65 new catalog skills, 83 new edges, and the 13-roadmap topic curation ratified by the product owner 2026-09-08 (after reviewing the detail pages + the roadmap.sh sources)

## Date
2026-09-08

## Context
M4 must load the **13 curated roadmap.sh roadmaps** (the owner's dataset) into
the schema from [ADR-021](ADR-021-roadmap-domain-and-schema.md). Constraints:

- **PRD Non-Goal / Blueprint §13 / §25:** "External roadmap content must not be
  copied wholesale." ORBIT stores *"roadmap name, source, source URL, external
  identifier, normalized topic names, normalized skills, dependencies,
  relevance"* — **not** third-party prose.
- **ADR-020 precedent:** the M3 skill catalog ships as a Flyway versioned seed
  (`V5`) with stable `uuid5` ids and `ON CONFLICT DO NOTHING`; "a later
  roadmap-ingestion pass can augment it."
- **[ADR-019](ADR-019-skill-dependency-graph.md):** the skill graph must stay
  acyclic; `SkillGraphValidator` runs at authoring time and at startup.
- **M5 acceptance:** ingestion is idempotent, dependencies are validated.

The 13 roadmaps: **P1** MLOps, Forward Deployed Engineer, LeetCode, System
Design · **P2** DevOps, DevSecOps, AI Engineer, Machine Learning · **P3**
Frontend, Backend, Full Stack, Blockchain, Cyber Security.

## Decision

### 1. Mechanism — a Flyway versioned seed, same pattern as ADR-020

- **`V6__roadmaps_schema.sql`** — the four tables from ADR-021.
- **`V7__seed_roadmaps.sql`** — the 13 roadmaps, their normalized topics, the
  topic→skill mappings, **plus** any new canonical skills and
  `skill_dependencies` edges the roadmaps introduce.

Rationale (ADR-020 §Alternatives, unchanged): SQL in a versioned migration is
inspectable, diffable, transactional with the schema, and matches ADR-009. A
runtime import command (for when roadmap.sh itself changes) is **deferred** —
there is no live re-sync requirement in M4, and `last_verified_at` records when
a human last checked the source.

The migration is **generated** by a committed-in-spirit Python script (kept with
the milestone working notes, as V5's was) that:
- assigns `uuid5(NS_DNS, "orbit.roadmap:" + external_identifier)` to roadmaps,
  `uuid5(NS_DNS, "orbit.roadmap_topic:" + roadmap_slug + "/" + topic_key)` to
  topics, and reuses `uuid5(NS_DNS, "orbit.skill:" + canonical_name)` for skills
  (identical scheme to V5, so an existing skill resolves to its existing id);
- runs `SkillGraphValidator`'s cycle check (ported to Python) over
  `V5 edges ∪ V7 new edges` and refuses to emit on a cycle;
- emits deterministic, sorted SQL so re-generation is a reviewable diff.

### 2. Idempotency

Every `INSERT` carries `ON CONFLICT DO NOTHING` on a natural key:

| table | conflict target |
|---|---|
| `orbit.roadmaps` | `(external_identifier)` |
| `orbit.roadmap_topics` | `(roadmap_id, external_identifier)` |
| `orbit.roadmap_topic_skills` | `(roadmap_topic_id, skill_id)` (PK) |
| `orbit.skills` | `(canonical_name)` — as V5 |
| `orbit.skill_dependencies` | `(prerequisite_skill_id, dependent_skill_id)` (PK) — as V5 |

Running the seed against an already-populated database inserts zero rows and
raises no error. Flyway itself will not re-run `V7` (checksum-tracked); the
`ON CONFLICT` guard covers a manual replay and a `flyway repair` scenario.
**Verification:** the seed body is also executed a second time by an integration
test and asserted to change no row counts (M4 Obj7).

### 3. Normalization — what actually gets stored

**Stored:** roadmap `name`; our **short, factual, authored** topic labels
(skill-name-like: "Kubernetes", "CI/CD Pipelines", "Vector Databases & RAG" —
never a sentence or a paragraph); `skill_id` mappings; `relevance` weights;
`source_url` + `external_identifier` for attribution; `last_verified_at`.

**Not stored:** roadmap.sh topic *guide* text, descriptions lifted from the
source, ordering rationale prose, recommended-resource lists, any block of
third-party copy. `roadmaps.description` is a **one-line summary ORBIT authors**
("MLOps — operationalising ML: pipelines, serving, monitoring, platform on
Kubernetes."), not lifted.

**Topic depth:** normalized to the ~10–25 topics per roadmap that map to a
meaningful canonical skill. roadmap.sh's long tail of sub-bullets is dropped —
consistent with UX §20 "roadmaps should not behave like separate courses".

**`relevance`** ∈ [0,1]: how central the skill is to *that* roadmap (Kubernetes
is 0.9 for DevOps, 0.5 for AI Engineer). Authored per mapping; drives progress
weighting and intersection ranking (ADR-023).

### 4. Attribution

Every roadmap row carries `source_name = 'roadmap.sh'`, the canonical
`source_url`, the `external_identifier`, and `last_verified_at` (the seed's
authoring date). The web Roadmap detail shows "Source: roadmap.sh" as an
outbound link. No ORBIT UI presents the roadmap as ORBIT's own creation.

### 5. Catalog augmentation — how ingested skills/edges merge

The seed `INSERT … ON CONFLICT (canonical_name) DO NOTHING` into `orbit.skills`
for any skill a roadmap needs that V5 did not define (e.g. *TypeScript*,
*React*, *Vector Databases & RAG*, *Smart Contracts / Solidity*, *Threat
Modeling*, *Model Monitoring & Drift*). New `skill_dependencies` edges are
inserted the same way.

Guardrails:
- **Acyclic:** the generator's Python cycle check + the existing startup
  `SkillsConfig` runner (which validates *all* edges and logs the count). A
  cyclic seed fails the build and fails app startup.
- **No silent catalog drift:** the exact set of new skills and new edges V7
  introduces is listed in the migration header comment and in the M4 report,
  **for the owner to review and sign off** — the ingestion is curated data, not
  a scrape, and the owner should see what entered the canonical catalog.
- **`SkillGraphService.invalidate()`** is called after the seed is not needed —
  the graph is built lazily on first request after boot, so a fresh process
  already sees V7's edges. (No runtime ingestion in M4, so no invalidation hook
  is wired; noted for the future import command.)

### 6. Security / observability
- The seed is data only — no dynamic SQL, no external calls at migration time.
- Ingestion counts (roadmaps, topics, mappings, new skills, new edges) are
  logged at `INFO` by a startup check analogous to the skill-graph runner, so a
  deploy shows what the catalog now contains.

## Alternatives Considered
1. **Runtime importer that fetches roadmap.sh JSON** — rejected for M4: adds an
   outbound dependency and a parsing/normalization pipeline for a one-time load
   of curated data. Revisit when live re-sync is a requirement.
2. **Repeatable migration (`R__`) for the seed** — rejected, as ADR-020 §1:
   reference data other rows FK to belongs in versioned history.
3. **A separate `roadmap_skills` staging table, promoted after review** —
   over-engineered for 13 curated roadmaps and one reviewer; the header-comment
   manifest + sign-off achieves the review goal.
4. **Store topic guide text with a "summary only" rule** — rejected; the safe
   line is *no* third-party prose, authored labels only. Users follow
   `source_url` for the real guide.
5. **One migration for schema + seed** — rejected; V4/V5 established
   schema-then-seed, and it keeps the (large) seed diff separate from the
   (small) DDL diff.

## Consequences
- **Positive:** ingestion is a reviewable SQL artifact, idempotent by
  construction, transactional, and augments the M3 graph without a parallel
  system.
- **Positive:** the third-party-content boundary is bright-line: authored labels
  + attribution links, zero lifted prose.
- **Negative:** the normalized topic set is hand-curated and will lag roadmap.sh
  changes until a real importer exists. Acceptable for one user; `ON CONFLICT`
  keeps a later refresh safe.
- **Negative:** new canonical skills enter the catalog via a migration, not a
  reviewed API. Mitigated by the header manifest + explicit sign-off; a
  catalog-edit API remains deferred (ADR-020 §3).

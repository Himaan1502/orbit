# ADR-025: Evidence Domain — Schema, Types, Verification Ceiling

## Status
Proposed — for product-owner scope sign-off (M5)

## Date
2026-09-08

## Context
"Evidence is one of ORBIT's core differentiators" (UX §23). PRD §7: *"A roadmap
item should not be considered fully demonstrated merely because it was marked
complete. ORBIT should distinguish learning from demonstrated capability."*
Blueprint §13 gives the schema (`evidence`, `skill_evidence`,
`project_evidence`) and the hard rule: **"Evidence must never automatically
imply mastery."**

Blueprint §13 leaves unstated:
1. The `evidence.type` **vocabulary**.
2. What `verification_status` **means** and who sets it.
3. Whether creating evidence **writes anything** to `skill_assessments`.

## Decision

### 1. `orbit.evidence` — per-user, Blueprint §13 verbatim

```text
orbit.evidence
- id                  UUID PK
- user_id             UUID NOT NULL REFERENCES orbit.users(id) ON DELETE CASCADE
- type                VARCHAR(32) NOT NULL       -- CHECK — see §2
- title               VARCHAR(200) NOT NULL
- description         TEXT
- source_url          TEXT                        -- link to the artifact (repo, PR, deploy, doc)
- external_identifier VARCHAR(120)
- verification_status VARCHAR(20) NOT NULL DEFAULT 'self_attested'   -- CHECK — see §3
- metadata            JSONB       NOT NULL DEFAULT '{}'::jsonb        -- @JdbcTypeCode(JSON), like priority_reason
- occurred_at         TIMESTAMPTZ                  -- when the work happened (may predate the row)
- created_at, updated_at, deleted_at   (BaseEntity shape — soft-delete)
```

### 2. `evidence.type` — a fixed set + `other`

From UX §23's list, normalised:

| token | UX §23 example |
|---|---|
| `project` | completed project / measurable project outcome |
| `repository` | GitHub contribution |
| `deployment` | deployed service |
| `benchmark` | benchmark |
| `document` | technical document |
| `certification` | certification |
| `assessment` | assessment result |
| `talk` | conference talk / internal presentation |
| `other` | anything else, with a required `title` |

Fixed vocabulary (a `CHECK` constraint + `@JsonValue` enum) so the Evidence
library can filter by type and Career Readiness can weight by type later. `other`
is the escape hatch — no free-text `type`.

### 3. `verification_status` — `self_attested` is the M5 ceiling

```text
self_attested   the user asserts this happened (the only status M5 writes)
pending         queued for an external check (M6+ integrations)
verified        confirmed by an integration — GitHub API, CI, a deploy probe (M6+)
disputed        a check failed / contradicts the claim (M6+)
```

M5 has **no external verification integrations**. Every evidence row is
`self_attested`. This is the same honest-ceiling move as M3's `confidence:
self-reported` — the schema and the vocabulary are ready; the integrations are a
later milestone. The UI shows the status next to every evidence item and never
renders `self_attested` as proof.

**This is flagged for sign-off** — that `self_attested`-only is the accepted M5
boundary, exactly as `confidence: self-reported` was for M3.

### 4. `orbit.skill_evidence` / `orbit.project_evidence` — Blueprint §13 verbatim

```text
orbit.skill_evidence
- skill_id            UUID NOT NULL REFERENCES orbit.skills(id)   ON DELETE CASCADE
- evidence_id         UUID NOT NULL REFERENCES orbit.evidence(id) ON DELETE CASCADE
- contribution_weight NUMERIC(3,2) NOT NULL DEFAULT 0.5   -- CHECK 0..1 — how strongly this evidences the skill
- PRIMARY KEY (skill_id, evidence_id)

orbit.project_evidence
- project_id  UUID NOT NULL REFERENCES orbit.projects(id)  ON DELETE CASCADE
- evidence_id UUID NOT NULL REFERENCES orbit.evidence(id)  ON DELETE CASCADE
- PRIMARY KEY (project_id, evidence_id)
```
Indexes on `evidence_id` (both) and `skill_id`.

An evidence item can link to several skills (with per-skill weight) and several
projects. Deleting evidence cascades the links; deleting a skill/project
cascades too (the evidence row survives — it may still evidence other things).

### 5. Creating evidence writes **nothing** to `skill_assessments`

Blueprint §13's rule, enforced structurally: `POST /api/v1/evidence` and its
skill/project links **never** create or mutate a `skill_assessments` row. What
evidence *does* affect — the displayed **confidence** of an existing assessment,
and the Career Readiness `evidenceCoverage` component — is the subject of
[ADR-026](ADR-026-evidence-backed-confidence-and-readiness.md), and even there it
**raises confidence, never a dimension score**. A skill with rich evidence but
no self-assessment still shows "not assessed" — evidence does not manufacture a
capability number.

### 6. API (Blueprint §20)

```
GET  /api/v1/evidence                 library — filter by ?type= ?skillId= ?projectId=, newest first
POST /api/v1/evidence                 create (+ optional skills[] with weights, projects[])
GET  /api/v1/evidence/{id}            detail (linked skills w/ weight, linked projects)
PATCH /api/v1/evidence/{id}           update (title, description, url, type, occurred_at, links)
DELETE /api/v1/evidence/{id}          soft-delete
GET  /api/v1/skills/{id}/evidence     evidence for one skill (Blueprint §20) — drives the Skill detail "Evidence" section
```
(§20 lists `GET/POST/GET{id}`; `PATCH`/`DELETE` added for parity, as prior milestones did.)

### 7. Ownership — the M1 pattern
Every lookup scoped to the caller; cross-user → **404**. A skill link must
reference a catalogue skill (404), a project link a project the caller owns
(404).

## Alternatives Considered
1. **Free-text `type`** — rejected; the Evidence library must filter by type
   (UX §23) and later weight readiness by type; `other` covers the long tail.
2. **Evidence auto-creates a `source = evidence` assessment** — rejected;
   directly violates Blueprint §13 "never automatically imply mastery." Evidence
   informs *confidence*, not the score (ADR-026).
3. **`verification_status` omitted until integrations exist** — rejected; §13
   has the column, and shipping the vocabulary now means M6 integrations only
   add a writer, not a migration + a UI change.
4. **A single polymorphic `evidence_links` table** instead of
   `skill_evidence` + `project_evidence`** — rejected; §13 specifies two tables,
   `skill_evidence` carries a `contribution_weight` that `project_evidence` does
   not, and two typed tables index better.

## Consequences
- **Positive:** the "claimed vs demonstrated" line is now a real object the user
  builds deliberately; `skill_assessments` stays a pure self-assessment log.
- **Positive:** schema is ready for M6 verification integrations with no further
  migration.
- **Negative:** M5 evidence is all `self_attested` — it raises *confidence*, not
  *proof*. The UI copy must be careful ("you've logged evidence for this",
  not "verified"). Flagged for sign-off.
- **Negative:** `metadata JSONB` is unstructured in M5 (holds e.g. a PR number,
  a benchmark figure). Structured, per-type metadata schemas are deferred.

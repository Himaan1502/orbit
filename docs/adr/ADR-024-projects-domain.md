# ADR-024: Projects Domain — Schema, Objective / Task Linkage, Contribution Model

## Status
Accepted — scope + sign-off items ratified by the product owner 2026-09-08

## Date
2026-09-08

## Context
Milestone 5 adds Projects + Evidence (the Blueprint groups them as its
"Milestone 6"). This ADR covers Projects.

Blueprint §14 gives the schema (`projects`, `project_skills`); §20 the API; §34
the Project UI; UX §22 the list/detail; the Milestone-6 acceptance: *"project
contributes to mapped skills."* PRD §6 names **Project Impact** as a Priority
Engine factor (neutral `0.5` since M2 — ADR-014). The domain model (Blueprint
§"Data Model") puts `Project` as a sibling of `Objective` under `User`, with
`Project ├── ProjectSkill └── Evidence`.

Blueprint §14 leaves three things unstated:
1. Whether a project has **its own milestones** or reuses an objective's.
2. How `project_skills.demonstrated_contribution` gets **set**.
3. The `projects.status` **vocabulary**.

## Decision

### 1. `orbit.projects` — per-user, Blueprint §14 verbatim

```text
orbit.projects
- id             UUID PK
- user_id        UUID NOT NULL REFERENCES orbit.users(id) ON DELETE CASCADE
- objective_id   UUID REFERENCES orbit.objectives(id) ON DELETE SET NULL   -- optional link
- name           VARCHAR(160) NOT NULL
- description    TEXT
- status         VARCHAR(20)  NOT NULL DEFAULT 'planned'   -- CHECK IN ('planned','active','completed','archived')
- repository_url TEXT
- demo_url       TEXT
- started_at     TIMESTAMPTZ
- completed_at   TIMESTAMPTZ                                 -- set when status -> completed; cleared on re-open
- created_at, updated_at, deleted_at   (BaseEntity shape — soft-delete, like objectives)
```

`status` mirrors the objective vocabulary shape (`active` / `completed` /
`archived`) plus `planned` for a project that exists but is not started. Unlike
objectives, `completed` **is** user-settable here — a project is "done" when the
person says so, and that is exactly the "self-reported completion" the PRD wants
kept distinct from evidence.

### 2. `orbit.project_skills` — Blueprint §14 verbatim

```text
orbit.project_skills
- project_id               UUID NOT NULL REFERENCES orbit.projects(id) ON DELETE CASCADE
- skill_id                 UUID NOT NULL REFERENCES orbit.skills(id)  ON DELETE CASCADE
- expected_contribution    NUMERIC(3,2) NOT NULL DEFAULT 0.5   -- CHECK 0..1 — set by the user
- demonstrated_contribution NUMERIC(3,2) NOT NULL DEFAULT 0     -- CHECK 0..1 — DERIVED (ADR-026)
- PRIMARY KEY (project_id, skill_id)
```
Index `(skill_id)` — the reverse lookup ("which projects target this skill").

- **`expected_contribution`** — the user's claim, when adding a skill to a
  project, of how much this project should build that skill.
- **`demonstrated_contribution`** — **derived, never user-set** (ADR-026 §3):
  `0` until the project is `completed` **and** has evidence linked to that skill;
  then a function of the project's linked evidence weight for that skill, capped
  at `expected_contribution`. It is what feeds Career Readiness's
  `projectCoverage` component and the Priority Engine's `projectImpact`.

### 3. Projects reuse the objective's milestones — no `project_milestones` table

UX §22 shows "Milestones" and "Tasks" on the project detail. Rather than a
second milestone model:
- A project **optionally links to one objective** (`projects.objective_id`).
- **`orbit.tasks` gains a nullable `project_id`** (`REFERENCES orbit.projects
  ON DELETE SET NULL`) — V3 already reserved this ("`project_id` / `skill_id`
  land with their domains in M3+"). A task may have an objective, a milestone,
  a skill, and/or a project.
- The project detail's **"Milestones"** = the linked objective's milestones;
  its **"Tasks"** = tasks with `project_id = this project`. If a project has no
  objective, it just shows its tasks.

`orbit.milestones` stays the single milestone model (ADR-016). One less table,
one progress rollup.

### 4. Ownership — the M1 pattern

Every project / project-skill lookup is scoped to `currentUser.currentUserId()`;
cross-user access → **404**. `objective_id` must reference an objective the
caller owns, `skill_id` a catalogue skill (404 otherwise) — the M3
`SkillCatalog` / M4 `CareerTargetLookup` pattern.

### 5. API (Blueprint §20)

```
GET   /api/v1/projects            list (status, derived progress, skill + evidence counts)
POST  /api/v1/projects            create
GET   /api/v1/projects/{id}       detail (skills w/ expected+demonstrated, linked objective, tasks, evidence)
PATCH /api/v1/projects/{id}       update (name, description, status, urls, dates, skills)
DELETE /api/v1/projects/{id}      soft-delete
```
(No `DELETE` in §20's list, but M2/M3/M4 all added one for parity; keeping it.)

### 6. Derived project progress

Pure `ProjectProgressCalculator` (the M2/M3/M4 pattern — no I/O, unit-tested):
progress from the linked objective's task completion for this project's tasks,
plus a "skills demonstrated" ratio (`Σ demonstrated / Σ expected`). Not stored.

### 7. Dashboard

The M2 Command Center aggregate (`GET /api/v1/dashboard`) gains **"active
projects"** (Blueprint §20 command-center item 5). "Recent evidence" (item 8)
lands with ADR-025.

## Alternatives Considered
1. **`project_milestones` table** — rejected; UX "Milestones" on a project maps
   cleanly to the linked objective's, and a second milestone model doubles the
   progress-rollup and status-derivation code (ADR-016) for no user-visible gain.
2. **`demonstrated_contribution` user-set** — rejected; it is precisely the
   "demonstrated vs claimed" line the PRD §7 draws. If the user could type it,
   it would just be another self-report. Derived from evidence keeps it honest.
3. **Project as a kind of Objective (status/priority reuse)** — rejected;
   Blueprint models them as siblings, objectives are outcomes and projects are
   "execution vehicles for building evidence" (UX §22) — different lifecycles
   (`repository_url` / `demo_url`, `planned` state).
4. **No `objective_id` link** — rejected; §14 has it and it is how a project's
   work rolls into objective progress (Blueprint §33 "completion should update
   relevant objective/project/skill progress").

## Consequences
- **Positive:** one milestone model; `tasks` gains the last of its three
  reserved foreign keys; the "claimed vs demonstrated" split is structural.
- **Positive:** `project_skills.demonstrated_contribution` gives Career
  Readiness and the Priority Engine a real, non-self-reported project signal
  (ADR-026).
- **Negative:** a project with no linked objective shows no "Milestones"
  section. Acceptable — a milestone-less project is just a skill-building spike.
- **Negative:** `demonstrated_contribution` is `0` until a project is *both*
  completed and evidenced, so a long in-flight project contributes nothing to
  readiness until it lands. Intentional (that is what "demonstrated" means); the
  Priority Engine's `projectImpact` still rewards *working on* it via
  `expected_contribution` (ADR-026 §4).

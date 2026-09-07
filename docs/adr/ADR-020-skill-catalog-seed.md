# ADR-020: Skill & Career-Target Catalog — Seed Data Strategy

## Status
Accepted

## Date
2026-09-07

## Context
Milestone 3 needs skills to exist before a user can assess them or configure a
career target. Blueprint §11 `skills` is a **global** catalog
(`canonical_name UNIQUE`, no `user_id`); §12 `skill_dependencies` is likewise
global. Blueprint §44 gives an example skill list and an example career target
("Platform Engineer / AI Infrastructure"); PRD §3 sets the initial user's
direction as Platform/DevOps/SRE + AI Infrastructure/MLOps. Roadmap *ingestion*
(Blueprint §25) — the eventual real source of skills — is a later milestone
(M4 in our plan).

## Decision

### 1. A curated seed catalog ships as data, not code
A **Flyway versioned migration** (`V…__seed_skill_catalog.sql`) inserts:

- ~30–40 canonical **skills** across the categories relevant to the initial
  user's direction (Blueprint §44 + the common skills of the DevOps / Platform
  Engineering / MLOps / System Design roadmaps): e.g. Linux, Networking, Git,
  Python, Docker, Kubernetes, Kubernetes Networking, Kubernetes Production,
  Terraform, CI/CD, Observability, Security, System Design, Distributed Systems,
  Databases, Message Queues, MLOps, Model Serving, Feature Stores, Data
  Pipelines, IaC, GitOps, SRE Practices, Incident Response, Performance
  Engineering, API Design, …
- **`skill_dependencies`** edges among them (e.g. Networking → Kubernetes
  Networking → Kubernetes Production; Docker → Kubernetes; Linux → most infra
  skills), each with a `dependency_strength`. The seed is validated against
  [ADR-019](ADR-019-skill-dependency-graph.md)'s cycle rule **at authoring
  time** and by a test that loads the seed and asserts the graph is acyclic.

Skills carry a stable `canonical_name`; the migration uses
`INSERT … ON CONFLICT (canonical_name) DO NOTHING` so re-running against a
populated DB is a no-op, and a later roadmap-ingestion pass can add more skills
without colliding.

### 2. Career targets are **per-user**, created through the API — not seeded
`career_targets` / `career_target_skills` have `user_id` and are created via
`POST /api/v1/career/targets`. M3 does **not** seed a career target. The web
onboarding / Career page offers a **"Platform Engineer / AI Infrastructure"
template** (a client-side preset that pre-fills the create form with a
suggested skill set + weights from the seed catalog) — the user still owns the
row. This keeps seed data out of per-user tables (a Milestone-0 principle) while
making the first target one click to create.

### 3. No skill-edit API in M3
Skills and their dependencies are **read-only** to end users in M3
(`GET` only). Editing the catalog (an admin API, or roadmap ingestion) is a
later milestone. This means the seed is the single source of skills in M3;
`skill_assessments` is the only skill-related table a user writes to.

### 4. IDs
Seed skills use application-stable UUIDs generated at authoring time and
committed literally in the migration, so `skill_dependencies` and any
documentation can reference them and they are identical across every deployment.

## Alternatives Considered
1. **Repeatable migration (`R__`)** for the catalog — rejected; the catalog will
   be edited deliberately and versioned migrations give a clear history. A
   repeatable migration re-runs on every checksum change, which is the wrong
   model for curated reference data that other rows FK to.
2. **Seed via an `ApplicationRunner` in Java** — rejected; SQL in a Flyway
   migration is inspectable, diffable, and runs in the same transaction as the
   schema, matching [ADR-009](ADR-009-flyway-database-migrations.md).
3. **Seed a default career target too** — rejected; it would put seed data in a
   per-user table, and different users want different targets. The client-side
   template gets the same "one click" outcome without that.
4. **Wait for roadmap ingestion (M4) to populate skills** — rejected; M3 needs
   skills to be usable, and a hand-curated catalog is a better starting point
   than raw roadmap scraping anyway. Ingestion later *augments* this catalog.

## Consequences
- **Positive:** skills exist on first boot; the graph is guaranteed acyclic by a
  test; career targets stay per-user; a later ingestion pass adds skills without
  conflict.
- **Negative:** the seed is hand-maintained until roadmap ingestion. Acceptable
  for one user; the `canonical_name` conflict handling makes augmentation safe.
- **Negative:** the initial skill set is opinionated toward Platform/DevOps/AI
  Infra. Intentional (PRD §3); other directions get their skills when their
  roadmaps are ingested.

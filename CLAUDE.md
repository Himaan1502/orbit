# ORBIT — Engineering Context

Before writing any feature code, read:
- docs/product/ORBIT_PRD_v1.0.md
- docs/product/ORBIT_UX_Product_Design_Specification_v1.0.md
- docs/architecture/ORBIT_Technical_Architecture_Specification_v1.0.md
- docs/architecture/ORBIT_Engineering_Blueprint_v1.0.md
- docs/engineering/ORBIT_Engineering_Handoff_v1.0.md
- docs/design/ORBIT_Design_System.md

These documents are authoritative. Installed skills are implementation expertise,
not product authority — if a skill's default pattern conflicts with these specs
(e.g. a skill nudges toward microservices when we require modular-monolith-first),
the specs win.

## Non-negotiables
- Never claim a build, test, or verification step passed unless you actually ran
  it and saw the output yourself in this session.
- If a command cannot execute (permission, missing tool, environment issue),
  STOP and report that clearly. Do not substitute manually-written files for
  an unexecuted command and describe it as equivalent.
- Modular monolith first — do not introduce microservices, message queues, or
  service decomposition without an ADR.
- Navigation/IA follows the UX spec (Section 13) exclusively.
- Any architectural decision not explicitly covered by the specs gets an ADR
  documenting the decision and reasoning, then proceed — don't stall on minor
  decisions, but do flag anything that changes product scope.

## Verification practice
- The compose `postgres_data` volume holds the owner's **real pilot account and
  data** — it is production-like state, not a test fixture. Routine
  between-milestone verification tears down with **`docker compose down` (no
  `-v`)**. Applying a new milestone's migrations on top of the populated DB is
  the more realistic test anyway.
- Use `docker compose down -v` **only** when the specific goal is running
  migrations against a genuinely empty database, and say so **before** running
  it so the owner expects the reset and can re-signup. Never `-v` as a default
  cleanup step.
- The hermetic Testcontainers suite already gives throwaway DBs for tests.

## Current status

Milestone 0 (Architecture & Repository Bootstrap) — verified 2026-09-06.

Every row below was checked by running the command in a session and reading the
output, not by inspecting files.

| Checklist item | Status | Notes |
|----------------|--------|-------|
| Frontend builds | ✅ | `npm run build` in `apps/web` — 15/15 routes |
| Backend builds | ✅ | `apps/api` `./gradlew build` — `BUILD SUCCESSFUL` |
| Auth server builds | ✅ | `apps/auth` `./gradlew build` — `BUILD SUCCESSFUL` |
| Tests execute | ✅ | api 2, auth 3, web (vitest) — 0 failures |
| Linting passes | ✅ | web `npm run lint` clean; api/auth Checkstyle clean; SpotBugs 0 warnings (still `ignoreFailures=true`) |
| Formatting passes | ✅ | `npm run format:check` exits 0 (`.prettierignore` added) |
| Database starts | ✅ | `docker compose up -d orbit-db` → healthy; Flyway V1 applied |
| Redis starts | ✅ | `docker compose up -d orbit-redis` → healthy |
| Applications start | ✅ | `apps/api` + `apps/auth` `bootRun` verified; web build serves |
| Health endpoints work | ✅ | api `/actuator/health` + `/api/v1/health` 200; auth `/actuator/health` 200 |
| OIDC flow works | ✅ | full Auth Code + PKCE: token issued, accepted by api on a protected route |
| Docker Compose build | ✅ | 2026-09-06 — `docker compose build` for `orbit-auth`, `orbit-api`, `orbit-web` all succeeded with real output; `orbit-web` fixed (`.dockerignore`, `public/`, `HOSTNAME=0.0.0.0`) and builds 15/15 routes |
| Docker Compose up | ✅ | 2026-09-06 — `docker compose up -d` → `docker compose ps` shows all 5 (`orbit-db`, `orbit-redis`, `orbit-auth`, `orbit-api`, `orbit-web`) `Up (healthy)`; web `/`, api + auth `/actuator/health` all 200 |
| CI configuration is valid | ✅ | 2026-09-06 — GitHub Actions run [#34048695491](https://github.com/Himaan1502/orbit/actions/runs/34048695491) on `main`: Frontend, Backend, Auth Server jobs all green (cache-service warnings only, non-blocking) |

### Milestone 1 (Identity) — verified 2026-09-07

Every row checked by running the command / driving the flow in a session.

| Item | Status | Notes |
|------|--------|-------|
| Persistent OIDC signing keys | ✅ | `auth.signing_keys` (RSA, private key AES-256-GCM sealed). `docker compose restart orbit-auth` → JWKS `kid` unchanged and a token issued **before** the restart still returns 200 from `apps/api`. [ADR-011](docs/adr/ADR-011-authorization-server-persistence.md) |
| Signing-key rotation (M1 follow-up) | ✅ | Overlapping rotation: `AUTH_ROTATE_SIGNING_KEY=true` + restart → JWKS publishes new active **and** retired key; a token from before the rotation still returns 200 from `apps/api`; plain restart does not re-rotate. `SigningKeyRotationIntegrationTest`. |
| `application-test.yml` relocated (M1 follow-up) | ✅ | Moved to `apps/api/src/test/resources/` — no longer in the production jar. `./gradlew test` still green. |
| Persistent client / consent / authorization store | ✅ | `JdbcRegisteredClientRepository` + `JdbcOAuth2AuthorizationService` + `JdbcOAuth2AuthorizationConsentService`, `auth` schema, Flyway V1. Refresh tokens survive restart. |
| Real registration / signup | ✅ | `auth.users` (BCrypt-12), server-rendered `/signup` + `POST /register`, email-uniqueness + validation, audit events (`auth.auth_events`), in-memory rate limit + lockout. [ADR-012](docs/adr/ADR-012-identity-and-registration-model.md) |
| Frontend login + signup + logout wired | ✅ | Next.js BFF: `/login` `/signup` `/auth/callback` `/logout`, PKCE + confidential client, AES-256-GCM `HttpOnly` session cookie, transparent refresh in `/api/orbit/*` proxy. [ADR-013](docs/adr/ADR-013-frontend-session-and-bff.md) |
| Full authed flow through a browser | ✅ | Playwright E2E vs the compose stack: signup → login → callback → `GET /api/orbit/me` (200) → create objective (201) → reload keeps session → logout bounces to sign-in. |
| Server-side resource-ownership enforcement | ✅ | `orbit.objectives` + `/api/v1/objectives`; ownership checked in `ObjectiveService` on every op. Live: user B GET/PATCH/DELETE of user A's objective → real `404`. `apps/api` `ObjectiveOwnershipTest`. |
| Hermetic test suite (Testcontainers) | ✅ | `apps/api` (PG + Redis) + `apps/auth` (PG), singleton-container pattern. `application-test.yml` localhost / Redis-password assumptions removed. CI service containers removed. api 8 tests, auth 11 tests, web 3 — 0 failures. |
| `docker compose build` + `up` (all 5) | ✅ | 2026-09-07 — clean `down` → `build` (all 3 images) → `up -d` → `docker compose ps` shows `orbit-db` `orbit-redis` `orbit-auth` `orbit-api` `orbit-web` all `Up (healthy)`. |
| `apps/auth` build + tests | ✅ | `./gradlew clean build` — `BUILD SUCCESSFUL`, 11 tests, Checkstyle 0, SpotBugs 0. |
| `apps/api` build + tests | ✅ | `./gradlew clean build` — `BUILD SUCCESSFUL`, 8 tests, Checkstyle 0, SpotBugs 0. |
| `apps/web` build / lint / format | ✅ | `npm run build` (22 routes), `lint` clean, `format:check` clean, `type-check` clean, `test` 3/3. |

Not in scope for M1 and still open: email verification, password reset, MFA,
Redis-backed distributed rate limiting, ADMIN role, a zero-restart rotation
trigger. See [deferred-items.md](docs/engineering/deferred-items.md).

### Milestone 2 (Objectives, Tasks & Daily Planning) — verified 2026-09-07

| Item | Status | Notes |
|------|--------|-------|
| Objectives fleshed out (Blueprint §9) | ✅ | V3 migration: `priority` (1–5), `progress` (derived 0–1), `career_target_id` (nullable). `GET/POST /api/v1/objectives`, `GET/PATCH/DELETE /objectives/{id}`. Status `active/completed/archived`; `completed` is derived, not settable. |
| Milestones (Blueprint §9) | ✅ | `orbit.milestones`, `GET/POST /objectives/{id}/milestones` + `GET/PATCH/DELETE /objectives/{id}/milestones/{mid}`. Auto-status from child tasks. |
| Progress rollup | ✅ | `ProgressCalculator` (pure, [ADR-016](docs/adr/ADR-016-objective-status-and-progress.md)) — milestone completion → objective progress; recomputed inline on task completion. `ProgressCalculatorTest` (8). Live: completing a milestone's task rolled its objective to 50%. |
| Tasks (Blueprint §10) | ✅ | `orbit.tasks`, `GET/POST /api/v1/tasks`, `GET/PATCH /tasks/{id}`, `POST /tasks/{id}/complete`. Objective required, milestone optional; both ownership-checked (404). |
| Priority Engine v1 (Blueprint §22) | ✅ | Weighted sum, Blueprint §22 weights, configurable (`orbit.priority.weights.*`, startup-validated to sum 1). Pure `PriorityEngine`; `priority_reason` JSONB carries every factor's value/weight/contribution/source. `PriorityEngineTest` (8, direct). [ADR-014](docs/adr/ADR-014-priority-engine-v1.md) — weights ratified (M2 sign-off). |
| Daily planning (Blueprint §10, §20) | ✅ | `orbit.daily_plans` + `daily_plan_tasks`. Pure `DailyPlanGenerator` (greedy by priority within budget); `DailyPlanService` the inline caller ([ADR-015](docs/adr/ADR-015-daily-plan-generation.md)). `GET /daily-plans/{date}`, `POST /generate`, `POST /{id}/regenerate`. `DailyPlanGeneratorTest` (6, direct). |
| Command Center aggregate | ✅ | `GET /api/v1/dashboard` — greeting, objective/execution health, next-best-action (with explanation), today block. `GET/PUT /api/v1/me/preferences` for the daily budget. |
| Web — real pages | ✅ | Command Center at `/` (moved from `/command-center`, ADR-008); real `/objectives` (list + create), `/objectives/[id]` (milestones, tasks, progress ring, "Why this?" explainer), `/today` (generate + complete). TanStack Query via the ADR-013 BFF proxy — no direct API calls. |
| Ownership enforcement (404) | ✅ | Extended to milestones, tasks, daily plans. `MilestoneIntegrationTest`, `ExecutionFlowIntegrationTest`, `DashboardIntegrationTest`. Live: user B → 404 on user A's objective/milestone/task. |
| Tests | ✅ | api `./gradlew build` — `BUILD SUCCESSFUL`, **47 tests** (29 pure unit + 18 Testcontainers integration), Checkstyle 0, SpotBugs 0. web `build` (18 routes) / `lint` / `format:check` / `type-check` clean, `test` 6/6. |
| M2 follow-ups | ✅ | `PriorityWeightsTest` (weights must sum to 1) + objective progress rollup now counts an objective's loose tasks as one synthetic unit alongside milestones ([ADR-016](docs/adr/ADR-016-objective-status-and-progress.md) §3, amended). Verified by [CI #34139559449](https://github.com/Himaan1502/orbit/actions/runs/34139559449) (local Docker Desktop was unstable that session). |
| Full browser E2E | ✅ | Playwright vs compose: signup → objective → 2 milestones → task under milestone → complete → objective 50% → generate daily plan → task shown in Today and Command Center. |
| `docker compose build` + `up` (all 5) | ✅ | 2026-09-07 — clean `down -v` → `build` → `up -d` → all 5 `Up (healthy)`; V1→V2→V3 migrations applied. |

Not in scope for M2 and still open: Career/Roadmaps/Skills/Projects/Evidence
(and the 4 Priority Engine factors that depend on them), a durable/async job
runner, real-time updates. See
[deferred-items.md](docs/engineering/deferred-items.md).

M2 ADRs: [ADR-014](docs/adr/ADR-014-priority-engine-v1.md) (priority weights —
ratified), [ADR-015](docs/adr/ADR-015-daily-plan-generation.md),
[ADR-016](docs/adr/ADR-016-objective-status-and-progress.md).

### Milestone 3 (Career & Skills) — verified 2026-09-07

| Item | Status | Notes |
|------|--------|-------|
| Skills domain (Blueprint §12, §20) | ✅ | V4 migration: `orbit.skills` (global catalog), `skill_dependencies` (DAG, `CHECK (prerequisite <> dependent)`), `skill_assessments` (append-only, 5 dims + overall). `GET /api/v1/skills`, `/skills/{id}`, `/skills/{id}/dependencies`, `POST /skills/{id}/assessments`. [ADR-019](docs/adr/ADR-019-skill-dependency-graph.md). |
| Skill catalog seed | ✅ | V5 migration — 34 curated skills + 41 dependency edges, stable `uuid5` ids, `ON CONFLICT DO NOTHING`. Asserted acyclic by `SkillGraphValidatorTest`, a Python generator check, **and** a startup `ApplicationRunner` (`SkillsConfig`) that fails fast on a cycle. Live: log line `Skill dependency graph verified acyclic (41 edges).` [ADR-020](docs/adr/ADR-020-skill-catalog-seed.md). |
| Skill Scoring v1 (Blueprint §23) | ✅ | Pure `SkillScoringEngine`: `overall = 0.20·knowledge + 0.25·practical + 0.20·evidence + 0.15·interview + 0.20·production`. Configurable (`orbit.skills.scoring.weights.*`), startup-validated to sum 1 (±0.001). Every score carries a weights `fingerprint` + `confidence: self-reported` (all M3 assessments are `self`). `SkillScoringEngineTest` (5), `SkillScoringWeightsTest` (3). [ADR-017](docs/adr/ADR-017-skill-scoring-v1.md) — weights ratified 2026-09-08. |
| Career targets (Blueprint §8) | ✅ | V4: `orbit.career_targets` + `career_target_skills` (importance / interview / production weights). `GET/POST /api/v1/career/targets`, `GET/PATCH/DELETE /targets/{id}`. Real `objectives.career_target_id` FK (`ON DELETE SET NULL`); linking an objective to a target the caller doesn't own → `404`. |
| Career Readiness (Blueprint §24) | ✅ | Pure `CareerReadinessCalculator`: importance-weighted skill capability (0.70) blended with interview-weight-weighted interview readiness (0.30), renormalised. Evidence/project coverage rendered as explicit `null` + `not-tracked-until-…`, never silent zero. Critical gaps + high-leverage skills ranked. `GET /api/v1/career/readiness`, `/career/gaps`. Reproducible. `CareerReadinessCalculatorTest` (7). [ADR-018](docs/adr/ADR-018-career-readiness.md) — blend weights ratified 2026-09-08. |
| Priority Engine factor swap (ADR-014) | ✅ | `careerRelevance`, `skillGap`, `dependencyImpact`, `interviewValue` now computed from real career/skill data when a task has a skill and/or its objective has a career target; otherwise the M2 neutral/proxy default is unchanged. Formula, weight set and `fingerprint` **byte-identical**. `projectImpact` stays neutral (no Projects domain). `PriorityEngineTest` (9), `PriorityFactorSwapIntegrationTest` (2). |
| Web — Career + Skills | ✅ | Real `/career` (readiness radial, component breakdown, critical gaps, high-leverage skills, target list, create-from-template), `/skills` (catalog grouped by category, capability bars), `/skills/[id]` (assess dialog, prerequisites/dependents, dimension breakdown). Objective↔target link in `ObjectiveForm` + shown on the objective detail page. TanStack Query via the ADR-013 BFF proxy. `CapabilityBar.test.tsx` (3). |
| Ownership enforcement (404) | ✅ | Extended to career targets and skill assessments. `CareerIntegrationTest` (7), `SkillsIntegrationTest` (4). Live E2E + tests: user B → 404 on user A's target; objective can only link a target the caller owns. |
| Tests | ✅ | `apps/api` `./gradlew clean build` — `BUILD SUCCESSFUL`, **84 tests** (0 failures), Checkstyle 0, SpotBugs 0 (fixed one latent M2 dead-store while here). `apps/auth` `build` — `BUILD SUCCESSFUL`, 12 tests. `apps/web` `build` (18 routes) / `lint` / `format:check` / `tsc` clean, `vitest` 9/9. |
| Full browser E2E | ✅ | Playwright vs compose: signup → browse catalog → self-assess Kubernetes → create a career target from the Platform Engineer template → readiness + gaps render (computed components + explicit not-tracked) → link an objective → the linked objective's task shows `careerRelevance` as **computed**, an unlinked objective's stays **proxy**. |
| `docker compose` (all 5) | ✅ | 2026-09-07 — `down -v` → `build orbit-api orbit-web` → `up -d` → `docker compose ps` all 5 `Up (healthy)`; Flyway V1→V5 applied; acyclic-graph runner passed. Docker Desktop was **stable** for the entire M3 session (build + up + two full E2E runs). ⚠️ The `-v` here wiped the owner's pilot data — see "Verification practice"; M4+ tears down without `-v`. |

Not in scope for M3 and still open: per-career-target skill scoring weight
overrides, readiness trend/history, evidence & project coverage in readiness
(need those engines), user-editable skill catalog, upgrading
`confidence` past `self-reported`, `projectImpact` in the Priority Engine. See
[deferred-items.md](docs/engineering/deferred-items.md).

M3 ADRs: [ADR-017](docs/adr/ADR-017-skill-scoring-v1.md) (skill scoring weights —
ratified 2026-09-08), [ADR-018](docs/adr/ADR-018-career-readiness.md) (readiness
blend weights — ratified 2026-09-08),
[ADR-019](docs/adr/ADR-019-skill-dependency-graph.md),
[ADR-020](docs/adr/ADR-020-skill-catalog-seed.md).

All M3 sign-off items (skill scoring weights, readiness blend, `careerRelevance`
proxy fallback, `confidence: self-reported` ceiling) — **ratified by the product
owner 2026-09-08**.

### Milestone 4 (Roadmaps) — verified 2026-09-08

| Item | Status | Notes |
|------|--------|-------|
| Roadmap domain + schema (Blueprint §11) | ✅ | V6 migration: `orbit.roadmaps` (+ `priority_tier` P1/P2/P3, `external_identifier` UNIQUE, source attribution), `roadmap_topics` (self-ref tree, `sort_order`), `roadmap_topic_skills` (`relevance`, one skill ↔ many roadmaps), `user_roadmaps` (lean per-user adoption). No `roadmap_topic_dependencies` table — topic order is `sort_order` + the skill graph ([ADR-021](docs/adr/ADR-021-roadmap-domain-and-schema.md)). |
| Ingestion of the 13 curated roadmaps | ✅ | V7 seed (Flyway versioned, `uuid5`, `ON CONFLICT DO NOTHING`) — 13 roadmaps, 125 normalized topics, 183 topic→skill mappings. **Augments** the M3 catalog: +65 canonical skills, +83 `skill_dependencies` edges (manifest in the migration header). No third-party prose — authored labels + `source_url` attribution only (PRD Non-Goal, Blueprint §25). Idempotent: `RoadmapSeedIdempotencyIntegrationTest` re-runs the seed → 0 rows changed. Live: applied V6→V7 **on top of the populated pilot DB**, `Skill dependency graph verified acyclic (124 edges)`, `Roadmap catalog: 13 roadmaps, 125 topics, 183 topic→skill mappings`. [ADR-022](docs/adr/ADR-022-roadmap-ingestion.md). |
| Roadmap read API (Blueprint §20) | ✅ | `GET /api/v1/roadmaps` (tier-ordered library + derived coverage + adopted flag), `/roadmaps/{id}` (topic tree + per-topic status + recommended-next + adoption), `/roadmaps/{id}/topics`. `PUT/DELETE /roadmaps/{id}/adoption` — idempotent; `DELETE` when not adopted → `404` (M1 pattern). |
| Derived roadmap progress | ✅ | Pure `RoadmapProgressCalculator` ([ADR-023](docs/adr/ADR-023-roadmap-progress-and-intersection.md)) — coverage = relevance-weighted mean capability; per-topic status (`covered ≥ 0.70` / `in_progress` / `not_started`); recommended-next ranked by prerequisite-readiness then weighted gap. **Not stored** — derived, reproducible. `RoadmapProgressCalculatorTest` (5). |
| Roadmap Intersection (Blueprint §32, PRD §5) | ✅ | `GET /api/v1/roadmaps/intersections?roadmapIds=…` (default: the caller's adopted roadmaps; `< 2` → `400`; unknown id → `404`). Shared skills across the set, each with contributes-to list, career relevance (reuses the M3 `career` domain via a new `PrimaryCareerSkillWeights` port), capability, `SkillGraph.leverage`, and a capability-band recommended action. Ranked `roadmapCount → careerRelevance → leverage → gap`. One indexed `GROUP BY` (M5 acceptance "queried efficiently"). `RoadmapIntersectionIntegrationTest` (4). |
| Priority Engine | ✅ | **Untouched.** `priority_tier` is surfacing-only and read by nothing in `PriorityEngine` / `PriorityInput` / `TaskScoringService` (ADR-021 §6, ADR-014 frozen). A roadmap-tier priority signal is a flagged future candidate, not built. |
| Web — Roadmaps | ✅ | Real `/roadmaps` (P1/P2/P3 grouped + ordered, coverage bars, Compare mode → intersection), `/roadmaps/[id]` (coverage ring, source link, recommended-next, topic tree with skill capability), `/roadmaps/intersection` (shared-skill matrix, overlap made visually obvious per UX §20). TanStack Query via the ADR-013 BFF proxy. `buildTopicTree` + `tree.test.ts` (3). |
| Ownership enforcement (404) | ✅ | `user_roadmaps` keyed by `(user_id, roadmap_id)` — cross-user access structurally impossible; `DELETE` of an un-adopted roadmap → 404. `RoadmapIntegrationTest` (6, incl. Alice/Bob isolation). |
| Tests | ✅ | `apps/api` `./gradlew clean build` — `BUILD SUCCESSFUL`, **104 tests** (0 failures), Checkstyle 0, SpotBugs 0. `apps/auth` `clean build` — 12 tests. `apps/web` `build` (19 routes) / `lint` / `format:check` / `tsc` clean, `vitest` 12/12. |
| Full browser E2E | ✅ | Playwright vs compose: signup → roadmap library (13, tier-grouped) → DevOps detail (source attribution, topic tree, recommended-next) → self-assess Docker+Kubernetes → coverage moves off 0% → adopt → Compare DevOps+MLOps+DevSecOps → intersection shows shared Kubernetes/Docker/CI-CD → shared skill links through to `/skills/[id]`. |
| `docker compose` (all 5) | ✅ | 2026-09-08 — `build orbit-api orbit-web` → `up -d` (no `-v`) → all 5 `Up (healthy)`; Flyway V6→V7 applied **on the existing pilot DB**, pilot account `himaanshu15028@gmail.com` **survived**. Torn down with `docker compose down` (no `-v`). Docker Desktop stable all session. |

Not in scope for M4 and still open: roadmap *generation* (OSCAR/LLM custom
roadmaps), a runtime roadmap importer (M4 is a curated Flyway seed), roadmap
detail ↔ Projects/Evidence (M5/M6), `user_roadmaps.last_activity_at` (needs an
activity hook), stored progress/trend, a roadmap-tier Priority Engine signal, a
`roadmap_topic_dependencies` table. See
[deferred-items.md](docs/engineering/deferred-items.md).

M4 ADRs — **all ratified by the product owner 2026-09-08**:
[ADR-021](docs/adr/ADR-021-roadmap-domain-and-schema.md) (schema, priority-tier
semantics, `user_roadmaps`), [ADR-022](docs/adr/ADR-022-roadmap-ingestion.md)
(ingestion — the 65 new catalog skills + 83 edges + the 13-roadmap topic
curation), [ADR-023](docs/adr/ADR-023-roadmap-progress-and-intersection.md)
(progress formula / intersection ranking / recommended-action bands). Milestone
4 fully closed.

### Milestone 5 (Evidence Engine & Projects) — verified 2026-09-08

Every row checked by running the command / driving the flow in a session.

| Item | Status | Notes |
|------|--------|-------|
| Projects domain (Blueprint §14) | ✅ | V8 migration: `orbit.projects` (status `planned/active/completed/archived` — `completed` user-settable), `project_skills` (`expected_contribution` user-set, `demonstrated_contribution` **derived**), `tasks.project_id` (FK, `ON DELETE SET NULL`). No `project_milestones` table — a project reuses its linked objective's milestones ([ADR-024](docs/adr/ADR-024-projects-domain.md)). `GET/POST /api/v1/projects`, `GET/PATCH/DELETE /projects/{id}`. Pure `ProjectProgressCalculator` + `ProjectProgressCalculatorTest` (4). |
| Evidence domain (Blueprint §13) | ✅ | V8: `orbit.evidence` (§13 columns verbatim + `metadata` JSONB; fixed `type` set; `verification_status` — `self_attested` is the M5 ceiling), `skill_evidence` (`contribution_weight`), `project_evidence`. `GET/POST /api/v1/evidence`, `GET/PATCH/DELETE /evidence/{id}`, `GET /api/v1/skills/{id}/evidence`. **Evidence never writes `skill_assessments`** ([ADR-025](docs/adr/ADR-025-evidence-domain.md) §5) — `EvidenceIntegrationTest.creatingEvidenceNeverWritesASkillAssessmentRow`. |
| Evidence-backed confidence | ✅ | Pure `EvidenceConfidenceCalculator` ([ADR-026](docs/adr/ADR-026-evidence-backed-confidence-and-readiness.md)): strength ≥ 1.0 + ≥ 2 evidence types → `VERIFIED`; ≥ 0.4 → `PARTIALLY_VERIFIED`; else `SELF_REPORTED`. Thresholds `@ConfigurationProperties` (`orbit.evidence.*`, startup-validated). A skill's assessment `confidence` now varies per skill from its evidence — **the score never moves**. `EvidenceConfidenceCalculatorTest` (6), `EvidenceConfidenceIntegrationTest` (2). `skills` owns the `SkillEvidenceConfidence` port; `evidence` implements it (keeps `skills` free of an `evidence` import). |
| `demonstrated_contribution` — derived | ✅ | Pure `DemonstratedContributionCalculator` (ADR-026 §3): `0` unless the project is `completed`, then `min(expected_contribution, Σ linked-evidence weight)`, rounded to the `NUMERIC(3,2)` column. Recomputed inline on a project's status/skill change (`ProjectService`) or an evidence-link change incl. delete (`EvidenceService`). Never set from a request body. `DemonstratedContributionCalculatorTest` (3) + `ProjectImpactAndDemonstratedIntegrationTest` (4, incl. evidence-deletion reversal). |
| Priority Engine — `projectImpact` swap (ADR-014) | ✅ | Formula + weights + `fingerprint` **byte-identical**. `PriorityInput.projectImpactSignal` (nullable) + the same `signalOrNeutral` helper as the M3 swaps. New `TaskProjectSignals` port (`projects` → consumed by `execution`): `expected_contribution` of the task's skill for its project, softened when the project isn't active, `null` (→ neutral 0.5) when there's no project or it's done/archived. **After M5 no factor sits on a placeholder.** `PriorityEngineTest` (9) + `PriorityFactorSwapIntegrationTest` (2). |
| Career Readiness amendment (ADR-026 §2) | ✅ | 4-way blend: `skillCapability 0.45 / interviewReadiness 0.20 / evidenceCoverage 0.20 / projectCoverage 0.15` (config, startup-validated to sum 1). `evidenceCoverage` + `projectCoverage` are now **computed** (were `null`/neutral in M3–M4). New `evidenceGaps` + `projectGaps` on `/career/readiness` + `/career/gaps`. `CareerReadinessCalculatorTest` (9, incl. the drop-vs-old-blend case). |
| ⚠️ Readiness numbers drop | ✅ (expected) | `skillCapability` weight went `0.70 → 0.45`. A target with self-assessments but no logged evidence / completed projects **reads lower** than under the M3–M4 blend — **by design** (ADR-026 §2). The Career page carries copy that says so. |
| Web — Projects & Evidence | ✅ | `/projects` (status-ordered list), `/projects/[id]` (status transitions, expected-vs-demonstrated bars, milestones from the linked objective, tasks filtered by `project_id`, linked evidence, delete), `/evidence` (search + type filter), `/evidence/[id]`. Skill detail gains an Evidence section + confidence badge (partially-verified/verified). Career page renders the two new coverage components + gap lists + the blend note. Command Center gains Active projects + Recent evidence (client-composed). TanStack Query via the ADR-013 BFF proxy. |
| Ownership enforcement (404) | ✅ | Extended to projects + evidence; a linked objective/skill/project is ownership-checked on write (404). `ProjectIntegrationTest` (5, incl. Alice/Bob), `EvidenceIntegrationTest` (4, incl. Bob can't link to Alice's project). |
| Module boundaries (Blueprint §5) | ✅ | `projects ↔ evidence` communicate only through interfaces the **consumer** owns (`ProjectEvidenceCount`, `ProjectSkillEvidenceStrength`, `ProjectDemonstratedRecompute` in `projects`, implemented in `evidence`); `projects` has zero compile-time dependency on `evidence`. |
| Tests | ✅ | `apps/api` `./gradlew clean build` — `BUILD SUCCESSFUL`, **134 tests** (0 failures), Checkstyle 0, SpotBugs 0. `apps/auth` `clean build` — 12 tests. `apps/web` `build` (25 routes) / `lint` / `prettier` / `tsc` clean, `vitest` 12/12. |
| Full browser E2E | ✅ | Playwright vs compose: signup → career target (Kubernetes-weighted) → self-assess Kubernetes → Career shows evidence/project-coverage components + both gap lists + the blend note → create project (expected 0.8), start it, link objective → task on the project → its "Why this?" shows **projectImpact = computed** → log a `deployment` evidence linked to the skill + project → Kubernetes confidence → **partially-verified** → complete the project → skill's demonstrated contribution → **80** and Career project-coverage moves off 0. |
| `docker compose` (all 5) | ✅ | 2026-09-08 — `build` → `up -d` (no `-v`) → all 5 `Up (healthy)`; **Flyway V8 applied on the existing pilot DB**, pilot account `himaanshu15028@gmail.com` **survived**. Torn down with `docker compose down` (no `-v`). Docker Desktop stable all session. |

Not in scope for M5 and still open: real evidence *verification* (`pending/verified/disputed` beyond the `self_attested` ceiling — needs a reviewer/integration), evidence-file upload/storage (URL + metadata only), a durable/async recompute job (still inline), project templates, the Evidence "portfolio export" (PRD §5). See [deferred-items.md](docs/engineering/deferred-items.md).

M5 ADRs — **all five sign-off items pre-approved by the product owner 2026-09-08**
(readiness blend, confidence thresholds, `self_attested` ceiling, evidence never
writes assessments, `demonstrated_contribution` gated on completion + evidence):
[ADR-024](docs/adr/ADR-024-projects-domain.md),
[ADR-025](docs/adr/ADR-025-evidence-domain.md),
[ADR-026](docs/adr/ADR-026-evidence-backed-confidence-and-readiness.md).

Auth: OIDC via self-hosted Spring Authorization Server (`apps/auth`) — see
[ADR-004](docs/adr/ADR-004-oidc-authentication.md). M1 persistence and identity:
[ADR-011](docs/adr/ADR-011-authorization-server-persistence.md),
[ADR-012](docs/adr/ADR-012-identity-and-registration-model.md),
[ADR-013](docs/adr/ADR-013-frontend-session-and-bff.md).

Known deferrals: [docs/engineering/deferred-items.md](docs/engineering/deferred-items.md).
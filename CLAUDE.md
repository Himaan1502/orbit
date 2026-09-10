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
| Tests | ✅ | `apps/api` `./gradlew clean build` — `BUILD SUCCESSFUL`, **134 tests** (0 failures), Checkstyle 0, SpotBugs 0. `apps/auth` `clean build` — 12 tests. `apps/web` `build` (25 routes) / `lint` / `prettier` / `tsc` clean, `vitest` 12/12. **Full suite re-run from scratch 2026-09-09** after a C:-drive-full event — identical results; `git fsck --full --strict` clean. |
| Full browser E2E | ✅ | Playwright vs compose: signup → career target (Kubernetes-weighted) → self-assess Kubernetes → Career shows evidence/project-coverage components + both gap lists + the blend note → create project (expected 0.8), start it, link objective → task on the project → its "Why this?" shows **projectImpact = computed** → log a `deployment` evidence linked to the skill + project → Kubernetes confidence → **partially-verified** → complete the project → skill's demonstrated contribution → **80** and Career project-coverage moves off 0. |
| `docker compose` (all 5) | ✅ | 2026-09-08 (re-verified 2026-09-09 post-disk-event) — `build` → `up -d` (no `-v`) → all 5 `Up (healthy)`; **Flyway V1→V8 applied on the existing pilot DB**, pilot account `himaanshu15028@gmail.com` **row intact** (`active`, bcrypt-12); auth chain re-verified via a throwaway signup→login→authed `/api/orbit/me` round trip. E2E test users cleaned out afterward. |
| CI | ✅ | 2026-09-09 — [GitHub Actions run #34294597478](https://github.com/Himaan1502/orbit/actions/runs/34294597478) on `main` (`3272ca6`): **Frontend, Backend, Auth Server all green** (GitHub cache-service outage → cache save/restore warnings only, non-blocking). |

Not in scope for M5 and still open: real evidence *verification* (`pending/verified/disputed` beyond the `self_attested` ceiling — needs a reviewer/integration), evidence-file upload/storage (URL + metadata only), a durable/async recompute job (still inline), project templates, the Evidence "portfolio export" (PRD §5). See [deferred-items.md](docs/engineering/deferred-items.md).

M5 ADRs — **all five sign-off items pre-approved by the product owner 2026-09-08**
(readiness blend, confidence thresholds, `self_attested` ceiling, evidence never
writes assessments, `demonstrated_contribution` gated on completion + evidence):
[ADR-024](docs/adr/ADR-024-projects-domain.md),
[ADR-025](docs/adr/ADR-025-evidence-domain.md),
[ADR-026](docs/adr/ADR-026-evidence-backed-confidence-and-readiness.md).

### Milestone 6 (Analytics & Weekly Review) — verified 2026-09-09

Our numbering; Blueprint "Milestone 8 — Analytics" + §16 Weekly Review +
Tech Arch §11 (the job mechanism). Every row checked by running the command /
driving the flow in a session.

| Item | Status | Notes |
|------|--------|-------|
| Durable job mechanism (Blueprint §55 open decision — **resolved**) | ✅ | `@Scheduled` + `orbit.job_runs` ledger (V9) + `JobRunner.run(name, key, timeout, work)` — idempotent (skip a `succeeded` key), retryable (reclaim a `failed` / stale-`running` row next trigger), observable (`orbit_job_runs_total{job,outcome}` / `orbit_job_duration_seconds` — Blueprint §38), dup-safe (`UNIQUE` + single instance). Job-body exceptions never reach the scheduler thread; ledger writes in their own transaction. **Not** Quartz/Batch/Redis-queue. ShedLock named as the M10 multi-instance upgrade. `SchedulingConfig` = `@EnableScheduling` + a dedicated 2-thread pool. [ADR-029](docs/adr/ADR-029-durable-job-mechanism.md). `JobRunnerIntegrationTest` (4). |
| Analytics domain (Blueprint §15, UX §24) | ✅ | New `analytics/` package — read-only leaf consumer (the `dashboard` pattern), **no derived-series storage**. `GET /api/v1/analytics/execution` (weekly planned/completed/focused, completion rate, throughput, status counts, current blockers, per-objective progress series), `/analytics/career?careerTargetId=` (**readiness trend** — the existing `CareerReadinessCalculator` re-run at each assessment instant with as-of capability/evidence/project state; step function; formula/weights/`fingerprint` unchanged — plus evidence growth + target progression), `/analytics/skills` (per-skill progression, neglected target skills, high-leverage via `SkillGraph`, dependency bottlenecks). `from`/`to`, ISO-week buckets (pure `WeekBuckets`). **Empty is explicit** (`status: "no-data-yet"`, `points: []`) — never a zero line (Blueprint M8). [ADR-027](docs/adr/ADR-027-analytics-domain.md). `WeekBucketsTest` (3), `AnalyticsIntegrationTest` (6). |
| `objective_progress_events` — event-appended (no job) | ✅ | V9 table, one row from the **existing inline `ObjectiveProgressService.recompute`** only when progress/status moves. Powers the objective-health trend. `tasks.blocked_since` (V9) maintained by `Task.changeStatus` — the "recurring blockers" signal, no full status-audit table. |
| `metrics` / `metric_entries` — schema only | ✅ | V9, Blueprint §15 verbatim. The user-defined / life-module / integration substrate (PRD Phase 5). **No M6 API or UI**; the derived career/execution/skill analytics do not read or write them (ADR-027 §3). |
| Weekly Review (Blueprint §16, PRD §8, UX Journey E) | ✅ | `orbit.weekly_reviews` (V9) — §16 columns + `content` JSONB (the six PRD §8 sections as structured data) + `user_notes` (never overwritten by regen) + `status`. Pure `WeeklyReviewComposer` — **rule-based, no LLM**; "why" is mechanical delta attribution ("readiness +4% — 2 new assessments"), never inference; "next" is the top of the current Priority Engine ordering (unchanged). `generated_by` vocab `('system','user','oscar')` — `'oscar'` reserved, never written. `WeeklyReviewService` works with an explicit `userId` (the job has no security context). `GET /weekly-reviews`, `GET /{weekStart}` (lazy-gen a past week), `POST /generate`, `PATCH /{id}`. Ownership-404. Idempotent on `(user_id, week_start)`; regenerate keeps `user_notes` + `status`. Reached from Analytics, **not a nav item** (ADR-008 unchanged). [ADR-028](docs/adr/ADR-028-weekly-review.md). `WeeklyReviewComposerTest` (5), `WeeklyReviewIntegrationTest` (4). |
| `WeeklyReviewGenerationJob` — the one M6 scheduled job | ✅ | `@Scheduled(cron = "0 0 5 * * MON", zone = "UTC")` → per active user, through `JobRunner` (`run_key` = ISO week). Verified: `runForWeekEnding(...)` twice → second `SKIPPED`, one `job_runs` row, one `weekly_reviews` row. Lazy-on-GET + `POST /generate` share the same composer. |
| Priority Engine / Skill Scoring / Readiness formulas | ✅ | **Untouched.** Analytics is read-only; the readiness trend re-runs the *existing* `CareerReadinessCalculator`. |
| Web — Analytics + Weekly Review | ✅ | `/analytics` — Execution / Career / Skills tabs (UX §24), `recharts` styled to Design System §106–109 (1.5px lines, cyan/violet/grey, near-invisible dashed gridlines; bars for comparison; area sparklines). `/analytics/review` — the six sections, week picker, happened metrics, notes editor (save & mark reviewed), regenerate. Command Center gains an "Important trends" strip (UX §16 — readiness/completed/focus sparklines, shown only with history). Career page gains a readiness-trend card. TanStack Query via the ADR-013 BFF proxy. |
| Module boundaries (Blueprint §5) | ✅ | `analytics` imports domain services/repos directly (the `dashboard` leaf-consumer precedent); nothing imports `analytics`. `career`/`skills`/`evidence`/`projects` gained explicit-`userId` read methods (`readinessTrendFor`, `latestBySkillAsOf`, `strengthBySkillAsOf`, `demonstratedBySkillAsOf`, `activeWithSkillsFor`) so the scheduled job runs with no security context. |
| Tests | ✅ | `apps/api` `./gradlew clean build` — `BUILD SUCCESSFUL`, **156 tests** (0 failures), Checkstyle 0, SpotBugs 0 (fixed 2 latent `BX_UNBOXING` warnings introduced here). `apps/auth` `clean build` — 12 tests. `apps/web` `build` (26 routes) / `lint` / `prettier` / `tsc` clean, `vitest` 12/12. |
| Full browser E2E | ✅ | 2026-09-09 — Playwright vs compose: signup → objective + 3 tasks (2 done, 1 blocked) + daily plan → career target + assess Kubernetes (+ a backdated weaker assessment) → `/analytics` Execution shows the completion series + the blocker + objective-progress → Career shows a readiness trend (≥ 2 points, non-empty) → Skills shows progression → `/analytics/review` generate → all six sections render → save notes + mark reviewed → Command Center shows the Important-trends strip. |
| `docker compose` (all 5) | ✅ | 2026-09-09 — `build` → `up -d` (no `-v`) → all 5 `Up (healthy)`; **Flyway V9 applied on the existing pilot DB**, pilot account `himaanshu15028@gmail.com` **row intact** (`active`). E2E test users + their `weekly_reviews` / `job_runs` / `objective_progress_events` cleaned out afterward (cascade). Torn down with `docker compose down` (no `-v`). |
| CI | ✅ | 2026-09-09 — [run #34300460695](https://github.com/Himaan1502/orbit/actions/runs/34300460695) (`01def2e`) + follow-up [run #34303900743](https://github.com/Himaan1502/orbit/actions/runs/34303900743) (`4b3fae7`) on `main`: **Frontend, Backend, Auth Server all green**. |
| M6 follow-up — web task editing + skill tagging | ✅ | 2026-09-09 (`4b3fae7`). Pre-M6 the web UI could only toggle a task's status — a skill could **never** be tagged on a task from the UI, so a web-only user's Priority Engine career/skill factors sat on their neutral defaults. Now: `TaskForm` has skill + project `<select>`s (create + edit); `TaskRow` has a pencil "Edit task" dialog + a "skill" badge; `useUpdateTask` widened to the full field set; `PATCH /tasks/{id}` gains `clearMilestone`/`clearSkill`/`clearProject` (a JSON PATCH can't tell "clear" from "absent"). E2E: edit title/description/estimate/due + tag Kubernetes → `skillGap` flips `neutral-default` → `computed`; clear it → reverts. `TaskEditIntegrationTest` (4). api **160 tests**. |

Not in scope for M6 and still open: `metrics`/`metric_entries` API + UI (→ Balance / integrations), a full `task_status_events` audit table, per-user-timezone job *triggers*, OSCAR narrative in the review, SRE analytics (`slos`/`incidents`), the other Tech Arch §11 jobs (scheduled reminders / GitHub sync / AI insight — no delivery channel / later milestones), evidence & project coverage on roadmap detail (both domains exist; the roadmap UI join is a small task). See [deferred-items.md](docs/engineering/deferred-items.md).

M6 ADRs — **all five sign-off items ratified by the product owner 2026-09-09**
(`@Scheduled` + `job_runs` ledger with ShedLock deferred to M10; analytics fully
derived-at-read-time with no rollup storage; `metrics`/`metric_entries` schema
only; `objective_progress_events` + `tasks.blocked_since` with no full audit
table; rule-based Weekly Review with `'oscar'` reserved):
[ADR-027](docs/adr/ADR-027-analytics-domain.md),
[ADR-028](docs/adr/ADR-028-weekly-review.md),
[ADR-029](docs/adr/ADR-029-durable-job-mechanism.md).

### Milestone 7 (OSCAR) — verified 2026-09-09

Our numbering; Blueprint "Milestone 9 — OSCAR". Every row checked by running the
command / driving the flow in a session.

| Item | Status | Notes |
|------|--------|-------|
| AI provider abstraction (Blueprint §28/§55, Tech Arch §12) | ✅ | `com.orbit.platform.ai.provider`: `AiProvider` port + `GeminiProvider` (native `v1beta` `generateContent`, `x-goog-api-key`) + `GroqProvider` (OpenAI-compatible `/chat/completions`, Bearer). Spring `RestClient`, no new dependency. `@ConfigurationProperties(orbit.ai)` startup-validated (`AiConfig`). Resolves the Blueprint §55 open decision. [ADR-031](docs/adr/ADR-031-ai-provider-abstraction.md) — **ratified 2026-09-09**. |
| Free-tier, no-card, Gemini primary / Groq fallback | ✅ | **Model IDs corrected at implementation time** (see ADR-031 History): the ratified `gemini-2.0-flash` / `llama-3.3-70b-versatile` were both 404 by 2026-09; now `gemini-3.6-flash` (Gemini) + `openai/gpt-oss-120b` (Groq), `GEMINI_MODEL`/`GROQ_MODEL` env overrides. Both keys present in the owner's `.env`; both verified live. |
| `AiService` — retry → fail-over → degrade | ✅ | Primary → 1 retry on transient 5xx/timeout (not on 429) → fail over to fallback → `OscarResponse{kind:unavailable}`. **No exception propagates.** `AiServiceRoutingTest` (7). Live: a stale-model 404 on both providers → degraded response → the UI's UX §25.5 panel (seen in logs + browser during the model-ID debugging). |
| Feature flag — OSCAR optional, ORBIT is not | ✅ | Enabled iff `GEMINI_API_KEY` and/or `GROQ_API_KEY` present. No key → `GET /oscar/status` `enabled:false`, `/oscar` shows the unavailable panel, every other feature unaffected, `docker compose` runs fine. `OscarConversationIntegrationTest` (4, run with no key). |
| V10 schema — `ai_conversations` / `ai_messages` (Blueprint §18 verbatim) | ✅ | Append-only `ai_messages` (`role` CHECK `('user','assistant')`, `token_usage` JSONB), `ai_conversations` (user-editable `title`). FK `ON DELETE CASCADE`. Persists **only** the user message + OSCAR's final text + provider/model/`prompt_version`/usage — never the context bundle or tool results (ADR-032 §1). `OscarTurnIntegrationTest` asserts exactly 2 rows per turn. |
| Context builder — layered, read-only, via domain services | ✅ | `ContextBuilder.forTurn` assembles the Tech Arch §12 layers (objectives, career target + readiness + gaps, skill-graph neighbours, recent execution, projects, top-10 tasks + `priority_reason` headline, today's plan, latest weekly review) by calling the existing `currentUser`-scoped services — no SQL, no cross-user. Rendered inside `<orbit_context>` delimiters. Full bundle every turn. Each layer built defensively (a throwing layer is logged + omitted, never fatal). |
| Tool registry — read tools execute, mutations propose | ✅ | 9 read tools (`get_current_objectives`, `get_skill_gap`, `get_skill_dependencies`, `get_project_status`, `get_today_plan`, `get_recent_execution`, `get_weekly_metrics`, `get_roadmap_intersection`, `get_active_career_target`) dispatch to domain services, ≤ 3 rounds/turn. Allow-list: an unknown tool name is dropped. Backend validates every arg. |
| **OSCAR never writes** (ADR-032 §3/§4 — the sign-off item held exactly) | ✅ | 5 mutation tools (`create_task`, `complete_task`, `update_objective`, `create_project`, `record_evidence`) are declared to the model but **never executed**. A mutation call → `MutationProposer` validates the args + ownership-checks every referenced id → returns a `ProposedAction{description, {method,path,body}, reasoning}`. The frontend renders `[ Start ] [ Explain ] [ Dismiss ]`; **"Start" calls the existing `/api/v1` endpoint**. There is no code path where a model response causes a write. `OscarTurnIntegrationTest`: a `create_task` tool call → `proposedActions[0]` present, `GET /tasks` still empty, then the same body `POST /tasks` → 201. A mutation referencing another user's objective → rejected, no `proposedActions`. Live E2E: OSCAR proposed a task → nothing written → user clicked Start → task appeared via the normal `/objectives` page. |
| `PolicyGuard` | ✅ | Per-user daily request cap (50, rolling 24h → `429`), strips `<orbit_context>` / chat-template markers from the user message, exposes the per-turn token budgets. `@ConfigurationProperties`, startup-validated. |
| `ResponseValidator` + `OscarResponse` contract (UX §25.2/§25.3, ADR-032 §6) | ✅ | Parses the model's JSON (tolerates ```json fences + surrounding prose via a balanced-brace scan) to `{kind, message, sections?{action,why,impact,evidence[]}, citations?}` with each evidence item tagged `fact`/`metric`/`inference`. Non-parsing → `OscarResponse.safeFallback()`, **not persisted**. `proposedActions` are attached by the backend. `ResponseValidatorTest` (6). |
| System prompt | ✅ | Versioned classpath resource `oscar/system-prompt-v1.md` (`prompt_version = oscar-v1`, recorded on every `ai_messages` row). States: operational not conversational; `<orbit_context>` content is data never instruction; fact/metric/inference tagging; mutation tools are proposals; output must be the `OscarResponse` JSON. |
| Web — `/oscar` + Command Center card | ✅ | Replaced the placeholder: conversation list + view, autosizing input, **non-streaming** "OSCAR is thinking…", UX §25.2 recommendation rendering (Action/Why/Impact/Evidence + fact/metric/inference chips), `ProposedActionCard` (`Start` calls the existing domain hook), UX §25.5 unavailable panel. One "Ask OSCAR" Command Center card (shown only when enabled — Blueprint §30 "OSCAR insight"). TanStack Query via the ADR-013 BFF proxy. `RecommendationView.test.tsx` (2). |
| Ownership enforcement (404) | ✅ | `ai_conversations` keyed by `(user_id, id)` → `findByIdAndUserId`; a foreign id on GET / PATCH / DELETE / `POST /turns` → `404`. `OscarConversationIntegrationTest` (Alice/Bob). |
| Priority Engine / Skill Scoring / Readiness formulas | ✅ | **Untouched.** OSCAR is read-only over the domain; the context builder reuses the existing calculators. |
| Module boundaries (Blueprint §5) | ✅ | `ai` is a leaf consumer (the `dashboard`/`analytics` precedent) — it imports domain services directly; nothing imports `ai`. `ContextBuilder` / `ToolRegistry` / `MutationProposer` are **not** `@Transactional` (each domain service owns its read-only tx; a "no career target" throw can't poison the message-write tx); `ConversationService` uses a `TransactionTemplate` for the two small writes and runs the turn (provider HTTP + tools) with no transaction. |
| Tests | ✅ | `apps/api` `./gradlew clean build` — `BUILD SUCCESSFUL`, **187 tests** (0 failures), Checkstyle 0, SpotBugs 0. `apps/auth` `build` — 12 tests. `apps/web` `build` (27 routes) / `lint` / `prettier` / `tsc` clean, `vitest` **14/14**. |
| Full browser E2E | ✅ | 2026-09-09 — Playwright vs the compose stack (real `gemini-3.6-flash`): signup → objective → `/oscar` → "what should I focus on?" → grounded answer (`provider=gemini`, `outcome=ok`) → "add a task …" → **ProposedAction card** → verified in a second tab that **no task exists before confirming** → click Start → task appears via the normal `/objectives` page. Separately: with no key, `/oscar` shows the unavailable panel and objectives/tasks still work. |
| `docker compose` (all 5) | ✅ | 2026-09-09 — `build orbit-api orbit-web` → `up -d` (**no `-v`**) → all 5 `Up (healthy)`; **Flyway V10 applied on the existing pilot DB** (v9 → v10), pilot account `himaanshu15028@gmail.com` **row intact** (`active`). `OSCAR enabled — primary=gemini, fallback=groq, providers[gemini=configured, groq=configured]`. E2E `@orbit.local` users cleaned out afterward (cascade removes their `ai_conversations`/`ai_messages`). Torn down with `docker compose down` (no `-v`). |
| CI | ✅ | 2026-09-10 — [run #34397836691](https://github.com/Himaan1502/orbit/actions/runs/34397836691) (`b9d64ae`) on `main`: **Frontend, Backend, Auth Server all green**. |

Not in scope for M7 and still open: streaming (SSE), OSCAR-narrated Weekly
Review (`generated_by='oscar'` still reserved), contextual recommendation cards
on other views, trusted automation mode, intent-based selective context
retrieval, vector/embedding retrieval, cost controls / usage dashboard, the
post-confirmation "system note" persisted to history (UI-only in M7), SRE /
Balance / Integration tools. See
[deferred-items.md](docs/engineering/deferred-items.md).

M7 ADRs — **all six sign-off items pre-approved by the product owner 2026-09-09**,
build authorised the same day; all now **Accepted**:
[ADR-030](docs/adr/ADR-030-frontend-component-primitives.md) (component
primitives — hand-built + Radix, ratified),
[ADR-031](docs/adr/ADR-031-ai-provider-abstraction.md) (provider abstraction —
Gemini/Groq free-tier, feature-flagged; model IDs corrected — see its History),
[ADR-032](docs/adr/ADR-032-oscar-domain.md) (OSCAR domain — context builder,
tool registry, **OSCAR never writes**).

One deviation to flag, none of it a spec-decision override: the two ratified
model IDs (`gemini-2.0-flash`, `llama-3.3-70b-versatile`) were **both dead** by
implementation time (Google's own error recommended `gemini-3.6-flash`; Groq had
dropped the Llama 3.3 line). Corrected to current GA free-tier equivalents with
env overrides. The abstraction, routing, free-tier handling and every sign-off
item are unchanged — only the version strings moved. Recorded in ADR-031's
History section.

### Pre-Milestone-8 pass — OSCAR streaming + contextual cards — verified 2026-09-10

Our numbering; a scoped pass *before* the Production Platform milestone (the
non-streaming 30–60s wait is a real UX cost). Every row checked by running the
command / driving the flow.

| Item | Status | Notes |
|------|--------|-------|
| SSE streaming ([ADR-033](docs/adr/ADR-033-oscar-streaming.md)) | ✅ | `POST /api/v1/oscar/conversations/{id}/turns/stream` → `text/event-stream` via MVC `SseEmitter` (`OscarWebConfig`: 120s emitter timeout, dedicated 2–4 thread `oscarStreamExecutor`). The non-streaming `/turns` endpoint stays (tests, nudges, client fallback). Events: `status` / `token` / `proposedAction` / `meta` / `done` / `error`. `OscarStreamOrchestrator` reuses the tool-round loop; a `ProseFilter` (`TokenSink`) forwards prose and stops at the `===OSCAR-META===` sentinel. |
| `oscar-v2` response format | ✅ | Prose first, then optional `===OSCAR-META===` + `{kind, sections?, citations?}` JSON — **one format, one parser, both paths**. `oscar-v1` retired. `ResponseValidator.split` / `parseMeta`; **prose that arrived is always real** — the "safe fallback, unpersisted" path now only fires when *no prose at all* arrived. Legacy JSON-only replies still handled. `prompt_version = oscar-v2` on every new `ai_messages` row. |
| Provider adapters → JDK `HttpClient` | ✅ | `HttpAiProviderSupport` migrated off Spring `RestClient` to `java.net.http.HttpClient` (no new dependency) — blocking `post` + streaming `postStream` (`BodyHandlers.ofLines()`, SSE `data:` frames, `[DONE]` terminated). `GeminiProvider` (`:streamGenerateContent?alt=sse`) + `GroqProvider` (`stream:true` + `stream_options.include_usage`, streamed tool-call argument fragments reassembled by index). `completeStream(AiRequest, TokenSink)`. |
| Provider routing moved to `ProviderGateway` | ✅ | Shared by the blocking + streaming loops: primary → 1 retry on transient 5xx (not on 429) → fail over → `null` (degraded). **Streaming fail-over is gated on the first token** (ADR-033 §5) — after a token has streamed, a provider drop ends the stream (`error{recoverable}` + unpersisted, the partial prose flushed to the user) with **no mid-answer switch**. `ProviderGatewayTest` (8). |
| Tool rounds as `status` events | ✅ | "Checking your skill gaps and today's plan…" while read tools run; `proposedAction` events emitted before the synthesis stream on a mutation call. Bounded by `orbit.ai.max-tool-rounds` exactly as M7. |
| BFF + web streaming client | ✅ | BFF (`/api/orbit/[...path]`) detects `text/event-stream` and pipes `res.body` through **unbuffered** (`no-cache, no-transform`, `x-accel-buffering: no`). `streamOscarTurn()` — native `fetch` + `response.body.getReader()` + a ~40-line SSE line parser (no `EventSource`, no dependency). `OscarChat` renders `token` into a live bubble with a `▍` caret, `status` into a progress line, `proposedAction`/`meta` as they arrive; **falls back to the non-streaming `useSendTurn`** if the stream fails before the first token. |
| Contextual cards ([ADR-034](docs/adr/ADR-034-oscar-contextual-cards.md)) | ✅ | `GET /api/v1/oscar/nudge?surface=today\|objective&objectiveId=` — **single-shot, no tool rounds**, a small surface-focused context, **prose-only** (the nudge generator declares no mutation tools — OSCAR never writes for nudges). `NudgeService` + `NudgeCache` (`StringRedisTemplate` — the **first real API Redis use**): result cached by `oscar:nudge:{user}:{surface}:{id}:{data-fingerprint}` (12h TTL) so an unchanged page = **zero provider calls**; a separate `orbit.ai.nudge-hourly-cap` (12) budget, isolated from the conversational cap. Redis down / provider down / `SKIP` → `status:"none"`/`"unavailable"` → the `<OscarNudge>` component **renders nothing** (no error, no banner). Dismissible per surface per day (localStorage). Surfaces: `/today` + `/objectives/[id]`. |
| Ownership / degradation | ✅ | Nudge for another user's objective → `status:"none"` (never a leak). Streamed turn: ownership-404 / 429 / 422 happen synchronously (normal JSON) before the SSE starts. `OscarNudgeIntegrationTest` (3, incl. the fingerprint-cache "no 2nd provider call" assertion + Alice/Bob), `OscarConversationIntegrationTest` (+1: nudge `unavailable` with no key). |
| Priority Engine / Skill Scoring / Readiness | ✅ | **Untouched.** |
| Tests | ✅ | `apps/api` `./gradlew clean build` — `BUILD SUCCESSFUL`, **204 tests** (0 failures), Checkstyle 0, SpotBugs 0. `apps/auth` `build` — 12 tests. `apps/web` `build` (27 routes) / `lint` / `prettier` / `tsc` clean, `vitest` **17/17** (+`OscarNudge.test.tsx`). |
| Full browser E2E | ✅ | 2026-09-10 — Playwright vs the compose stack (real `gemini-3.6-flash`): `/oscar` → ask a question → **the `▍` caret shows, answer text appears and grows before the turn finishes**, caret clears on completion, the answer persists across a reload. Contextual card: renders on `/today` + `/objectives/[id]` (or is silently absent on a `SKIP`), **Dismiss sticks across a reload**, **no error surface** on either page. |
| `docker compose` (all 5) | ✅ | 2026-09-10 — `build orbit-api orbit-web` → `up -d` (**no `-v`**) → all 5 `Up (healthy)`; **no new migration** (still Flyway V10), pilot account `himaanshu15028@gmail.com` **row intact** (`active`). E2E `@orbit.local` users cleaned out afterward. Torn down with `docker compose down` (no `-v`). |
| CI | ✅ | 2026-09-10 — [run #34430743402](https://github.com/Himaan1502/orbit/actions/runs/34430743402) (`d1e5c9f`) on `main`: **Frontend, Backend, Auth Server all green**. |

Not in scope for this pass and still open: streaming for contextual nudges
(cached, latency hidden), skill / roadmap / Career nudge surfaces, a nudge
`proposedAction` (prose-only this pass), scheduled nudge pre-warm, any SSE
library, WebFlux migration, a mid-stream idle watchdog (hard stream cap only),
the post-confirmation "system note" (still M7's deferral). See
[deferred-items.md](docs/engineering/deferred-items.md).

Pre-M8 ADRs — **all 12 sign-off items ratified by the product owner 2026-09-10**:
[ADR-033](docs/adr/ADR-033-oscar-streaming.md) (SSE, `oscar-v2`, JDK `HttpClient`,
fail-over pre-first-token), [ADR-034](docs/adr/ADR-034-oscar-contextual-cards.md)
(`/oscar/nudge`, Redis fingerprint cache, own hourly budget, silent-by-default).
New tunable: `orbit.ai.nudge-hourly-cap` (12).

Auth: OIDC via self-hosted Spring Authorization Server (`apps/auth`) — see
[ADR-004](docs/adr/ADR-004-oidc-authentication.md). M1 persistence and identity:
[ADR-011](docs/adr/ADR-011-authorization-server-persistence.md),
[ADR-012](docs/adr/ADR-012-identity-and-registration-model.md),
[ADR-013](docs/adr/ADR-013-frontend-session-and-bff.md).

Known deferrals: [docs/engineering/deferred-items.md](docs/engineering/deferred-items.md).
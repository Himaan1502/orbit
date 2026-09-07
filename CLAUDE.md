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
| Priority Engine v1 (Blueprint §22) | ✅ | Weighted sum, Blueprint §22 weights, configurable (`orbit.priority.weights.*`, startup-validated to sum 1). Pure `PriorityEngine`; `priority_reason` JSONB carries every factor's value/weight/contribution/source. `PriorityEngineTest` (8, direct). [ADR-014](docs/adr/ADR-014-priority-engine-v1.md) — **weight values flagged for sign-off**. |
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
**needs sign-off**), [ADR-015](docs/adr/ADR-015-daily-plan-generation.md),
[ADR-016](docs/adr/ADR-016-objective-status-and-progress.md).

Auth: OIDC via self-hosted Spring Authorization Server (`apps/auth`) — see
[ADR-004](docs/adr/ADR-004-oidc-authentication.md). M1 persistence and identity:
[ADR-011](docs/adr/ADR-011-authorization-server-persistence.md),
[ADR-012](docs/adr/ADR-012-identity-and-registration-model.md),
[ADR-013](docs/adr/ADR-013-frontend-session-and-bff.md).

Known deferrals: [docs/engineering/deferred-items.md](docs/engineering/deferred-items.md).
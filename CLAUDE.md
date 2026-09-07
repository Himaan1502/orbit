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

Auth: OIDC via self-hosted Spring Authorization Server (`apps/auth`) — see
[ADR-004](docs/adr/ADR-004-oidc-authentication.md). M1 persistence and identity:
[ADR-011](docs/adr/ADR-011-authorization-server-persistence.md),
[ADR-012](docs/adr/ADR-012-identity-and-registration-model.md),
[ADR-013](docs/adr/ADR-013-frontend-session-and-bff.md).

Known deferrals: [docs/engineering/deferred-items.md](docs/engineering/deferred-items.md).
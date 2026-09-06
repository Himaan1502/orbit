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

Auth: OIDC via self-hosted Spring Authorization Server (`apps/auth`) — see
[ADR-004](docs/adr/ADR-004-oidc-authentication.md).

Known deferrals: [docs/engineering/deferred-items.md](docs/engineering/deferred-items.md).
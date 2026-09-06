# Deferred Engineering Items

Cross-cutting work consciously postponed, with the milestone it is expected to
land in. Add an entry here instead of a bare `TODO` in code, and link back to
this file from the relevant comment.

---

## M1 — Hermetic test suite (Testcontainers)

**Context.** The `apps/api` test suite currently talks to externally-provided
infrastructure: `application-test.yml` points Postgres and Redis at
`localhost:5432` / `localhost:6379`, so `./gradlew test` requires
`docker compose up -d orbit-db orbit-redis` (and, for Redis, an exported
`REDIS_PASSWORD=redis_pass`) to be running first. CI works around this with
service containers. The `testcontainers` core, `junit-jupiter`, and `postgresql`
modules are already on the test classpath but unused.

**Deferred decision.** Wire Testcontainers for both Postgres and Redis so the
suite is self-contained on any machine with a Docker daemon, and remove the
`localhost` assumptions (and the Redis password mismatch) from
`application-test.yml`. Add the `testcontainers` Redis module (or a
`GenericContainer`).

**Why not now.** Milestone 0 is bootstrap-only; the two existing tests
(`contextLoads`, `HealthControllerTest`) pass against the running compose stack,
and no test exercises Redis. Not worth the setup churn before M1 adds real
persistence tests.

**Touch points.**
- `apps/api/src/main/resources/application-test.yml`
- `apps/api/build.gradle.kts` (test dependencies)
- `.github/workflows/ci.yml` (service containers become removable once tests
  self-provision)

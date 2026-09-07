# Deferred Engineering Items

Cross-cutting work consciously postponed, with the milestone it is expected to
land in. Add an entry here instead of a bare `TODO` in code, and link back to
this file from the relevant comment.

---

## ✅ Closed in Milestone 1 (incl. follow-ups)

- **Hermetic test suite (Testcontainers).** `apps/api` and `apps/auth` now start
  PostgreSQL (and Redis, for the API) via Testcontainers using the
  singleton-container pattern. `application-test.yml` no longer assumes a local
  instance or a particular Redis password. CI service containers removed.
- **`apps/api/application-test.yml` relocated** from `src/main/resources/` to
  `src/test/resources/` — it no longer ships in the production jar.
- **Signing-key rotation.** Overlapping rotation implemented in `SigningKeyManager`
  ([ADR-011](../adr/ADR-011-authorization-server-persistence.md) §3): the JWKS
  publishes the active key plus retired keys still inside the overlap window
  (`AUTH_SIGNING_KEY_OVERLAP`, default 7d); the signer is pinned to the active
  `kid` via the JWS header. Operator-triggered by `AUTH_ROTATE_SIGNING_KEY=true`
  + restart; the in-memory JWK set refreshes live. A zero-restart admin HTTP
  trigger remains a possible future addition but is not needed.

---

## ✅ Closed in Milestone 2

- **Objectives / Milestones / Tasks / Daily Planning** — Blueprint §9, §10, §20.
  See CLAUDE.md's M2 status table and ADR-014/015/016.
- **Priority Engine v1** — Blueprint §22, configurable weights
  ([ADR-014](../adr/ADR-014-priority-engine-v1.md)).

---

## M3 — Priority Engine: real factor providers

**Context.** [ADR-014](../adr/ADR-014-priority-engine-v1.md) ships the full
Blueprint §22 eight-factor formula, but in M2 four factors return a neutral
`0.5` (`skillGap`, `dependencyImpact`, `projectImpact`, `interviewValue`) and
`careerRelevance` is proxied by the objective's manual priority.

**Deferred decision.** As each domain lands — Career Engine (M3), Skills, the
dependency graph, Projects — replace its neutral/proxy factor provider with a
real computation. The formula, weights, `priority_reason` shape and API do not
change; only the provider swaps.

**Touch points.** `apps/api` `execution/priority/PriorityEngine.java`,
`execution/service/TaskScoringService.java`.

---

## M4+ — Durable / async job runner

**Context.** Daily-plan generation runs **inline** in the request
([ADR-015](../adr/ADR-015-daily-plan-generation.md) §5). Blueprint §33 / Tech
Arch §11 call for "a durable job mechanism appropriate to the Spring ecosystem".

**Deferred decision.** Introduce the runner (Quartz, Spring Batch, or a
DB-polling worker — its own ADR) when analytics aggregation (Blueprint §15) or
scheduled plan refresh needs background work. `DailyPlanGenerator.generate(...)`
is already a pure function such a runner can call directly.

**Why not now.** Inline generation completes within the request for a single
user; adding a runner before there is scheduled work to run is premature.

---

## ✅ Resolved (2026-09-07) — Objective progress when milestones AND loose tasks coexist

An objective's loose tasks (objective set, milestone null) now count together as
**one synthetic unit** of progress alongside its milestones
([ADR-016](../adr/ADR-016-objective-status-and-progress.md) §3, amended).
Milestone-only and task-only objectives are unchanged.

---

## ✅ Resolved (2026-09-07) — Priority Engine weight-sum validation test

`PriorityWeightsTest` exercises the package-private `validate()` directly: the
context refuses to start if the eight weights do not sum to 1 (±0.001).

---

## M2+ — Daily-plan packing quality

**Context.** `DailyPlanGenerator` uses greedy selection by priority within the
minute budget ([ADR-015](../adr/ADR-015-daily-plan-generation.md), alternative 2).

**Deferred decision.** If users find the plan wastes time at the end of the
budget, replace greedy with a bounded knapsack / best-fit packing. Greedy is
kept for v1 because it is explainable and O(n log n).

---

## M2+ — Redis-backed rate limiting and durable lockout

**Context.** Login / registration rate limiting and failed-login lockout in
`apps/auth` are **in-memory** (Bucket4j + a `ConcurrentHashMap`), per
[ADR-012](../adr/ADR-012-identity-and-registration-model.md) §5. State resets on
restart and is not shared across replicas. This is a deviation from
[ADR-005](../adr/ADR-005-redis-usage-boundaries.md), which names Redis for
rate-limit state.

**Deferred decision.** When `apps/auth` runs more than one replica (or restart
resets become a real abuse gap), move rate-limit buckets and lockout counters to
Redis, and add a `auth.account_locks` table (or Redis TTL keys) for durable
lockout with an admin unlock path.

**Why not now.** `apps/auth` is single-instance in M1; adding Redis as a hard
dependency of that service is not yet justified.

**Touch points.** `apps/auth` `ratelimit/` package, `build.gradle.kts`,
`docker-compose.yml` (`orbit-auth` would gain a Redis dependency).

---

## M2+ — Password reset, email verification, MFA

**Context.** M1 scope is registration + login + logout only. There is no email
delivery in the platform yet.

**Deferred decision.** Email-verification-on-signup (with a `verified_at` column
on `auth.users`), self-service password reset (time-boxed single-use tokens),
and TOTP/WebAuthn MFA. Each needs an outbound email/notification channel first.

**Why not now.** Explicitly out of scope for Milestone 1. Single user today.

**Touch points.** `apps/auth` `identity/` package, a new `auth` migration, a
mail/notification service (does not exist yet).

---

## M2+ — HIBP (Have I Been Pwned) password check

**Context.** `RegistrationService` rejects short passwords, passwords containing
the email, all-digit / low-variety passwords, and a small embedded base-word
deny-list. It does **not** check breach corpora.

**Deferred decision.** Add a k-anonymity range query to the HIBP Pwned Passwords
API (send only the first 5 SHA-1 hex chars) during registration, fail-open on
API error.

**Why not now.** Adds an outbound dependency on a third-party API for a
single-user tool; the embedded checks cover the common cases.

**Touch points.** `apps/auth` `RegistrationService#validatePassword`.

---

## M2+ — ADMIN role and RBAC surface

**Context.** [ADR-004](../adr/ADR-004-oidc-authentication.md) allows `USER` /
`ADMIN`. M1 issues only `roles: ["USER"]`
([ADR-012](../adr/ADR-012-identity-and-registration-model.md) §2). The resource
server already maps a `roles` claim to `ROLE_*`.

**Deferred decision.** An `auth.users.role` column (or a roles table), admin
assignment, and `@PreAuthorize("hasRole('ADMIN')")` on the endpoints that need
it.

**Why not now.** No admin-only functionality exists yet.

---

## M2+ — BFF CSRF hardening (double-submit token)

**Context.** The web BFF relies on `SameSite=Lax` + an `Origin`/`Sec-Fetch-Site`
check for its state-changing routes
([ADR-013](../adr/ADR-013-frontend-session-and-bff.md) §4). No rotating
anti-CSRF token.

**Deferred decision.** Add a double-submit CSRF token for non-GET requests
through `/api/orbit/*` and `/logout` if the residual `SameSite=Lax` gap (a
same-site subdomain, or an old browser) becomes a concern.

**Why not now.** With `SameSite=Lax`, the origin check, and GET requests being
side-effect-free at the proxy, the M1 residual surface is low for a single-user
tool on one origin.

---

## M2+ — Auth-server session store & issuer for multi-instance / hosted

**Context.** `apps/auth` uses the default in-memory HTTP session for the login
UI, and the compose issuer is `http://localhost:9000`
([ADR-004](../adr/ADR-004-oidc-authentication.md) "Consequences").

**Deferred decision.** For a hosted / multi-replica deployment: a shared session
store (Spring Session + Redis or JDBC) for the auth server's login UI, and a
single externally-resolvable HTTPS issuer URL.

**Why not now.** Single instance, local only.

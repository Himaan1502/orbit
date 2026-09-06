# ADR-012: Identity & Registration Model

## Status
Accepted

## Date
2026-09-07

## Context
[ADR-004](ADR-004-oidc-authentication.md) chose OIDC via the self-hosted
authorization server and left one question explicitly open for Milestone 1:

> The credential store: whether the authorization server reads `orbit.users`
> directly or owns a dedicated identity table, and whether `password_hash`
> becomes nullable for future federated logins.

The Milestone 0 scaffold predates the OIDC revert. It was built against the
superseded *JWT* ADR-004, in which `apps/api` verified passwords. Artifacts of
that assumption remain:

- `apps/api` `V1__create_users_table.sql` and the `User` entity carry
  `password_hash VARCHAR(255) NOT NULL`.
- `apps/api` `UserRepository` has `findByEmailAndDeletedAtIsNull`.

Under OIDC, `apps/api` "performs no password verification" (ADR-004 §1), so
credential columns do not belong in its domain.

Blueprint §6 (Core Data Model) and §7 (PostgreSQL Schema) are authoritative:
`User ├── UserProfile`, with `users` = `id, email, display_name, status,
created_at, updated_at` (**no password column**) and `user_profiles` =
`user_id PK/FK, timezone, locale, available_daily_minutes, preferences JSONB,
timestamps`. Blueprint §5 lists `identity/` as a backend domain.

Milestone 1 objective 3 requires a real registration flow with proper password
hashing, email uniqueness, and validation — built to support multiple users
even though there is one today.

## Decision

### 1. Two tables, two owners, one identifier
| Concern | Owner | Table | Contents |
|---------|-------|-------|----------|
| **Credential + account** | `apps/auth` | `auth.users` | `id UUID PK`, `email VARCHAR(254) UNIQUE NOT NULL`, `password_hash NOT NULL`, `status NOT NULL` (`active` / `disabled`), `display_name`, `created_at`, `updated_at` |
| **Domain profile** | `apps/api` | `orbit.users` + `orbit.user_profiles` | Per Blueprint §7.1 / §7.2. `orbit.users` has **no** `password_hash`. |

`auth.users.id` **is** the value used as the OIDC `sub` claim, and
`orbit.users.id` is set equal to it. One user, one UUID, everywhere. This is the
standard OIDC integration shape (an app keyed by the IdP's `sub`) and the one
ADR-004 anticipated ("owns a dedicated identity table").

Email is normalised to lower case by the application before every insert and
lookup, with a `CHECK (email = lower(email))` constraint as the backstop, so
`Alice@x.com` and `alice@x.com` are the same account. (No `citext` extension —
one fewer database dependency.)

### 2. `sub` = UUID, not email
The authorization server issues tokens whose `sub` is `auth.users.id`. OIDC
defines `sub` as the stable, never-reassigned identifier for the end user;
email is mutable and must not be `sub`. An `OAuth2TokenCustomizer` sets:

- `sub` → `auth.users.id`
- `email` → the user's email
- `roles` → `["USER"]` (the `roles` claim ADR-004 §1 maps to `ROLE_*`;
  `ADMIN` is not issued in M1)

The login principal name (used in logs and the consent screen) remains the
email; only the token claim is the UUID.

### 3. `apps/api` provisions the profile just-in-time
`apps/api` does not call the auth server and the auth server does not write the
`orbit` schema. Instead, on the first authenticated request for a given `sub`,
`apps/api` inserts an `orbit.users` row (`id` = `sub`, `email` / `display_name`
from token claims) and an empty `orbit.user_profiles` row with default
`timezone = 'UTC'`. Subsequent requests read the existing row. A
`CurrentUserService.getOrProvision()` encapsulates this; it is the only way
controllers obtain the current user.

Rationale: keeps the resource server dependency-free of the auth server at
runtime (ADR-004's portability goal — swap the IdP via config), and means a
federated login added later Just Works with no credential row.

### 4. Registration flow (auth server)
- `POST /register` (form-encoded, CSRF-protected) and a server-rendered
  `GET /signup` page, both on `apps/auth`. Password entry never touches the SPA
  (UX §15.1: "no unnecessary credential collection").
- Validation (Bean Validation + explicit checks):
  - email: syntactically valid, ≤ 254 chars, normalised to lower case, unique
    (checked pre-insert **and** enforced by the `UNIQUE` constraint — the
    constraint is the race-condition backstop, returning a friendly "email
    already registered" on `DataIntegrityViolationException`).
  - password: 12–128 chars, not equal to the email, checked against a small
    embedded list of the most common passwords. No composition rules (NIST
    800-63B guidance). Deferred: HIBP k-anonymity check.
  - display name: optional, ≤ 100 chars, trimmed, control chars stripped.
- Hashing: **BCrypt, strength 12**, via Spring Security's `PasswordEncoder`
  (the M0 `DefaultSecurityConfig` bean, unchanged). Never a hand-rolled scheme.
- On success: create `auth.users`, write an `auth.auth_events` row
  (`type = REGISTERED`), and redirect to `/login?registered` (M1 does not
  auto-login on register — one less state transition to get wrong; the user
  logs in immediately after).
- `UserDetailsService` is replaced with a JDBC/JPA-backed implementation
  reading `auth.users` (`status = 'active'` required).

### 5. Audit events and abuse controls
- `auth.auth_events` table: `id`, `user_id NULL`, `email`, `type`
  (`REGISTERED` / `LOGIN_SUCCESS` / `LOGIN_FAILURE` / `LOGOUT`), `ip`,
  `user_agent`, `created_at`. Written for every attempt. Also emitted as
  structured log lines (`event=auth.login.failure email=... ip=...`).
- Rate limiting: **Bucket4j in-memory**, per-IP on `POST /login` and
  `POST /register` (e.g. 10/min), plus a per-email failed-login throttle
  (progressive delay, temporary 15-minute cooldown after 10 consecutive
  failures). This is the M1 "rate limiting" and "account lockout" from
  Blueprint §35. **Deviation from [ADR-005](ADR-005-redis-usage-boundaries.md)**,
  which names Redis for rate-limit state: `apps/auth` is single-instance in M1
  and adding Redis to it is not justified yet. Redis-backed distributed limiting
  and durable lockout state are deferred (see `deferred-items.md`).

### 6. `apps/api` cleanup (V2 migration)
- `ALTER TABLE orbit.users DROP COLUMN password_hash;`
- Add `orbit.user_profiles` per Blueprint §7.2.
- Remove `passwordHash` from the `User` entity; drop
  `findByEmailAndDeletedAtIsNull` password-oriented helpers not needed by the
  resource server (keep `findByEmail` for provisioning lookups).
- `V1` is **not** edited (immutable-migrations rule, ADR-009). Existing dev
  volumes get `V2` on next boot; `password_hash` had no real data.

## Alternatives Considered
1. **`apps/auth` reads `orbit.users` directly** — rejected; couples the two
   services' schemas and migration ordering, and forces credential columns into
   the API domain that ADR-004 says holds none.
2. **`sub` = email** — rejected; violates OIDC `sub` semantics, breaks if the
   user ever changes email, and leaks the email into every resource-server log
   as the principal id.
3. **`apps/api` `users` row created by a call from `apps/auth` on registration**
   — rejected; makes the auth server depend on the API being up during signup
   and puts an ORBIT-schema write in the auth server. JIT provisioning has
   neither problem.
4. **Auto-login on registration** — deferred; a nice-to-have that adds a
   code→token exchange path from the register handler. M1 redirects to `/login`.
5. **Argon2id instead of BCrypt** — BCrypt-12 is what M0 already configured and
   what ADR-004's history references; Spring Security's default support is
   first-class. Argon2 is a reasonable future migration (the `PasswordEncoder`
   is swappable and `DelegatingPasswordEncoder` handles the `{id}` prefix).

## Consequences
- **Positive**
  - Credentials live in exactly one place, owned by the service whose job is
    authentication.
  - `apps/api` stays a clean resource server; its `users` table finally matches
    Blueprint §7.1.
  - Ownership checks key off a stable UUID that is identical in the token, the
    auth DB, and the API DB.
  - Registration is a real, validated, rate-limited, audited flow.
- **Negative**
  - Two `users` tables can confuse a newcomer; mitigated by this ADR and code
    comments on both entities pointing here.
  - JIT provisioning means an `orbit.users` row does not exist until the user's
    first authenticated API call. Acceptable — the web app always makes one
    (loading the shell) immediately after login.
  - In-memory rate limiting resets on `apps/auth` restart and is not shared
    across replicas. Documented as deferred.

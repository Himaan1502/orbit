# ADR-011: Authorization Server Persistence (Clients, Authorizations, Consent, Signing Keys)

## Status
Accepted

## Date
2026-09-07

## Context
[ADR-004](ADR-004-oidc-authentication.md) established the self-hosted Spring
Authorization Server (`apps/auth`) and explicitly deferred its persistence to
Milestone 1 (User Identity & Authentication). The Milestone 0 scaffold runs
entirely in memory:

- `InMemoryRegisteredClientRepository` — the single `orbit-web` client is
  reconstructed from configuration on every boot.
- No `OAuth2AuthorizationService` bean — authorization-code and refresh-token
  state lives only in the default in-memory store, so an auth-server restart
  drops every in-flight authorization and every issued refresh token.
- `jwkSource()` generates a fresh RSA keypair at startup
  (`AuthorizationServerConfig.generateRsaKey()`), logging a warning. Every
  restart rotates the signing key, which invalidates **all** previously issued
  access and ID tokens and breaks the resource server (`apps/api`) until its
  JWKS cache refreshes.

Milestone 1 objectives 1 and 2 require this to be fixed: signing keys and the
client/consent/authorization store must survive restarts.

ADR-004's deferred-items note suggested keys be "loaded from a mounted keystore
or secrets manager". That was a sketch, not a decision — the mechanism was left
open for this ADR.

## Decision

### 1. Storage substrate: the existing PostgreSQL instance, dedicated `auth` schema
`apps/auth` connects to the same `orbit_db` PostgreSQL database as `apps/api`,
but owns a separate `auth` schema. It never reads or writes the `orbit` schema,
and `apps/api` never touches `auth`. Schema separation (not a separate database)
keeps local development and compose to one Postgres container while giving each
service an independent migration history and clear ownership boundary.

- `apps/auth` gets `spring-boot-starter-data-jpa`, the PostgreSQL driver, and
  `flyway-core`.
- Flyway for `apps/auth` is configured with `default-schema: auth`,
  `schemas: auth`, `create-schemas: true`, migrations under
  `apps/auth/src/main/resources/db/migration`.
- `docker/postgres/init.sql` also creates the `auth` schema and grants (for
  fresh volumes); `create-schemas: true` covers existing volumes.

This does **not** make `apps/auth` a domain service or violate
[ADR-001](ADR-001-modular-monolith-architecture.md). Per ADR-004 it is supporting
infrastructure, the same category as the database itself. It holds identity and
OAuth2 protocol state only — no ORBIT business domain data.

### 2. Client / authorization / consent store: Spring Authorization Server JDBC
Replace the in-memory beans with the framework's JDBC implementations:

| Bean | Implementation | Table(s) |
|------|----------------|----------|
| `RegisteredClientRepository` | `JdbcRegisteredClientRepository` | `auth.oauth2_registered_client` |
| `OAuth2AuthorizationService` | `JdbcOAuth2AuthorizationService` | `auth.oauth2_authorization` |
| `OAuth2AuthorizationConsentService` | `JdbcOAuth2AuthorizationConsentService` | `auth.oauth2_authorization_consent` |

Tables are created by a Flyway migration containing the PostgreSQL-adapted DDL
from Spring Authorization Server 1.2's bundled schema resources (`blob` → `bytea`,
`clob`/`varchar(4000)` → `text` where the token payloads exceed 4000 chars).

The `orbit-web` client registration is **seeded idempotently on startup** from
configuration (`ApplicationRunner`): if no client with the configured
`client-id` exists, insert it; if it exists, update its redirect URIs, scopes,
and token settings to match configuration. Configuration stays the source of
truth for the client definition; the database makes it durable and lets
`oauth2_authorization` rows carry a stable `registered_client_id` foreign
reference across restarts.

### 3. Signing keys: `auth.signing_keys`, private key encrypted at rest, rotation-ready
A Flyway-created table:

```
auth.signing_keys
- id            UUID PK
- key_id        VARCHAR   -- the JWK "kid"
- public_key    TEXT      -- X.509 SubjectPublicKeyInfo, base64
- private_key   TEXT      -- PKCS#8, AES-GCM encrypted, base64
- algorithm     VARCHAR   -- "RS256"
- active        BOOLEAN   -- exactly one row is the active signer
- created_at    TIMESTAMPTZ
- rotated_at    TIMESTAMPTZ NULL
```

- On startup the server loads the `active` row. If none exists, it generates a
  2048-bit RSA keypair, encrypts the PKCS#8 private key with **AES-256-GCM**
  using a key derived (PBKDF2WithHmacSHA256) from the `AUTH_KEYSTORE_SECRET`
  environment variable, and inserts it as `active`.
- **M1 scope:** the `JWKSource` publishes exactly the one active key, and signs
  with it. This is what makes restarts non-destructive (the objective).
- **Rotation is deferred** (see `deferred-items.md`): overlapping-key rotation —
  insert a new `active` key, keep publishing the previous public key until every
  token it signed has expired — needs a `JWKSource` that returns all valid
  public keys for the JWKS endpoint but a single key for signing (Nimbus'
  `NimbusJwtEncoder` rejects an ambiguous multi-key signing selection). The
  `signing_keys` table already carries `active` / `rotated_at` so the history
  model does not change when rotation lands. Until then, a key change is an
  explicit operator action that invalidates outstanding tokens — acceptable,
  unlike the M0 behaviour of doing that on every restart.
- `AUTH_KEYSTORE_SECRET` is the single piece of key material outside the
  database. It is not committed; `docker-compose.yml` supplies a documented
  local-only default via `.env`, exactly as the database password is handled.
  Losing it means regenerating keys (all tokens invalidated) — the same blast
  radius as today, but only on secret loss rather than on every restart.

### Why not the alternatives
| Option | Why not |
|--------|---------|
| **Mounted JKS/PKCS12 keystore file** (ADR-004's sketch) | Needs a Docker volume mount and a keystore-password secret anyway; rotation means regenerating and redistributing a file; not exercised by the Testcontainers-based test suite without extra fixture plumbing. The DB table gives the same "survives restart" guarantee with less moving infrastructure. |
| **Secrets manager (Vault / AWS SM)** | Real answer for hosted production; disproportionate for a single-user tool with no hosted environment yet. The `SigningKeyRepository` abstraction leaves this swap open. |
| **Keys in the DB, unencrypted** | A Postgres dump would contain usable signing keys. AES-GCM with an out-of-band secret is a small amount of code for a meaningful reduction in blast radius. |
| **Separate `auth` database** | More connection config and a second `docker-compose` DB or a manual `CREATE DATABASE`; no real isolation benefit over a schema for a single-tenant tool. |

## Alternatives Considered
1. **Keep keys in memory, accept restart rotation** — rejected; it is exactly
   the M1 objective. It also makes `apps/api` flaky in local development every
   time the auth server restarts.
2. **Shared JDBC store in the `orbit` schema** — rejected; couples the auth
   server's migrations to the API's schema and forces `orbit.users` to carry
   credential columns, which [ADR-012](ADR-012-identity-and-registration-model.md)
   explicitly moves out.
3. **`COMPOSE`-only Postgres, H2 for tests** — rejected; the suite must exercise
   the real PostgreSQL DDL (Blueprint §43 "Integration: PostgreSQL, migrations").
   Testcontainers is used instead (Milestone 1 objective 7).

## Consequences
- **Positive**
  - Restarting `apps/auth` no longer invalidates issued tokens (verified in M1
    by restarting the container and replaying a token against `apps/api`).
  - In-flight authorization codes and refresh tokens survive restarts.
  - One migration history per service; `apps/auth` schema evolution is
    reviewable and versioned like the API's.
  - Signing keys are recoverable and rotatable without a redeploy.
- **Negative**
  - `apps/auth` now depends on PostgreSQL to start (previously it needed
    nothing). `docker-compose` already gates it behind `orbit-db` health via a
    new `depends_on`. Local `bootRun` needs the DB up.
  - One more secret to manage (`AUTH_KEYSTORE_SECRET`).
  - The Spring Authorization Server JDBC schema is framework-defined; a major
    SAS upgrade may ship a migration we have to adapt. Acceptable — pinned
    version, changelog is watched.

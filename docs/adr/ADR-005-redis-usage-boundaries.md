# ADR-005: Redis Usage Boundaries

## Status
Accepted (amended 2026-09-07 — see History)

## Date
2026-09-05

## Context
Redis is included in the technology stack. We need to define clear boundaries for what Redis is and is not used for to prevent it from becoming a shadow database.

## Decision
Redis is used exclusively for:
1. **Rate limiting** state
2. **Caching** frequently-read, expensive-to-compute aggregations
3. **Session state** for ephemeral data (short-lived AI/session state per Tech Arch §10)

Redis is **NOT** used for:
- Primary data storage for any domain entity
- Message queuing (use database-backed jobs instead)
- Full-text search

PostgreSQL remains the authoritative source of truth. All data in Redis is ephemeral and can be safely flushed without data loss.

## Alternatives Considered
1. **No Redis**: Considered deferring Redis until cache pressure justifies it. Kept from Milestone 0 for caching and rate limiting.
2. **Redis as primary store for some entities**: Rejected. Violates the PostgreSQL-as-system-of-record principle.

## Consequences
- **Positive**: Clear boundaries prevent Redis from becoming a shadow database. Ephemeral data can be safely flushed.
- **Negative**: Additional infrastructure component from day one. Mitigated by lightweight Redis deployment.
- Rate-limit state in Milestone 1 is **in-memory** in `apps/auth`, not Redis (single instance — see [ADR-012](ADR-012-identity-and-registration-model.md) §5). Moving it to Redis is a tracked deferred item.

## History
- **2026-09-05** — Accepted with four Redis uses, the first being "JWT token
  blacklisting (logout invalidation)".
- **2026-09-07** — Bullet 1 removed. It was an artifact of the superseded
  *JWT* ADR-004; under OIDC ([ADR-004](ADR-004-oidc-authentication.md)) the API
  is a stateless resource server with **no** token blacklist. Logout is the
  authorization server's `/connect/logout` (OIDC end-session) plus
  refresh-token revocation ([ADR-013](ADR-013-frontend-session-and-bff.md) §2).

# ADR-005: Redis Usage Boundaries

## Status
Accepted

## Date
2026-09-05

## Context
Redis is included in the technology stack. We need to define clear boundaries for what Redis is and is not used for to prevent it from becoming a shadow database.

## Decision
Redis is used exclusively for:
1. **JWT token blacklisting** (logout invalidation)
2. **Rate limiting** state
3. **Caching** frequently-read, expensive-to-compute aggregations
4. **Session state** for ephemeral data

Redis is **NOT** used for:
- Primary data storage for any domain entity
- Message queuing (use database-backed jobs instead)
- Full-text search

PostgreSQL remains the authoritative source of truth. All data in Redis is ephemeral and can be safely flushed without data loss.

## Alternatives Considered
1. **No Redis**: Considered deferring Redis until cache pressure justifies it. Included from Milestone 0 because JWT blacklisting and rate limiting benefit from it immediately.
2. **Redis as primary store for some entities**: Rejected. Violates the PostgreSQL-as-system-of-record principle.

## Consequences
- **Positive**: Clear boundaries prevent Redis from becoming a shadow database. Ephemeral data can be safely flushed.
- **Negative**: Additional infrastructure component from day one. Mitigated by lightweight Redis deployment.

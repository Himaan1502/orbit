# ADR-002: PostgreSQL as System of Record

## Status
Accepted

## Date
2026-09-05

## Context
ORBIT needs a reliable, ACID-compliant data store for all core entities. The platform manages career objectives, skills, projects, evidence, and execution plans — all of which require strong consistency guarantees.

## Decision
PostgreSQL is the single authoritative source of truth for all ORBIT data. Redis serves only as a secondary cache and ephemeral state store.

- All core entities are stored in normalized relational tables.
- JSONB is used only for genuinely semi-structured data (user preferences, priority factor weights) that is not regularly queried relationally.
- UUIDs are used for all primary keys.
- All tables include `created_at` and `updated_at` audit timestamps.
- Schema evolution is managed via versioned Flyway migrations committed to source control.

## Alternatives Considered
1. **MongoDB**: Rejected. The domain model is deeply relational with many cross-entity queries. Document stores would require denormalization and lose referential integrity.
2. **SQLite**: Rejected. Lacks concurrent access support and advanced features (JSONB, full-text search) needed for the platform.

## Consequences
- **Positive**: Strong consistency, referential integrity, mature tooling, excellent query capabilities, JSONB for flexibility where needed.
- **Negative**: Requires more upfront schema design. Mitigated by Flyway migrations.

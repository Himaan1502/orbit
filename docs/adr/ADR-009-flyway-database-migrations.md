# ADR-009: Flyway for Database Migrations

## Status
Accepted

## Date
2026-09-05

## Context
The specifications mention both Flyway and Liquibase as options for database schema migration management.

## Decision
We will use **Flyway** for database migrations:
- Migration files: SQL-based, located at `src/main/resources/db/migration/`
- Naming convention: `V{version}__{description}.sql`
- Migrations run automatically on application startup
- Milestone 0 includes only `V1__create_users_table.sql`

## Alternatives Considered
1. **Liquibase**: Both are capable. Flyway is chosen for its simpler SQL-first approach, which aligns better with a PostgreSQL-centric architecture where we want direct control over DDL.

## Consequences
- **Positive**: Simple, SQL-native, well-integrated with Spring Boot, immutable migrations prevent accidental schema drift.
- **Negative**: Less flexible than Liquibase for complex rollback scenarios. Mitigated by careful migration planning.

# ADR-004: JWT-Based Authentication

## Status
Accepted

## Date
2026-09-05

## Context
The Technical Architecture Specification recommends OIDC/OAuth 2.0. The Engineering Blueprint and PRD specify JWT-based authentication with Spring Security. For Milestone 0, we need to establish the authentication infrastructure.

ORBIT is initially a single-user personal tool deployed privately. Full OIDC with an external provider adds operational complexity (provider dependency, callback URLs, token exchange) that is not justified at this stage.

## Decision
We will implement JWT-based authentication with Spring Security:
- Access tokens: 15-minute expiry, stored in memory on the frontend.
- Refresh tokens: 7-day expiry, stored in HttpOnly, Secure, SameSite=Strict cookies.
- BCrypt password hashing (strength 12).
- Token blacklisting via Redis on logout.

The architecture supports migration to OIDC later by keeping the authentication layer abstracted behind Spring Security interfaces.

## Alternatives Considered
1. **OIDC with Auth0/Keycloak**: Deferred. Adds external dependency and operational complexity not justified for initial single-user deployment. Will revisit when multi-user support is needed.
2. **Session-based auth**: Rejected. Stateless JWT aligns better with the API-first architecture.

## Consequences
- **Positive**: Self-contained, no external auth dependency, well-supported by Spring Security, works offline.
- **Negative**: Requires careful token management (refresh, blacklisting). Mitigated by Redis-backed blacklist and HttpOnly cookies for refresh tokens.

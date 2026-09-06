# ADR-004: OIDC Authentication via Self-Hosted Spring Authorization Server

## Status
Accepted

## Date
2026-09-06

## Approval
Explicitly approved by the product owner (himaanshu15028@gmail.com) on 2026-09-06,
reversing the earlier `ADR-004: JWT-Based Authentication` (see "History" below).
Authentication is a platform-level, security-critical decision; per `CLAUDE.md`
it was escalated for a direct decision rather than resolved inside an ADR alone.

## Context
Authentication is specified consistently across the authoritative documents — the
earlier JWT ADR described a spec conflict that does not exist in the text:

- **Technical Architecture Specification §9 (Authentication & Authorization)**:
  "OIDC/OAuth login. Short-lived access/session mechanism. Secure, HttpOnly,
  SameSite cookies where appropriate. CSRF protection where cookie authentication
  requires it. Server-side authorization on every protected resource." Roles
  USER / ADMIN.
- **Technical Architecture Specification §28 ("ADRs to Create First")**: lists
  this ADR by name as **"ADR-004 OIDC authentication"**.
- **Engineering Blueprint §35 (Authentication Flow)**:
  `Browser → OIDC Provider → Callback → Backend session → Secure cookie →
  Authenticated API`, with secure cookies, SameSite policy, CSRF protection,
  server-side authorization, resource-ownership checks, rate limiting, and login
  audit events.
- **Engineering Blueprint §55 ("Initial Open Decisions")**: "exact OIDC
  provider" is an open decision requiring an ADR before the relevant milestone.
- **PRD §11 / §12**: "Secure authentication", "Authorization/RBAC" — mechanism-
  agnostic; the PRD names no token or session technology.

Per the Engineering Handoff precedence rule, the Technical Architecture
Specification governs infrastructure and platform decisions, and "do not silently
pick one — raise an ADR if a real conflict is found." There is no conflict here:
both documents that name a mechanism name OIDC. The only genuinely open question
is *which* OIDC provider (Blueprint §55).

The concern that motivated the earlier JWT ADR was operational: a full external
OIDC provider (Auth0, Okta, Cognito) adds a third-party dependency, callback and
token-exchange configuration, and a billing relationship that is hard to justify
for an initially single-user, privately deployed tool.

## Decision

### 1. Protocol: OIDC / OAuth 2.1 Authorization Code + PKCE
The frontend is a public client. It obtains tokens via the Authorization Code
flow with PKCE against the OIDC provider. The backend (`apps/api`) is a pure
**OAuth2 resource server**: it holds no signing secret and performs no password
verification; it validates RS256-signed access tokens against the provider's
JWKS and authorizes every request server-side.

- Access token TTL: 15 minutes.
- Refresh token TTL: 7 days, rotating (no reuse).
- Token signing: RS256 (asymmetric). The provider publishes its public keys at
  `/oauth2/jwks`; there is **no shared symmetric secret anywhere in the system**.
- Authorities: a `roles` claim maps to `ROLE_*` Spring authorities; OAuth scopes
  map to `SCOPE_*`. Resource-ownership checks are enforced in domain services
  independently of any role.

### 2. Provider: self-hosted Spring Authorization Server (`apps/auth`)
This resolves Blueprint §55's "exact OIDC provider" open decision.

`apps/auth` is a standalone Spring Boot application (Spring Authorization Server,
the first-party Spring project — the same team and release train as Spring
Security). It runs as one additional container in `docker-compose.yml` alongside
PostgreSQL, Redis, the API, and the web app. It exposes the standard OIDC
endpoints: `/.well-known/openid-configuration`, `/oauth2/authorize`,
`/oauth2/token`, `/oauth2/jwks`, `/userinfo`, `/connect/logout`.

**Why Spring Authorization Server over the alternatives:**

| Option | Why not chosen |
|--------|----------------|
| **External SaaS** (Auth0 / Okta / Cognito) | Third-party runtime dependency, per-MAU billing, egress for every login, data-residency questions — the exact operational cost the team wanted to avoid. Reconsider only if we outsource identity deliberately. |
| **Keycloak** (self-hosted) | Capable and standards-complete, but a heavyweight Java app with its own admin console, theming system, upgrade cadence, and ~500 MB image. More surface area than a single-tenant personal tool needs, and its config lives outside our codebase. |
| **Ory Hydra** | Solid OAuth2/OIDC, but delegates login/consent UI to an app we would have to build anyway, and it is a separate Go toolchain in an otherwise JVM + Node repo. |
| **Spring Authorization Server** (chosen) | First-party Spring, version-aligned with Spring Boot 3.2 / Spring Security 6.2 already in use. Client registrations, scopes, token settings, and the login page are ordinary code and config in our repo and our CI. Minimal image (same Temurin base as `apps/api`). Clear, documented path to a persisted client/consent/key store and to swapping in an external IdP later, since the API only ever depends on standard OIDC discovery + JWKS. |

### 3. Milestone 0 scope
`apps/auth` is scaffolded, not finished:
- One registered client (`orbit-web`, public, PKCE required).
- One in-memory dev user; its password is generated and logged on startup
  (Spring Boot style) unless `AUTH_DEV_PASSWORD` is provided. No credentials are
  committed.
- Signing key is an ephemeral RSA keypair generated at startup.

Deferred to Milestone 1 (User Identity & Authentication):
- Persistent registered-client / authorization / consent store (JDBC on the
  `orbit` database or a dedicated `auth` schema — to be decided in M1).
- Persistent, rotatable signing keys loaded from a mounted keystore or secrets
  manager, so tokens survive restarts.
- The credential store: whether the authorization server reads `orbit.users`
  directly or owns a dedicated identity table, and whether `password_hash`
  becomes nullable for future federated logins.
- Login audit events, rate limiting on the token endpoint, account lockout
  (Blueprint §35 requirements).
- The frontend callback route and token handling (`apps/web`).

### 4. Relationship to ADR-001 (Modular Monolith)
A self-hosted identity provider is supporting infrastructure — the same category
as running PostgreSQL or Redis as their own containers — not a decomposition of
ORBIT's business domain. All domain modules (`objectives`, `career`, `skills`,
`projects`, …) remain a single deployable within `apps/api`. ADR-001 is
unaffected. `apps/auth` contains no ORBIT business logic and must never gain any.

## Alternatives Considered
1. **Custom JWT implementation with a shared HMAC secret** (the superseded ADR-004):
   Rejected. It made the application responsible for issuing, signing, rotating,
   and revoking its own tokens — the classic "roll your own auth" risk surface.
   Concrete problems: a single symmetric `JWT_SECRET` whose leak is total account
   takeover with no recovery but a global rotation (and the default was committed
   to `docker-compose.yml` and `.env.example`); no key rotation or asymmetric
   signing; revocation via a Redis blacklist that fails **open** if Redis is
   unavailable; and it silently replaced Blueprint §35's stateful cookie/session
   flow with stateless bearer tokens. It also contradicted Technical Architecture
   Specification §9 without saying so.
2. **External SaaS OIDC provider**: Deferred, not permanently rejected — see the
   table above. The architecture (standard discovery + JWKS on the resource
   server) keeps this a configuration change, not a rewrite.
3. **Keycloak / Ory Hydra**: Rejected for this milestone — see the table above.
4. **Session-based auth with server-side sessions in Redis**: Closest to a literal
   reading of Blueprint §35 ("Backend session"). Not chosen because the platform
   is API-first with a separate SPA and (later) native surfaces; OIDC with a
   resource-server API is the better-supported shape for that and is what Tech
   Arch §9 leads with ("OIDC/OAuth login").

## Consequences
- **Positive**:
  - No shared token-signing secret anywhere; signing key never leaves the auth
    server; compromise of `apps/api` does not yield the ability to mint tokens.
  - Standards-based: the API depends only on OIDC discovery + JWKS, so the
    provider can be replaced (self-hosted → SaaS, or vice versa) via config.
  - Login, consent, MFA, password policy, and lockout become the auth server's
    single responsibility instead of being scattered through the API.
  - Aligns with Technical Architecture Specification §9 and §28 and Engineering
    Blueprint §35; closes Blueprint §55's open decision.
  - Uses the Spring stack already in the project — no new language or toolchain.
- **Negative**:
  - A third backend service to build, run, deploy, monitor, and secure. Mitigated
    by sharing the `apps/api` build/Docker patterns, one CI job, and a health
    check; the container is small.
  - Local development now needs the auth server running for authenticated flows
    (unauthenticated endpoints and tests are unaffected — the test profile uses a
    placeholder JWKS URI that is never contacted).
  - The issuer URL must resolve identically for the browser and for `apps/api`.
    In `docker-compose.yml` both use `http://localhost:9000`; the API validates
    via the compose-network JWKS URL and does not perform discovery, avoiding the
    split-horizon DNS problem. A production deployment must give the auth server a
    single externally-resolvable issuer URL.
  - Full OIDC value (persistent keys/clients, audit, lockout, MFA) is not
    realized until Milestone 1; Milestone 0 delivers the wiring only.

## History
- **2026-09-05** — Originally accepted as *ADR-004: JWT-Based Authentication*
  (custom JWT + Spring Security, 15-min access token, 7-day refresh cookie,
  BCrypt-12, Redis token blacklist).
- **2026-09-06** — Reverted to OIDC (this document) after review found the
  original ADR (a) asserted a PRD/Blueprint "JWT" specification that is not in
  those documents, (b) overrode Technical Architecture Specification §9 without
  acknowledging it, and (c) made a security-scoping decision that `CLAUDE.md`
  requires be brought to the product owner directly. Approved by the product
  owner the same day. The file `ADR-004-jwt-authentication.md` was replaced by
  `ADR-004-oidc-authentication.md`.

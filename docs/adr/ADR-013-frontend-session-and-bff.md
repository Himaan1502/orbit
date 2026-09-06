# ADR-013: Frontend Session & Backend-for-Frontend (BFF)

## Status
Accepted

## Date
2026-09-07

## Context
Milestone 1 objective 4 requires wiring the real login/signup flow in
`apps/web`: the Authorization Code + PKCE redirect to `apps/auth`, the callback
route, "token/session handling per Blueprint §35's cookie-based model", and
logout.

Blueprint §35 specifies:

```
Browser → OIDC Provider → Callback → Backend session → Secure cookie → Authenticated API
```

with secure cookies, appropriate SameSite policy, CSRF protection where
applicable, and server-side authorization.

[ADR-004](ADR-004-oidc-authentication.md) established that `apps/api` is a
**stateless** OAuth2 resource server — it validates bearer tokens and holds no
session. ADR-004 explicitly considered and rejected "session-based auth with
server-side sessions in Redis" *for the API*. It said nothing about where the
browser-facing session in Blueprint §35 lives.

The M0 `apps/web` calls `apps/api` directly from browser JavaScript
(`lib/api.ts`, axios → `http://localhost:8080`). That cannot hold OIDC tokens
safely: a public SPA storing access/refresh tokens in JS-reachable storage is
the "token leakage" / "account takeover" item in the Blueprint §36 threat model.

## Decision

### `apps/web` is Blueprint §35's "Backend". It runs a BFF.
The Next.js server tier — not a new stateful layer in `apps/api` — is the
"Backend session" in the §35 diagram. This is consistent with ADR-004 (the API
stays stateless) and with the redirect URI ADR-004 already registered
(`http://localhost:3000/auth/callback` points at the web app, not the API).

### `orbit-web` is a **confidential** OAuth client (adjusts ADR-004 §1)
ADR-004 §1 described the frontend as "a public client … Authorization Code +
PKCE", written before the BFF was decided and while ADR-004 itself deferred
"the frontend callback route and token handling" to Milestone 1. Under the BFF
the browser is **not an OAuth client at all** — it holds only the session
cookie. The OAuth client is the Next.js **server**, which can keep a secret.

So `orbit-web` is registered with `client_secret_basic` + a `WEB_CLIENT_SECRET`
(server-side only, never shipped to the browser), **and** `requireProofKey`
stays on (PKCE as defence in depth). Reasons this is the right call, not just a
workaround:

1. **Refresh tokens.** Spring Authorization Server deliberately does **not**
   issue refresh tokens to a public client (`ClientAuthenticationMethod.NONE`).
   ADR-004 §1 also mandates "Refresh token TTL: 7 days, rotating". A confidential
   client satisfies both; a public client cannot.
2. **Least exposure.** A public client's refresh token would live in the BFF
   with no client authentication protecting the token endpoint. Confidential +
   rotating refresh tokens is the OAuth 2.1 BFF recommendation.
3. No downside for our topology — the secret has a safe home (the server
   process / its env), which is exactly what "confidential" requires.

`apps/api` is unaffected: it still only validates RS256 tokens against the JWKS
and never sees the client secret.

### 1. Session cookie
- One cookie, `orbit_session`, `HttpOnly`, `Secure` (except on
  `http://localhost` in dev), `SameSite=Lax`, `Path=/`.
- Contents: the OIDC `access_token`, `refresh_token`, `id_token`, access-token
  expiry, and the user's `sub` / `email`, serialised and **AES-256-GCM
  encrypted (JWE, `dir` + `A256GCM`)** with a key from `SESSION_SECRET`. The
  browser never sees token contents; JS cannot read the cookie.
- Implementation: the `jose` library (already a transitive dep via the OIDC
  needs; added explicitly). A thin `lib/session.ts` wraps seal/unseal and the
  `cookies()` API. No `iron-session` — one small module is enough and avoids a
  dependency whose App-Router story has churned.
- `SameSite=Lax` (not `Strict`) so the top-level redirect back from `apps/auth`
  arrives with the pre-callback cookie (PKCE verifier + state) attached.

### 2. Flow
| Route (web) | Behaviour |
|---|---|
| `GET /login` | Server route. Generates PKCE `code_verifier` + `state` + `nonce`, stores them in a short-lived `HttpOnly` `orbit_txn` cookie, 302 → `apps/auth` `/oauth2/authorize?response_type=code&client_id=orbit-web&code_challenge=…&scope=openid profile orbit.read orbit.write`. |
| `GET /signup` | Server route. 302 → `apps/auth` `/signup`. After registering, that page links to `/login`. |
| `GET /auth/callback` | Server route. Validates `state`, exchanges `code` + `code_verifier` at `apps/auth` `/oauth2/token`, validates the ID token (`iss`, `aud`, `nonce`, signature via JWKS), writes `orbit_session`, clears `orbit_txn`, 302 → `/` (or the saved `returnTo`). |
| `POST /logout` | Server route. Reads `id_token`, clears `orbit_session`, 302 → `apps/auth` `/connect/logout?id_token_hint=…&post_logout_redirect_uri=http://localhost:3000`. Refresh-token revocation at the auth server is best-effort. |
| `GET /auth/me` | Server route. Returns `{ authenticated, email }` from the session for client components. |

### 3. Calls to `apps/api` go through a same-origin proxy
- A catch-all route handler `app/api/orbit/[...path]/route.ts` on the web server
  forwards to `apps/api` (`/api/v1/...`), attaching
  `Authorization: Bearer <access_token>` from the session **server-side**.
- Before forwarding, if the access token is within 60s of expiry it refreshes
  using the refresh token and rewrites the cookie (rotating refresh token per
  ADR-004).
- `lib/api.ts` retargets from `http://localhost:8080/api/v1` to the relative
  `/api/orbit`. Browser code is unchanged otherwise. `withCredentials` stays so
  the session cookie rides along to our own origin.
- On a 401 from `apps/api` that a refresh cannot fix, the proxy clears the
  session and returns 401; the client redirects to `/login`.

### 4. CSRF
- `apps/api` remains CSRF-exempt: it only accepts `Authorization: Bearer` and
  no longer receives auth cookies from the browser (the proxy calls it
  server-to-server).
- The web BFF's state-changing routes (`/logout`, proxied non-GET) are
  protected by: `SameSite=Lax` on the session cookie + an
  `Origin`/`Sec-Fetch-Site` check in the proxy (reject cross-site). A rotating
  double-submit token is deferred — with `SameSite=Lax` and the origin check
  the residual CSRF surface for M1 is GET-based only, which the proxy makes
  side-effect-free.
- `apps/auth` form pages (`/login`, `/register`) use Spring Security's default
  synchroniser-token CSRF; the custom signup template renders the
  `_csrf` hidden field.

### 5. Route protection
`apps/web/middleware.ts` matches all app routes except `/login`, `/signup`,
`/auth/*`, static assets. Missing/invalid session → 302 `/login?returnTo=…`.
This is defence-in-depth for UX only; the real authorization is
`apps/api` rejecting un-bearer'd requests and enforcing ownership (ADR-012 §3,
Tech Arch §9 "independently of UI").

## Alternatives Considered
1. **SPA holds tokens (localStorage / memory)** — rejected; token leakage /
   XSS-to-takeover, contradicts Blueprint §35's "secure cookie" and §36 threat
   model.
2. **Stateful session in `apps/api` (Spring Session + Redis), browser cookie to
   the API** — rejected; ADR-004 already rejected this, it re-introduces the
   split-horizon issuer problem for the browser, and it makes the API
   non-stateless against Tech Arch §5.
3. **`@auth/nextjs` (NextAuth v5)** — capable, but brings its own provider
   abstraction, DB adapter surface, and opinions; a ~200-line hand-rolled OIDC
   client against our own single provider is smaller, fully legible, and has no
   version-coupling risk. Reconsider if we add social logins.
4. **Server-side session store for the BFF (Redis) instead of a stateful
   cookie** — deferred; the encrypted-cookie approach has no infra and is fine
   for one user. If cookie size (roughly 2–4 KB with three JWTs) becomes a
   problem, move token storage server-side keyed by an opaque cookie id.

## Consequences
- **Positive**
  - OIDC tokens never reach browser JavaScript. Matches Blueprint §35 literally:
    browser↔web is a secure `HttpOnly` cookie, web↔API is the authenticated
    (bearer) call.
  - `apps/api` stays stateless and portable (ADR-004 preserved).
  - Token refresh is invisible to the client and centralised in the proxy.
  - One integration point to change if the IdP is swapped later.
- **Negative**
  - Every API call is one extra hop (browser → Next → API). Negligible for a
    single-user tool; both processes are co-located in compose.
  - The web tier now has server-side responsibilities and secrets
    (`SESSION_SECRET`) — it is no longer a pure static export. `output:
    'standalone'` already assumes a Node server, so no deployment change.
  - Hand-rolled OIDC client code to own and test. Mitigated by keeping it to
    one module with unit tests for token/ID-token validation.
- **Follow-up:** [ADR-005](ADR-005-redis-usage-boundaries.md) lists "JWT token
  blacklisting (logout invalidation)" as a Redis use. That bullet is a artifact
  of the superseded JWT ADR-004 — under OIDC there is no API-side token
  blacklist; logout is the auth server's `/connect/logout` plus refresh-token
  revocation. ADR-005 should be amended to drop bullet 1. Tracked in
  `deferred-items.md`.

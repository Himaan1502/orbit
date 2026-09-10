# ADR-035: Single-VM Deployment on OCI Always Free (Milestone 8a)

## Status
**Proposed** — decision approved in principle by the product owner 2026-09-10;
sign-off items 2–5 answered (see §Open Questions). **Item 1 (tenancy type)
outstanding — the product owner is confirming it before Phase 0 begins.**
Nothing in this ADR has been provisioned or built. Moves to **Accepted** when
the milestone is verified.

## Date
2026-09-10

## Context

Milestones 0–7 plus the pre-M8 streaming/nudge pass are complete and on `main`
(`cb557d8`). ORBIT runs today only as a local 5-service `docker compose` stack
(`orbit-db`, `orbit-redis`, `orbit-auth`, `orbit-api`, `orbit-web`) on the
owner's machine. The goal of **Milestone 8a** is a real, always-on, phone-
reachable deployment **at zero monthly cost**, keeping the owner's existing
pilot account and all its data.

This is "our numbering". The Blueprint's **Milestone 10 — Production Platform**
(Terraform, Kubernetes, GitOps, security gates, alerting, runbooks) stays
**deferred** — there is no driver for it (single user, no zero-downtime or
horizontal-scale requirement). M8a is the pragmatic subset: one host, the
existing compose stack, TLS, and backups.

**Specs bearing on this decision.** Tech Arch §33 lists "Exact hosting provider
mix" as an *open decision* — this ADR closes it. Tech Arch §21: *"For the first
hosted version, keep infrastructure minimal … do not introduce a cluster merely
to satisfy architecture aesthetics."* Tech Arch §22 lists the hosted target as
*"managed PostgreSQL, managed Redis if needed"* — **this ADR deviates** (§7
below); flagged for explicit sign-off.

There is **no prior deployment ADR**. The Tech Arch ADR index names "ADR-009
Deployment strategy", but ADR-009 was actually spent on Flyway; deployment was
never written up. ADR-035 is the first.

### What was already decided in prior planning (carried in, not re-litigated)

- **Single VM** running the existing compose stack essentially as-is. A
  decomposition onto free PaaS platforms (Vercel + Render + Supabase) was
  considered and **rejected**: cold-starts stacking on top of OSCAR latency;
  4 new network hops where there are 0 today; preview-deploy auth breakage;
  the `AUTH_KEYSTORE_SECRET` sealing model becoming fragile across hosts;
  Vercel's 60s function cap conflicting with OSCAR SSE.
- **Caddy** as reverse proxy with automatic Let's Encrypt TLS.
- **No domain purchased** — `nip.io` wildcard DNS against the VM's public IP
  (`web.<ip>.nip.io`, `api.<ip>.nip.io`, `auth.<ip>.nip.io`).
- Images **built in CI → GHCR → VM does `docker compose pull && up -d`**. The
  VM carries **no Java/Node build toolchain**.
- A **`docker-compose.prod.yml` overlay**: no host port exposure for
  Postgres/Redis, `restart: unless-stopped`, JVM heap caps, env from
  `/opt/orbit/.env` (chmod 600).
- Firewall: only 22, 80, 443 — enforced at **both** the OS layer and the OCI
  Security List / NSG layer.

### Investigation findings (this session, real output)

| Finding | Evidence |
|---|---|
| OCI CLI works, authenticated, `region = ap-mumbai-1` | `oci --version` → 3.92.1; `oci iam region list` → full list; `oci iam availability-domain list` → `LJCm:AP-MUMBAI-1-AD-1` (Mumbai has **one** AD) |
| **Ampere A1 capacity is available in ap-mumbai-1 right now** | `oci limits resource-availability get --limit-name standard-a1-core-count --availability-domain LJCm:AP-MUMBAI-1-AD-1` → `available: 41, used: 0`. No regional capacity block; **no need for a Hyderabad fallback**. (OCI has no `ap-delhi-1` region — India is Mumbai + Hyderabad only.) |
| The tenancy has **Pay-As-You-Go-shaped service limits**, not Always-Free-locked ones | `standard-a1-core-count` limit = **41** (a locked Always Free account shows 4); block-storage `total-storage-gb` = 30720; `standard-e4/e5-core-count` = 16/13 (locked free = 0). **There is no hard guardrail stopping a billable resource from being created** — staying within the Always Free *allowances* is a discipline we must enforce, not something the account enforces for us. Flagged in §Open Questions. |
| **Oracle halved the Always Free Ampere A1 allowance to 2 OCPU / 12 GB** (was 4/24) effective 2026-06-15 | Oracle docs (`FreeTier/resourceref.htm`): A1 = 1,500 OCPU-hrs/mo (≈ 2 OCPU always-on) + 9,000 GB-hrs/mo (≈ 12 GB); news coverage (heise, InfoQ) confirms the June 2026 cut. **The owner's brief assumed 4/24 — corrected here.** 2/12 is still ample for ORBIT (measured stack RSS ≈ 3 GB, see §4). |
| Always Free storage / IP / LB, from Oracle docs | Block Volume: **200 GB** total (boot + block), **5** volume backups. Object Storage: **20 GB**, 50k requests/mo. Outbound transfer: 10 TB/mo. 1 flexible LB free. **Reserved public IP is not on the Always-Free list** but OCI bills reserved public IPs **only while unattached** — attached to our always-on VM it is $0 (confirm in cost tracking after allocation). |
| The two Always Free **AMD** shapes are `E2.1.Micro` only — 1/8 OCPU, **1 GB RAM** each, max 2 | Oracle docs. **Not viable for ORBIT** regardless of the ARM question (2 JVMs + Postgres + Node ≫ 1 GB, and ≫ 2 GB across both). The AMD path is off the table on RAM grounds. See §2. |
| Base images are all multi-arch (arm64 published) | `postgres:16-alpine`, `redis:7-alpine`, `eclipse-temurin:21-jdk-alpine` / `:21-jre-alpine`, `node:20-alpine` — all official multi-arch. Our Dockerfiles add no arch-specific step. |
| **CI builds no images today** | `.github/workflows/ci.yml` runs lint/test/build only. There is **no** image push to any registry. `docker compose build` is local-only. M8a must **add** an image build+push job — and it must produce **arm64** images (§3). |
| **GHCR private-package storage on the GitHub Free plan is 500 MB / 1 GB transfer per month** | GitHub Packages billing docs / community threads. The repo is **private** (`gh repo view` → `PRIVATE`). Three images must fit in 500 MB — drives the single-arch + prune decision in §3, with public-package fallback. |
| No free native arm64 CI runners for private repos | GitHub's free `ubuntu-24.04-arm` hosted runners are **public-repo only**; private repos need a paid larger-runner plan. arm64 image builds on the Free plan must use QEMU on the amd64 runner, or the split-build trick in §3. |
| `RegisteredClientSeeder` **upserts** the `orbit-web` client from env on every startup | `apps/auth/.../config/RegisteredClientSeeder.java` — `clients.save(webClient)` reuses the existing row id and rewrites redirect URI + secret from `WEB_REDIRECT_URI` / `WEB_CLIENT_SECRET`. **The redirect-URI / client-secret change on migration is automatic — no manual SQL.** |
| `apps/web` is already fully parameterised for split-horizon HTTPS | `src/lib/auth/config.ts` — `OIDC_ISSUER_URI` (browser), `OIDC_INTERNAL_ISSUER_URI` (container↔container), `WEB_SECURE_COOKIES` override, `WEB_PUBLIC_URL`. |
| `apps/api` prod profile forces OIDC **discovery** | `application-prod.yml` → `issuer-uri: ${OIDC_ISSUER_URI}` (not `jwk-set-uri`). Needs the public auth hostname resolvable from inside the api container — solved with a compose `extra_hosts` pin to the Caddy container (§5), a wiring detail not an architecture choice. |
| `apps/auth` has **no** `application-prod.yml` | Only `application.yml` + `application-dev.yml`. It already reads every needed knob from env (per `docker-compose.yml`). A tiny `application-prod.yml` (hide health details, quieter logging) is a small build task. |
| `nip.io` is **not** on the Public Suffix List | Long-open PSL request issue. Good: `<ip>.nip.io` is the eTLD+1, so `web.` / `api.` / `auth.` are **same-site** → `SameSite=Lax` on `orbit_session` survives the OAuth redirect. Bad: all `*.nip.io` share one Let's Encrypt rate-limit bucket — issuance can transiently fail (§Open Questions, with fallbacks). |
| Docker Desktop is currently **unresponsive** on the owner's machine | `docker version` / `docker ps` hang → killed at timeout. Known recurring issue ([[orbit-docker-desktop-flaky]]). It must be healthy before the backup/migration phase (which needs `pg_dump` from the running `orbit-db`). Not a blocker for this ADR. |

## Decision

### 1. One Always Free **Ampere A1** VM in **ap-mumbai-1**, `VM.Standard.A1.Flex` at **2 OCPU / 12 GB**

- Region **ap-mumbai-1**, AD **`LJCm:AP-MUMBAI-1-AD-1`** (the only one). Capacity
  confirmed available this session.
- Shape `VM.Standard.A1.Flex`, **2 OCPU / 12 GB** — exactly the current Always
  Free A1 allowance. A 2-OCPU instance run 24×7 for a 31-day month = 1,488
  OCPU-hours, just under the 1,500 cap; 12 GB × 744 h = 8,928 GB-hrs, under
  9,000. **Margin is ~1%** — a second A1 instance alongside this one is not
  possible without incurring charges. Accepted: single user, single box.
- OS image: **Canonical Ubuntu 24.04 (aarch64)** — well-trodden for Docker,
  matches the CI toolchain mental model.
- **Boot volume 120 GB** (of the 200 GB block allowance) — headroom for Docker
  images, DB growth, local dumps. No separate block volume in M8a.
- **Reserved public IP** (not ephemeral) — every `nip.io` hostname, TLS cert,
  and OAuth redirect URI is bound to the IP; it must survive a stop/start.
  Free while attached.

### 2. Ampere A1 (arm64), **not** AMD — and why the arch change is unavoidable

The only Always Free AMD shape, `VM.Standard.E2.1.Micro`, is **1 GB RAM**. Two
of them is 2 GB total. ORBIT's stack (two Spring Boot JVMs + Postgres + a
Next.js node server + Caddy) needs ≈ 3 GB resident and does not shard across
two tiny hosts without inventing cross-host networking we explicitly don't
want. **AMD Always Free cannot run ORBIT.** The choice is therefore Ampere A1
or nothing free — which means arm64 images. §3 makes that cheap rather than
painful.

Trade-off acknowledged: arm64 adds a one-time CI change and a permanent
"images must be multi-arch-or-arm64" constraint. Weighed against "no free
option exists otherwise", it is clearly worth it.

### 3. CI change — build and publish **arm64** images to GHCR

New CI job (`build-images`, `on: push` to `main`), after the existing
lint/test/build jobs pass:

1. **Compile on the native amd64 runner.** Java bytecode is arch-independent —
   `./gradlew bootJar` runs at full speed on `ubuntu-latest`, no emulation.
   Likewise `next build` (its output is JS + static assets; the standalone
   server is portable).
2. **Package a thin arm64 final stage.** `docker buildx build --platform
   linux/arm64` with QEMU, where the arm64 final stage is only
   `FROM …-jre-alpine` + `COPY app.jar` (for the JVM apps) / `COPY
   .next/standalone` (for web). No compilation under emulation → the arm64
   build is seconds, not tens of minutes. Achieved by splitting the
   Dockerfiles into a builder stage (runs on the build platform,
   `--platform=$BUILDPLATFORM`) and a runtime stage (target platform).
3. **Publish `linux/arm64` only.** The sole consumer is the ARM VM. Local dev
   keeps using `docker compose build` (native to the dev machine, never
   pulls). Single-arch halves registry storage and push time.
4. **Tags:** `:latest` and `:<short-sha>`. A prune step
   (`actions/delete-package-versions`, keep the last 3 per package) keeps the
   three private packages inside the **500 MB** GHCR Free allowance.
   Estimated footprint ≈ 250–300 MB (shared `jre-alpine` base counted once).
   **Fallback if 500 MB proves too tight:** set the three *packages* (not the
   repo) to **public** — GHCR public storage is unlimited and free. The images
   contain compiled app code but **no secrets** (all injected at runtime from
   `/opt/orbit/.env`). Recommendation: start private + prune; go public only
   if forced. Product-owner call.
5. Auth to GHCR uses the built-in `GITHUB_TOKEN` with `packages: write` — **no
   new secret**.

### 4. The VM runs the existing compose stack + a `docker-compose.prod.yml` overlay + Caddy

Six containers on one user-defined bridge network:

| Container | Image source | Notes |
|---|---|---|
| `orbit-db` | `postgres:16-alpine` (pulled) | named volume `postgres_data`; **no host port**; `shared_buffers`/`work_mem` tuned small |
| `orbit-redis` | `redis:7-alpine` (pulled) | `--requirepass`, `--maxmemory 128mb --maxmemory-policy allkeys-lru`; **no host port** |
| `orbit-auth` | `ghcr.io/…/orbit-auth:latest` | `-Xmx384m`; env from `/opt/orbit/.env` |
| `orbit-api` | `ghcr.io/…/orbit-api:latest` | `-Xmx1g`; the heavy one (OSCAR, analytics) |
| `orbit-web` | `ghcr.io/…/orbit-web:latest` | node standalone; `NODE_OPTIONS=--max-old-space-size=384` |
| `orbit-caddy` | `caddy:2-alpine` (pulled) | ports 80/443; ACME HTTP-01; host-based routing to the three services; `caddy_data` volume persists certs |

Heap caps sum to ~1.8 GB max heap; with non-heap + Postgres + Redis + node +
Caddy the ceiling is ~3–4 GB against 12 GB available. Comfortable.

`restart: unless-stopped` on all six. Compose starts on boot via a
`systemd` unit (`orbit.service` → `docker compose … up -d`).

### 5. Networking, TLS, and the split-horizon issuer

- **Browser → Caddy (443) → service**, by `Host`:
  `web.<ip>.nip.io` → `orbit-web:3000`, `api.<ip>.nip.io` → `orbit-api:8080`,
  `auth.<ip>.nip.io` → `orbit-auth:9000`. Caddy obtains and renews Let's
  Encrypt certs automatically (HTTP-01 on port 80).
- **Container → container** stays on the compose network by service name
  (`orbit-api` → `http://orbit-auth:9000/oauth2/jwks`, `orbit-web` →
  `http://orbit-api:8080`). No internal traffic traverses Caddy or the public
  IP.
- **Issuer.** `OIDC_ISSUER_URI = https://auth.<ip>.nip.io` everywhere (the
  `iss` claim the browser and the API both see). Because `apps/api`'s prod
  profile does OIDC *discovery* against that URL, the api container gets an
  `extra_hosts: ["auth.<ip>.nip.io:<caddy-net-ip>"]` pin so it resolves the
  public hostname to Caddy on the internal network (Caddy already serves the
  valid cert for that name). Alternative wiring (internal `jwk-set-uri` + a
  standalone issuer-claim validator) is the fallback if the pin is awkward.
- **Cookies.** `WEB_SECURE_COOKIES=true` (real HTTPS). `SameSite=Lax` is
  retained and works — `nip.io` is not a public suffix, so the three
  subdomains are same-site (verified §Findings).
- A **real domain later** is a **Caddy config change only** (swap the three
  site addresses, update `OIDC_ISSUER_URI` / `WEB_REDIRECT_URI` /
  `WEB_PUBLIC_URL` / `CORS_ORIGINS` in `.env`, `RegisteredClientSeeder`
  re-syncs the redirect URI on restart). **Not** an architecture change. No
  code change.

### 6. Firewall — enforced at both layers

- **OCI layer:** the VCN Security List (or an NSG on the VNIC) allows ingress
  only on **22, 80, 443** from `0.0.0.0/0`; all else denied. Egress open.
- **OS layer:** `ufw` (or nftables) on the VM — same three ports — as
  defence in depth and in case the VM is ever moved to a subnet with a looser
  Security List. Neither layer is assumed to cover the other.
- Postgres (5432) and Redis (6379) are reachable **only** on the Docker
  bridge — no host publish, both firewall layers would drop them anyway.

### 7. **Deviation from Tech Arch §22** — self-hosted Postgres/Redis in containers, not managed services

Tech Arch §22 names "managed PostgreSQL, managed Redis if needed" for the
hosted target. M8a runs both as containers on the VM instead, because:

- **Cost.** No managed Postgres exists in the OCI Always Free tier (Autonomous
  DB free is Oracle DB, not Postgres; the compose stack expects Postgres 16 +
  Flyway). A managed Postgres anywhere else reintroduces a network hop, a
  second bill risk, and cross-provider credential handling — the same reasons
  the PaaS split was rejected.
- **§21 outranks §22 here.** "Keep infrastructure minimal" for the first
  hosted version. One VM with Docker volumes is the minimal thing.
- **Reversible.** `ADR-002` keeps Postgres as the system of record with no
  vendor lock-in; moving to a managed instance later is a connection-string
  change plus a dump/restore. `ADR-005` already bounds Redis to a cache/
  ephemeral role — losing the Redis container costs a nudge cache, nothing
  durable.

**This deviation needs explicit sign-off.** It does not override an ADR (no
deployment ADR exists); it consciously departs from a spec section, which
CLAUDE.md says to flag rather than resolve silently.

### 8. Data migration — keep the existing account and history

Full detail is in `docs/engineering/M8a-deployment-scope-and-runbook.md`. The
load-bearing rules:

- **`pg_dump` the entire local DB** (`-Fc`, no schema filter) — captures
  `orbit` **and** `auth` schemas, extensions, and both Flyway history tables.
- **`AUTH_KEYSTORE_SECRET` is copied byte-for-byte** from the local `.env` to
  the VM `.env`. It is the PBKDF2 input that AES-256-GCM-unseals the RSA
  private key in `auth.signing_keys` (ADR-011 §3). Regenerating it = auth
  starts but cannot sign tokens. **This is the one secret that must be copied,
  not generated** — called out in bold in the runbook.
- **All other secrets are freshly generated** strong values for the VM:
  `POSTGRES_PASSWORD`, `REDIS_PASSWORD`, `WEB_CLIENT_SECRET`, `SESSION_SECRET`.
  `RegisteredClientSeeder` propagates the new `WEB_CLIENT_SECRET` to the DB
  row on first auth boot; the new `SESSION_SECRET` just invalidates any old
  local session cookie (a re-login, which the owner does once anyway).
- **Restore before first app boot:** bring up `orbit-db` alone → `pg_restore`
  → then bring up `orbit-auth` / `orbit-api` / `orbit-web` so Flyway sees a
  fully-migrated schema and no-ops.
- Old `oauth2_authorization` refresh-token rows were minted with
  `iss=http://localhost:9000`; after the issuer changes they may fail refresh
  → one re-login. The `auth.users` BCrypt row (the actual password) is
  untouched, so the existing password keeps working.

### 9. Backups

- **Before anything:** timestamped local `pg_dump`, **restore-tested against a
  throwaway container** (not just "file exists"). Milestone-process rule.
- **After migration verifies:** one **OCI boot-volume backup** via
  `oci bv boot-volume-backup create` — within the Always Free "5 backups"
  allowance, expected $0 (confirm in cost tracking; do not proceed if it
  shows a charge). A rollback point on the infra side.
- **Ongoing:** a nightly `pg_dump` on the VM (`systemd` timer) pushed
  **off the VM to OCI Object Storage** (same tenancy, 20 GB free, VM authorised
  via an **instance principal** + a dynamic-group policy — **no credentials on
  the VM**). 14-day retention via a bucket lifecycle rule. Backblaze B2 (10 GB
  free) is the documented alternative if a truly independent provider is
  wanted; OCI Object Storage is the default for zero extra accounts/secrets.

### 10. Deploy-on-push (optional, phase 2)

If enabled: a final CI step `ssh orbit@<host> 'cd /opt/orbit && docker compose
… pull && up -d'`. The **only** GitHub secrets are `DEPLOY_SSH_KEY` (a
dedicated ed25519 deploy key, its public half in the VM's `authorized_keys`
with a forced command) and `DEPLOY_HOST`. **No** production `.env` values,
**no** OCI credentials in CI, ever. OCI API keys stay on the owner's machine
for CLI provisioning only. Until phase 2, deploys are a one-line manual SSH.

## Alternatives Considered

1. **Free PaaS split (Vercel + Render + Supabase).** Rejected in prior
   planning — see Context. Not reopened.
2. **Full Blueprint M10 (Terraform + OKE + GitOps) now.** No driver; violates
   Tech Arch §21. Stays deferred. M8a does not preclude it — the compose files
   and Dockerfiles are the same inputs a later Helm chart would wrap.
3. **Two AMD `E2.1.Micro` VMs** (avoids arm64). Rejected: 1 GB RAM each, ≈ 3 GB
   needed; no clean way to split the stack. §2.
4. **4 OCPU / 24 GB A1.** No longer an Always Free allowance (halved 2026-06-15)
   — the extra 2 OCPU would be **billed** on this PAYG-shaped tenancy. §1.
5. **`ap-hyderabad-1` primary.** Only needed as a capacity fallback; Mumbai has
   capacity now and is lower-latency for the owner. Kept as the documented
   fallback if a re-provision ever hits "out of host capacity".
6. **OCI Flexible Load Balancer for TLS/routing** instead of Caddy. It is free
   (1 allowed), but adds a hop and a moving part, does host-based routing less
   flexibly than Caddy, and still needs something terminating ACME. Caddy in a
   container is simpler and portable off OCI.
7. **Multi-arch (amd64 + arm64) images.** No consumer for amd64 (local dev
   builds locally). Doubles GHCR storage against the 500 MB cap. arm64-only.
8. **QEMU-emulated full arm64 build in CI** (no split-build trick). Works but
   `./gradlew bootJar` under emulation is 10–25 min per JVM app — risks the
   2,000-min/month Free Actions budget on a daily cadence. The split build
   (§3) avoids it.
9. **Managed Postgres/Redis** (Tech Arch §22 literal reading). §7.
10. **Ephemeral public IP.** Lost on stop/terminate, taking every `nip.io`
    hostname and cert with it. Reserved IP for ~$0. §1.

## Consequences

**Easier**
- ORBIT becomes always-on and phone-reachable with real TLS at $0/month.
- The deployment artifact is the same `docker compose` the owner already runs
  — minimal new concepts, trivial to reason about, portable off OCI.
- A real domain later is a config-only change (§5).
- CI gains a genuine container-publish pipeline (a Blueprint M10 prerequisite
  done early).

**Harder / new obligations**
- Dockerfiles must be restructured for split-platform builds; CI grows an
  image job and a prune step. One-time.
- A permanent "arm64 image must exist" constraint on every future image.
- The 12 GB / 1,500-OCPU-hr ceiling is real — no room for a second A1 VM, and
  heavy new services (a vector DB, a metrics stack) need memory budgeting.
- Operational surface the owner now owns personally: cert renewal (Caddy
  automates it, but nip.io rate limits can bite), OS patching, backup
  monitoring, the single-VM = single-point-of-failure reality.
- `nip.io` in the OAuth issuer and redirect URIs — a dependency on a free
  third-party DNS service in the auth-critical path until a real domain is
  bought.

## Open Questions for Sign-Off

1. **The tenancy is not Always-Free-locked** (PAYG-shaped limits). There is no
   account-level block on creating billable resources — M8a's discipline is to
   provision exactly the 2 OCPU / 12 GB A1 + 120 GB boot + reserved IP + one
   bucket, all within Always Free, and nothing else. Is the tenancy an upgraded
   PAYG account, or a 30-day trial with credits? (Changes what happens at day
   30.) — **OUTSTANDING: product owner confirming via Billing & Cost Management
   before Phase 0. This is the one item to double-confirm given no enforced
   cap.**
2. **Tech Arch §22 deviation** — self-hosted Postgres/Redis containers instead
   of managed services (§7). — **APPROVED 2026-09-10**: §21 minimal-
   infrastructure wins over §22's "managed" preference for a single-user
   deployment.
3. **GHCR private vs public packages** (§3.4). — **DECIDED 2026-09-10**: keep
   packages **private**, prune to the last 3 versions. Go public only if 500 MB
   is later shown unworkable, and only with a fresh decision.
4. **`nip.io` risk tolerance**. — **DECIDED 2026-09-10**: `nip.io` for now. The
   implementation report must *demonstrate* (not assume) that moving to a real
   domain later is config-only.
5. **Deploy-on-push** (§10). — **DECIDED 2026-09-10**: **not** in M8a. Manual
   one-line SSH deploys only. Revisit after several manual deploys. No
   `DEPLOY_SSH_KEY` / `DEPLOY_HOST` GitHub secrets are created in M8a.

## Follow-ups (not M8a)

- Real domain → Caddy + `.env` change.
- Blueprint M10 proper (IaC, observability stack, alerting) when there is a
  driver.
- `ShedLock` for the scheduled jobs is already M10-tagged (ADR-029) — single
  instance keeps the current single-node assumption valid.

## Related

[[orbit-milestone-process]] · [[orbit-compose-no-volume-wipe]] ·
[[orbit-docker-desktop-flaky]] · ADR-002 (Postgres SoR) · ADR-004 (OIDC) ·
ADR-005 (Redis boundaries) · ADR-011 (auth persistence / keystore secret) ·
ADR-013 (frontend session & BFF) · ADR-029 (job mechanism / ShedLock deferral) ·
ADR-033 (OSCAR SSE — the 120s async timeout Caddy must not buffer)

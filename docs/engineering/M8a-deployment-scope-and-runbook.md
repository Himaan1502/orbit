# Milestone 8a — Deployment Scope & Runbook

**Status:** In progress. Paired with
[ADR-035](../adr/ADR-035-single-vm-oci-deployment-topology.md). Phase 0 done +
verified; Phase 1 satisfied; **Phase 2 Resource Group A provisioned 2026-09-10**
(networking only — all $0). Phases B/C/D pending per-resource approval.

**Goal:** ORBIT always-on at `https://web.<vm-ip>.nip.io`, reachable from any
device, real TLS, ~~existing pilot account + all history intact~~ (lost
2026-09-10, see [incidents.md](incidents.md) — fresh account), $0/month.

---

## Provisioned OCI resources (ap-mumbai-1, compartment `orbit`)

| Resource | OCID | Created | Cost |
|---|---|---|---|
| Compartment `orbit` | `ocid1.compartment.oc1..aaaaaaaawybixrcjnbtrvhxgbzf5s3nasvo232evsp4rcdm2fu6hojvp4yiq` | 2026-09-10 | $0 |
| VCN `orbit-vcn` (10.0.0.0/16) | `ocid1.vcn.oc1.ap-mumbai-1.amaaaaaaicfyggyahbdsmxyv5d3kyegazobnscyejgatxd6ezh5bhzbdajiq` | 2026-09-10 | $0 |
| Internet GW `orbit-igw` | `ocid1.internetgateway.oc1.ap-mumbai-1.aaaaaaaaotlgzwtodobkr4tzioogduistz3we4iatvmtan7r2ztx4ugsvfaa` | 2026-09-10 | $0 |
| Default route table (0.0.0.0/0 → IGW) | `ocid1.routetable.oc1.ap-mumbai-1.aaaaaaaauepcmhqanmytxjuqeodn3otjdq63h5p3wwtdxcfpsjlgl2wxyjmq` | 2026-09-10 | $0 |
| Security list `orbit-sl` (ingress 22/80/443) | `ocid1.securitylist.oc1.ap-mumbai-1.aaaaaaaadimo3f27uhbqifj4rsrltsek5sz32i6346hkgiuhc2fobn6chjha` | 2026-09-10 | $0 |
| Public subnet `orbit-subnet` (10.0.0.0/24, regional) | `ocid1.subnet.oc1.ap-mumbai-1.aaaaaaaabinpuds75lxpttc2eqif6g7f5ghshxb33gfq6ef4vquanenry2mq` | 2026-09-10 | $0 |
| AD | `LJCm:AP-MUMBAI-1-AD-1` | — | — |
| Reserved public IP (Group B) | _pending_ | | |
| Compute `orbit-vm` (Group C) | _pending_ | | |
| Object Storage bucket + dynamic group + policy (Group D) | _pending_ | | |

Tenancy `orbit-platform` (`OCID …aaaaaaaa27ytjp7jyreoaslgtv4blrjwoxf3sxdjnmkcxt5rvioc7ijyv77a`,
home region BOM). **Not** a locked Always Free tenancy — paid shapes are
available (`standard-e4-core-count` availability = 16), so there is no
account-level spend guard; the discipline is entirely in provisioning only the
Always-Free-native resources above. Every Phase-2 resource survives a
trial→PAYG/Always-Free transition (none depends on trial credit). Exact
trial-vs-PAYG label: to be filled from the console.

**Teardown (total abandon):** `oci iam compartment delete --compartment-id
<orbit-compartment>` removes everything above in one call once the compute /
bucket are gone; local ORBIT is unaffected.

---

## 1. What you're signing off

- ADR-035's decision: one Always Free **Ampere A1** VM (**2 OCPU / 12 GB** —
  note the halved allowance), **ap-mumbai-1**, arm64 images from GHCR, Caddy +
  Let's Encrypt, `nip.io` DNS, self-hosted Postgres/Redis containers, OCI
  Object Storage nightly backups.
- The five **Open Questions for Sign-Off** in ADR-035 (tenancy type, §22
  deviation, GHCR private/public, nip.io tolerance, deploy-on-push now/later).
- This runbook's step list and the division of labour in §3.

Once you approve, work proceeds **phase by phase**, each ending with real
verification output before the next starts (milestone-process rule).

---

## 2. Prerequisites / blockers to clear first

| Blocker | Owner | Note |
|---|---|---|
| **Docker Desktop is unresponsive right now** | you | `docker ps` hangs. Phase 3 (local backup) needs a healthy `orbit-db`. Restart Docker Desktop; if it wedges again, that's the [[orbit-docker-desktop-flaky]] pattern — stop and tell me, don't force it. |
| Confirm tenancy type (trial-with-credits vs PAYG) | you | Console → top-right → *your tenancy* → look for "Upgrade" (⇒ still trial) vs "Pay As You Go". Changes day-30 behaviour. |
| Answer ADR-035 Open Questions 2–5 | you | |
| GitHub plan is Free (confirmed) — GHCR 500 MB private cap applies | — | Already checked. |

---

## 3. Division of labour

### I can do directly via the OCI CLI (read + create), once you approve

- Create/verify a compartment for ORBIT (or use root).
- Create the VCN, public subnet, internet gateway, route table, **Security
  List** (ingress 22/80/443 only).
- Allocate a **reserved public IP**.
- Launch the `VM.Standard.A1.Flex` 2/12 instance (Ubuntu 24.04 aarch64, 120 GB
  boot) with a **cloud-init** that installs Docker, creates `/opt/orbit`,
  hardens SSH, sets up `ufw`.
- Attach the reserved public IP.
- Create the Object Storage **bucket** for backups + a **dynamic group** and
  **IAM policy** so the instance can write to it via instance principal.
- Create the **boot-volume backup** after migration (checking cost first).
- All read-only verification (`oci compute instance get`, limits, etc.).

### I can do over SSH to the VM, once it's up (you provide/approve the key)

- Install/verify the compose stack, write `docker-compose.prod.yml`, the
  `Caddyfile`, the `orbit.service` systemd unit, the backup timer.
- `docker compose pull && up -d`, restore the DB dump, drive verification
  (`curl` health checks, a login round-trip, row counts).

### You must do (CLI can't, or policy says you)

| Task | Why you |
|---|---|
| **Restart Docker Desktop** and confirm `docker ps` works | local machine |
| Run the **local `pg_dump`** (or approve me running it) — it touches your real data | milestone rule: I don't touch local data without approval; you may prefer to run it yourself |
| Provide an **SSH keypair** for the VM (or approve me generating one locally and adding the public key to the launch config) | key custody |
| ~~Add 2 GitHub secrets for deploy-on-push~~ | **not in M8a** — manual SSH deploys only (decided 2026-09-10) |
| **Copy `AUTH_KEYSTORE_SECRET`** from local `.env` into the VM `.env` | I cannot read your `.env` (access denied, correctly). You paste this one value into the VM `/opt/orbit/.env` over SSH, or into a step I run. |
| Confirm **`nip.io` resolves** for the VM IP (`nslookup web.<ip>.nip.io`) and that Let's Encrypt issued (watch Caddy logs with me) | quick manual check |
| Eyeball the **OCI cost tracking** page after provisioning shows **$0** | console |
| Final **browser** verification from your phone | it's your device/session |

### Never happens

- OCI API keys / tenancy OCIDs into CI or the repo — they stay on your machine
  for CLI use only.
- Production `.env` values into CI — the only CI secrets are the SSH key + host.
- `docker compose down -v` on the local stack — [[orbit-compose-no-volume-wipe]].

---

## 4. Phases

### Phase 0 — Code prep (local branch, no infra, reviewable PR)

1. `docker-compose.prod.yml` overlay — image refs to GHCR, drop `ports:` for
   db/redis, `restart: unless-stopped`, heap caps, `env_file: /opt/orbit/.env`,
   add `orbit-caddy`.
2. `deploy/Caddyfile` — three site blocks, `reverse_proxy` to each service
   (Caddy does **not** buffer `text/event-stream` — OSCAR SSE works as-is;
   confirm during verification).
3. `deploy/orbit.service` + `deploy/orbit-backup.{service,timer}` +
   `deploy/backup.sh` (pg_dump → `oci os object put` via instance principal).
4. `deploy/cloud-init.yaml`.
5. Dockerfile restructure — builder stage `--platform=$BUILDPLATFORM`, thin
   arm64 runtime stage (ADR-035 §3).
6. `.github/workflows/ci.yml` — add `build-images` job (arm64, GHCR,
   `:latest` + `:sha`, prune-to-3). **No deploy step** — deploys are manual SSH
   in M8a (owner's decision 2026-09-10).
7. `apps/auth/.../application-prod.yml` (small).
8. `docs/engineering/deferred-items.md` + `CLAUDE.md` status table updated when
   the milestone closes.

**Verify:**

- ✅ **2026-09-10** — `docker compose -f docker-compose.yml -f
  docker-compose.prod.yml config` merges clean: `ports:` closed for
  db/redis/auth/api/web (only Caddy publishes 80/443), GHCR image refs, no
  `build:`, `SPRING_PROFILES_ACTIVE=prod`, `WEB_SECURE_COOKIES=true`, heap
  caps, and the `postgres_data` volume identity unchanged
  (`name: orbit_postgres_data`).
- ✅ **2026-09-10** — `docker buildx build --platform linux/arm64` builds all
  three images; result arch confirmed `linux/arm64`. A `--no-cache` cold build
  of `orbit-auth` ran `./gradlew bootJar` in **28s** (native amd64 build stage,
  not emulated arm64 — the `--platform=$BUILDPLATFORM` split works).
- ⏳ CI green on the branch — pending push.
- Merge to `main` → CI publishes the first arm64 images to GHCR.

### Phase 1 — Local backup (BEFORE any migration or infra touching data)

> **SATISFIED 2026-09-10.** The original local pilot DB was lost to a Docker
> Desktop data-disk recreation ([`incidents.md`](incidents.md)) before this
> phase ran. After a fresh signup, `scripts/restore-test-local.sh` produced and
> **restore-verified** `backups/orbit-local-20260910T173702Z.dump` — dump valid
> (`pg_restore --list` OK), restored into a throwaway container, row-count
> parity **PASS** (`auth.users` 1, `auth.signing_keys` 1, all domain tables 0 —
> a clean fresh account). That dump is the migration source + rollback baseline
> for Phase 3. Nothing further to do here.
>
> Going forward, `scripts/backup-local.sh` is the standing one-command backup
> (documented in the root README); re-run `restore-test-local.sh` before any
> migration test.

Original intent (kept for reference): `pg_dump -Fc` the whole `orbit_db`;
confirm non-empty; restore into a throwaway `postgres:16-alpine` and assert
row-count parity for the load-bearing tables; keep the dump as the rollback
baseline.

### Phase 2 — Provision the VM (CLI, billable-resource step — needs your "go")

1. Compartment / VCN / subnet / IGW / route table / Security List (22/80/443).
2. Reserved public IP.
3. Launch `VM.Standard.A1.Flex` 2/12, Ubuntu 24.04 aarch64, 120 GB boot,
   cloud-init, SSH key.
4. Attach reserved IP.
5. Object Storage bucket + dynamic group + policy.

**Verify:** `oci compute instance get` → `RUNNING`; SSH in; `docker --version`
from cloud-init; `ufw status` = 22/80/443; **you** confirm OCI cost tracking
shows $0 and the A1 shows as Always Free.

### Phase 3 — Configure the stack on the VM

1. `/opt/orbit/` — `docker-compose*.yml`, `Caddyfile`, `.env` (chmod 600).
2. `.env`: fresh strong `POSTGRES_PASSWORD` / `REDIS_PASSWORD` /
   `WEB_CLIENT_SECRET` / `SESSION_SECRET`; **`AUTH_KEYSTORE_SECRET` pasted
   byte-for-byte from local** (you); `OIDC_ISSUER_URI=https://auth.<ip>.nip.io`,
   `WEB_REDIRECT_URI=https://web.<ip>.nip.io/auth/callback`,
   `WEB_PUBLIC_URL=https://web.<ip>.nip.io`,
   `CORS_ORIGINS=https://web.<ip>.nip.io`, `WEB_SECURE_COOKIES=true`,
   `SPRING_PROFILES_ACTIVE=prod`, `GEMINI_API_KEY` / `GROQ_API_KEY` (you paste),
   `extra_hosts` pin for the api container.
3. `docker compose … pull` (GHCR arm64 images).
4. `docker compose … up -d orbit-db` **only**.
5. `pg_restore` the Phase-1 dump into `orbit-db`.
6. `docker compose … up -d` the rest (auth → api → web → caddy).
7. `systemctl enable --now orbit.service` + the backup timer.

**Verify:** `docker compose ps` → all 6 `Up (healthy)`; Flyway logs show
"no migration needed" (schema already at V10); auth logs show the signing key
**decrypted** (not "unable to decrypt") and `Registered client 'orbit-web'
updated`.

### Phase 4 — TLS + DNS

1. `nslookup web.<ip>.nip.io` → the VM IP (you or me).
2. Watch Caddy issue 3 Let's Encrypt certs (logs).
3. `curl -I https://web.<ip>.nip.io/api/health`,
   `https://api.<ip>.nip.io/actuator/health`,
   `https://auth.<ip>.nip.io/actuator/health` → 200, valid chain.

**Verify:** the three `curl` outputs with valid TLS.

### Phase 5 — Migration verification (the firm requirement)

Show real output for **all** of:

1. `himaanshu15028@gmail.com` logs in on the deployed instance with the
   **existing password** → callback → authed session. (Driven headless with me,
   then by you in a browser.)
2. `GET https://api.<ip>.nip.io/api/v1/objectives` (authed) returns the
   existing objectives.
3. Row-count / spot-check parity vs the local DB for: objectives, milestones,
   tasks, career target(s), skill assessments, roadmaps adopted, projects,
   evidence, `ai_conversations` + `ai_messages`.
4. Open an existing OSCAR conversation — history renders.
5. Ask OSCAR a new question end-to-end against the deployed instance →
   grounded answer, SSE streams (caret visible), `provider=gemini outcome=ok`.
6. A contextual nudge renders on `/today` or is cleanly absent.

### Phase 6 — Infra backup + ongoing backups

1. `oci bv boot-volume-backup create` — **after** confirming in the pricing
   page / cost estimator it is $0 within the 5-backup allowance. If it would
   charge: skip, tell you, we rely on the pg_dump chain only.
2. Confirm the nightly `orbit-backup.timer` fired once (force-run it):
   `pg_dump` object lands in the OCI bucket; bucket lifecycle rule set to
   14-day expiry.

**Verify:** `oci os object list` shows the backup object; boot-volume backup
`AVAILABLE` (or a note that it was skipped on cost).

### Phase 7 — Close-out

- `CLAUDE.md` status table: add the M8a row with real verification evidence.
- `deferred-items.md`: deploy-on-push (if not done), real domain, M10 proper.
- ADR-035 → **Accepted**, sign-off items recorded.
- Clean up any E2E test users created during Phase 5.

---

## 5. Known risks carried into execution

| Risk | Mitigation |
|---|---|
| `nip.io` Let's Encrypt rate limit ("too many certs for nip.io") | Retry with backoff; Caddy falls back to ZeroSSL automatically; worst case buy a domain (config-only change). |
| A1 "out of host capacity" at launch despite the check | Re-run the availability check; fall back to `ap-hyderabad-1` (ADR-035 alt 5). |
| api OIDC discovery can't reach the public auth hostname from inside the container | `extra_hosts` pin to Caddy (planned); fallback = internal `jwk-set-uri` + standalone issuer validator. |
| GHCR 3 images > 500 MB private cap | Prune-to-3 step; fallback = public packages (secret-free). |
| Reserved public IP shows a charge | It shouldn't while attached; if it does, switch to ephemeral + accept IP-change fragility, or investigate the policy. |
| Old refresh tokens fail after issuer change | Expected — one re-login. Password (BCrypt row) unaffected. |
| Monthly OCPU-hour cap (1,500) at ~1% margin | Don't run a second A1; monitor usage in the console. |
| Docker Desktop wedges during Phase 1 | Stop, report — do not force ([[orbit-docker-desktop-flaky]]). |

---

## 6. Rollback

- **Local stack:** never modified. The Phase-1 dump + the untouched
  `postgres_data` volume are both intact.
- **VM:** `docker compose down` (no `-v`) + re-`pull` an older `:sha` tag, or
  `pg_restore` the last good dump, or restore the Phase-6 boot-volume backup.
- **Total abandon:** `oci compute instance terminate` + release the reserved
  IP + delete the bucket. Local ORBIT keeps working exactly as today.

# Incidents

Short, factual postmortems for events that caused data loss, downtime, or a
material change of plan. Newest first.

---

## 2026-09-10 — Local pilot database lost to a Docker Desktop data-disk recreation

**Severity:** data loss (personal data, unrecoverable). No production impact —
nothing was deployed yet.

### What was lost

The compose `postgres_data` volume — the **only** copy of the owner's real
pilot data:

- the pilot account (`himaanshu15028@gmail.com`) and its `auth.users` /
  `auth.signing_keys` rows,
- all objectives, milestones, tasks, daily plans,
- the career target and all skill assessments,
- adopted roadmaps,
- projects and evidence,
- the entire OSCAR conversation history (`ai_conversations` / `ai_messages`).

### What was NOT affected

- **Source code, migrations, ADRs** — all in git, untouched.
- **Seed data** — the Flyway migrations rebuilt the full skill catalog
  (~99 skills), all 13 roadmaps, 125 topics and their skill mappings, and the
  complete schema exactly as designed. Verified after a fresh signup.
- **CI** — uses Testcontainers (throwaway DBs), never touched the real volume.

### Timeline (UTC+5:30)

- Throughout the session: Docker Desktop (Windows, WSL2 backend) was
  unresponsive — `docker version` timed out (exit 124). This is a **recurring**
  issue on this machine (M0, M2, M4, and earlier this session — see
  `.claude` memory `orbit-docker-desktop-flaky`).
- The named pipe `//./pipe/dockerDesktopLinuxEngine` did not exist — the Linux
  engine was not running, not merely wedged.
- The owner force-killed Docker Desktop (Task Manager end-task) and relaunched
  it.
- On relaunch, Docker Desktop **created a brand-new WSL2 data disk**:
  `C:\Users\HIMANSHU\AppData\Local\Docker\wsl\disk\docker_data.vhdx`,
  file **birth time 22:41:34** that day (confirmed via `stat`). The previous
  disk was gone.
- Post-restart state: `docker system df` → **0 volumes, 0 containers**;
  `docker volume ls` empty; only `orbit-api` / `orbit-auth` images present,
  rebuilt minutes earlier. No `postgres:16-alpine` / `redis:7-alpine` images
  survived — consistent with a full data-distro reinitialisation, not a
  remount.
- No recoverable backup existed anywhere: no `backups/` directory, no `.dump`
  file in the repo, no manual export, nothing deployed.

### Root cause

1. **Trigger:** a force-kill of a wedged Docker Desktop left the WSL2 data
   distro / `docker_data.vhdx` in a state Docker Desktop could not mount on the
   next start, so it recreated the disk from scratch. (Underlying cause of the
   *recurring* wedge itself is still not fully diagnosed — candidates: WSL2
   backend memory/disk pressure, a Desktop auto-update mid-session, Windows
   fast-startup interaction.)
2. **Why it became data loss:** ORBIT's local database lived in a single Docker
   volume with **no backup of any kind**, and had since M0. Every milestone's
   "verification practice" note in `CLAUDE.md` treated that volume as
   production-like state to preserve — but nothing ever dumped it. The one
   safety step (a `pg_dump` before risky operations) was scheduled as
   "Milestone 8a Phase 1" and the disk was recreated one restart before we got
   there.

### Resolution

- Data loss **accepted** by the owner — personal data to be rebuilt manually
  over time; not a blocker for M8a.
- A fresh signup + the Flyway-rebuilt seed data is the new local baseline.
- **`pg_dump` taken immediately** and restore-tested (row-parity into a
  throwaway container) before any further command touched the new database:
  `scripts/restore-test-local.sh` → PASS.

### Corrective actions

| # | Action | Status |
|---|--------|--------|
| 1 | `scripts/backup-local.sh` — one-command whole-DB dump + archive validation, keeps the last 20. Documented in `README.md`. | **done (2026-09-10)** |
| 2 | `scripts/restore-test-local.sh` — dump + restore into a throwaway container + row-count parity. Run it before any migration test or risky op. | **done (2026-09-10)** |
| 3 | ADR-035 §9 — the deployed VM gets a nightly `pg_dump` pushed **off-box** to OCI Object Storage, plus an OCI boot-volume backup after migration. | planned (M8a Phase 6) |
| 4 | Backup discipline written into the milestone process, not just "remembered". | `.claude` memory updated |
| 5 | Diagnose the recurring Docker Desktop wedge properly (WSL2 backend limits, Desktop settings, Windows fast-startup). | open — tracked in `deferred-items.md` |

### Lesson

A single copy is not a backup. "Treat the volume as precious" is not a control;
an automated dump you can restore is. The gap existed for eight milestones
because the data was never *quite* important enough to stop and fix it — until
it was gone.

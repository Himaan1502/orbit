# `deploy/` — ORBIT single-VM OCI deployment

Artifacts for the Milestone 8a deployment. Authoritative design:
[ADR-035](../docs/adr/ADR-035-single-vm-oci-deployment-topology.md).
Step-by-step: [`../docs/engineering/M8a-deployment-scope-and-runbook.md`](../docs/engineering/M8a-deployment-scope-and-runbook.md).

| File | Runs where | Purpose |
|---|---|---|
| `cloud-init.yaml` | OCI, at instance launch (`user_data`) | Docker + compose plugin, `/opt/orbit`, `ufw` 22/80/443, SSH hardening. Does **not** deploy the stack. |
| `Caddyfile` | `orbit-caddy` container | TLS termination (Let's Encrypt) + host-based routing for `web.` / `api.` / `auth.` `<ip>.nip.io`. `flush_interval -1` on `api.` keeps OSCAR SSE unbuffered. |
| `orbit.service` | VM systemd | `docker compose pull && up -d` on boot; `down` on shutdown. `enable --now`. |
| `orbit-backup.service` + `.timer` | VM systemd | Nightly 02:30 UTC → `backup.sh`. |
| `backup.sh` | VM | `pg_dump` (whole DB) → OCI Object Storage via **instance principal** (no creds on the VM); 14 local copies kept. |
| `local-backup-and-restore-test.sh` | dev machine | Runbook **Phase 1**: dump the local DB, restore into a throwaway container, assert row-count parity. Never touches the real `postgres_data` volume. |

## The deploy bundle on the VM (`/opt/orbit/`)

```
/opt/orbit/
├── docker-compose.yml            # from repo root, unchanged
├── docker-compose.prod.yml       # from repo root — the prod overlay
├── deploy/Caddyfile
├── deploy/backup.sh
├── docker/postgres/init.sql      # from repo — first-init schema/grants
├── .env                          # chmod 600, NEVER committed — see .env.prod.example
└── backups/                      # local dump cache
```

Compose invocation (also baked into `orbit.service`):

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --no-build
```

## Switching to a real domain later (config-only — ADR-035 §5)

1. Point the domain's `A` / `*` record at the reserved public IP.
2. In `/opt/orbit/.env`: set `ORBIT_DOMAIN=example.com` and update
   `OIDC_ISSUER_URI` / `WEB_REDIRECT_URI` / `WEB_POST_LOGOUT_URI` /
   `WEB_PUBLIC_URL` / `CORS_ORIGINS` to the new host.
3. `docker compose ... up -d` — Caddy issues fresh certs; `RegisteredClientSeeder`
   rewrites the auth client's redirect URI on the auth container's restart.

No image rebuild. No code change. The nip.io values are not baked into any image.

#!/usr/bin/env bash
# ORBIT nightly database backup — ADR-035 §9.
#
# pg_dump (custom format, whole DB — both `orbit` and `auth` schemas) from the
# running orbit-db container, then push OFF the VM to OCI Object Storage using
# the instance principal (no credentials stored on the VM). Keeps 14 local
# copies as a fast-restore cache; the bucket lifecycle rule owns long retention.
#
# Install: /opt/orbit/deploy/backup.sh, mode 0755. Driven by orbit-backup.timer.
#
# Required env (from /opt/orbit/.env, sourced below):
#   POSTGRES_USER  POSTGRES_DB  ORBIT_BACKUP_BUCKET  ORBIT_BACKUP_NAMESPACE
set -euo pipefail

cd /opt/orbit
set -a; . ./.env; set +a

: "${ORBIT_BACKUP_BUCKET:?set ORBIT_BACKUP_BUCKET in .env}"
: "${ORBIT_BACKUP_NAMESPACE:?set ORBIT_BACKUP_NAMESPACE in .env}"

ts="$(date -u +%Y%m%dT%H%M%SZ)"
out="/opt/orbit/backups/orbit-${ts}.dump"
compose="docker compose -f docker-compose.yml -f docker-compose.prod.yml"

echo "[$(date -u +%FT%TZ)] pg_dump -> ${out}"
$compose exec -T orbit-db \
	pg_dump -U "${POSTGRES_USER:-orbit_user}" -d "${POSTGRES_DB:-orbit_db}" -Fc \
	> "${out}"

# Sanity: a custom-format dump is never smaller than a few KB and starts "PGDMP".
size=$(stat -c%s "${out}")
head -c 5 "${out}" | grep -q PGDMP || { echo "FATAL: dump header not PGDMP"; exit 1; }
[ "${size}" -gt 4096 ] || { echo "FATAL: dump only ${size} bytes"; exit 1; }
echo "[$(date -u +%FT%TZ)] dump ok (${size} bytes)"

echo "[$(date -u +%FT%TZ)] upload -> oci://${ORBIT_BACKUP_BUCKET}/orbit-${ts}.dump"
oci os object put \
	--auth instance_principal \
	--namespace "${ORBIT_BACKUP_NAMESPACE}" \
	--bucket-name "${ORBIT_BACKUP_BUCKET}" \
	--name "orbit-${ts}.dump" \
	--file "${out}" \
	--no-multipart

# Local retention: keep the newest 14.
ls -1t /opt/orbit/backups/orbit-*.dump | tail -n +15 | xargs -r rm --
echo "[$(date -u +%FT%TZ)] done"

#!/usr/bin/env bash
# Back up the local ORBIT database — one command, run it often.
#
#   ./scripts/backup-local.sh
#
# Dumps the WHOLE database (both the `orbit` and `auth` schemas, extensions,
# and the Flyway history) from the running orbit-db container to a timestamped
# custom-format archive under ./backups/, and validates the archive.
#
# Why this exists: the compose `postgres_data` volume is the only copy of the
# owner's real pilot data. On 2026-09-10 a Docker Desktop force-restart
# recreated the WSL2 data disk and that volume — and everything in it — was
# lost, because no backup existed. See docs/engineering/incidents.md.
# Take a dump before any risky operation (Docker upgrades, `compose down`,
# migration testing, machine reboots you're unsure about).
#
# To PROVE a dump restores cleanly: ./scripts/restore-test-local.sh
set -euo pipefail

cd "$(dirname "$0")/.."

PGUSER_LOCAL="${POSTGRES_USER:-orbit_user}"
PGDB_LOCAL="${POSTGRES_DB:-orbit_db}"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="backups/orbit-local-${TS}.dump"

mkdir -p backups

if ! docker compose ps --status running --services 2>/dev/null | grep -qx orbit-db; then
	echo "ERROR: orbit-db is not running. Start it with:  docker compose up -d orbit-db" >&2
	exit 1
fi

echo "==> pg_dump  ->  ${OUT}"
docker compose exec -T orbit-db \
	pg_dump -U "${PGUSER_LOCAL}" -d "${PGDB_LOCAL}" -Fc > "${OUT}"

SIZE=$(stat -c%s "${OUT}" 2>/dev/null || stat -f%z "${OUT}")
head -c 5 "${OUT}" | grep -q PGDMP || { echo "FAIL: '${OUT}' is not a PGDMP archive" >&2; rm -f "${OUT}"; exit 1; }
[ "${SIZE}" -gt 2048 ] || { echo "FAIL: '${OUT}' is only ${SIZE} bytes" >&2; exit 1; }
# Validate the archive is readable by pg_restore. Stream it into a container via
# stdin rather than a bind mount — avoids Git-Bash/Windows path translation.
docker run --rm -i postgres:16-alpine sh -c 'cat > /tmp/d && pg_restore --list /tmp/d' < "${OUT}" > /dev/null \
	|| { echo "FAIL: pg_restore --list rejected the archive" >&2; exit 1; }

# Keep the newest 20 local dumps.
ls -1t backups/orbit-local-*.dump 2>/dev/null | tail -n +21 | xargs -r rm --

echo "==> OK  (${SIZE} bytes, archive valid)"
echo "    ${OUT}"

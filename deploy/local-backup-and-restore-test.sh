#!/usr/bin/env bash
# Runbook Phase 1 — full local backup + a REAL restore test. ADR-035 §9.
#
# 1. pg_dump the running local orbit-db (whole DB: `orbit` + `auth` schemas).
# 2. Assert the dump is a valid non-empty custom-format archive.
# 3. Restore it into a THROWAWAY postgres:16-alpine container.
# 4. Diff row counts for the load-bearing tables — source vs restored — and
#    fail on any mismatch.
# 5. Tear the throwaway container down (its own volume, never `-v` on the real
#    stack — see docs/engineering/... [[orbit-compose-no-volume-wipe]]).
#
# Prereqs: Docker healthy; the local ORBIT stack up (orbit-db running).
# Nothing here writes to the real postgres_data volume.
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-$(pwd)/backups}"
PGUSER_LOCAL="${POSTGRES_USER:-orbit_user}"
PGDB_LOCAL="${POSTGRES_DB:-orbit_db}"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
DUMP="${BACKUP_DIR}/orbit-local-${TS}.dump"
TMP_CTR="orbit-restore-test-${TS}"
TMP_PW="restore-test-pw"

mkdir -p "${BACKUP_DIR}"

echo "==> 1. pg_dump  (source: local orbit-db container)"
docker exec -i orbit-db pg_dump -U "${PGUSER_LOCAL}" -d "${PGDB_LOCAL}" -Fc > "${DUMP}"
SIZE=$(stat -c%s "${DUMP}" 2>/dev/null || stat -f%z "${DUMP}")
echo "    ${DUMP}  (${SIZE} bytes)"

echo "==> 2. validate archive"
head -c 5 "${DUMP}" | grep -q PGDMP || { echo "FAIL: not a PGDMP archive"; exit 1; }
[ "${SIZE}" -gt 4096 ] || { echo "FAIL: implausibly small"; exit 1; }
docker run --rm -i -v "${DUMP}:/d.dump:ro" postgres:16-alpine \
  pg_restore --list /d.dump > /dev/null && echo "    pg_restore --list OK"

echo "==> 3. restore into throwaway container ${TMP_CTR}"
docker run -d --name "${TMP_CTR}" -e POSTGRES_PASSWORD="${TMP_PW}" \
  -e POSTGRES_DB="${PGDB_LOCAL}" -e POSTGRES_USER="${PGUSER_LOCAL}" \
  postgres:16-alpine >/dev/null
trap 'docker rm -f "${TMP_CTR}" >/dev/null 2>&1 || true' EXIT
for i in $(seq 1 30); do
  docker exec "${TMP_CTR}" pg_isready -U "${PGUSER_LOCAL}" -d "${PGDB_LOCAL}" >/dev/null 2>&1 && break
  sleep 1
done
docker exec -i "${TMP_CTR}" pg_restore -U "${PGUSER_LOCAL}" -d "${PGDB_LOCAL}" \
  --no-owner --clean --if-exists < "${DUMP}" 2>&1 | tail -5 || true

echo "==> 4. row-count parity (source vs restored)"
Q="SELECT
  (SELECT count(*) FROM auth.users)               AS auth_users,
  (SELECT count(*) FROM auth.signing_keys)        AS signing_keys,
  (SELECT count(*) FROM orbit.objectives)         AS objectives,
  (SELECT count(*) FROM orbit.milestones)         AS milestones,
  (SELECT count(*) FROM orbit.tasks)              AS tasks,
  (SELECT count(*) FROM orbit.career_targets)     AS career_targets,
  (SELECT count(*) FROM orbit.skill_assessments)  AS skill_assessments,
  (SELECT count(*) FROM orbit.user_roadmaps)      AS user_roadmaps,
  (SELECT count(*) FROM orbit.projects)           AS projects,
  (SELECT count(*) FROM orbit.evidence)           AS evidence,
  (SELECT count(*) FROM orbit.ai_conversations)   AS ai_conversations,
  (SELECT count(*) FROM orbit.ai_messages)        AS ai_messages;"

SRC=$(docker exec -i orbit-db psql -U "${PGUSER_LOCAL}" -d "${PGDB_LOCAL}" -At -F',' -c "${Q}")
RES=$(docker exec -i "${TMP_CTR}" psql -U "${PGUSER_LOCAL}" -d "${PGDB_LOCAL}" -At -F',' -c "${Q}")
echo "    source  : ${SRC}"
echo "    restored: ${RES}"
[ "${SRC}" = "${RES}" ] && echo "==> PASS: restore is row-identical" \
  || { echo "==> FAIL: row counts differ"; exit 1; }

echo
echo "Keep this dump as the migration source + rollback baseline:"
echo "  ${DUMP}"

#!/usr/bin/env bash
# Consistent logical backup of the paspm database BEFORE every deployment (Flyway has no down migrations).
#   scripts/staging/backup-db.sh [output-file]      default: backups/paspm-<UTC timestamp>.sql.gz
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"; cd "$REPO"
COMPOSE_FILES="${COMPOSE_FILES:--f compose.yml -f compose.backend.yml -f compose.staging.yml}"
OUT="${1:-backups/paspm-$(date -u +%Y%m%dT%H%M%SZ).sql.gz}"
mkdir -p "$(dirname "$OUT")"
# shellcheck disable=SC2086
docker compose $COMPOSE_FILES exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --triggers --set-gtid-purged=OFF --databases paspm' | gzip > "$OUT"
gzip -t "$OUT"
echo "backup written: $OUT ($(wc -c < "$OUT") bytes)"
if command -v sha256sum >/dev/null; then sha256sum "$OUT"; else shasum -a 256 "$OUT"; fi

#!/usr/bin/env bash
# Restore a backup made by backup-db.sh. DESTRUCTIVE: drops the paspm database first. Stop the backend before running it.
#   scripts/staging/restore-db.sh backups/paspm-<timestamp>.sql.gz --yes
set -euo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"; cd "$REPO"
FILE="${1:?usage: restore-db.sh <backup.sql.gz> --yes}"; [ "${2:-}" = "--yes" ] || { echo "refusing without --yes (this drops database paspm)" >&2; exit 2; }
COMPOSE_FILES="${COMPOSE_FILES:--f compose.yml -f compose.backend.yml -f compose.staging.yml}"
gzip -t "$FILE"
# shellcheck disable=SC2086
docker compose $COMPOSE_FILES exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "DROP DATABASE IF EXISTS paspm"'
# shellcheck disable=SC2086
gunzip -c "$FILE" | docker compose $COMPOSE_FILES exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot'
echo "restored from $FILE"

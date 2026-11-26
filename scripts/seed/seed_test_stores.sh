#!/usr/bin/env bash
# TDP-01: create Store B / Store C and their test accounts. Usage: scripts/seed/seed_test_stores.sh [path-to-env-file]
# (default env file: scripts/seed/.env.seed; the same file as seed.sh, plus SEED_TEST_ACCOUNT_PASSWORD)
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
ENV_FILE="${1:-$HERE/.env.seed}"
if [ -f "$ENV_FILE" ]; then
  set -a; . "$ENV_FILE"; set +a
fi
cd "$REPO"
exec python3 -I "$HERE/seed_test_stores.py"

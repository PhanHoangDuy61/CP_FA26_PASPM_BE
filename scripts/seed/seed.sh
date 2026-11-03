#!/usr/bin/env bash
# Run the demo seed. Usage: scripts/seed/seed.sh [path-to-env-file]   (default: scripts/seed/.env.seed)
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
ENV_FILE="${1:-$HERE/.env.seed}"
if [ -f "$ENV_FILE" ]; then
  set -a; . "$ENV_FILE"; set +a
fi
cd "$REPO"
exec python3 -I "$HERE/seed_demo.py"

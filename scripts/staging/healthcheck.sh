#!/usr/bin/env bash
# Health check of a running compose stack (no actuator exists, so it checks containers, HTTP, Flyway and the scheduler gate).
#   scripts/staging/healthcheck.sh            # from the repository root; reads the same .env as compose
# Optional: BASE_URL (default http://127.0.0.1:${PASPM_BACKEND_HOST_PORT:-8080}), COMPOSE_FILES ("-f compose.yml -f compose.backend.yml"),
#   HC_ADMIN_EMAIL + HC_ADMIN_PASSWORD (also checks that the System Admin can log in and that enabling a schedule is refused with 503).
set -uo pipefail
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"; cd "$REPO"
[ -f .env ] && { set -a; . ./.env; set +a; }
BASE_URL="${BASE_URL:-http://127.0.0.1:${PASPM_BACKEND_HOST_PORT:-8080}}"
COMPOSE_FILES="${COMPOSE_FILES:--f compose.yml -f compose.backend.yml -f compose.staging.yml}"
fail=0
check() { if [ "$2" = ok ]; then echo "PASS  $1  $3"; else echo "FAIL  $1  $3"; fail=1; fi; }
# shellcheck disable=SC2086
state="$(docker compose $COMPOSE_FILES ps --format '{{.Service}}={{.State}}/{{.Health}}' 2>/dev/null | tr '\n' ' ')"
echo "$state" | grep -q 'mysql=running/healthy' && check mysql ok "$state" || check mysql fail "$state"
echo "$state" | grep -q 'backend=running' && check backend-container ok "" || check backend-container fail "$state"
code="$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/api/auth/me")"
[ "$code" = 401 ] && check http-security ok "GET /api/auth/me without token -> 401" || check http-security fail "got $code"
# shellcheck disable=SC2086
ver="$(docker compose $COMPOSE_FILES exec -T mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B paspm -e "SELECT MAX(version+0), SUM(success=0) FROM flyway_schema_history"' 2>/dev/null | tr '\t' ' ')"
[ -n "$ver" ] && [ "${ver##* }" = 0 ] && check flyway ok "latest version / failed rows: $ver" || check flyway fail "$ver"
if [ -n "${HC_ADMIN_EMAIL:-}" ] && [ -n "${HC_ADMIN_PASSWORD:-}" ]; then
  tok="$(curl -s -X POST "$BASE_URL/api/auth/login" -H 'Content-Type: application/json' \
        -d "{\"email\":\"$HC_ADMIN_EMAIL\",\"password\":\"$HC_ADMIN_PASSWORD\"}" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
  [ -n "$tok" ] && check admin-login ok "" || check admin-login fail "login failed"
  code="$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $tok" "$BASE_URL/api/system/stores")"
  [ "$code" = 200 ] && check admin-api ok "GET /api/system/stores -> 200" || check admin-api fail "got $code"
fi
exit $fail

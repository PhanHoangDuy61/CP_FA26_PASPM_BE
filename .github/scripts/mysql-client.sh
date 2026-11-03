#!/usr/bin/env bash
set -euo pipefail
tool="$(basename "$0")"
case "$tool" in
  mysql|mysqldump) ;;
  *) printf '%s\n' 'Unsupported MySQL CLI wrapper' >&2; exit 1 ;;
esac
: "${RUNNER_TEMP:?GitHub disposable runner temp directory required}"
# Match the service's MySQL version. Private option/dump files stay in runner temp.
exec docker run --rm --interactive --network host \
  --mount "type=bind,source=$RUNNER_TEMP,target=$RUNNER_TEMP,readonly" \
  --env MYSQL_PWD --env MYSQL_HISTFILE=/dev/null \
  --entrypoint "$tool" mysql:8.4 "$@"

#!/usr/bin/env bash
# PASPM Option B verification runner (macOS; works on Linux for the preflight).
#
#   scripts/verify-option-b-macos.sh              read-only preflight, nothing is executed or created
#   scripts/verify-option-b-macos.sh --run        preflight, then every stage whose prerequisites exist
#   scripts/verify-option-b-macos.sh --inspect-local-history
#                                                 read-only SELECT of a LOCAL database's flyway_schema_history
#
# Statuses: PASS (ran, evidence is clean) | FAIL (ran, evidence shows a defect)
#           BLOCKED (a prerequisite is missing, stage not attempted) | NOT RUN (not requested)
#
# Safety: never touches an existing database. All integration work uses a disposable mysql:8.4 container this
# script creates with a unique name on a free loopback port. It never runs flyway repair/clean, never deletes a
# volume, never connects to the application's default database, never commits/pushes/deploys.
# Bash 3.2 compatible (macOS default). Credentials are passed through the environment only and are not logged.
set -uo pipefail

MODE="preflight"
case "${1:-}" in
  "") ;;
  --run) MODE="run" ;;
  --inspect-local-history) MODE="inspect" ;;
  -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
  *) echo "Unknown argument: $1 (use --run, --inspect-local-history or no argument)" >&2; exit 64 ;;
esac

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TS="$(date -u +%Y%m%dT%H%M%SZ)"
EVID_ROOT="${PASPM_EVIDENCE_ROOT:-$HOME/paspm-verification-evidence}"
EVID="$EVID_ROOT/option-b-$TS"
SUMMARY="$EVID/summary.tsv"
CONTAINER="paspm-verify-mysql-$TS"
STARTED_CONTAINER=0
APP_PID=""
J17=""; J25=""; MVN=""; SPMF_JAR=""; PORT=""; MYSQL_IMAGE="mysql:8.4"
TMPWORK=""

mkdir -p "$EVID" || { echo "Cannot create evidence directory $EVID" >&2; exit 70; }
: > "$SUMMARY"

c_red=""; c_grn=""; c_yel=""; c_off=""
if [ -t 1 ]; then c_red=$'\033[31m'; c_grn=$'\033[32m'; c_yel=$'\033[33m'; c_off=$'\033[0m'; fi

record() { # id status detail
  printf '%s\t%s\t%s\n' "$1" "$2" "$3" >> "$SUMMARY"
  local col="$c_off"
  case "$2" in PASS) col="$c_grn";; FAIL) col="$c_red";; BLOCKED|"NOT RUN") col="$c_yel";; esac
  printf '%s[%-7s]%s %-34s %s\n' "$col" "$2" "$c_off" "$1" "$3"
}
log() { printf '%s\n' "$*" >> "$EVID/commands.log"; }

cleanup() {
  if [ -n "$APP_PID" ] && kill -0 "$APP_PID" 2>/dev/null; then kill "$APP_PID" 2>/dev/null; wait "$APP_PID" 2>/dev/null; fi
  if [ "$STARTED_CONTAINER" = 1 ]; then docker stop "$CONTAINER" >/dev/null 2>&1; fi   # container is --rm and was created by this run
  [ -n "$TMPWORK" ] && [ -d "$TMPWORK" ] && rm -rf "$TMPWORK"
}
trap cleanup EXIT INT TERM

# ----------------------------------------------------------------------------------------------- preflight
find_java() { # $1 major
  local home=""
  if [ -x /usr/libexec/java_home ]; then home="$(/usr/libexec/java_home -v "$1" 2>/dev/null || true)"; fi
  if [ -n "$home" ] && [ -x "$home/bin/java" ]; then printf '%s' "$home"; return 0; fi
  return 1
}
java_major() { "$1" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1; }   # ignores a leading "Picked up JAVA_TOOL_OPTIONS" line

preflight() {
  echo "== Preflight (read-only) =="
  local os arch; os="$(uname -s)"; arch="$(uname -m)"
  if [ "$os" = "Darwin" ]; then record "pre.os" PASS "macOS $(sw_vers -productVersion 2>/dev/null) $arch"
  else record "pre.os" BLOCKED "$os $arch is not macOS (preflight output is still valid; --run needs PASPM_VERIFY_ALLOW_NON_MACOS=1)"; fi

  J17="${PASPM_JAVA17_HOME:-}"; [ -z "$J17" ] && J17="$(find_java 17 || true)"
  if [ -n "$J17" ] && [ "$(java_major "$J17/bin/java")" = "17" ]; then record "pre.java17" PASS "$J17"; else J17=""; record "pre.java17" BLOCKED "JDK 17 not found (install Temurin 17 or set PASPM_JAVA17_HOME)"; fi

  local j25="${PASPM_JAVA25_HOME:-}"; [ -z "$j25" ] && j25="$(find_java 25 || true)"
  J25=""
  if [ -n "${SPMF_JAVA_PATH:-}" ] && [ -x "${SPMF_JAVA_PATH}" ] && [ "$(java_major "$SPMF_JAVA_PATH")" -ge 25 ] 2>/dev/null; then J25="$SPMF_JAVA_PATH"
  elif [ -n "$j25" ] && [ -x "$j25/bin/java" ] && [ "$(java_major "$j25/bin/java")" -ge 25 ] 2>/dev/null; then J25="$j25/bin/java"; fi
  # java_home -v N can silently return a different JDK (e.g. 24 for -v 25), so the major version is checked, not assumed.
  if [ -n "$J25" ]; then record "pre.java25" PASS "$J25 (Java $(java_major "$J25"))"; else record "pre.java25" BLOCKED "JDK 25+ for the SPMF child not found (set PASPM_JAVA25_HOME or SPMF_JAVA_PATH; a JDK below 25 is rejected)"; fi

  SPMF_JAR="${SPMF_JAR_PATH:-${PASPM_MINING_SPMF_JAR:-}}"
  if [ -n "$SPMF_JAR" ] && [ -f "$SPMF_JAR" ]; then
    if [ -n "$J25" ] && "$J25" -Djava.awt.headless=true -jar "$SPMF_JAR" version 2>&1 | grep -q "SPMF version 2.67a"; then record "pre.spmf" PASS "SPMF 2.67a at $SPMF_JAR"
    else record "pre.spmf" BLOCKED "jar present but version 2.67a could not be confirmed with Java 25"; fi
  else SPMF_JAR=""; record "pre.spmf" BLOCKED "SPMF jar not configured (set SPMF_JAR_PATH; see docs/setup/F05_SPMF_RUNTIME_SETUP.md)"; fi

  if command -v mvn >/dev/null 2>&1; then MVN="mvn"; record "pre.maven" PASS "$(mvn -v 2>/dev/null | head -1)"
  elif [ -x "$REPO/mvnw" ]; then MVN="$REPO/mvnw"; record "pre.maven" PASS "Maven Wrapper (distribution is downloaded on first use)"
  else record "pre.maven" BLOCKED "neither mvn nor ./mvnw available"; fi

  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    record "pre.docker" PASS "$(docker --version 2>/dev/null)"
    if docker image inspect "$MYSQL_IMAGE" >/dev/null 2>&1; then record "pre.mysql84" PASS "image $MYSQL_IMAGE present locally"
    else record "pre.mysql84" PASS "image $MYSQL_IMAGE will be pulled on --run (network required; stage becomes BLOCKED if the pull fails)"; fi
  else record "pre.docker" BLOCKED "Docker daemon not reachable (start Docker Desktop)"; record "pre.mysql84" BLOCKED "needs Docker for a disposable MySQL 8.4"; fi

  local m2="${HOME}/.m2/repository"
  if [ -d "$m2/org/springframework/boot/spring-boot-starter-parent/3.5.7" ]; then record "pre.m2cache" PASS "Spring Boot 3.5.7 parent cached ($(find "$m2" -name '*.jar' 2>/dev/null | wc -l | tr -d ' ') jars)"
  else
    if curl -sS -o /dev/null -m 10 -I https://repo.maven.apache.org/maven2/ 2>/dev/null; then record "pre.m2cache" PASS "cache empty but Maven Central reachable"
    else record "pre.m2cache" BLOCKED "no local Maven cache and Maven Central unreachable"; fi
  fi

  local branch dirty; branch="$(git -C "$REPO" rev-parse --abbrev-ref HEAD 2>/dev/null)"; dirty="$(git -C "$REPO" status --porcelain 2>/dev/null | wc -l | tr -d ' ')"
  record "pre.git" PASS "branch $branch, $dirty uncommitted paths (read only; nothing is staged or changed)"
  if git -C "$REPO" rev-parse --verify -q origin/dev >/dev/null; then record "pre.origin_dev" PASS "origin/dev = $(git -C "$REPO" rev-parse --short origin/dev) (run 'git fetch origin dev' first for the freshest schema)"
  else record "pre.origin_dev" BLOCKED "origin/dev not available locally; run: git fetch origin dev"; fi
  command -v python3 >/dev/null 2>&1 && record "pre.python3" PASS "$(python3 --version 2>&1)" || record "pre.python3" BLOCKED "python3 missing (needed for test counting)"
  record "pre.env" PASS "optional: PASPM_JAVA17_HOME PASPM_JAVA25_HOME SPMF_JAR_PATH SPMF_JAVA_PATH PASPM_EVIDENCE_ROOT (values are never logged)"
}

need() { # id requirement... ; returns 0 if all prerequisites for stage are satisfied
  local id="$1"; shift; local miss=""
  for r in "$@"; do
    case "$r" in
      java17) [ -n "$J17" ] || miss="$miss java17" ;;
      java25) [ -n "$J25" ] || miss="$miss java25" ;;
      spmf)   [ -n "$SPMF_JAR" ] || miss="$miss spmf" ;;
      maven)  [ -n "$MVN" ] || miss="$miss maven" ;;
      docker) { command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; } || miss="$miss docker" ;;
      mysql)  [ -n "$PORT" ] || miss="$miss mysql84-container" ;;
      python3) command -v python3 >/dev/null 2>&1 || miss="$miss python3" ;;
    esac
  done
  if [ -n "$miss" ]; then record "$id" BLOCKED "missing:$miss"; return 1; fi
  return 0
}

count_surefire() { # dir -> "tests failures errors skipped"
  python3 - "$1" <<'PY_EOF'
import glob, sys, xml.etree.ElementTree as ET
t=f=e=s=0
for p in glob.glob(sys.argv[1] + "/TEST-*.xml"):
    r = ET.parse(p).getroot()
    t += int(r.get("tests", 0)); f += int(r.get("failures", 0)); e += int(r.get("errors", 0)); s += int(r.get("skipped", 0))
print(t, f, e, s)
PY_EOF
}

mvn_stage() { # id label outdir -- mvn args...
  local id="$1" label="$2" out="$3"; shift 3
  mkdir -p "$out"
  rm -rf "$REPO/target/surefire-reports"   # otherwise a -Dtest run without clean counts stale reports from an earlier stage
  log "[$id] JAVA_HOME=$J17 $MVN --batch-mode --no-transfer-progress $*"
  ( cd "$REPO" && JAVA_HOME="$J17" PATH="$J17/bin:$PATH" "$MVN" --batch-mode --no-transfer-progress "$@" ) > "$out/maven.log" 2>&1
  local rc=$?
  rm -rf "$out/surefire-reports"; cp -R "$REPO/target/surefire-reports" "$out/surefire-reports" 2>/dev/null
  local counts; counts="$(count_surefire "$out/surefire-reports" 2>/dev/null || echo "0 0 0 0")"
  set -- $counts
  local t="$1" f="$2" e="$3" s="$4"
  echo "tests=$t failures=$f errors=$e skipped=$s mavenExit=$rc" > "$out/counts.txt"
  if [ "$rc" -eq 0 ] && [ "$t" -gt 0 ] && [ "$f" -eq 0 ] && [ "$e" -eq 0 ]; then
    if [ "$s" -gt 0 ]; then record "$id" PASS "$label: $t run, $f failed, $e errors, $s SKIPPED (skipped tests are NOT passes; see $out)"; else record "$id" PASS "$label: $t run, 0 failed, 0 errors, 0 skipped"; fi
    return 0
  fi
  if [ "$t" -eq 0 ]; then record "$id" FAIL "$label: Maven exit $rc and no tests were executed (dependency or compile failure; see $out/maven.log)"
  else record "$id" FAIL "$label: $t run, $f failed, $e errors, $s skipped, Maven exit $rc (see $out)"; fi
  return 1
}

# ------------------------------------------------------------------------------------- disposable MySQL
free_port() {
  local p=33084
  while [ "$p" -lt 33200 ]; do
    if ! nc -z 127.0.0.1 "$p" >/dev/null 2>&1; then printf '%s' "$p"; return 0; fi
    p=$((p+1))
  done; return 1
}
start_mysql() {
  need "mysql.start" docker || return 1
  if ! docker image inspect "$MYSQL_IMAGE" >/dev/null 2>&1; then
    docker pull "$MYSQL_IMAGE" > "$EVID/docker-pull.log" 2>&1 || { record "mysql.start" BLOCKED "cannot pull $MYSQL_IMAGE (registry unreachable); see $EVID/docker-pull.log"; return 1; }
  fi
  PORT="$(free_port)" || { record "mysql.start" BLOCKED "no free loopback port in 33084-33199"; return 1; }
  log "docker run -d --rm --name $CONTAINER -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -p 127.0.0.1:$PORT:3306 --tmpfs /var/lib/mysql $MYSQL_IMAGE  (disposable; empty root password, loopback only)"
  docker run -d --rm --name "$CONTAINER" -e MYSQL_ALLOW_EMPTY_PASSWORD=yes -e MYSQL_ROOT_HOST=% \
    -p "127.0.0.1:$PORT:3306" --tmpfs /var/lib/mysql "$MYSQL_IMAGE" > "$EVID/docker-run.log" 2>&1 || { record "mysql.start" FAIL "docker run failed; see $EVID/docker-run.log"; PORT=""; return 1; }
  STARTED_CONTAINER=1
  local i=0
  while [ $i -lt 60 ]; do
    if docker exec "$CONTAINER" mysqladmin ping -uroot --silent >/dev/null 2>&1; then
      # the image restarts once after init; require two consecutive successes
      sleep 3; docker exec "$CONTAINER" mysqladmin ping -uroot --silent >/dev/null 2>&1 && break
    fi
    sleep 2; i=$((i+1))
  done
  local v; v="$(docker exec "$CONTAINER" mysql -uroot -N -e 'SELECT VERSION()' 2>/dev/null)"
  case "$v" in 8.4.*) record "mysql.start" PASS "disposable MySQL $v on 127.0.0.1:$PORT (container $CONTAINER)";; *) record "mysql.start" FAIL "unexpected server version '$v'"; return 1;; esac
}
sql() { docker exec -i "$CONTAINER" mysql -uroot --batch --raw "$@"; }   # root has an empty password inside the throwaway container only

# --------------------------------------------------------------------------------------- app runtime
start_app() { # jar db logfile apiport
  local jar="$1" db="$2" logf="$3" aport="$4"
  JAVA_HOME="$J17" PATH="$J17/bin:$PATH" \
  DB_URL="jdbc:mysql://127.0.0.1:$PORT/$db?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true" DB_USER=root DB_PASSWORD="" \
  JWT_SECRET="$(openssl rand -hex 24)" OTP_SECRET="$(openssl rand -hex 24)" PORT="$aport" \
  BOOTSTRAP_EMAIL="" BOOTSTRAP_PASSWORD="" PROFILE_IMAGE_DIRECTORY="$TMPWORK/avatars" \
  PASPM_MINING_SPMF_JAR="${SPMF_JAR}" PASPM_MINING_JAVA_EXECUTABLE="${J25}" \
  "$J17/bin/java" -jar "$jar" > "$logf" 2>&1 &
  APP_PID=$!
  local i=0
  while [ $i -lt 90 ]; do
    if curl -fsS -m 3 "http://127.0.0.1:$aport/v3/api-docs" -o "$TMPWORK/api-docs.json" 2>/dev/null; then return 0; fi
    kill -0 "$APP_PID" 2>/dev/null || return 1
    sleep 2; i=$((i+1))
  done
  return 1
}
stop_app() { if [ -n "$APP_PID" ]; then kill "$APP_PID" 2>/dev/null; wait "$APP_PID" 2>/dev/null; APP_PID=""; fi; }
history() { # db outfile
  sql -D "$1" -e "SELECT installed_rank, IFNULL(version,'-') AS version, description, type, checksum, success FROM flyway_schema_history ORDER BY installed_rank" > "$2" 2>&1
}
data_fingerprint() { # db
  sql -N -D "$1" -e "
    SELECT 'store', MD5(CONCAT_WS('|',store_id,name)) FROM store WHERE store_id=91;
    SELECT 'utility_snapshot', MD5(CONCAT_WS('|',snapshot_id,store_id,source_hash,matrix_hash,total_weighted_utility,status)) FROM utility_snapshot WHERE snapshot_id=91;
    SELECT 'mining_request', MD5(CONCAT_WS('|',request_id,store_id,requested_by,utility_snapshot_id,request_key,payload_hash,algorithms,min_util_mode,min_util_value,min_util_spmf,input_snapshot_hash,frozen_input,utility_config_snapshot,status,comparison_status)) FROM mining_request WHERE request_id=91;
    SELECT 'mining_run', MD5(CONCAT_WS('|',run_id,store_id,request_id,algorithm,attempt_no,library_version,status)) FROM mining_run WHERE store_id=91;" 2>&1
}
seed_dev_data() { # db
  sql -D "$1" <<'SEED_EOF'
SET @a = REPEAT('a',64), @b = REPEAT('b',64), @c = REPEAT('c',64), @d = REPEAT('d',64);
INSERT INTO store(store_id,name) VALUES (91,'Synthetic migration store');
INSERT INTO app_user(user_id,role_id,email,name,status) SELECT 91,role_id,'synthetic91@example.test','Test','ACTIVE' FROM role WHERE code='SYSTEM_ADMIN';
INSERT INTO utility_snapshot(snapshot_id,store_id,created_by,range_start,range_end,as_of_date,config_snapshot,source_hash,matrix_hash,total_weighted_utility,rounding_scale,status) VALUES (91,91,91,'2026-01-01','2026-01-01','2026-01-01','{}',@a,@b,0,6,'READY');
INSERT INTO mining_request(request_id,store_id,requested_by,utility_snapshot_id,request_key,payload_hash,algorithms,min_util_mode,min_util_value,min_util_spmf,input_snapshot_hash,frozen_input,utility_config_snapshot,status,comparison_status) VALUES (91,91,91,91,'00000000-0000-0000-0000-000000000091',@c,'EFIM','ABSOLUTE',1,1,@d,'{}','{}','COMPLETED','NOT_REQUESTED');
INSERT INTO mining_run(store_id,request_id,algorithm,library_version,conversion_version,memory_method,runtime_method,status) VALUES (91,91,'EFIM','spmf-2.67a','v1','jvm','wall','COMPLETED');
SEED_EOF
}

# --------------------------------------------------------------------------------- candidate / dev trees
make_candidate() { # builds working tree + any origin/dev migration files that the branch lacks
  local dst="$1"; mkdir -p "$dst"
  ( cd "$REPO" && tar --exclude=./target --exclude=./.git -cf - . ) | ( cd "$dst" && tar -xf - )
  local f missing=0 conflict=0
  for f in $(git -C "$REPO" ls-tree -r --name-only origin/dev -- src/main/resources/db/migration src/main/java/db/migration); do
    if [ ! -e "$dst/$f" ]; then mkdir -p "$(dirname "$dst/$f")"; git -C "$REPO" show "origin/dev:$f" > "$dst/$f"; missing=$((missing+1)); echo "overlay $f" >> "$EVID/candidate-overlay.txt"
    elif ! git -C "$REPO" show "origin/dev:$f" | cmp -s - "$dst/$f"; then conflict=$((conflict+1)); echo "DIFFERS $f" >> "$EVID/candidate-overlay.txt"; fi
  done
  echo "overlaid=$missing differing=$conflict" > "$EVID/candidate-overlay-summary.txt"
  [ "$conflict" -eq 0 ]
}

flyway_stages() {
  need "flyway" java17 maven docker mysql python3 || { for x in flyway.fresh flyway.upgrade flyway.rerun flyway.v11 jpa.validate openapi.export; do record "$x" BLOCKED "prerequisites missing (see flyway/pre.* lines)"; done; return 1; }
  git -C "$REPO" rev-parse --verify -q origin/dev >/dev/null || { for x in flyway.fresh flyway.upgrade flyway.rerun flyway.v11 jpa.validate openapi.export; do record "$x" BLOCKED "origin/dev missing locally"; done; return 1; }
  TMPWORK="$(mktemp -d)"; mkdir -p "$TMPWORK/avatars"
  local cand="$TMPWORK/candidate" dev="$TMPWORK/origin-dev" out="$EVID/flyway"; mkdir -p "$out"

  if ! make_candidate "$cand"; then
    record "flyway.fresh" FAIL "a migration file exists on both branches with DIFFERENT content (see $EVID/candidate-overlay.txt); resolve before verifying"
    for x in flyway.upgrade flyway.rerun flyway.v11 jpa.validate openapi.export; do record "$x" BLOCKED "candidate tree conflicts with origin/dev"; done; return 1
  fi
  mkdir -p "$dev"; git -C "$REPO" archive origin/dev | tar -xf - -C "$dev"
  local jc="$out/candidate-build.log" jd="$out/dev-build.log"
  log "[flyway] candidate = working tree + origin/dev migration files the branch lacks; built with mvn -DskipTests package (JAVA_HOME=$J17); dev = git archive origin/dev, same build; apps started with java -jar against disposable databases on 127.0.0.1:$PORT"
  ( cd "$cand" && JAVA_HOME="$J17" PATH="$J17/bin:$PATH" "$MVN" --batch-mode --no-transfer-progress -DskipTests package ) > "$jc" 2>&1 \
    || { record "flyway.fresh" FAIL "candidate jar did not build; see $jc"; for x in flyway.upgrade flyway.rerun flyway.v11 jpa.validate openapi.export; do record "$x" BLOCKED "no candidate jar"; done; return 1; }
  ( cd "$dev" && JAVA_HOME="$J17" PATH="$J17/bin:$PATH" "$MVN" --batch-mode --no-transfer-progress -DskipTests package ) > "$jd" 2>&1 \
    && DEV_OK=1 || DEV_OK=0
  local cjar djar; cjar="$(ls "$cand"/target/paspm-backend-*.jar 2>/dev/null | grep -v '\.original$' | head -1)"; djar="$(ls "$dev"/target/paspm-backend-*.jar 2>/dev/null | grep -v '\.original$' | head -1)"
  local fresh="paspm_fresh_$(date -u +%H%M%S)" up="paspm_upgrade_$(date -u +%H%M%S)"

  # A. fresh install -----------------------------------------------------------------------------------
  sql -e "CREATE DATABASE $fresh" 2>>"$out/sql-errors.log"
  if start_app "$cjar" "$fresh" "$out/fresh-start1.log" 18084; then
    cp "$TMPWORK/api-docs.json" "$EVID/openapi.runtime.json"
    record "jpa.validate" PASS "application started with ddl-auto=validate against the fresh Flyway schema (no schema-validation exception in $out/fresh-start1.log)"
    record "openapi.export" PASS "runtime OpenAPI saved to $EVID/openapi.runtime.json (compare with docs/api/openapi.json, then replace it)"
  else
    record "jpa.validate" FAIL "application did not become ready; see $out/fresh-start1.log"; record "openapi.export" BLOCKED "application did not start"
  fi
  stop_app; history "$fresh" "$out/fresh-history-1.tsv"
  local bad nver; bad="$(awk -F'\t' 'NR>1 && $6!=1' "$out/fresh-history-1.tsv" | wc -l | tr -d ' ')"
  nver="$(awk -F'\t' 'NR>1 && $2!="-"{print $2}' "$out/fresh-history-1.tsv" | sort -n | uniq | tr '\n' ' ')"
  local dup; dup="$(awk -F'\t' 'NR>1 && $2!="-"{print $2}' "$out/fresh-history-1.tsv" | sort | uniq -d | tr '\n' ' ')"
  local order_ok; order_ok="$(awk -F'\t' 'NR>1 && $2!="-"{v=$2+0; if(v<last){bad=1}; last=v} END{print bad?"NO":"YES"}' "$out/fresh-history-1.tsv")"
  if [ "$bad" = 0 ] && [ -z "$dup" ] && [ "$order_ok" = YES ] && [ "$nver" = "1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 " ]; then record "flyway.fresh" PASS "versions 1-19 each applied once, in ascending order, success=1 for every row ($out/fresh-history-1.tsv)"
  else record "flyway.fresh" FAIL "failed_rows=$bad duplicates='$dup' ascending=$order_ok versions='$nver' ($out/fresh-history-1.tsv)"; fi
  if awk -F'\t' 'NR>1 && $2=="11" && $4=="JDBC" && $6==1{f=1} END{exit f?0:1}' "$out/fresh-history-1.tsv"; then record "flyway.v11" PASS "Java migration V11 (type JDBC) executed successfully on the fresh database"; else record "flyway.v11" FAIL "V11 Java migration missing or failed in $out/fresh-history-1.tsv"; fi

  # repeated startup: no migration is run twice, no checksum drift
  if start_app "$cjar" "$fresh" "$out/fresh-start2.log" 18084; then
    stop_app; history "$fresh" "$out/fresh-history-2.tsv"
    if cmp -s "$out/fresh-history-1.tsv" "$out/fresh-history-2.tsv" && ! grep -qiE "Migrating schema|checksum mismatch|Validate failed" "$out/fresh-start2.log"; then record "flyway.rerun" PASS "second start left flyway_schema_history byte-identical and logged no migration or validation error"
    else record "flyway.rerun" FAIL "history changed or log contains migration/validation messages ($out/fresh-start2.log)"; fi
  else stop_app; record "flyway.rerun" FAIL "second start failed; see $out/fresh-start2.log"; fi

  # B. upgrade from the exact origin/dev schema ---------------------------------------------------------
  if [ "$DEV_OK" != 1 ] || [ -z "$djar" ]; then
    record "flyway.upgrade" BLOCKED "origin/dev jar did not build; see $jd"
  else
    sql -e "CREATE DATABASE $up" 2>>"$out/sql-errors.log"
    if start_app "$djar" "$up" "$out/upgrade-dev-start.log" 18085; then
      stop_app; history "$up" "$out/upgrade-history-dev.tsv"
      if seed_dev_data "$up" 2>>"$out/sql-errors.log"; then
        data_fingerprint "$up" > "$out/upgrade-data-before.txt"
        if start_app "$cjar" "$up" "$out/upgrade-candidate-start.log" 18084; then
          stop_app; history "$up" "$out/upgrade-history-after.tsv"; data_fingerprint "$up" > "$out/upgrade-data-after.txt"
          local prefix_ok data_ok bad2 newv
          prefix_ok="NO"; head -n "$(wc -l < "$out/upgrade-history-dev.tsv")" "$out/upgrade-history-after.tsv" | cmp -s - "$out/upgrade-history-dev.tsv" && prefix_ok="YES"
          cmp -s "$out/upgrade-data-before.txt" "$out/upgrade-data-after.txt" && data_ok="YES" || data_ok="NO"
          bad2="$(awk -F'\t' 'NR>1 && $6!=1' "$out/upgrade-history-after.tsv" | wc -l | tr -d ' ')"
          newv="$(awk -F'\t' 'NR>1 && $2!="-"{print $2}' "$out/upgrade-history-after.tsv" | tail -n 7 | tr '\n' ' ')"
          if [ "$prefix_ok" = YES ] && [ "$data_ok" = YES ] && [ "$bad2" = 0 ] && [ "$newv" = "13 14 15 16 17 18 19 " ]; then record "flyway.upgrade" PASS "origin/dev schema (V1-V12) + seeded rows upgraded by V13-V19: earlier history rows and checksums unchanged, seeded data fingerprints identical, no failed rows"
          else record "flyway.upgrade" FAIL "prefix_unchanged=$prefix_ok data_unchanged=$data_ok failed_rows=$bad2 last7='$newv' (see $out/upgrade-*)"; fi
        else stop_app; record "flyway.upgrade" FAIL "candidate failed to start on the dev schema; see $out/upgrade-candidate-start.log"; fi
      else record "flyway.upgrade" FAIL "seeding representative data failed; see $out/sql-errors.log"; fi
    else stop_app; record "flyway.upgrade" BLOCKED "origin/dev jar did not start on a fresh database; see $out/upgrade-dev-start.log"; fi
  fi
}

inspect_local_history() {
  echo "== Read-only inspection of a LOCAL Flyway history (SELECT only) =="
  local host="${PASPM_INSPECT_HOST:-127.0.0.1}" port="${PASPM_INSPECT_PORT:-3306}" user="${PASPM_INSPECT_USER:-}" db="${PASPM_INSPECT_DB:-paspm}"
  [ -z "$user" ] && { echo "Set PASPM_INSPECT_USER (and MYSQL_PWD in the environment, never on the command line)." ; exit 64; }
  local q="SELECT installed_rank, IFNULL(version,'-') AS version, description, success FROM flyway_schema_history ORDER BY installed_rank"
  local res
  if command -v mysql >/dev/null 2>&1; then res="$(mysql --no-defaults -h "$host" -P "$port" -u "$user" -D "$db" --batch -e "$q" 2>&1)"
  elif command -v docker >/dev/null 2>&1; then
    [ "$host" = "127.0.0.1" ] && host="host.docker.internal"
    res="$(docker run --rm -e MYSQL_PWD "$MYSQL_IMAGE" mysql --no-defaults -h "$host" -P "$port" -u "$user" -D "$db" --batch -e "$q" 2>&1)"
  else echo "BLOCKED: neither mysql nor docker available"; exit 2; fi
  printf '%s\n' "$res" | tee "$EVID/local-history.tsv"
  if printf '%s\n' "$res" | awk -F'\t' '$2>=12 && $2<=16 && tolower($3) ~ /mining|scheduled|lease|recovery|occurrence/{f=1} END{exit f?0:1}'; then
    echo; echo "FINDING: this database already applied OLD F05 mining migrations as V12-V16. Do NOT repair or delete automatically."
    echo "Back up first (mysqldump), then recreate the LOCAL database yourself or restore from backup; see docs/setup/LOCAL_SETUP_TROUBLESHOOTING.md."
  else echo; echo "No old F05 V12-V16 migrations detected in this history."; fi
}

# ------------------------------------------------------------------------------------------------- main
echo "PASPM Option B verification  |  mode=$MODE  |  evidence=$EVID"
if [ "$MODE" = inspect ]; then inspect_local_history; exit 0; fi

preflight
if [ "$MODE" = preflight ]; then
  for id in mvn.verify test.flag test.mysql_group test.full_optin flyway.fresh flyway.upgrade flyway.rerun flyway.v11 jpa.validate openapi.export; do record "$id" "NOT RUN" "preflight only; re-run with --run"; done
else
  if [ "$(uname -s)" != Darwin ] && [ "${PASPM_VERIFY_ALLOW_NON_MACOS:-0}" != 1 ]; then
    for id in mvn.verify test.flag test.mysql_group test.full_optin flyway.fresh flyway.upgrade flyway.rerun flyway.v11 jpa.validate openapi.export; do record "$id" BLOCKED "not macOS"; done
  else
    # S1/S2: no database needed
    # SPMF suites need the Java 25 child and the jar; without them they return 503 (SPMF_JAVA_VERSION_UNSUPPORTED / SPMF_JAR_NOT_FOUND).
    SPMF_ARGS=(); [ -n "$J25" ] && SPMF_ARGS+=("-Dpaspm.test.spmf-java=$J25"); [ -n "$SPMF_JAR" ] && SPMF_ARGS+=("-Dpaspm.test.spmf-jar=$SPMF_JAR")
    # ${arr[@]+...} keeps an empty array safe under `set -u` on macOS bash 3.2
    if need "mvn.verify" java17 maven python3; then mvn_stage "mvn.verify" "default Maven verify (H2 suites)" "$EVID/mvn-verify" clean verify ${SPMF_ARGS[@]+"${SPMF_ARGS[@]}"} -Dlogging.level.root=ERROR; fi
    if need "test.flag" java17 maven python3; then mvn_stage "test.flag" "dispatcher flag + migration versioning tests" "$EVID/flag-test" test -Dtest=MiningOccurrenceDispatcherFlagTest,MigrationVersioningTest -Dsurefire.failIfNoSpecifiedTests=false -Dlogging.level.root=ERROR; fi
    # disposable MySQL for the rest
    start_mysql
    if [ -n "$PORT" ]; then
      export PASPM_TEST_MYSQL_SERVER="jdbc:mysql://127.0.0.1:$PORT/" PASPM_TEST_MYSQL_USER=root PASPM_TEST_MYSQL_PASSWORD="" PASPM_CI_DISPOSABLE_MYSQL=true
      # The CI helper runs the mysql CLI in a container on the host network; make sure that works before using it.
      WRAP="$(mktemp -d)"; mkdir -p "$WRAP/bin" "$WRAP/tmp"; export RUNNER_TEMP="$WRAP"
      # Executable copies live in the throwaway $WRAP: chmod on the repo files would leave tracked mode changes (644 -> 755) behind.
      mkdir -p "$WRAP/helpers"; cp "$REPO/.github/scripts/mysql-client.sh" "$REPO/.github/scripts/provision-restore.py" "$WRAP/helpers/"
      chmod +x "$WRAP/helpers/mysql-client.sh" "$WRAP/helpers/provision-restore.py"
      ln -s "$WRAP/helpers/mysql-client.sh" "$WRAP/bin/mysql"; ln -s "$WRAP/helpers/mysql-client.sh" "$WRAP/bin/mysqldump"
      export PASPM_TEST_RESTORE_PROVISIONER="$WRAP/helpers/provision-restore.py"
      if docker run --rm --network host -e MYSQL_PWD="" "$MYSQL_IMAGE" mysqladmin ping -h127.0.0.1 -P"$PORT" -uroot --silent >/dev/null 2>&1; then WRAP_OK=1; else WRAP_OK=0; fi
      if [ "$WRAP_OK" = 1 ] && need "test.mysql_group" java17 maven python3 mysql; then
        mvn_stage "test.mysql_group" "CI 'mysql' test group with -Dpaspm.test.mysql=true" "$EVID/mysql-group" clean verify -Dtest="$(python3 "$REPO/.github/scripts/test-groups.py" mysql)" -DargLine="-Djava.io.tmpdir=$WRAP/tmp" -Dpaspm.test.mysql=true -Dlogging.level.root=ERROR
      elif [ "$WRAP_OK" != 1 ]; then record "test.mysql_group" BLOCKED "Docker host networking unavailable (the CI mysql CLI wrapper needs it); enable it in Docker Desktop > Settings > Resources > Network, or run this group on Linux/CI"; fi
      if [ "$WRAP_OK" != 1 ]; then
        record "test.full_optin" BLOCKED "Docker host networking unavailable (see test.mysql_group)"
      elif need "test.full_optin" java17 java25 spmf maven python3 mysql; then
        export PASPM_MINING_SPMF_JAR="$SPMF_JAR" PASPM_MINING_JAVA_EXECUTABLE="$J25"
        mvn_stage "test.full_optin" "full opt-in verify (MySQL + SPMF + Java 17 backend)" "$EVID/full-optin" clean verify -DargLine="-Djava.io.tmpdir=$WRAP/tmp" -Dpaspm.test.mysql=true -Dpaspm.test.java17-backend=true -Dpaspm.test.java17="$J17/bin/java" -Dpaspm.test.spmf-jar="$SPMF_JAR" -Dpaspm.test.spmf-java="$J25" -Dpaspm.avatar.directory="$WRAP/avatars" -Dlogging.level.root=ERROR
      fi
      rm -rf "$WRAP"
      flyway_stages
    else
      for id in test.mysql_group test.full_optin flyway.fresh flyway.upgrade flyway.rerun flyway.v11 jpa.validate openapi.export; do record "$id" BLOCKED "no disposable MySQL 8.4"; done
    fi
  fi
fi

echo; echo "== Summary ($SUMMARY) =="
awk -F'\t' '{c[$2]++} END{for(k in c) printf "%s=%d  ", k, c[k]; print ""}' "$SUMMARY"
echo "Commands run: $EVID/commands.log   Evidence: $EVID"
if grep -q "	FAIL	" "$SUMMARY"; then exit 1; fi
if [ "$MODE" = run ] && grep -q "	BLOCKED	" "$SUMMARY"; then exit 2; fi
exit 0

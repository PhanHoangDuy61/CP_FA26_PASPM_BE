#!/usr/bin/env bash
# F05 Scheduled Mining - native Linux acceptance runner.
#
# Purpose: produce NATIVE_LINUX_VERIFIED evidence for the exact checked-out Backend commit on an approved Linux host or CI runner.
# It never installs anything, never touches an existing database (tests create new disposable schemas), never prints secrets and
# refuses to call a run "native" unless the environment proves it (see preflight). Docker-on-macOS and QEMU-emulated containers are
# classified NOT_NATIVE even if every test passes.
#
#   scripts/verify-native-linux.sh --preflight            # environment checks only (safe anywhere)
#   scripts/verify-native-linux.sh --run [--natural-lease] [--legacy-jar PATH] [--out DIR]
#
# Required environment (values are never logged):
#   JAVA_HOME                      JDK 17 (Backend build and runtime)
#   PASPM_MINING_JAVA_EXECUTABLE   Java 25+ executable for the SPMF child
#   PASPM_MINING_SPMF_JAR          approved SPMF 2.67a jar (SHA-256 pinned below; any other checksum is refused)
#   PASPM_TEST_MYSQL_SERVER        jdbc:mysql://127.0.0.1:<port>/   (empty local MySQL 8.4 server)
#   PASPM_TEST_MYSQL_USER / PASPM_TEST_MYSQL_PASSWORD   account allowed to CREATE DATABASE and CREATE TRIGGER (default root / empty)
# Also needed: python3 with ctypes (parent-death launcher), git, maven, a NON-ROOT user.
set -uo pipefail

PINNED_SPMF_SHA256=4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a
MODE=""; NATURAL=false; LEGACY_JAR="${PASPM_LEGACY_JAR:-}"; OUT=""
while [ $# -gt 0 ]; do
  case "$1" in
    --preflight) MODE=preflight ;; --run) MODE=run ;; --natural-lease) NATURAL=true ;;
    --legacy-jar) LEGACY_JAR="$2"; shift ;; --out) OUT="$2"; shift ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac; shift
done
[ -n "$MODE" ] || { sed -n 2,22p "$0"; exit 2; }
REPO="$(cd "$(dirname "$0")/.." && pwd)"
COMMIT="$(git -C "$REPO" rev-parse HEAD 2>/dev/null || echo unknown)"
DIRTY="$(git -C "$REPO" status --porcelain 2>/dev/null | grep -c . || true)"
OUT="${OUT:-$REPO/target/native-linux-evidence}"; mkdir -p "$OUT"

FAILS=(); NOTES=()
check() { # check <name> <condition-exit-status> <detail>
  if [ "$2" -eq 0 ]; then echo "  PASS  $1  $3"; else echo "  FAIL  $1  $3"; FAILS+=("$1"); fi
}

echo "== preflight (commit $COMMIT, uncommitted files: $DIRTY)"
OS="$(uname -s)"; KERNEL="$(uname -r)"; ARCH="$(uname -m)"; UID_NOW="$(id -u)"
check "operating system is Linux" $([ "$OS" = Linux ]; echo $?) "$OS $KERNEL $ARCH"
NATIVE=true
if [ "$OS" = Linux ]; then
  case "$KERNEL" in
    *linuxkit*|*microsoft*|*WSL*) NATIVE=false; NOTES+=("kernel '$KERNEL' belongs to a Docker Desktop / WSL virtual machine") ;;
  esac
  # QEMU user-mode emulation (for example linux/amd64 containers on Apple Silicon): the claimed architecture and the CPU do not match.
  if [ "$ARCH" = x86_64 ] && ! grep -qiE 'GenuineIntel|AuthenticAMD|HygonGenuine|Zhaoxin|CentaurHauls' /proc/cpuinfo 2>/dev/null; then NATIVE=false; NOTES+=("x86_64 userland without an x86 CPU in /proc/cpuinfo: instruction emulation"); fi
  if [ "$ARCH" = aarch64 ] && ! grep -qi 'CPU implementer' /proc/cpuinfo 2>/dev/null; then NATIVE=false; NOTES+=("aarch64 userland without an Arm CPU in /proc/cpuinfo: instruction emulation"); fi
  if grep -qE 'qemu|rosetta' /proc/self/maps 2>/dev/null || ls /proc/sys/fs/binfmt_misc/ 2>/dev/null | grep -qiE 'qemu|rosetta'; then
    # binfmt registrations alone are harmless on a native host; they matter only when this very process is translated.
    grep -qE 'qemu|rosetta' /proc/self/maps 2>/dev/null && { NATIVE=false; NOTES+=("this process is mapped through an emulator"); }
  fi
else NATIVE=false; NOTES+=("not a Linux kernel"); fi
check "kernel and CPU are native (no VM kernel, no instruction emulation)" $([ "$NATIVE" = true ]; echo $?) "${NOTES[*]:-ok}"
check "running as a non-root user" $([ "$UID_NOW" -ne 0 ]; echo $?) "uid=$UID_NOW"
check "no uncommitted changes (evidence names one exact commit)" $([ "$DIRTY" -eq 0 ] && [ "$COMMIT" != unknown ]; echo $?) "commit=$COMMIT"
JAVA17="${JAVA_HOME:+$JAVA_HOME/bin/java}"; JAVA17="${JAVA17:-java}"
V17="$("$JAVA17" -version 2>&1 | head -1)"; check "Backend runtime is Java 17" $(echo "$V17" | grep -q '"17\.' ; echo $?) "$V17"
J25="${PASPM_MINING_JAVA_EXECUTABLE:-}"; V25="$([ -x "$J25" ] && "$J25" -version 2>&1 | head -1)"
check "SPMF child runtime is Java 25+" $(echo "$V25" | grep -qE '"(2[5-9]|[3-9][0-9])[.\"]'; echo $?) "${V25:-not configured}"
JAR="${PASPM_MINING_SPMF_JAR:-}"; SUM="$([ -f "$JAR" ] && sha256sum "$JAR" | cut -d' ' -f1)"
check "SPMF 2.67a jar has the approved SHA-256" $([ "$SUM" = "$PINNED_SPMF_SHA256" ]; echo $?) "sha256=${SUM:-missing}"
python3 -c 'import ctypes, ctypes.util; ctypes.CDLL(ctypes.util.find_library("c"), use_errno=True).prctl' >/dev/null 2>&1; check "python3 ctypes can reach prctl (parent-death launcher)" $? ""
SERVER="${PASPM_TEST_MYSQL_SERVER:-}"; MYSQL_VER=unknown
check "MySQL server URL is a local jdbc:mysql URL" $(echo "$SERVER" | grep -qE '^jdbc:mysql://(127\.0\.0\.1|localhost):[0-9]+/$'; echo $?) "${SERVER:-not configured}"
if command -v mysql >/dev/null 2>&1 && echo "$SERVER" | grep -qE '^jdbc:mysql://'; then
  HP="$(echo "$SERVER" | sed -E 's#jdbc:mysql://([^:/]+):([0-9]+)/#\1 \2#')"; set -- $HP
  MYSQL_VER="$(MYSQL_PWD="${PASPM_TEST_MYSQL_PASSWORD:-}" mysql -h "$1" -P "$2" -u "${PASPM_TEST_MYSQL_USER:-root}" -N -e 'SELECT VERSION()' 2>/dev/null || echo unknown)"
fi
check "MySQL is 8.4" $(echo "$MYSQL_VER" | grep -q '^8\.4\.'; echo $?) "$MYSQL_VER"

VERDICT_PREFLIGHT=$([ ${#FAILS[@]} -eq 0 ] && echo OK || echo "FAILED:${FAILS[*]// /_}")
{ echo "{"; echo "  \"commit\": \"$COMMIT\", \"uncommittedFiles\": $DIRTY, \"os\": \"$OS\", \"kernel\": \"$KERNEL\", \"arch\": \"$ARCH\", \"uid\": $UID_NOW,"
  echo "  \"native\": $NATIVE, \"nativeNotes\": \"${NOTES[*]:-}\", \"java17\": \"$V17\", \"java25\": \"$V25\", \"spmfSha256\": \"${SUM:-}\", \"mysql\": \"$MYSQL_VER\","
  echo "  \"preflight\": \"$VERDICT_PREFLIGHT\""; echo "}"; } > "$OUT/preflight.json"
echo "preflight: $VERDICT_PREFLIGHT   (details: $OUT/preflight.json)"
if [ "$MODE" = preflight ]; then
  [ "$NATIVE" = true ] && [ ${#FAILS[@]} -eq 0 ] && { echo "READY for --run"; exit 0; }
  echo "NATIVE_LINUX_NOT_RUN: this environment cannot produce native Linux evidence"; exit 1
fi
[ ${#FAILS[@]} -eq 0 ] || { echo "NATIVE_LINUX_NOT_RUN: fix the preflight failures first"; exit 1; }

echo "== run (this takes a while; the natural-lease tests add about 14 minutes)"
export PASPM_MULTIJVM_EVIDENCE_FILE="$OUT/multijvm-evidence.txt"; : > "$PASPM_MULTIJVM_EVIDENCE_FILE"
export PASPM_TEST_REQUIRE_REAP=false   # a real host has no container init; kernel parent-death is asserted inside the tests
TESTS="MiningMultiJvmMySqlTest,MiningSchedulerMultiJvmMySqlTest,MiningSchedulerUpgradeCompatibilityMySqlTest,MiningScheduledActivationE2EMySqlTest,MiningScheduledDatabaseOutageMySqlTest,MiningMigrationMySqlTest,LinuxParentDeathLauncherTest,SpmfRuntimeTest,SpmfMiningEngineTest,MiningDispatchLeaseMySqlTest,MiningScheduleReservationMySqlTest,MiningMySqlIntegrationTest"
ARGS=(-o -B --no-transfer-progress clean test -Dpaspm.test.mysql=true -Dpaspm.test.multijvm=true "-Dtest=$TESTS" "-Dpaspm.test.spmf-java=$J25" "-Dpaspm.test.spmf-jar=$JAR" -Dlogging.level.root=ERROR)
[ "$NATURAL" = true ] && ARGS+=(-Dpaspm.test.natural-lease=true)
[ -n "$LEGACY_JAR" ] && ARGS+=("-Dpaspm.test.legacy-jar=$LEGACY_JAR")
( cd "$REPO" && mvn "${ARGS[@]}" ) > "$OUT/maven.log" 2>&1; MVN=$?
rm -rf "$OUT/surefire"; cp -R "$REPO/target/surefire-reports" "$OUT/surefire" 2>/dev/null
python3 - "$OUT" "$MVN" "$NATIVE" "$COMMIT" "$NATURAL" "$LEGACY_JAR" <<'PY'
import sys, glob, json, xml.etree.ElementTree as ET
out, mvn, native, commit, natural, legacy = sys.argv[1:7]
t = f = e = s = 0; skipped = []
for p in glob.glob(out + "/surefire/TEST-*.xml"):
    r = ET.parse(p).getroot()
    for case in r.iter("testcase"):
        t += 1
        if case.find("failure") is not None: f += 1
        elif case.find("error") is not None: e += 1
        elif case.find("skipped") is not None: s += 1; skipped.append(r.get("name").split(".")[-1] + "#" + case.get("name"))
ok = mvn == "0" and f == 0 and e == 0 and t > 0
verdict = "NATIVE_LINUX_VERIFIED" if ok and native == "true" else "NATIVE_LINUX_NOT_RUN" if native != "true" else "BLOCKED"
res = {"commit": commit, "testsRun": t, "failures": f, "errors": e, "skipped": s, "skippedTests": skipped, "mavenExit": int(mvn), "naturalLeaseIncluded": natural == "true", "legacyJarIncluded": bool(legacy), "verdict": verdict}
json.dump(res, open(out + "/result.json", "w"), indent=2); print(json.dumps(res, indent=2))
PY
echo "evidence directory: $OUT (preflight.json, result.json, multijvm-evidence.txt, maven.log, surefire/)"

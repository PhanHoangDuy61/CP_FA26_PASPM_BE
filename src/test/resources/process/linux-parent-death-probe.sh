#!/bin/sh
set -eu
mkdir -p /tmp/pdeath-classes
rm -f /tmp/pdeath-child
javac -d /tmp/pdeath-classes /work/src/main/java/vn/edu/fpt/paspm/mining/LinuxParentDeathLauncher.java /work/src/test/resources/process/LinuxParentDeathProbe.java
java -cp /tmp/pdeath-classes vn.edu.fpt.paspm.mining.LinuxParentDeathProbe /tmp/unused mismatch
PATH=/nonexistent /usr/lib/jvm/java-17-openjdk-amd64/bin/java -cp /tmp/pdeath-classes vn.edu.fpt.paspm.mining.LinuxParentDeathProbe /tmp/unused missing-python
java -cp /tmp/pdeath-classes vn.edu.fpt.paspm.mining.LinuxParentDeathProbe /tmp/pdeath-child &
parent=$!
sleep 0.1
n=0
while [ ! -s /tmp/pdeath-child ]; do
  n=$((n+1)); [ "$n" -lt 200 ] || { echo CHILD_START_TIMEOUT; kill -KILL "$parent" || true; exit 1; }
  sleep 0.05
done
child=$(cat /tmp/pdeath-child)
sleep 120 &
unrelated=$!
sleep 1
kill -STOP "$child"
kill -KILL "$parent"
wait "$parent" 2>/dev/null || true
n=0
while kill -0 "$child" 2>/dev/null && [ "$(awk '{print $3}' "/proc/$child/stat" 2>/dev/null || true)" != Z ]; do
  n=$((n+1)); [ "$n" -lt 100 ] || { echo CHILD_SURVIVED; kill -KILL "$child" "$unrelated" 2>/dev/null || true; exit 1; }
  sleep 0.05
done
if [ "${PASPM_TEST_REQUIRE_REAP:-false}" = true ]; then
  n=0
  while [ -e "/proc/$child" ]; do
    n=$((n+1)); [ "$n" -lt 100 ] || { echo CHILD_NOT_REAPED; kill -KILL "$unrelated" 2>/dev/null || true; exit 1; }
    sleep 0.05
  done
  echo CHILD_REAPED_BY_INIT
fi
kill -0 "$unrelated"
kill -KILL "$unrelated" 2>/dev/null || true
wait "$unrelated" 2>/dev/null || true
echo "LINUX_PDEATH_PROBE parent=$parent child=$child unrelated=$unrelated child_terminated=true unrelated_survived=true"

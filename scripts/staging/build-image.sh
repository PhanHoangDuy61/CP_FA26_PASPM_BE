#!/usr/bin/env bash
# Build the backend JAR (JDK 17) and the Docker image used by compose.backend.yml.
#
#   JAVA_HOME=<JDK 17>  SPMF_JAR_PATH=/path/to/spmf.jar  scripts/staging/build-image.sh <image-tag> [--skip-jar]
#
# Environment (optional): PLATFORM=linux/amd64|linux/arm64 (default linux/amd64, the deployment platform),
#   JAVA25_IMAGE (default eclipse-temurin:25-jre-jammy; its JRE becomes the separate Java 25 SPMF runtime),
#   WORK_DIR (default target/staging-build), SKIP_SMOKE=1 (skip the run of `spmf.jar version` inside the image).
# The Dockerfile itself verifies the SPMF SHA-256; this script verifies it first so a wrong file fails early.
# SPMF is GPL v3: the image carries its license, SOURCE.md and THIRD_PARTY_NOTICES.md in /opt/paspm/licenses (DEC-20261125-01).
# On a host of another architecture (for example an arm64 Mac) the RUN steps execute under emulation (QEMU/Rosetta).
set -euo pipefail
TAG="${1:?usage: build-image.sh <image-tag> [--skip-jar]}"
SKIP_JAR="${2:-}"
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO"
PINNED=4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a
: "${SPMF_JAR_PATH:?set SPMF_JAR_PATH to the approved spmf.jar}"
sha() { if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }
[ "$(sha "$SPMF_JAR_PATH")" = "$PINNED" ] || { echo "SPMF jar SHA-256 does not match the approved pin" >&2; exit 1; }
PLATFORM="${PLATFORM:-linux/amd64}"
case "$PLATFORM" in linux/amd64|linux/arm64) ;; *) echo "PLATFORM must be linux/amd64 or linux/arm64" >&2; exit 1;; esac
EXPECTED_ARCH="${PLATFORM#linux/}"
JAVA25_IMAGE="${JAVA25_IMAGE:-eclipse-temurin:25-jre-jammy}"
WORK_DIR="${WORK_DIR:-$REPO/target/staging-build}"
[ -f third_party/spmf/LICENSE-GPL-3.0.txt ] && [ -f third_party/spmf/SOURCE.md ] && [ -f THIRD_PARTY_NOTICES.md ] \
  || { echo "SPMF license, SOURCE.md or THIRD_PARTY_NOTICES.md is missing" >&2; exit 1; }

if [ "$SKIP_JAR" != "--skip-jar" ]; then
  : "${JAVA_HOME:?set JAVA_HOME to a JDK 17}"
  "$JAVA_HOME/bin/java" -version 2>&1 | head -1 | grep -q '"17\.' || { echo "JAVA_HOME is not JDK 17" >&2; exit 1; }
  mvn --batch-mode --no-transfer-progress -DskipTests package
fi
[ -f target/paspm-backend-0.1.0.jar ] || { echo "target/paspm-backend-0.1.0.jar missing" >&2; exit 1; }

rm -rf "$WORK_DIR"; mkdir -p "$WORK_DIR/java25" "$WORK_DIR/spmf"
cp "$SPMF_JAR_PATH" "$WORK_DIR/spmf/spmf.jar"
cid="$(docker create --platform "$PLATFORM" "$JAVA25_IMAGE")"
trap 'docker rm -f "$cid" >/dev/null 2>&1 || true' EXIT
docker cp "$cid:/opt/java/openjdk/." "$WORK_DIR/java25/"
test -x "$WORK_DIR/java25/bin/java"

docker buildx build --platform "$PLATFORM" --load \
  --build-context java25="$WORK_DIR/java25" --build-context spmf="$WORK_DIR/spmf" \
  -t "paspm-backend:$TAG" .
ARCH="$(docker image inspect "paspm-backend:$TAG" --format '{{.Architecture}}')"
[ "$ARCH" = "$EXPECTED_ARCH" ] || { echo "image architecture is $ARCH, expected $EXPECTED_ARCH" >&2; exit 1; }
echo "built paspm-backend:$TAG ($ARCH)"

# The GPL files must sit next to the jar, and the jar must still be the approved 2.67a.
docker run --rm --platform "$PLATFORM" --entrypoint ls "paspm-backend:$TAG" -R /opt/paspm/licenses
if [ "${SKIP_SMOKE:-}" != "1" ]; then
  docker run --rm --platform "$PLATFORM" --entrypoint /opt/paspm/java25/bin/java "paspm-backend:$TAG" \
    -Djava.awt.headless=true -jar /opt/paspm/spmf.jar version | grep -q -- '-- SPMF version 2.67a --' \
    || { echo "SPMF banner check failed inside the image" >&2; exit 1; }
  echo "SPMF 2.67a banner verified inside the image"
fi

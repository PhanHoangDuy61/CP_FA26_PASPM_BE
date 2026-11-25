#!/usr/bin/env bash
# Build the backend JAR (JDK 17) and the Docker image used by compose.backend.yml.
#
#   JAVA_HOME=<JDK 17>  SPMF_JAR_PATH=/path/to/spmf.jar  scripts/staging/build-image.sh <image-tag> [--skip-jar]
#
# Environment (optional): PLATFORM=linux/amd64|linux/arm64 (default: the Docker host's platform),
#   JAVA25_IMAGE (default eclipse-temurin:25-jre-jammy; its JRE becomes the separate Java 25 SPMF runtime),
#   WORK_DIR (default target/staging-build).
# The Dockerfile itself verifies the SPMF SHA-256; this script verifies it first so a wrong file fails early.
set -euo pipefail
TAG="${1:?usage: build-image.sh <image-tag> [--skip-jar]}"
SKIP_JAR="${2:-}"
REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO"
PINNED=4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a
: "${SPMF_JAR_PATH:?set SPMF_JAR_PATH to the approved spmf.jar}"
sha() { if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }
[ "$(sha "$SPMF_JAR_PATH")" = "$PINNED" ] || { echo "SPMF jar SHA-256 does not match the approved pin" >&2; exit 1; }
PLATFORM_ARG=(); [ -n "${PLATFORM:-}" ] && PLATFORM_ARG=(--platform "$PLATFORM")
JAVA25_IMAGE="${JAVA25_IMAGE:-eclipse-temurin:25-jre-jammy}"
WORK_DIR="${WORK_DIR:-$REPO/target/staging-build}"

if [ "$SKIP_JAR" != "--skip-jar" ]; then
  : "${JAVA_HOME:?set JAVA_HOME to a JDK 17}"
  "$JAVA_HOME/bin/java" -version 2>&1 | head -1 | grep -q '"17\.' || { echo "JAVA_HOME is not JDK 17" >&2; exit 1; }
  mvn --batch-mode --no-transfer-progress -DskipTests package
fi
[ -f target/paspm-backend-0.1.0.jar ] || { echo "target/paspm-backend-0.1.0.jar missing" >&2; exit 1; }

rm -rf "$WORK_DIR"; mkdir -p "$WORK_DIR/java25" "$WORK_DIR/spmf"
cp "$SPMF_JAR_PATH" "$WORK_DIR/spmf/spmf.jar"
cid="$(docker create ${PLATFORM_ARG[@]+"${PLATFORM_ARG[@]}"} "$JAVA25_IMAGE")"
trap 'docker rm -f "$cid" >/dev/null 2>&1 || true' EXIT
docker cp "$cid:/opt/java/openjdk/." "$WORK_DIR/java25/"
test -x "$WORK_DIR/java25/bin/java"

docker build ${PLATFORM_ARG[@]+"${PLATFORM_ARG[@]}"} \
  --build-context java25="$WORK_DIR/java25" --build-context spmf="$WORK_DIR/spmf" \
  -t "paspm-backend:$TAG" .
echo "built paspm-backend:$TAG ($(docker image inspect "paspm-backend:$TAG" --format '{{.Architecture}}'))"

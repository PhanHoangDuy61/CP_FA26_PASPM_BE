# F05 SPMF Runtime Setup

The backend targets Java 17. The approved SPMF binary runs as a separate Java 25+ process. Configure both paths independently; F05 no longer silently uses the backend's JVM for SPMF. Missing runtime configuration leaves other modules available and rejects new mining with HTTP 503.

## Approved artifact and prerequisites

- Backend: JDK 17, Maven compatible with the existing pom.xml, MySQL 8.4.
- SPMF child: JDK/JRE 25+ from a trusted publisher, separately installed/provisioned by the operator.
- SPMF: approved 2.67a JAR, class major 69. Obtain the exact approved artifact manually from the [official distribution](https://www.philippe-fournier-viger.com/spmf/download.php) or an approved internal artifact store.
- SHA-256: 4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a.
- The official download page may move to a newer release. A different checksum is NOT accepted; stop and request artifact approval instead of changing the pin.
- Local provenance: the workspace README records an official SPMF download, and the inspected binary reports 2.67a. No signed upstream checksum/chain of custody has been independently authenticated. The project pin fixes the reviewed binary identity.
- [SPMF license/developer guidance](https://www.philippe-fournier-viger.com/spmf/developers.php): upstream GPLv3 obligations apply. This change does not redistribute the binary or install it automatically.

Provision the JAR in an operator-managed directory outside Git, for example /opt/paspm/runtime/spmf-2.67a.jar or C:\paspm\runtime\spmf-2.67a.jar. On a developer machine use a chosen absolute directory. The service account needs read access, but should not be allowed to replace trusted runtime artifacts. The backend temp directory must permit create/write/delete.

## Configuration

Spring resolves the existing canonical properties from the environment; no additional config import or developer-specific path is needed.

| Property | Environment variable | Default |
|---|---|---|
| paspm.mining.spmf-jar | PASPM_MINING_SPMF_JAR | Empty; required for mining |
| paspm.mining.java-executable | PASPM_MINING_JAVA_EXECUTABLE | Empty; explicit Java 25+ executable required |
| paspm.mining.heap-mib | PASPM_MINING_HEAP_MIB | 256 (valid 32..2048) |
| paspm.mining.timeout-millis | PASPM_MINING_TIMEOUT_MILLIS | 60000 (valid 1..300000) |
| paspm.mining.max-patterns | PASPM_MINING_MAX_PATTERNS | 2000 (valid 1..10000) |

Existing limits remain: one worker, queue of 8, log 64 KiB, pattern output 4 MiB. Actual mining uses a private paspm-mining-* directory in the backend JVM temp location; preflight uses paspm-spmf-preflight-*. Both are cleaned after execution. Set Java's java.io.tmpdir at startup only if the default system temp location is unsuitable.

Spring Boot does not automatically load an arbitrary .env file. Set variables in the launching shell/service manager, or use established external Spring configuration. Keep database/auth/email secrets out of Git and shell output.

### macOS

Install/provision JDK 17 and a separate JDK 25 according to the publisher's instructions, then substitute your chosen absolute paths:

~~~sh
export JAVA_HOME="/path/to/jdk17/Contents/Home"
export PASPM_MINING_JAVA_EXECUTABLE="/path/to/jdk25/Contents/Home/bin/java"
export PASPM_MINING_SPMF_JAR="/path/to/runtime/spmf-2.67a.jar"
"$JAVA_HOME/bin/java" -version
"$PASPM_MINING_JAVA_EXECUTABLE" -version
shasum -a 256 "$PASPM_MINING_SPMF_JAR"
"$PASPM_MINING_JAVA_EXECUTABLE" -Djava.awt.headless=true -jar "$PASPM_MINING_SPMF_JAR" version
~~~

### Linux

~~~sh
export JAVA_HOME="/path/to/jdk17"
export PASPM_MINING_JAVA_EXECUTABLE="/path/to/jdk25/bin/java"
export PASPM_MINING_SPMF_JAR="/opt/paspm/runtime/spmf-2.67a.jar"
"$JAVA_HOME/bin/java" -version
"$PASPM_MINING_JAVA_EXECUTABLE" -version
sha256sum "$PASPM_MINING_SPMF_JAR"
"$PASPM_MINING_JAVA_EXECUTABLE" -Djava.awt.headless=true -jar "$PASPM_MINING_SPMF_JAR" version
~~~

### Windows PowerShell

~~~powershell
$env:JAVA_HOME = "C:\path\to\jdk17"
$env:PASPM_MINING_JAVA_EXECUTABLE = "C:\path\to\jdk25\bin\java.exe"
$env:PASPM_MINING_SPMF_JAR = "C:\paspm\runtime\spmf-2.67a.jar"
& "$env:JAVA_HOME\bin\java.exe" -version
& $env:PASPM_MINING_JAVA_EXECUTABLE -version
(Get-FileHash $env:PASPM_MINING_SPMF_JAR -Algorithm SHA256).Hash
& $env:PASPM_MINING_JAVA_EXECUTABLE -Djava.awt.headless=true -jar $env:PASPM_MINING_SPMF_JAR version
~~~

Expected versions: backend reports 17; child reports 25 or later; checksum exactly matches the pin (case-insensitive when comparing visually); SPMF prints "-- SPMF version 2.67a --". No normal API request accepts a JAR/Java path.

## Build, migration and startup

1. Start MySQL 8.4 and provide an empty dedicated development schema plus the existing DB_URL, DB_USER, DB_PASSWORD and F01 secrets through your normal external configuration.
2. For a fresh database, backend startup applies corrected V1–V9 and validates them automatically. For an existing database, first follow [V7 recovery](FLYWAY_MYSQL_V7_RECOVERY.md); old V7 checksums must not be silently repaired.
3. Build with Maven under JAVA_HOME=JDK17. Tests requiring SPMF must receive the external test paths below.
4. Start the packaged backend explicitly with JDK 17:

~~~sh
"$JAVA_HOME/bin/java" -jar target/paspm-backend-0.1.0.jar
~~~

PowerShell equivalent:

~~~powershell
& "$env:JAVA_HOME\bin\java.exe" -jar target/paspm-backend-0.1.0.jar
~~~

5. Verify startup logs show Java 17 and all Flyway migrations validate/apply.
6. Check the independent version commands above. Backend preflight is lazy: it runs before a valid new mining request is persisted, and again before execution. It validates paths/readability, pinned hash, executable, Java major, real SPMF version entrypoint and temp create/write/delete.
7. JAR and executable contents are SHA-256 checked on every preflight call. Successful expensive version probes are cached for at most 30 seconds with both content fingerprints and real paths. Changed binaries invalidate the cache even when size/timestamps match. Probes have a 5-second per-process timeout and 64 KiB log bound.
8. Open /swagger (actual server port defaults to 8080 unless configured), authenticate with an authorized Merchandiser, and follow the workflow below. This is the API-level readiness check; no extra public health/config endpoint was added.

## Valid API workflow

Use the implemented contracts in [F05 API](../api/F05_MINING_API.md) and [frontend handoff](../frontend_handoff/F05_HANDOFF.md).

1. Create or select a valid persisted F03 Utility Snapshot in your Store.
2. GET /api/mining/snapshots to select its snapshotId.
3. POST /api/mining/requests with a new UUID requestKey, snapshotId, algorithms ["HUI_MINER","EFIM"], minUtilMode "ABSOLUTE" or "PERCENTAGE", and a valid minUtilValue.
4. Expect 202, then poll GET /api/mining/requests/{id}.
5. Read GET /api/mining/requests/{id}/runs. Both should be COMPLETED when their algorithms succeed.
6. GET /api/mining/runs/{id}/patterns for each run and compare canonical Product sets/utilities.
7. GET /api/mining/runs/{id}/benchmark for the real measured metrics/provenance. For a shared fixture, comparisonStatus should be MATCH.

The deterministic existing fixture has 5 transactions, total utility 96, and 8 matching patterns at absolute minUtil 30. Do not assume those values for arbitrary business snapshots.

## Failure diagnostics

The existing API envelope remains {"error":"CODE: actionable message"}, HTTP 503 for invalid dependency readiness. Messages omit sensitive server paths.

| Code | Action |
|---|---|
| SPMF_JAR_NOT_CONFIGURED | Set the external JAR environment variable/property |
| SPMF_JAR_NOT_FOUND | Use an existing absolute regular-file path |
| SPMF_JAR_NOT_READABLE | Grant read permission to the service account |
| SPMF_JAR_HASH_MISMATCH | Stop; verify the approved binary source/hash; do not bypass the pin |
| SPMF_JAVA_NOT_FOUND | Configure an existing executable absolute Java 25+ path |
| SPMF_JAVA_VERSION_UNSUPPORTED | Selected JVM is older than Java 25; configure the separate child executable |
| SPMF_RUNTIME_UNAVAILABLE | Check version/entrypoint exit status, probe timeout/output and temp permissions |

A runtime rejected during new-request admission creates no request/run. Read APIs and existing historical records remain available. If a runtime fails after admission, normal worker failure handling persists a failed run, never a fake successful one. Errors during mining, including timeout, still follow the existing run lifecycle.

## Reproduce verification

From BE after provisioning paths, run the standard suite using your chosen JAR/child executable:

~~~sh
mvn -Dpaspm.avatar.directory=/path/to/test-avatars \
  -Dpaspm.test.spmf-jar="$PASPM_MINING_SPMF_JAR" \
  -Dpaspm.test.spmf-java="$PASPM_MINING_JAVA_EXECUTABLE" verify
~~~

Opt-in local MySQL tests create new schemas only and require a test account with CREATE DATABASE privileges. They retain schemas for inspection. They never drop/reuse existing databases:

~~~sh
export PASPM_TEST_MYSQL_SERVER="jdbc:mysql://127.0.0.1:3306/"
export PASPM_TEST_MYSQL_USER="your_test_user"
# Supply PASPM_TEST_MYSQL_PASSWORD using your normal secret handling.
mvn -Dpaspm.test.mysql=true \
  -Dpaspm.test.java17-backend=true \
  -Dpaspm.test.java17="$JAVA_HOME/bin/java" \
  -Dpaspm.test.spmf-jar="$PASPM_MINING_SPMF_JAR" \
  -Dpaspm.test.spmf-java="$PASPM_MINING_JAVA_EXECUTABLE" \
  -Dpaspm.avatar.directory=/path/to/test-avatars clean verify
~~~

Run that command from an environment with JAVA_HOME selecting JDK17 and both PASPM_MINING_* variables exported. The HTTP smoke test explicitly checks backend Java 17 and actual environment-variable runtime configuration.

Executed validation was on this workstation using isolated MySQL/H2 and a temporary downloaded/verified Corretto JDK17. Windows/Linux instructions were reviewed but not executed, and a pristine clean-machine installation was NOT performed. See the [P0 fix report](../audits/F05_P0_DEPLOYMENT_FIX_REPORT.md).

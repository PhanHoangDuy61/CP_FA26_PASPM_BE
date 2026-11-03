# F05 multi-JVM evidence — 2026-11-01

Timezone: Asia/Ho_Chi_Minh. Synthetic data only; local disposable MySQL 8.4.11 at port 33317, Java 17.0.21 Backend and Java 25.0.1 SPMF. The actual test class starts independent Spring Boot JVMs on separate dynamic ports. A third, test-runner Spring context seeds fixtures and creates tokens; its worker is shut down before the child JVMs start. Tokens and connection credentials are not included here.

- `F05_MULTIJVM_FINAL.txt`: three-test grouped run, process IDs, request IDs, owner/fence observations, heartbeat times and outcomes.
- `F05_MULTIJVM_FINAL_ROWS_1.txt`: grouped-run synthetic request/run/pattern counts and frozen snapshot/configuration identifiers. `ROWS_2` records zero committed requests for the pre-commit crash; `ROWS_3` records the real-SPMF crash result.
- `F05_MULTIJVM_FINAL_LOG_EXCERPTS.txt`: selected, sanitized startup/interruption/fencing log lines. Older `_RESULTS`, `_RESULTS_RENEWAL`, `_DATABASE_ROWS`, `_LOGS_RENEWAL`, `_PRECOMMIT`, and `_SPMF_CRASH` files preserve earlier actual executions; they are not substitutes for the grouped final run.
- `MiningMultiJvmBackend.java` is test-classpath-only. The long-running and stale-worker cases hold the worker after a real SPMF result. The real-SPMF crash case waits until the actual Java 25 child starts, sends it SIGSTOP, kills Backend A, observes whether that child remains alive, and explicitly cleans up the test child. Thus the observed orphan is real but its long lifetime was intentionally forced by the test.

## Reproduction

Use a dedicated local MySQL 8.4 server and confirm the test account may create disposable schemas. Supply its loopback URL and test credentials via environment variables, plus the already approved SPMF 2.67a JAR and Java 25 executable. Never point these commands at a source database. Start Maven with Java 17. The test creates new `paspm_p0_test_<random>` schemas and does not delete existing databases.

```sh
export PASPM_TEST_MYSQL_SERVER='jdbc:mysql://127.0.0.1:<disposable-port>/'
export PASPM_TEST_MYSQL_USER='<test-user>'
export PASPM_TEST_MYSQL_PASSWORD='<test-password>'
export PASPM_MINING_SPMF_JAR='<absolute-approved-jar-path>'
export PASPM_MINING_JAVA_EXECUTABLE='<absolute-java-25-path>'
export PASPM_MULTIJVM_EVIDENCE_FILE='<new-absolute-evidence-file>'
mvn -q -Dpaspm.test.multijvm=true -Dtest=MiningMultiJvmMySqlTest test
```

The grouped run took several minutes because normal 30-second leases must expire. The evidence file is appended to, so use a new file for each run. The tests send POSIX STOP/CONT/KILL signals and therefore require a compatible host. No production configuration enables these barriers.

The separately executed MySQL regression selected `MiningDispatchLeaseMySqlTest`, `MiningMySqlIntegrationTest`, `MiningHttpMySqlTest`, `RetailCatalogUtilityMySqlIntegrationTest`, `TransactionImportMySqlIntegrationTest`, `RecommendationMySqlIntegrationTest` and `RecommendationExpiryMySqlIntegrationTest`, with `-Dpaspm.test.mysql=true`, `-Dpaspm.test.java17-backend=true` and `-Dpaspm.test.spmf-java=<java-25-path>`: 59 passed, none skipped. The final default `mvn -q -Dpaspm.test.spmf-java=<java-25-path> verify` ran 357 cases with 125 environment-gated skips; the separately run 59 MySQL and three multi-JVM tests account for 62 skipped cases. Other skipped cases and supervisor-level recovery remain outstanding.

## Orphan cleanup follow-up — 2026-11-01

The evidence above remains a failure of automatic orphan cleanup, not evidence of a remedy. The workstation is macOS Darwin arm64; the Docker CLI could not connect to a running daemon. Backend deployment OS, container supervision and privileges remain unconfirmed. No process supervisor, parent-death mechanism, durable child-identity metadata, new SIGKILL cleanup test or target-platform verification has been implemented in this follow-up. P0-1 remains OPEN. The read-only legacy reconciliation inventory is documented separately in `docs/architecture/F05_LEGACY_RUNNING_RECONCILIATION.md` and its SQL was executed against an H2 synthetic fixture; no live historical request was modified.

## Linux Docker kernel parent-death verification — 2026-11-01

This later section supersedes the preceding unimplemented status. Linux Docker was selected as the target and Docker Desktop was started. Tests ran in Linux/amd64 containers under emulation on an Apple Silicon macOS host; this is not a clean native production-host certification. The Backend JVM was Java 17.0.20.1, MySQL 8.4.13, and the actual SPMF child was Eclipse Temurin JRE 25.0.1. The Linux JRE archive was downloaded temporarily from the publisher and verified against its supplied SHA-256; SPMF's existing SHA-256/version preflight remained in force. Separate synthetic MySQL containers had no host-exposed port or persistent volume and were removed after testing.

- `F05_LINUX_PDEATH_REPEATED.txt`: three Linux kernel probes, each testing wrong parent identity rejection, absence of Python failing closed, parent SIGKILL while its direct child was stopped, survival of an unrelated child, and reaping by container init. The probe child is `sleep`, not SPMF.
- `F05_LINUX_MULTIJVM_FINAL.txt`: grouped MySQL multi-JVM ownership, heartbeat, stale-write fencing, request recovery and queue tests, including a real Java 25 SPMF kill and an unrelated instance's live SPMF. That group used a regular container; kernel termination and init reaping are separate.
- `F05_LINUX_SUPERVISION_FINAL.txt`: Linux `--init` real-SPMF lease-loss and SIGKILL cases; the terminated SPMF was reaped, B's independently owned SPMF survived, B recovered with a newer fence and eight patterns. `F05_LINUX_SPMF_REPEAT.txt` repeats the real-SPMF kill/reap scenario twice in one init-enabled container; `F05_LINUX_SPMF_REPEAT_LOGS.txt` has selected sanitized process identity and Backend startup logs.
- `F05_LINUX_GRACEFUL.txt`: a separate Linux `--init` test stopped Backend A with a normal termination signal while its real SPMF child was active; the child terminated and Backend B recovered the request. `F05_LINUX_VERIFICATION_SUMMARY.txt` summarizes the tested versions and result counts.
- Initial Linux attempts did not pass: the first offline Maven run lacked a cached `plexus-utils` dependency; the next test's fixture could not observe SPMF via its original process scanner. Maven was rerun with a temporary cache, and the fixture was changed to use the actual Backend start log plus parent/argument verification. Later executions above passed. These failures were not production test successes.

The Linux runner required Python 3 `ctypes`, `prctl` permission, a trusted unprivileged Java executable, and an init to reap dead children. Kernel parent-death signaling, not `--init`, killed the child. No user-space watchdog remains after `execv`; killing the temporary launcher before exec leaves no Java child. Windows/macOS host process cleanup is not covered by this Linux guarantee. The tests do not certify the project's eventual Backend deployment image, seccomp policy, external log retention or temp-directory cleanup after Backend SIGKILL.

The final Java 17 Maven verify after adding the graceful-shutdown test completed 362 cases, 0 failures/errors and 127 skipped. The 59 Linux MySQL cases and five multi-JVM test methods were executed separately; the earlier 357-case count above is historical. See `F05_LINUX_VERIFICATION_SUMMARY.txt` for the current classification.

With Java 17 and the approved SPMF/Java 25 provisioned, an equivalent Linux test runner can invoke:

```sh
mvn -Dpaspm.test.multijvm=true -Dtest=MiningMultiJvmMySqlTest test
```

Set the `PASPM_TEST_MYSQL_*`, `PASPM_MINING_*` and `PASPM_MULTIJVM_EVIDENCE_FILE` variables described above; use a new disposable local MySQL schema server and an init-enabled container. To require the Linux `/proc` reaping assertion, additionally set `PASPM_TEST_REQUIRE_REAP=true`. Never point test tooling at a shared/source database. These commands need an application build/test image; the repository does not yet provide a production Backend Dockerfile.

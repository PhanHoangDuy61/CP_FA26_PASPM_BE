# ROC-20261116-012 — F05 FoodMart-scale mining input

**Date:** 2026-11-16 / Asia/Ho_Chi_Minh
**Status (owner decision 2026-11-16):** APPROVED FOR MERGE WITH KNOWN LIMITATIONS — the owner explicitly accepted the deferred items below as limitations, not blockers. The actual merged status and remote head will be recorded after the authorized Git operations succeed.
**Branch / starting HEAD:** `Khoi/feat/f05-foodmart-scalability` / `c8d778fd8801dff5aff6b9a2c767d9b18822eb42`. No commit was created.

## Objective and original issue

Enable bounded F05 mining input for the public FoodMart reference scale while preserving EFIM-only execution, frozen F03 semantics, F06 compatibility, and resource guards. The original **256 distinct Product** limit was enforced by `MiningInputBuilder.MAX_ITEMS`; it rejected input before a request was enqueued. The **20,000 matrix-row** limit was separate and was not exceeded by the reference SPMF file's 18,319 transaction-Product entries.

## Changes

- `src/main/java/vn/edu/fpt/paspm/mining/MiningInputBuilder.java`: changed `MAX_ITEMS` from 256 to 1,600. `MAX_ROWS=20,000` remains unchanged. No Flyway migration and no historical data rewrite.
- `src/test/java/vn/edu/fpt/paspm/mining/MiningInputTest.java`: added `acceptsProductCountAtFoodMartScaleAndRejectsOneAboveConfiguredLimit` (1,600 accepted; 1,601 rejected).
- `src/test/java/vn/edu/fpt/paspm/recommendation/FrozenEvidenceReaderTest.java`: added `readsFrozenEvidenceAtTheConfiguredFoodMartProductLimit` (synthetic frozen evidence at 1,600 read by F06).
- `scripts/verify-option-b-macos.sh`: corrected Flyway assertions that hard-coded V1–V19; expected versions now come from the candidate migration files. This matches the actual current V1–V26 schema and does not alter migrations.
- F05 API/feature docs now state the bound and errors; F06 docs describe the shared frozen-reader validation; staging notes mark the former 256 limit as superseded. No OpenAPI shape changed.

The 1,600 limit is 1,559 measured reference Products plus 41 bounded headroom. It is not a claim that EFIM has a 1,600-item algorithmic maximum or that arbitrary lower thresholds will fit every guard.

## Dataset and benchmark

The external experiment workspace's public FoodMart SPMF utility file had SHA-256 `0e77ec1b913f6ace0905dac16811bfe2c78e13c32d07647d316e593042d970e6`; 4,141 reference transactions, 18,319 item entries, 1,559 unique Product IDs, 179,510 source bytes, total integer utility 12,011,023. SPMF 2.67a JAR SHA-256 `4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a`, Java 25.0.1. The reference utilities are sales revenue in cents and do not make a PASPM F03 snapshot; therefore the actual `utility_matrix_item` row count after valid F03 construction is **NOT RUN**.

At `minUtil=10,000`, bounded standalone EFIM passed at 256 / 512 / 1,024 / 1,559 Products, generating 62 / 139 / 275 / 428 patterns. The 1,559 Product run took 0.3845 s wall / 0.286 s EFIM time, reported 17.12 MiB SPMF heap, and emitted 7,579 bytes. Peak process RSS was not measured. At 1,559 Products and `minUtil=1,000`, the standalone child hit OUTPUT_LIMIT: it was terminated with exit 143 after the 4 MiB polling threshold was observed at 4,481,024 bytes (polling overshoot 286,720 bytes); no completed pattern count is reported.

The early pre-fix test run intentionally demonstrated RED: 20 tests, 2 expected assertion failures because the actual limit was still 256. The early benchmark `results.json` was invalid because an old output file remained and did not correspond to the later successful log/output; it is retained as an invalid attempt, not evidence. A corrected harness cleared output files and produced the four final runs. An early full-runner attempt also reported two false Flyway FAIL results because the verifier expected V1–V19 while actual history was V1–V26; all rows were successful. The verifier was corrected and the final complete runner rerun passed.

## Verification actually executed

Commands (temporary directory and binary locations are represented by placeholders to avoid committing machine-specific paths):

- Focused RED run: `mvn -Dpaspm.avatar.directory=<external-temp>/avatars -Dpaspm.f05.export-dir=<external-temp>/exports -Dtest=MiningInputTest,FrozenEvidenceReaderTest test` — 20 tests, 2 failures, 0 errors, 0 skipped (expected before limit change).
- Focused final run: same command after the code change — 20 tests, 0 failures, 0 errors, 0 skipped.
- `bash -n scripts/verify-option-b-macos.sh` — exit 0.
- `scripts/verify-option-b-macos.sh --run` with Java17 Backend, Java25 SPMF child, hash-pinned SPMF JAR and disposable MySQL 8.4.13 — final exit 0, 23 runner stages PASS.

Final counts from the runner:

| Stage | Result |
|---|---|
| Default `clean verify` | 653 total; 445 passed, 0 failed, 0 errors, 208 skipped; exit 0 |
| Dispatcher flag / migration versioning | 6 passed; 0 failed/errors/skips |
| MySQL group | 72 passed; 0 failed/errors/skips |
| Full opt-in (MySQL + SPMF + Java17 Backend) | 663 total; 636 passed, 0 failed, 0 errors, 27 skipped; exit 0 |
| Flyway fresh / restart / upgrade | V1–V26 PASS; second-start history byte-identical; seeded upgrade data fingerprints preserved |
| JPA schema validation / runtime OpenAPI export | PASS |

The 27 full-opt-in skips were 5 `MiningMultiJvmMySqlTest`, 12 `MiningSchedulerMultiJvmMySqlTest`, 5 `MiningSchedulerUpgradeCompatibilityMySqlTest` (all require `paspm.test.multijvm`), and 5 `PilotCloneApiIntegrationTest` (requires `paspm.test.jpa-clone`). They were **not executed** by this runner. The 208 default-verify skips include opt-in MySQL and runtime-gated suites; the separate MySQL and full-opt-in stages cover the corresponding enabled suites, but do not turn gated multi-JVM/JPA-clone cases into passes. Hosted GitHub CI and deployment were NOT RUN.

Disposable MySQL verification created no changes to the configured application database. The test runner stopped its uniquely named disposable container at completion. No frontend source was touched.

## F03 → F05 → F06 impact and acceptance status

- F03's frozen snapshot format, scale-6/GCD conversion and Store scope did not change. However, a complete FoodMart F03 snapshot was not created, so actual PASPM matrix rows and the real snapshot's conversion/int32 outcome are unknown.
- F05 now admits up to 1,600 products / 20,000 rows before enqueue; standalone EFIM used the real public reference file under bounded resources. A separate opt-in test executed a database-backed synthetic F04 → F03 → F05 request/run/pattern persistence lifecycle at 1,559 products; authentic FoodMart snapshot mining remains NOT RUN.
- F06's `FrozenEvidenceReader` shares the increased max and its 1,600-product synthetic boundary test passed. F06 consumed the synthetic 1,559-product completed-run evidence in the end-to-end test; authentic FoodMart evidence consumption remains NOT RUN.
- F06's requirements document was clarified to separate the independent `maxBundleSize` policy cap of 256 from F05's per-input Product bound.
- Existing F03 snapshots, F05 requests/runs and F06 recommendation JSON were not rewritten. Store authorization paths were untouched; full RBAC regression passed within the reported suites, while skipped multi-JVM suites remain unverified.

**Owner merge decision:** the synthetic 1,559-Product lifecycle passes through persisted F03, F05 and F06. The owner accepted the remaining FoodMart-scale verification items below as known limitations, not blockers to this limit-change merge. No deferred item is represented as passed.

## Evidence, docs and Git

- Detailed benchmark, inputs, SPMF logs/outputs, runner logs, count reports and hashes: `outputs/evidence/2026-11-16/f05-foodmart-scalability/` under the project folder, outside Git. `manifest.json` hashes each retained artifact; the source dataset is referenced by hash and not copied into Git.
- Benchmark report: [F05_FOODMART_SCALABILITY_BENCHMARK.md](../../testing/F05_FOODMART_SCALABILITY_BENCHMARK.md).
- Updated indexes: `docs/record-of-changes/README.md`, this day's `README.md`, and `docs/testing/README.md`.
- Updated documentation: `docs/api/API_CHANGELOG.md`, `docs/api/F05_MINING_API.md`, `docs/features/F05/README.md`, `docs/features/F06/README.md`, `docs/requirements/F06_RECOMMENDATION_RULES_v1_1.md`, `docs/setup/STAGING.md`, `docs/testing/README.md`, `docs/testing/F05_FOODMART_SCALABILITY_BENCHMARK.md`, `docs/record-of-changes/README.md`, and `docs/record-of-changes/2026-11-16/README.md`.
- Exact changed files are shown by `git status`/`git diff --stat`; no migrations, frontend, secrets, commits, pushes, merges or deployments.
- CodeGraph was available and used to inspect Backend dependency context; it did not replace source/test verification. No generated CodeGraph index files were added to Git.

## Acceptance update — synthetic F03 → F05 → F06 lifecycle and MySQL retest

This update supersedes the earlier 653/663 test totals, the earlier “end-to-end NOT RUN” wording, and the earlier statement that all 23 stages of the main runner passed. The latest main runner summary records its MySQL-group collection as FAIL/inconsistent; the corrected, separately executed MySQL group then passed 72/72. It does not change the original standalone benchmark measurements or turn synthetic data into authentic FoodMart evidence.

### Owner-approved multi-JVM coverage

| Class | Owner's case count | Actual Maven invocations | Actual result | Evidence |
|---|---:|---:|---|---|
| `MiningSchedulerMultiJvmMySqlTest` | 12 | 12 | 12 PASS, 0 FAIL, 0 ERROR, 0 SKIP in the natural-lease-enabled follow-up on disposable MySQL | `e2e-acceptance/natural-lease-20261116T154400Z/maven.log` |
| `MiningMultiJvmMySqlTest` | 5 test methods | 6 | 6 PASS, 0 FAIL, 0 ERROR, 0 SKIP; one method is `@RepeatedTest(2)`, so Maven records six invocations | `e2e-acceptance/option-b-20261116T152400Z/option-b-20261116T152400Z/multijvm/surefire-reports/TEST-vn.edu.fpt.paspm.mining.MiningMultiJvmMySqlTest.xml` |
| `MiningSchedulerUpgradeCompatibilityMySqlTest` | 5 | 5 | 5 PASS, 0 FAIL, 0 ERROR, 0 SKIP with the baseline JAR built from `c8d778f` | `e2e-acceptance/legacy-backend/legacy-test-maven.log` |

All 22 owner-grouped test cases have been executed and passed on disposable MySQL. Maven records 23 invocations because the five-method MiningMultiJvm class contains one repeated test. The initial multijvm invocation had skips/errors in an earlier run; the rows above refer to the subsequent successful executions, not that earlier run.

### Data distinction

The original public FoodMart extracts do not provide a canonical order ID for PASPM F04 order grouping, nor the approved inventory expiry/push-day and season evidence needed to create a valid W2 F03 snapshot. Consequently, an authentic FoodMart `utility_matrix_item` row count remains **unknown / NOT RUN**. No sales rows were arbitrarily grouped and no inventory evidence was invented.

`FoodMartScaleSyntheticE2EMySqlIntegrationTest` is a deliberately synthetic Backend acceptance fixture, clearly labelled `SYNTHETIC_PASPM_SCALE_FIXTURE_NOT_AUTHENTIC_FOODMART_SNAPSHOT`. On a disposable MySQL 8.4.13 database it drove F04 CSV import → F03 READY snapshot → F05 EFIM request, worker, run and persisted patterns → F06 recommendation/evidence consumption. Results: 1,659 synthetic transactions, 1,759 transaction items and `utility_matrix_item` rows, 1,559 synthetic Products, 1,560 returned/persisted patterns under the 2,000 cap. It checked the conversion metadata and frozen input hash and cross-Store 404s. It passed 1/1, with no failure, error or skip. This proves the Backend lifecycle for that synthetic fixture only.

### Final verification ledger

| Run | Actual result | Classification |
|---|---|---|
| Default Java 17 `clean verify` | 654 discovered: 445 PASS, 0 FAIL, 0 ERROR, 209 SKIP; exit 0 | PASS with explicitly skipped opt-in tests |
| Dispatcher flag and migration-versioning selector | 6 PASS, 0 FAIL/ERROR/SKIP; exit 0 | PASS |
| Full opt-in Java 17 + disposable MySQL + Java 25 SPMF + synthetic scale E2E | 664 discovered: 637 PASS, 0 FAIL, 0 ERROR, 27 SKIP; exit 0. Scale E2E method 1/1 PASS | PASS with skips disclosed |
| Initial MySQL group run | Maven log selected 11 classes but collected unexpected extra multi-JVM reports: 90 report cases, 1 FAIL, 13 ERROR. Not accepted | INVALID GROUP RESULT; retained, not erased |
| Clean MySQL group without provisioner | 72 discovered, 7 FAIL, 0 ERROR/SKIP; all 7 failed because `PASPM_TEST_RESTORE_PROVISIONER` was null before backup/restore assertions | FAIL — missing test environment prerequisite, not an application-code fix |
| Corrected MySQL group with repository provisioner | 72 PASS, 0 FAIL, 0 ERROR, 0 SKIP; Maven exit 0; 11 selected suites; `LegacyRecoveryMySqlTest` 11/11 | PASS, MySQL 8.4.13 disposable tmpfs container |
| Explicit multi-JVM initial selector | 23 discovered, 20 PASS, 0 FAIL/ERROR, 3 SKIP | Partial selector execution; skips were then closed below |
| Natural lease/recovery follow-up | `MiningSchedulerMultiJvmMySqlTest`: 12/12 PASS, no skip; live lease expiration, no timestamp edits | PASS |
| Legacy scheduler-upgrade compatibility follow-up | `MiningSchedulerUpgradeCompatibilityMySqlTest`: 5/5 PASS, no skip; legacy backend built from verified baseline commit | PASS |
| JPA pilot clone API cases | 5 NOT RUN: clone properties were absent and mutation opt-in was not enabled | NOT RUN; test safety was not weakened |
| Flyway, JPA schema validation and OpenAPI export from final runner | Fresh/restart/upgrade V1–V26 PASS; restart history identical; schema validation and runtime OpenAPI PASS | PASS |

The three multi-JVM skips in the first selector (two natural-lease and one legacy compatibility case) were later executed in the dedicated follow-up runs; all 23 distinct multi-JVM methods are therefore covered by passing executions across those runs. The 5 JPA clone tests remain unexecuted. Skipped tests are never counted as passes.

### MySQL restore failure and resolution

The clean group exposed seven `LegacyRecoveryMySqlTest` setup failures because `restrictedRestore()` requires `PASPM_TEST_RESTORE_PROVISIONER`. The failing assertion occurred before the tests' intended restore and migration checks. The corrected rerun used the existing `.github/scripts/provision-restore.py`, copied outside the repository to the run evidence directory and supplied with `PASPM_CI_DISPOSABLE_MYSQL=true`, an explicit loopback-only test server and a disposable `paspm_p0_test_*` schema. It used the standard mysql:8.4 test server on tmpfs; no source/shared application database was involved. The provisioner did not change, and no assertions or safety guards were weakened. The rerun's Maven log and complete Surefire reports show 72/72 with zero skips.

### Commands and evidence

- Main runner: `scripts/verify-option-b-macos.sh --run` with Java 17 for Maven/Backend, Java 25 for the SPMF child, the hash-verified SPMF 2.67a JAR, and a disposable MySQL 8.4.13 container. The complete command and per-stage logs are retained under external evidence.
- Dedicated MySQL retest: `mvn --batch-mode --no-transfer-progress clean verify -Dtest=<output-of-.github/scripts/test-groups.py-mysql> -Dpaspm.test.mysql=true -Dlogging.level.root=ERROR`, with `PASPM_TEST_RESTORE_PROVISIONER` set to the copied repository helper and the disposable-server variables above. The actual selected class list is retained in `commands.log`.
- Natural lease and legacy compatibility commands and logs are retained in their respective evidence folders; the lease run used natural time passage rather than direct timestamp changes.
- External evidence directory: `outputs/evidence/2026-11-16/f05-foodmart-scalability/` under the project folder (not Git). `manifest.json` records hashes for the runner, E2E JSON, CSV fixture, standalone benchmark outputs, corrected MySQL Maven log/counts and Surefire reports. Raw FoodMart source files, credentials and database dumps are not stored there.

### Remaining limitations and status

- Dense FoodMart-shaped fixture: deferred; the synthetic persisted E2E used 1,759 matrix rows, not the public reference file's denser 18,319 item entries.
- 2,000-pattern-cap boundary: deferred; the synthetic E2E produced 1,560 patterns and the standalone low-threshold probe reached the output cap first.
- Output-overshoot follow-up: deferred; the observed 286,720-byte polling overshoot remains measured, but a process-level hard quota analysis was not performed.
- Authentic FoodMart F03 snapshot row count and F05/F06 evidence flow: NOT RUN; canonical order grouping plus approved inventory/season source values are unavailable.
- Five JPA clone tests: NOT RUN; required clone properties were absent and mutation opt-in remained disabled. Hosted GitHub CI and production Linux image acceptance were also NOT RUN.
- Not verified on Linux or on the target server: child JVM cleanup, memory under container limits, peak RSS. GitHub CI integration is still blocked on `PASPM_CI_SPMF_ARTIFACT_URL`.
- Existing F03 snapshots, historical F05 records and F06 frozen recommendation JSON were not rewritten. The production database was not accessed. No migration was added. No Frontend was changed.

**Status:** the owner approved these items as known limitations, not blockers to merging the F05 limit change. The record's final integration status and `dev` head must be confirmed from the authorized Git operations; deferred checks remain NOT RUN as listed.

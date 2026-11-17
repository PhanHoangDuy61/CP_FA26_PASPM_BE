# F05 FoodMart-Scale Mining Input — Benchmark and Verification

Date: 2026-11-16 / Asia/Ho_Chi_Minh. Branch: `Khoi/feat/f05-foodmart-scalability`, starting HEAD `c8d778fd8801dff5aff6b9a2c767d9b18822eb42`. Local changes are uncommitted and unreleased.

## Result

**Owner-approved for merge with known limitations (decision 2026-11-16).** The synthetic 1,559-product Backend lifecycle passed; authentic FoodMart F03 → F05 → F06 acceptance was not run and is an owner-accepted limitation for this merge. The 256-product rejection came from `MiningInputBuilder.MAX_ITEMS`, before a mining request could be enqueued. It was not an EFIM algorithm limit. This change raises the item bound to 1,600; the 20,000-row bound is unchanged. F06 uses the same bound when reading a frozen completed run.

The public FoodMart SPMF reference file measured 4,141 source transactions, 18,319 transaction-Product entries, 1,559 Product IDs, 179,510 bytes and total integer utility 12,011,023. The Backend's separate FoodMart scale integration test used a **synthetic** fixture and passed through F04 import → F03 snapshot → F05 request/run/pattern persistence → F06 recommendation. It produced 1,659 synthetic transactions, 1,759 transaction items, 1,759 `utility_matrix_item` rows and 1,559 Product IDs. These synthetic counts are not FoodMart dimensions. No authentic PASPM snapshot can be derived from the source extract: it lacks canonical order IDs for F04 grouping and does not include the approved batch expiry/push-day and season evidence required by F03. Thus authentic FoodMart F03 matrix rows remain unknown; no authentic-data acceptance is claimed.

## Limits and implementation

| Limit | Before | Current | Evidence / reason |
|---|---:|---:|---|
| Distinct Products per input | 256 | 1,600 | 1,559-Product reference benchmark succeeded; 41-Product headroom; builder boundary and over-bound tests |
| Frozen matrix rows | 20,000 | 20,000 | 18,319 reference item entries fit dimensionally; actual PASPM snapshot rows NOT RUN |
| Child JVM heap | 256 MiB | 256 MiB | Kept unchanged |
| Child execution timeout | 60 s default | 60 s default | Kept unchanged |
| Patterns | 2,000 | 2,000 | Kept unchanged |
| Algorithm output | 4 MiB | 4 MiB | Kept unchanged; see measured low-threshold rejection and poll overshoot note |
| Process log | 64 KiB | 64 KiB | Kept unchanged |

The 1,600-item limit is intentionally a small bounded increment above the measured target rather than an unbounded/configurable limit. It does not guarantee that every `minUtil` will produce an admissible result; timeout, pattern and output guards remain in force. Numeric conversion still needs lossless GCD normalization and int32 validation in the production builder. No Flyway migration was needed; no historical snapshots, mining requests or recommendation JSON were rewritten.

Validation errors for new requests remain HTTP 400:

- `Snapshot exceeds 1600 distinct products`
- `Snapshot matrix exceeds mining row limit`

The response envelope and OpenAPI schema shape are unchanged; `docs/api/openapi.json` was not regenerated.

## Standalone EFIM measurements

Input identity: `paspm-experiments/data/raw/foodmart_utility.txt` from the adjacent experiment workspace, SHA-256 `0e77ec1b913f6ace0905dac16811bfe2c78e13c32d07647d316e593042d970e6`. The experiment assumptions identify it as the published FoodMart SPMF file derived from `sales_fact_dec_1998`; item utility is revenue in cents, baskets may be fragmented across multiple lines, 24 Stores are pooled, and it is not PASPM inventory/snapshot evidence. The Backend worktree did not contain a valid F03 snapshot for this input.

The benchmark selected the lowest Product IDs deterministically for the 256/512/1,024 subsets, then mapped source Product IDs densely in ascending order. Every emitted SPMF row was checked for item/utility arity and transaction utility equality. The raw file is already integer-valued: benchmark input GCD=1, scale=1, totals are within signed int32. This standalone run did **not** invoke `MiningInputBuilder`'s scale-6 conversion on a real F03 snapshot. The production F05 builder's scale/GCD/int32 logic remains covered by its automated tests.

Runtime: macOS 26.5.2 arm64; Java 25.0.1 child; SPMF 2.67a; JAR SHA-256 `4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a`; command used `-Xmx256m`, 60-second wall bound, 4 MiB output bound and 64 KiB log bound. `minUtil=10,000` was held constant for the four successful size measurements.

| Products | Transactions | Item rows | Input bytes | Total utility | EFIM result | Wall / EFIM time | SPMF reported heap | Output |
|---:|---:|---:|---:|---:|---|---:|---:|---:|
| 256 | 1,999 | 2,822 | 30,757 | 1,936,764 | PASS, 62 patterns | 0.1325 s / 0.028 s | 12.62 MiB | 1,127 B |
| 512 | 3,194 | 6,090 | 63,114 | 4,036,035 | PASS, 139 patterns | 0.2173 s / 0.116 s | 13.62 MiB | 2,436 B |
| 1,024 | 3,929 | 11,944 | 115,413 | 7,842,732 | PASS, 275 patterns | 0.3482 s / 0.241 s | 15.62 MiB | 4,754 B |
| 1,559 | 4,141 | 18,319 | 175,369 | 12,011,023 | PASS, 428 patterns | 0.3845 s / 0.286 s | 17.12 MiB | 7,579 B |

The memory values are SPMF's reported Java heap measurement, not process RSS. The harness did not capture a reliable peak RSS because the child finished before the host sampler observed it; peak child RSS is NOT MEASURED. Times are single cold runs, not a statistical benchmark or production SLA.

At 1,559 Products, `minUtil=1,000` reached the output guard in 0.3986 s: the harness terminated the child with exit 143 after observing 4,481,024 bytes (4 MiB configured limit: 4,194,304 bytes). The output grew between polling samples, so the observed size overshot the nominal threshold by 286,720 bytes. This is an `OUTPUT_LIMIT` rejection, not a mining success or timeout. The count did not reach a valid completed result and is NOT reported. This exposes a bounded polling overshoot; it does not justify weakening the output protection.

## Backend code and verification

Source/test changes:

- `src/main/java/vn/edu/fpt/paspm/mining/MiningInputBuilder.java` — `MAX_ITEMS=1600`; `MAX_ROWS=20000` unchanged.
- `src/test/java/vn/edu/fpt/paspm/mining/MiningInputTest.java` — 1,600 accepted and 1,601 rejected.
- `src/test/java/vn/edu/fpt/paspm/recommendation/FrozenEvidenceReaderTest.java` — synthetic frozen evidence at 1,600 products remains readable by F06.
- `src/test/java/vn/edu/fpt/paspm/mining/FoodMartScaleSyntheticE2EMySqlIntegrationTest.java` — opt-in, disposable-MySQL end-to-end acceptance across F04 import, F03 snapshot, F05 EFIM and F06 recommendation using explicitly synthetic scale data.
- `scripts/verify-option-b-macos.sh` — Flyway verification now derives the expected migration versions from the candidate tree instead of incorrectly assuming V1–V19. It reports validation results for V1–V26 on this base.

Documentation changes include `docs/features/F05/README.md`, `docs/features/F06/README.md`, `docs/requirements/F06_RECOMMENDATION_RULES_v1_1.md`, `docs/api/F05_MINING_API.md`, `docs/api/API_CHANGELOG.md`, `docs/setup/STAGING.md`, `docs/testing/README.md`, and the 2026-11-16 Record of Changes indexes and entry. The F06 policy reference was clarified: its independent `maxBundleSize` cap of 256 is a bundle-size rule, not the F05 per-input Product limit.

The two added tests first failed on the old `MAX_ITEMS=256` as intended (20 executed, 2 failures, 0 errors/skips), then passed after the fix (20 executed, 0 failures/errors/skips).

Runner command: `scripts/verify-option-b-macos.sh --run` with Java 17, Java 25, the pinned SPMF JAR and an isolated disposable MySQL 8.4.13 container. Its Flyway, default, flags, full-opt-in and OpenAPI stages passed, but its first MySQL-group evidence collection was inconsistent and the runner summary marks that group FAIL; do not treat the overall runner as green. The isolated MySQL group was subsequently rerun with the required provisioner and passed 72/72. Evidence is external to Git at `outputs/evidence/2026-11-16/f05-foodmart-scalability/`; `manifest.json` contains SHA-256 hashes.

### Final acceptance counts and exact lifecycle result

The final full runner was executed after the scale test was added. Results from its actual Maven logs and Surefire reports:

| Run | Actual result |
|---|---|
| Default `clean verify` (H2/default profile) | 654 discovered: 445 PASS, 0 FAIL, 0 ERROR, 209 SKIP; exit 0. The added opt-in synthetic E2E is skipped in this ordinary run. |
| Dispatcher flag + migration versioning | 6 PASS, 0 FAIL/ERROR/SKIP; exit 0 |
| MySQL group, initial configured run | 90 reports: 76 PASS, 1 FAIL, 13 ERROR; invalid as a group result because the saved Maven selector log listed 11 suites but stale/extra multi-JVM reports were also included. Not accepted as PASS. |
| Full opt-in (MySQL + Java 17 Backend + Java 25 SPMF + synthetic scale E2E) | 664 discovered: 637 PASS, 0 FAIL, 0 ERROR, 27 SKIP; exit 0. The synthetic E2E method itself: 1 PASS, 0 FAIL/ERROR/SKIP. |
| Explicit multi-JVM selector | 23 discovered: 20 PASS, 0 FAIL/ERROR, 3 SKIP on the first selection. Follow-up natural-lease suite: 12/12 PASS; legacy-upgrade compatibility: 5/5 PASS. The three previously skipped cases have now each executed and passed in these follow-up runs. |
| Corrected isolated MySQL group rerun | 72 PASS, 0 FAIL, 0 ERROR, 0 SKIP; exit 0, MySQL 8.4.13 disposable container. All 11 selected classes are in the Maven log and Surefire reports. `LegacyRecoveryMySqlTest` passed 11/11. |
| JPA pilot clone | 5 cases NOT RUN: the required clone properties file was absent and the mutation opt-in was not enabled. Safety was not bypassed. |
| Flyway fresh/restart/upgrade; schema validation; runtime OpenAPI | PASS for current V1–V26 candidate; restart history byte-identical; no API schema change. |

The corrected MySQL rerun supplies `PASPM_TEST_RESTORE_PROVISIONER` using the repository's `.github/scripts/provision-restore.py`, against only a new loopback-published MySQL container with tmpfs storage. The prior 7 failures were caused by the missing test helper environment variable, before the intended backup/restore assertions could execute; no production code was changed to address them. The anomalous initial 90-report result is retained as an invalid attempt, not relabelled as success. Separate multi-JVM follow-ups used live lease expiry/recovery; no direct database timestamp edits were made.

**Synthetic F03 → F05 → F06 acceptance:** `FoodMartScaleSyntheticE2EMySqlIntegrationTest` exercised actual Backend request paths, Flyway and MySQL persistence, Java 25 SPMF 2.67a EFIM, pattern persistence, F06 generation and cross-Store 404 checks. It used 1,659 synthetic transactions / 1,759 rows / 1,559 generated Products and returned 1,560 patterns (below the 2,000 cap), with successful result persistence. This proves the tested synthetic 1,559-Product lifecycle only. It does **not** prove a real FoodMart F03 snapshot, nor does the fixture masquerade as authentic FoodMart sales or inventory.

The 27 skips in full opt-in comprise 22 multi-JVM cases whose separate follow-up suites later passed, plus 5 JPA pilot clone API cases that remain NOT RUN. The 209 default skips are condition-gated/default-disabled suites; enabled MySQL, SPMF, multi-JVM and clone coverage must be read from their separate runs above, not inferred from the default run. No result is inferred from an interrupted execution.

### Historical references to the former 256 limit

The current code, F05 API contract, F05/F06 feature documentation and staging notes state the 1,600-product limit. Remaining `256` mentions in `docs/audits/F05_DOCUMENT_COMPLIANCE_AUDIT.md`, `docs/review-2/SETUP_REPORT.md`, old F03 backlog/reference documents, and F06's `maxBundleSize` policy are historical or refer to a separate F06 bundle cap; they are not current F05 per-input limits. The dated audit/review records were preserved as evidence and not rewritten.

The separately selected MySQL retest command was `mvn --batch-mode --no-transfer-progress clean verify -Dtest=<mysql-selector> -Dpaspm.test.mysql=true -Dlogging.level.root=ERROR` on a disposable MySQL 8.4.13 server. The environment included the exact `test-groups.py mysql` selector, `PASPM_CI_DISPOSABLE_MYSQL=true`, loopback-only `PASPM_TEST_MYSQL_SERVER`, and `PASPM_TEST_RESTORE_PROVISIONER` pointing to a copied repository provisioner. Evidence includes the Maven log, counts and Surefire XML/text reports outside Git.

| Run | Result |
|---|---|
| Default Maven `clean verify` (H2 suites) | 653 run, 0 failures, 0 errors, 208 skipped; exit 0. Skipped tests are not passes. |
| Dispatcher flag + migration versioning | 6 run, 0 failures/errors/skips; exit 0 |
| MySQL group | 72 run, 0 failures/errors/skips; MySQL 8.4.13 disposable container; exit 0 |
| Full opt-in (MySQL + real SPMF + Java 17 Backend) | 663 run, 0 failures, 0 errors, 27 skipped; exit 0. Skipped tests are not passes. |
| Flyway fresh | PASS, all candidate migrations V1–V26 applied once, ascending, success=1 |
| Flyway restart | PASS, history remained byte-identical |
| Flyway upgrade | PASS, origin/dev history prefix and seeded data fingerprints preserved; candidate V1–V26 valid |
| JPA schema validation / runtime OpenAPI export | PASS; OpenAPI schema shape did not change |

An earlier run of the verification script reported false Flyway failures because it hard-coded V1–V19; its history showed successful V1–V26 and no failed rows. The script was corrected; all Flyway fresh/restart/upgrade stages in the later run passed for V1–V26. The runner's initial MySQL report collection was anomalous (see table) and is retained as invalid evidence, not accepted as a migration failure or a group pass; a separate clean rerun with the standard restricted restore provisioner passed. No migrations were edited.

The synthetic test is the first persisted 1,559-Product F03 → F05 → F06 lifecycle; the distinct public reference-file EFIM benchmark remains a standalone SPMF run. Neither uses a genuine F03 snapshot derived from the FoodMart transaction extract. No authentic FoodMart API request or F06 consumption of FoodMart evidence occurred.

## Blockers and next verification

- Build/import an authorized FoodMart transaction fixture through PASPM F04 and F03, with approved analysis date, Store and utility/snapshot rules. Record the actual `utility_matrix_item` row count rather than assuming it equals source sales rows or SPMF lines.
- Obtain an authoritative FoodMart transaction grouping key and approved inventory expiry/push-day and season evidence, or an explicitly approved data-construction policy; then build a genuine F03 snapshot and record its actual matrix row count.
- Once the authentic-data blocker is resolved, execute that F03 snapshot through F05 request persistence, worker/lease execution, completed-pattern persistence and F06 evidence consumption on disposable MySQL.
- Measure representative low `minUtil` output behavior at the Backend process boundary. The output-size poll overshot 4 MiB in the standalone harness; retain the cap and assess whether process-level bounded output is needed before production-scale release.
- A separate experiment could not establish the maximum safe Product count above 1,559; 1,600 is a narrowly bounded code limit with 41-Product headroom, not a proof of performance at arbitrary larger inputs.
- GitHub CI, production Linux runtime, deployment and release approval were NOT RUN.

## Owner decision — merge with known limitations

The owner approved merging the F05 input-limit change into `dev` on 2026-11-16 and explicitly designated the following items as **known limitations, not merge blockers**. This approval does not convert any NOT RUN item into a pass:

| Deferred item | Current evidence and reason it remains deferred |
|---|---|
| Dense FoodMart-shaped fixture | The passing 1,559-Product PASPM end-to-end fixture contains 1,759 matrix rows. It validates the service lifecycle and product scale, but not the denser 18,319-entry public SPMF reference shape. |
| 2,000-pattern-cap behavior | The synthetic run persisted 1,560 patterns, below the cap. The low-threshold standalone run reached the 4 MiB output cap first, so the exact pattern-cap boundary was not independently exercised. |
| Output-overshoot analysis | The standalone poller observed 4,481,024 bytes, 286,720 above the configured 4 MiB threshold. A process-level hard quota/overshoot follow-up was deferred; the existing cap and child cleanup remain in place. |
| JPA-clone tests | Five clone API tests were NOT RUN because the clone properties were absent and the mutation opt-in was not enabled. The test safety gate was not bypassed. |
| Authentic FoodMart acceptance | NOT RUN: available source lacks canonical order-grouping identifiers and approved expiry/push-day/season evidence, so a truthful valid F03 snapshot cannot be constructed from it. |
| Platform and hosted CI verification | Not verified on Linux or on the target server: child JVM cleanup, memory under container limits, peak RSS. GitHub CI integration is still blocked on `PASPM_CI_SPMF_ARTIFACT_URL`. |

The owner-approved disposition is **APPROVED FOR MERGE WITH KNOWN LIMITATIONS**. Record 013 below adds a later local-only acceptance run on the merged `dev` base; it closes the dense synthetic, pattern-cap, output-overshoot and JPA-clone items but does not change authentic FoodMart or platform/hosted-CI limitations.

## Follow-up — realistic utility conversion and remaining multi-JVM cases (2026-11-17)

This section adds evidence to the local acceptance follow-up in [Record 013](../record-of-changes/2026-11-17/13-f05-dense-acceptance.md). It does not revise the historical merge decision in Record 012 or represent authentic FoodMart data.

### Realistic-utility variant

An additional opt-in MySQL E2E fixture retained the same approximate density: **4,100 orders, 18,040 transaction-item/F03 matrix rows, 1,559 distinct products, 4.4 products/order**. It used 2-decimal unit prices spanning 0.50–200.00, quantities spanning 1–10, and `W2_EXPIRY_SEASON_V1` with push-day batches, batches missing push days, and products assigned to an active season. F04 imported the CSV fixture, F03 produced a READY snapshot, then scale-6/GCD conversion was evaluated against the production int32 guard.

The test fixture set the System Admin mining policy to the current value `ABSOLUTE 500.0`; the mining configuration was not reduced to force acceptance.

| Converted measurement | Value | Relation to signed int32 maximum |
|---|---:|---:|
| Scale-6 GCD divisor | 100 | — |
| Largest transaction utility | 26,225,721 | 2,121,257,926 below maximum |
| Largest item TWU | 3,217,593,703 | 1,070,110,056 above maximum |
| Total utility | 53,254,362,673 | 51,106,879,026 above maximum |

F05 returned HTTP 400 with the exact error `Lossless converted dataset exceeds SPMF int32 capacity`. No mining request was persisted, so EFIM runtime, pattern count and output bytes are **NOT RUN**; F06 consumption for this rejected variant is **NOT RUN**. The rejection is expected and the limit was not changed. Full snapshot and conversion metrics are in external evidence `outputs/evidence/2026-11-17/f05-dense-acceptance/followup/realistic-utilities/foodmart-dense-realistic-utilities.json`.

### Non-POSIX/Windows fallback

On OS names other than macOS/Linux, `MiningProcessLauncher` returns the original child argv without a shell or `ulimit`; `SpmfMiningEngine` uses the existing process polling and final output-size checks. `MiningProcessOutputLimitTest#nonPosixLauncherLeavesCommandUnwrappedAndCanStartChildJvm` mocked `Windows 11` and launched a Java 17 child successfully (1/1 PASS). The whole `MiningProcessOutputLimitTest` class passed 2/2 on macOS. A real Windows host run is **NOT RUN**. The fallback still has polling-based output enforcement; Windows overshoot is not measured and the kernel file-size ceiling is unavailable there.

### Previously skipped multi-JVM cases

With a disposable MySQL 8.4.13 database, `-Dpaspm.test.natural-lease=true`, the c8d778f legacy backend JAR prerequisite, Java 17 Backend and Java 25 SPMF, final class outcomes were:

| Test class | Final result |
|---|---|
| `MiningMultiJvmMySqlTest` | 6/6 PASS after rerun with the required inherited `PASPM_MINING_SPMF_JAR` and `PASPM_MINING_JAVA_EXECUTABLE` variables |
| `MiningSchedulerMultiJvmMySqlTest` | 12/12 PASS |
| `MiningSchedulerUpgradeCompatibilityMySqlTest` | 5/5 PASS |

The three cases that had been skipped by the prior verifier now passed: natural occurrence-lease expiry after 309 seconds; stale-owner fencing after a natural 384-second wait; and `characterizeTheOldBackendRunningOnTheNewSchema` using the c8d778f legacy JAR. Neither natural-lease test edited timestamps. The initial combined invocation omitted the two SPMF environment variables, causing six unrelated request-submission tests to receive 503; its 17 PASS / 6 FAIL result is retained as a failed configuration attempt. The class-only corrected rerun passed 6/6. Therefore all 23 unique tests have successful corrected class evidence, but no single aggregate 23-test invocation was green.

The previous `scripts/verify-option-b-macos.sh --run` result remains the last complete 24/24-stage verifier. It was not rerun after these test-only additions; the realistic E2E and the corrected multi-JVM class were run independently. Native Windows, authentic FoodMart, and target-server/cgroup/RSS acceptance remain open. Evidence and commands are outside Git in `outputs/evidence/2026-11-17/f05-dense-acceptance/followup/`; its manifest SHA-256 is `fe13ffd0093898d339c63a264a3cf737051ee5de6672624950ae845ddeb58415`.

## Follow-up acceptance — 2026-11-17

This section supersedes the 2026-11-16 deferred status for the dense fixture, 2,000-pattern behavior, output overshoot and JPA clone tests. Historical tables above remain dated evidence and are not rewritten.

### Dense synthetic F04 → F03 → F05 → F06 fixture

On a disposable MySQL 8.4.13 database, the E2E used 4,100 synthetic orders, 18,040 transaction-item rows and 1,559 distinct products (4.4 products/order). F04's existing 10,000 data-row/file limit was preserved by importing two files with 9,840 and 8,200 rows. F03 created a READY snapshot with 18,040 `utility_matrix_item` rows. F05 EFIM completed in 152 ms, persisted 3 patterns and wrote 141 output bytes. F06 consumed the persisted evidence and produced an AVAILABLE Bundle with support count 250.

F03 scale-6/GCD conversion used divisor 10,000,000. Largest converted transaction utility: 5, leaving `2,147,483,642` int32 headroom. Total converted utility: 18,040, leaving `2,147,465,607` headroom. The combined synthetic fixture is 1,371,109 bytes, SHA-256 `80cbb5bb6cae381c5b11e6f81ef234d3c8ad73e4dcd9895aac6324df63bb5102`. Detailed result: `outputs/evidence/2026-11-17/f05-dense-acceptance/dense/foodmart-dense-synthetic-e2e.json` in the project folder, outside Git. This is synthetic acceptance only; authentic FoodMart snapshot dimensions remain unknown.

### Pattern cap and F06 handling

Real SPMF EFIM with a 12-item single-transaction fixture and `minUtil=1` exceeded the configured 2,000-pattern bound. The run failed as `OUTPUT_LIMIT`; partial patterns were not reported as a successful/truncated run. The persisted mining request/run remained FAILED with zero patterns, and F06's evidence reader rejected it with HTTP 409. Focused test methods: `SpmfMiningEngineTest#actualEfimFailsAtThePatternCapInsteadOfReturningTruncatedResults` and `MiningIntegrationTest#durableFailureAndTimeoutStatusesContainNoPartialPatterns`; both passed.

### Output cap and overshoot

The 4 MiB output guard is now backed by a kernel file-size limit in the child process on Linux and macOS. Producers asked to write 4,259,840 bytes and 20,971,520 bytes; both wrote exactly 4,194,304 bytes, with 0-byte overshoot. A direct Linux-container 20,971,520-byte write also stopped at 4,194,304 bytes (writer exit 153 / `SIGXFSZ`). A real EFIM output crossing the limit is classified `OUTPUT_LIMIT`. The cap is unchanged. The 64 KiB process-log poll and non-POSIX fallback remain unchanged; Windows overshoot was not measured.

### JPA clone tests and full verification

`PilotCloneApiIntegrationTest`: 5/5 PASS on a newly provisioned disposable schema; Flyway migration used the disposable container admin because binary logging rejected function creation by the schema-only account, while the test application used the schema-only account. The failed initial migration was discarded with its disposable container; no shared/source DB was used.

One complete `scripts/verify-option-b-macos.sh --run` finished exit 0 with all 24 runner stages PASS. Stages: default Maven 658 discovered / 448 PASS / 210 SKIP; dispatcher flag 6/6; MySQL 72/72; full opt-in 668 discovered / 641 PASS / 27 SKIP; multi-JVM 23 invocations / 20 PASS / 3 SKIP; Flyway fresh V1–V26, V11, restart-identical history, upgrade-preserved data, JPA schema validation and runtime OpenAPI all PASS. The 3 multi-JVM skips were two natural-lease tests requiring `paspm.test.natural-lease` and one legacy-upgrade case requiring `paspm.test.legacy-jar`; these properties were not supplied in that run. The 5 clone API skips in full-opt-in were executed separately and passed 5/5. Skips are not counted as passes.

The database-outage test deliberately injected connection loss and logged database communication errors during recovery; its test passed 1/1. Full logs, Surefire reports, focused output probes and the dense result are under `outputs/evidence/2026-11-17/f05-dense-acceptance/` outside Git; `manifest.json` contains SHA-256 hashes.

## EFIM-aware exact int32 guard — 2026-11-17

This addendum records owner decision [DEC-20261117-01](../decisions/2026-11-17/DEC-20261117-01-efim-aware-lossless-int32-guard.md). It supersedes the earlier realistic-utility result above only for the reason for rejection and current numeric contract: that run correctly exposed a Product TWU above int32, but the old message also rejected because the global total exceeded int32. The exact conversion remains lossless; no rounding or quantization is used.

### Demo seed snapshot measurements

The seed worktree used commit `331fe910900ce6689211cef5976645785aea6713`; the two imported CSV files had SHA-256 `5e45ffbdccd4947a1e4c2ee2528e6ae58e8b7364b999288c268d3dcc39b2a8ff` and `7357315a4d485901a5f2344d8d39eb3b2d16514e0e33880df39deecaca07c46f`. F03 analysis date was 2026-10-05. The following are the exact persisted frozen matrix measurements after scale-6/GCD conversion. `TU` is maximum transaction utility, `TWU` is maximum Product TWU, and minUtil is `PERCENTAGE 0.25` rounded upward. Each checked value fits signed int32; only the full/global normalized total and multi-day range totals exceed it. This is demo seed data, not authentic FoodMart acceptance.

| Date range | Rows | Transactions | Products | Exact divisor | Total (int64) | max TU | max TWU | minUtil | EFIM bounds fit? |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| Full 4 days | 18,319 | 4,141 | 1,559 | 10,000 | 5,196,907,628 | 5,189,374 | 43,859,587 | 12,992,270 | Yes; global total alone exceeds int32 |
| Day 1 | 4,066 | 900 | 1,444 | 10,000 | 1,163,610,588 | 3,746,798 | 17,550,210 | 2,909,027 | Yes |
| Day 2 | 6,243 | 1,440 | 1,527 | 10,000 | 1,791,992,761 | 5,189,374 | 20,203,259 | 4,479,982 | Yes |
| Day 3 | 6,389 | 1,440 | 1,530 | 10,000 | 1,779,174,736 | 4,347,417 | 18,543,286 | 4,447,937 | Yes |
| Day 4 | 1,621 | 361 | 1,002 | 10,000 | 462,129,543 | 3,401,688 | 11,865,908 | 1,155,324 | Yes |
| Days 1–2 | 10,309 | 2,340 | 1,554 | 10,000 | 2,955,603,349 | 5,189,374 | 28,883,771 | 7,389,009 | Yes; global total alone exceeds int32 |
| Days 2–3 | 12,632 | 2,880 | 1,559 | 10,000 | 3,571,167,497 | 5,189,374 | 33,169,667 | 8,927,919 | Yes; global total alone exceeds int32 |
| Days 3–4 | 8,010 | 1,801 | 1,551 | 10,000 | 2,241,304,279 | 4,347,417 | 21,603,172 | 5,603,261 | Yes; global total alone exceeds int32 |
| Days 1–3 | 16,698 | 3,780 | 1,559 | 10,000 | 4,734,778,085 | 5,189,374 | 41,349,131 | 11,836,946 | Yes; global total alone exceeds int32 |
| Days 2–4 | 14,253 | 3,241 | 1,559 | 10,000 | 4,033,297,040 | 5,189,374 | 34,952,669 | 10,083,243 | Yes; global total alone exceeds int32 |

The seed project verification states a total weighted utility of 54,013,051.55. The persisted READY snapshot measured here normalizes to `5,196,907,628 × 10,000 / 1,000,000 = 51,969,076.28`. This difference of 2,043,975.27 is not explained by the checked snapshot measurement; it remains an evidence discrepancy to reconcile before treating the seed's stated aggregate as equivalent to the F03 frozen weighted-utility total.

### Break-even under the measured seed profile (projection, not a benchmark)

For the exact seed conversion quantum `10,000 / 1,000,000 = 0.01` weighted utility per normalized unit, the retired global-total cap corresponded to `2,147,483,647 × 0.01 = 21,474,836.47` weighted utility. At the observed average `51,969,076.28 / 4,141 = 12,549.8856` per order, this was about 1,711 orders; the actual full-range seed has 4,141 orders and 51,969,076.28 utility, so the old guard rejected it despite all EFIM int32 accumulators fitting.

Under the new guard with this distribution and `PERCENTAGE 0.25`, projected Product-TWU capacity would be about 202,754 orders, while converted percentage `minUtil` capacity would be about 684,463 orders. Both are projections holding observed maxima/ratios and conversion quantum constant. The unchanged 20,000-row ceiling binds first at about 4,520 whole orders (18,319 rows / 4,141 orders), corresponding to roughly 56.7 million weighted utility at the observed average. This is an estimate under unchanged density and utility profile; it is not a universal capacity claim or a reason to raise the 1,600-product/20,000-row bounds.

### W=1.3 sensitivity and conservative raw bounds

For the owner-provided `λ=0.3, α=0.6, β=0.4` scenario, a separately constructed W=1.3 fixture has exact GCD 1,300,000; all its resulting normalized totals/TUs/TWUs/minUtil values fit int32 for all ten ranges. This is a scenario, not a bound: the divisor changes with the data. The conservative bound instead applies W=1.3 to scale-6 source values and forces divisor 1 (actual GCD is at least 1). Those raw upper bounds are listed below; they exceed int32 and therefore are not sufficient to prove that an arbitrary W=1.3 snapshot is admissible. The guard evaluates the actual frozen values and actual GCD per snapshot.

| Range | Exact W=1.3 GCD | Total | max TU | max TWU | 0.25% minUtil |
|---|---:|---:|---:|---:|---:|
| Full 4 days | 1,300,000 | 50,544,274 | 49,333 | 429,184 | 126,361 |
| Day 1 | 1,300,000 | 11,316,164 | 36,781 | 172,266 | 28,291 |
| Day 2 | 1,300,000 | 17,432,174 | 49,333 | 195,924 | 43,581 |
| Day 3 | 1,300,000 | 17,304,344 | 41,399 | 182,186 | 43,261 |
| Day 4 | 1,300,000 | 4,491,592 | 33,538 | 115,660 | 11,229 |
| Days 1–2 | 1,300,000 | 28,748,338 | 49,333 | 281,416 | 71,871 |
| Days 2–3 | 1,300,000 | 34,736,518 | 49,333 | 326,767 | 86,842 |
| Days 3–4 | 1,300,000 | 21,795,936 | 41,399 | 209,326 | 54,490 |
| Days 1–3 | 1,300,000 | 46,052,682 | 49,333 | 405,086 | 115,132 |
| Days 2–4 | 1,300,000 | 39,228,110 | 49,333 | 341,482 | 98,071 |

| Range | Conservative raw total | max TU | max TWU | 0.25% minUtil |
|---|---:|---:|---:|---:|
| Full 4 days | 65,707,556,200,000 | 64,132,900,000 | 557,939,200,000 | 164,268,890,500 |
| Day 1 | 14,711,013,200,000 | 47,815,300,000 | 223,945,800,000 | 36,777,533,000 |
| Day 2 | 22,661,826,200,000 | 64,132,900,000 | 254,701,200,000 | 56,654,565,500 |
| Day 3 | 22,495,647,200,000 | 53,818,700,000 | 236,841,800,000 | 56,239,118,000 |
| Day 4 | 5,839,069,600,000 | 43,599,400,000 | 150,358,000,000 | 14,597,674,000 |
| Days 1–2 | 37,372,839,400,000 | 64,132,900,000 | 365,840,800,000 | 93,432,098,500 |
| Days 2–3 | 45,157,473,400,000 | 64,132,900,000 | 424,797,100,000 | 112,893,683,500 |
| Days 3–4 | 28,334,716,800,000 | 53,818,700,000 | 272,123,800,000 | 70,836,792,000 |
| Days 1–3 | 59,868,486,600,000 | 64,132,900,000 | 526,611,800,000 | 149,671,216,500 |
| Days 2–4 | 50,996,543,000,000 | 64,132,900,000 | 443,926,600,000 | 127,491,357,500 |

### SPMF EFIM bound and implementation result

Offline inspection of the pinned SPMF 2.67a JAR (SHA-256 `4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a`) found: `Dataset.createTransaction(String)` parses transaction and item utilities using `Integer.parseInt`; `Transaction` stores `int transactionUtility`, `int prefixUtility` and `int[] utilities`; `AlgoEFIM.runAlgorithm(int,...)` and `minUtil` use int; `utilityBinArrayLU` and `utilityBinArraySU` are int arrays; `backtrackingEFIM` uses int for local pattern utility and output. PASPM's utilities are nonnegative. Each transaction utility is checked; a Product's TWU is the sum of transaction utilities containing that Product, computed in checked long and bounded by int32. EFIM's projected TU/prefix, LU/SU for a candidate and pattern utility are bounded by that candidate's TWU; no global database-total accumulator was found in the inspected EFIM path. `minUtil` remains positive int32. The removed global sum guard was sufficient but stricter than the SPMF accumulators require.

The implementation widens `Dataset.totalUtility`, frozen-input total arithmetic, `RequestView.totalScaledUtility` and F06 total validation to signed 64-bit; keeps exact scale-6/GCD and int64 overflow checks; and rejects transaction utility, Product TWU or minUtil overflow with separate 400 messages. The global sum is no longer an EFIM rejection criterion. A real EFIM unit fixture with global total 3,000,000,001 and per-item TWU 1,500,000,001 completed and pattern utilities matched an independent long recomputation.

### Disposable MySQL F03 → F05 → F06 acceptance

On the 4,141-transaction, 1,559-product demo snapshot at `PERCENTAGE 0.25`, request creation and mining completed: 232,502 input bytes, `totalScaledUtility=5,196,907,628`, 4,141 transactions, 1,559 Products; EFIM 236 ms, 0 patterns, 100 output bytes, sampled heap 16.6228 MiB. F06 generation completed with 0 recommendations for that run. This exercises successful F05/F06 processing with a total above int32.

The threshold search found `minUtilSpmf=3,836,041` (`ABSOLUTE 38,360.409900`) as the lowest tested threshold completing within the pattern cap: request/run 25 completed in 386 ms, persisted 2,000 patterns, output 92,758 bytes, sampled heap 19.122848 MiB. The next lower tested threshold (`3,836,040`, ABSOLUTE `38,360.399900`) failed `OUTPUT_LIMIT` with “Pattern count exceeds configured limit”; request/run 26 failed and F06 was not invoked for it. F06 generation on run 25 returned HTTP 200 and created 2,006 recommendations; disposable-database frozen eligibility counts were 2,003 ELIGIBLE / 3 INELIGIBLE. An API list pagination attempt was stopped due to slow response; all pages were not verified. Heap figures are Java heap samples, not peak RSS.

### Verification and remaining capacity limits

Final `scripts/verify-option-b-macos.sh --run` after correcting the realistic fixture's expected error completed all 24 stages with exit 0: default Maven verify 667 run / 0 fail / 0 error / 211 skipped; flag and migration tests 6/6; MySQL group 72/72; full opt-in 677 run / 0 fail / 0 error / 27 skipped; multi-JVM runner 23 invocations / 0 fail / 0 error / 3 skipped. Separate natural-lease/legacy-JAR opt-in run passed all 23/23 multi-JVM tests, and isolated JPA clone passed 5/5. Flyway fresh/restart/upgrade and runtime OpenAPI export passed through V26; no migration was added. Relative-to-baseline full opt-in is +9 executed tests, +9 passes, same 27 skipped (baseline 668 total, 641 pass, 27 skip). The skipped multi-JVM cases are covered by the separate 23/23 run, not counted as passes in the full runner. Actual runtime API export remains 100 paths / 121 operations; only `RequestView.totalScaledUtility` widens to `integer/int64`.

This guard makes the measured demo full-range snapshot admissible, but does not eliminate the realistic synthetic 4,100-order TWU overflow: its maximum Product TWU is 3,217,593,703, so it remains correctly rejected before request persistence. The 1,600 Product, 20,000 matrix-row, positive int32 minUtil, per-TU/TWU int32, 60-second timeout, 256 MiB child heap, 2,000-pattern and 4 MiB output protections remain. Authentic FoodMart F03 evidence, native Windows, target Linux image/cgroup peak RSS, hosted GitHub CI, and all-page verification of the 2,006 F06 records remain NOT RUN or NOT VERIFIED.

External evidence files and SHA-256 values are listed in [Record 015](../record-of-changes/2026-11-17/15-f05-efim-aware-int32-guard.md). They are kept outside Git; the evidence set includes the Step 0 range metrics, final verifier summary/runtime OpenAPI and disposable MySQL demo JSON.

### Remaining limitations

- Authentic FoodMart F03 → F05 → F06 remains **NOT RUN**: the available source lacks canonical F04 order grouping plus approved inventory/batch-season evidence.
- Production target-server/Backend image process cleanup, memory under container limits and peak RSS remain **NOT VERIFIED**. The Linux file-limit probe does not establish deployment acceptance.
- Hosted GitHub CI remains **NOT RUN** and still depends on the approved SPMF artifact setup.
- Two natural-lease and one legacy-JAR multi-JVM test methods were skipped in the full verifier's explicit multi-JVM stage; this follow-up does not claim they ran.

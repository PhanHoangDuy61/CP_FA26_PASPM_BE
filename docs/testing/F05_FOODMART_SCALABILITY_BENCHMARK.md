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

The owner-approved disposition is **APPROVED FOR MERGE WITH KNOWN LIMITATIONS**. The actual merged status and remote `dev` head will be recorded after the authorized Git operations succeed. The passing synthetic E2E, clean 72/72 MySQL group, multi-JVM coverage, and Flyway/API checks are reported separately above; this disposition does not claim the deferred scenarios passed.

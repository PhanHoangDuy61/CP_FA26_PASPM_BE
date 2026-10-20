# F05 Audit Against the FA26_SE048 Project Document

**Audit date:** 2026-10-20 (Asia/Ho_Chi_Minh)
**Repository:** BE, branch Khoi/Fear/F05-high-utility-mining-engine, commit 19f94be
**Scope:** Read-only implementation audit and verification. No application source, test, migration, configuration, or existing document was changed.

## A. Executive Summary

The supplied original document at /Users/khoinm/Downloads/Copy of FA26_SE048.docx was available and inspected. Its project requirements call for working HUI-Miner and EFIM, a mining API, a comparison using runtime and RAM, multiple minUtil experiments, public datasets, and raw high-utility patterns for later recommendation logic. It also proposes recurring scheduled mining. Its older React/ASP.NET architecture is not treated as a failure: the current approved backend is Spring Boot.

1. **What library does F05 use?** SPMF, through an external Java process.
2. **Does it use spmf.jar?** Yes. The local test runtime uses the workspace sibling file spmf_run/spmf.jar. Production configuration expects a trusted external absolute JAR path; the JAR is not in Maven, the BE Git repository, or the packaged application.
3. **Does HUI-Miner really run?** Yes. Fresh automated tests invoked SPMF HUI-Miner and matched eight independently specified patterns and utilities.
4. **Does EFIM really run?** Yes. The same tests invoked SPMF EFIM on the same bytes and threshold, and got the same eight patterns and utilities.
5. **Are Time/Memory/Itemsets real?** Yes, the recorded values are produced by actual child processes and parsed from SPMF output. They are real measurements for a tiny fixture, but one cold run per algorithm is not a statistically reliable performance comparison. Memory is SPMF sampled JVM heap, not process RSS.
6. **Does F05 satisfy FA26_SE048?** Core mining and raw-result API behavior are implemented and verified. The current branch has material deployment and research-evaluation gaps: clean MySQL install is blocked by inherited V7, the exact external Java 25 JAR must be provisioned, public datasets exceed current API input limits, and the benchmark evidence is too small for choosing a final algorithm.
7. **Should F05 be approved to move to F06?** Approve F06 contract/integration work against the raw pattern API, conditionally. Do not call F05 production-ready or use its tiny-fixture timings to select the final algorithm until the deployment blockers and reproducible benchmark plan are resolved.

**Overall:** F05 demonstrates genuine HUI-Miner/EFIM execution and usable persisted patterns. It is **PARTIAL** against the whole project document and current production-readiness expectations.

## B. Audit Method and Checklist

Completed checks:

- Read repository instructions and Git state. No AGENTS.md was found in the repository or its searched parent. The existing dirty local configuration and unrelated untracked files were left untouched.
- Located and read the supplied original DOCX. Treated its contents as project requirements, not as instructions to change this audit's scope.
- Queried the indexed project using CodeGraph, then checked the source, tests, migrations, OpenAPI, and existing F05 reports for behavior and runtime evidence.
- Inspected F03 snapshot persistence and the F05 data capture path.
- Ran a fresh full Maven verify suite and inspected the actual F05 Surefire output.
- Reviewed existing Swagger UI evidence, actual F05 OpenAPI operations, external SPMF files, and public dataset artifacts.
- Prepared the compliance matrix, risks, and remediation priorities below. No fixes were applied.

CodeGraph was available. Its project index reported 791 indexed files, 10,030 nodes, and 19,036 edges. CodeGraph was used to trace Controller, Service, Worker, Engine, Repository, F03 snapshot, and F06 references. It supplements source/test evidence; the index alone is not used as proof of correctness.

## C. Actual Mining Execution Architecture

The current request path is:

1. HTTP: MiningController.create accepts POST /api/mining/requests, validates CreateRequest, and returns 202. The controller also exposes snapshot, request, run, pattern, and benchmark reads. See src/main/java/vn/edu/fpt/paspm/mining/MiningController.java:15-38.
2. Authorization and snapshot capture: MiningService.create derives Store scope from the authenticated user, verifies that the snapshot belongs to that Store, and enters a repeatable-read transaction. It locks the snapshot row, reads the persisted F03 matrix, checks Product and transaction ownership, builds one immutable request input, and persists request/run metadata before enqueueing. See src/main/java/vn/edu/fpt/paspm/mining/MiningService.java:28-50.
3. F03 input: UtilitySnapshotRepository.page reads utility_matrix_item weighted_utility rows ordered by transaction and Product, computes transaction totals from those stored rows, and associates them with the frozen Utility Snapshot. UtilitySnapshotService.create persists the snapshot configuration and matrix rows. See src/main/java/vn/edu/fpt/paspm/utility/UtilitySnapshotService.java:37-46 and UtilitySnapshotRepository.java:25-41.
4. Conversion: MiningInputBuilder.build consumes only the frozen SnapshotView and MatrixItem values. It validates scale/version/hash shape, row and Product limits, duplicate items, transaction totals, nonnegative utilities, and Store ownership checks performed by the service. It assigns dense Product IDs in sorted order, uses exact scale-6 conversion, losslessly divides by the global GCD, and hashes the exact normalized SPMF text. Percentage minUtil is converted using CEILING against normalized total utility; absolute minUtil is converted against the divisor. See src/main/java/vn/edu/fpt/paspm/mining/MiningInputBuilder.java:11-85.
5. Algorithm dispatch: MiningWorker claims the durable request and each run, then passes the persisted algorithm, frozen Dataset, and same effective integer minUtil to MiningEngine.execute. See src/main/java/vn/edu/fpt/paspm/mining/MiningWorker.java:30-49. MiningEngine defines HUI_MINER and EFIM in src/main/java/vn/edu/fpt/paspm/mining/MiningEngine.java:6-12.
6. Real runner: SpmfMiningEngine.execute creates a temporary SPMF-format transaction file and starts the configured Java executable with ProcessBuilder: java -Xmx... -jar <jar> run HUI-Miner|EFIM <input> <output> <minUtil>. It checks the binary hash, timeout, output bounds, process exit status, and SPMF memory output. See src/main/java/vn/edu/fpt/paspm/mining/SpmfMiningEngine.java:15-80.
7. Result validation and persistence: SpmfMiningEngine.parse checks SPMF output syntax, IDs, duplicate patterns, minUtil, and recomputes each pattern utility from frozen item utilities. MiningRepository.complete writes patterns/items and marks a run completed in one transaction; the request comparison checks exact canonical itemset-to-utility maps. See SpmfMiningEngine.java:87-117, MiningWorker.java:42-45, and MiningRepository.java:48-69.
8. Result API: GET /api/mining/requests/{id}/runs, GET /api/mining/runs/{id}, GET /api/mining/runs/{id}/patterns, and GET /api/mining/runs/{id}/benchmark expose persisted run state, raw Product IDs/utilities, and metrics.

No F06 rule engine or final bundle/layout consumer was found in the code graph or source. F05 returns raw patterns and handoff documentation; it does not approve recommendations.

## D. SPMF Dependency Findings

- **Integration type:** External SPMF JAR through ProcessBuilder. No direct SPMF Java imports and no SPMF Maven dependency appear in pom.xml.
- **Local JAR actually exercised:** /Users/khoinm/Desktop/khoi/fpt/ki9/FA26SE048_HUPAS/spmf_run/spmf.jar. MiningIntegrationTest configures that sibling path for tests. The file is outside the BE Git repository and is not bundled into the backend artifact.
- **Version and pin:** Source labels it SPMF_2.67a and pins SHA-256 4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a. The current file's SHA-256 matched. The actual classes ca/pfv/spmf/algorithms/frequentpatterns/hui_miner/AlgoHUIMiner.class, ca/pfv/spmf/algorithms/frequentpatterns/efim/AlgoEFIM.class, and ca/pfv/spmf/gui/Main.class have Java class major version 69, requiring Java 25.
- **Origin:** The sibling spmf_run/README.md says the JAR was downloaded from SPMF's official download page. The local file matches the application's pinned hash and its output carries the SPMF source banner. The downloaded file itself has no separately verified upstream signature/checksum in this repository, so chain-of-custody from the upstream publisher is not independently authenticated.
- **Runtime configuration:** SpmfMiningEngine reads paspm.mining.spmf-jar and paspm.mining.java-executable. The application defaults the JAR path to empty and returns 503 until a trusted absolute JAR and executable are configured. The Java executable defaults to the backend JVM's java.home. Therefore, running the backend on Java 17 requires a separately configured Java 25 child executable; Java 17 cannot load this binary.
- **Reproducibility:** The algorithm version/hash and configuration requirements are documented, but a clean checkout cannot mine until an operator separately obtains the exact binary and configures Java 25. The runtime binary and two datasets are local workspace siblings rather than version-controlled BE dependencies. The test suite also assumes the sibling JAR path unless overridden.

**Finding:** F05 uses a real, locally downloaded SPMF binary via an external process. This is not a mock, simulation, direct Java API dependency, or custom algorithm. The project file describes Java 17 for the backend, while the selected SPMF process binary needs Java 25. These are separate runtimes; backend compilation remains Java 17.

## E. Original Document Compliance Matrix

The original DOCX contains the research objective and mining/benchmark requirements under its Mining Module and Mining Engine Configuration & Benchmark Hub sections, plus Phase 1–4 deliverables. The later approved PASPM architecture and the F05 backlog are distinguished below.

| Requirement | DOCX expectation | Current implementation | Evidence | Status |
|---|---|---|---|---|
| HUI-Miner | Execute HUI-Miner and expose discovered patterns | SPMF HUI-Miner is selected by algorithm enum and actually invoked | SpmfMiningEngine.java:49; SpmfMiningEngineTest.actualAlgorithmsProduceKnownEightPatternsAndEqualUtilities | PASS |
| EFIM | Execute EFIM and expose discovered patterns | SPMF EFIM has a distinct command dispatch and actually runs | SpmfMiningEngine.java:49; same real-algorithm test | PASS |
| SPMF/library reuse | Use SPMF or a justified genuine implementation; document integration | Uses a pinned external SPMF JAR, not a Maven dependency; binary must be configured separately | pom.xml; SpmfMiningEngine.java:15-40; test environment record below | PASS for library choice; PARTIAL for clean-environment provisioning |
| Real algorithm execution | Stable mining, actual itemsets, errors visible | Real subprocess, bounded output/timeout, parser rejects invalid output; errors persist as failed runs | SpmfMiningEngine.java:42-80, 87-127; fresh real test | PASS |
| Core Mining API | Configure, execute, inspect actual results | 8 actual operations over 7 paths: 1 POST and 7 GET; supports one/both algorithms and percentage/absolute minUtil | MiningController.java:15-38; docs/api/openapi.json; 23 historical interactive Swagger cases | PASS |
| Runtime benchmark | Compare execution time in same conditions | Child-process wall time includes JVM startup and file I/O, excludes shared input prep; one cold sequential run per algorithm | SpmfMiningEngine.java:49-66; BenchmarkView states one cold run | PARTIAL |
| Memory benchmark | Compare RAM | Parses SPMF in-process heap logger output, labeled sampled Java heap; not RSS; not repeated or independently cross-calibrated | SpmfMiningEngine.java:20, 61-66; fresh output below | PARTIAL |
| Itemset count | Report discovered itemset count | Count equals parsed/persisted patterns for each run | MiningRepository.java:53; fresh results and integration test | PASS |
| Multiple minUtil experiments | Run experiments at several thresholds for the RBL report | API accepts multiple threshold values and tests absolute 30/100, but no structured multi-threshold, repeated benchmark suite exists | MiningContracts.java:16-22; F05 test suite and benchmark endpoint | PARTIAL |
| Public datasets | Prepare 2–3 public datasets and cleaning/reproduction process | Workspace sibling spmf_run contains public Foodmart and Ecommerce files, hashes, compact-ID mapping, scripts, and raw logs/results. These are not in the BE repository or F05 API evidence. Current F05 row/Product limits prevent using these datasets through the F03-to-F05 API | spmf_run/README.md; dataset counts in Section F; MiningInputBuilder.java:14-15 | PARTIAL |
| F03 integration | Mine the approved frozen Utility Matrix, without recalculating scores | Reads persisted utility_matrix_item weighted_utility and frozen snapshot/config; captures request input and provenance before queueing | MiningService.java:34-46; UtilitySnapshotRepository.java:25-41; mutation/frozen-copy integration tests | PASS |
| F06 handoff | Supply raw HUI patterns for later recommendation logic | API supplies run/request/snapshot/Store/algorithm IDs, Product IDs, utility and size; support_count is null because it is not measured. No F06 consumer is implemented | MiningContracts.java:30-39; F05_F06_HANDOFF.md; source scan | PASS for handoff contract; PARTIAL for end-to-end F06 integration |
| Scheduled mining | Weekly/monthly scheduled jobs are described for System Admin | No scheduler or recurring job API; recorded as deferred P1 in F05 handoff | F05_F06_HANDOFF.md; MiningWorker.java is request-queue based | FAIL against DOCX requirement; deferred by current F05 scope |
| Database persistence | Persist requests, runs, patterns and traceable Product mapping | V9 adds request/run/pattern/item tables, snapshot and same-Store composite keys, metrics and immutable input/config snapshots | V9__f05_mining_engine.sql:1-84; MiningRepository.java:20-75 | PASS on tested schema; clean MySQL deployment PARTIAL due inherited V7 blocker |
| RBAC and Store isolation | Authorized Store users only; no cross-Store data | Merchandiser creates; Merchandiser/Store Admin read; queries and composite keys are Store-scoped | MiningController.java:17-25; service/repository; automated and Swagger security cases | PASS |
| Swagger/OpenAPI | API and user workflow should be demonstrable | Runtime OpenAPI has 7 F05 paths/8 operations; prior actual Swagger UI verification executed 23 cases; today's build also ran API/OpenAPI tests | F05_VERIFICATION.md and evidence/UI01–UI23; MiningIntegrationTest.openApiIncludesEveryMiningRoute | PASS for API contract and historical UI tests; UI not re-run today |

**Architecture assessment:** The DOCX's older ASP.NET proposal is not counted as an F05 violation. The current PASPM backend is Java/Spring Boot and F05 follows that approved architecture.

## F. Algorithm, Dataset, and Benchmark Evidence

### Fresh verification

Command run on 2026-10-20:

mvn -Dpaspm.avatar.directory=/tmp/paspm-audit-avatars -Dpaspm.f05.export-dir=/tmp/paspm-audit-f05-examples verify

Result: BUILD SUCCESS; 113 tests, 0 failures, 0 errors, 0 skipped. Java runtime in Maven logs: 25.0.1. Flyway validated and applied V1–V9 on isolated in-memory H2. This verifies test migration compatibility, not a clean MySQL deployment.

F05-specific tests in that suite:

- MiningInputTest: 3 tests.
- SpmfMiningEngineTest: 5 tests.
- MiningIntegrationTest: 15 tests.
- Total F05 tests: 23, all passed.

SpmfMiningEngineTest invoked both algorithms as actual child processes. The fixture has 5 transactions, 7 dense item IDs, total scaled utility 96, and absolute minUtil 30. Both algorithms returned the same eight itemsets with the same utilities: {2,4}=30, {2,5}=31, {1,3,5}=31, {2,3,4}=34, {2,3,5}=37, {2,4,5}=36, {2,3,4,5}=40, and {1,2,3,4,5,6}=30. The expected map is declared in the test, and the parser independently recomputes each utility from the same fixture.

Fresh Surefire process output measured:

| Algorithm | Patterns | Child wall time | SPMF sampled Java heap |
|---|---:|---:|---:|
| HUI-Miner | 8 | 86 ms | 10.622848510742188 MiB |
| EFIM | 8 | 88 ms | 11.122848510742188 MiB |

These values are real, but the dataset is deliberately tiny, the runtime difference is 2 ms, and the experiment performs one cold sequential run per algorithm. They are not reliable evidence that one algorithm is faster or more memory-efficient. No repeated runs, warmup, confidence interval, process RSS, production-scale F03 snapshot, or final algorithm recommendation was measured.

MiningIntegrationTest.realSnapshotToBothAlgorithmsPersistsEightPatternsAndBenchmark exercises the real Spring MockMvc API path against H2: it creates a synthetic F03 Utility Snapshot, posts a mining request, waits for the actual worker, reads both runs/patterns/benchmark, checks persistence and cross-Store isolation, changes live Product weights/prices and confirms the frozen input remains unchanged. This test uses real SPMF execution; other lifecycle tests intentionally use controlled test doubles to test queue/failure handling.

### Public dataset evidence and input-limit mismatch

The workspace sibling spmf_run contains two public SPMF utility datasets and a separate reproducible script/log set:

| Dataset | Transactions | Item rows | Distinct items | Stored standalone result at threshold |
|---|---:|---:|---:|---|
| Foodmart utility | 4,141 | 18,319 | 1,559 | minUtil 8,000; 769 itemsets |
| Ecommerce utility | 14,975 | 174,354 | 3,468 | minUtil 1,500,000; 86 itemsets |

Source URLs and original-file hashes are in spmf_run/README.md. The standalone script invokes both SPMF algorithms on the same file and threshold, and results/logs exist under spmf_run/results. This is useful RBL groundwork, but it is outside the BE Git repository and separate from the F05 API.

F05 currently caps input at 20,000 matrix rows and 256 distinct Products in MiningInputBuilder.java:14-15. Foodmart fits the row cap but exceeds the Product cap; Ecommerce exceeds both. Therefore, neither public dataset can currently be evaluated through the F03 snapshot → F05 API path. The standalone SPMF runs do not prove that F05 can consume those datasets. This is an important gap for the document's dataset-based research objective.

### minUtil experiment assessment

The API can create a new request for a chosen PERCENTAGE or ABSOLUTE threshold. Conversion is explicit and persisted, and both algorithms in a two-algorithm request receive the same integer threshold. Fresh tests exercise absolute 30 and 100; historical Swagger evidence includes a percentage threshold that resolves to absolute 30. That proves the feature can vary thresholds, but it does not provide a systematic experiment matrix over public data. The sibling standalone runs compare Foodmart at 8,000 and Ecommerce at 1,500,000, which are different datasets and cannot be compared as a minUtil sweep. The original document's RBL experiments remain incomplete.

## G. Database, API, and Security Readiness

### Database

V9 creates mining_request, mining_run, high_utility_pattern, and pattern_item. Composite Store foreign keys tie a request to a Store-owned Utility Snapshot, a run to a same-Store request, patterns to same-Store runs, and pattern items to same-Store Products. Utilities, algorithm, effective integer threshold, snapshot/input hashes, frozen input/config, conversion and measurement methods, count, timestamps, and failure information are persisted. Pattern persistence and run completion share a transaction; an automated failure test confirms partial patterns roll back.

Fresh Maven verification ran all migrations on H2. Existing F05 evidence records V9 success after starting from a prepared MySQL baseline v8. Existing evidence also records that clean MySQL 8.4 V1–V9 startup is blocked by the earlier V7 migration's unquoted reserved column row_number (src/main/resources/db/migration/V7__f04_transaction_import_pipeline.sql:95). Do not report a clean MySQL install as passing. This is an inherited F04 migration defect that blocks provisioning a new database for F05.

### API and OpenAPI

Current F05 operations (7 paths, 8 HTTP operations):

| Method | Route | Access |
|---|---|---|
| GET | /api/mining/snapshots | MERCHANDISER, STORE_ADMIN |
| POST | /api/mining/requests | MERCHANDISER |
| GET | /api/mining/requests | MERCHANDISER, STORE_ADMIN |
| GET | /api/mining/requests/{id} | MERCHANDISER, STORE_ADMIN |
| GET | /api/mining/requests/{id}/runs | MERCHANDISER, STORE_ADMIN |
| GET | /api/mining/runs/{id} | MERCHANDISER, STORE_ADMIN |
| GET | /api/mining/runs/{id}/patterns | MERCHANDISER, STORE_ADMIN |
| GET | /api/mining/runs/{id}/benchmark | MERCHANDISER, STORE_ADMIN |

The request accepts requestKey, snapshotId, one or both distinct algorithms, minUtilMode, and minUtilValue. It returns 202 with a durable request ID and provenance. Store ID is never accepted from the client. Request validation, idempotency conflict, missing dependency, Store 404, authentication 401, and role 403 behaviors are covered by tests and prior Swagger execution.

The existing F05 verification report records 23 actual Swagger UI cases executed 2026-10-19 through Authorize → Try it out → Execute, including create, wait/read, both runs, pattern results, benchmark, invalid threshold, idempotency, roles, no/invalid token, cross-Store 404, and read after backend restart. This audit did not repeat the interactive browser session; today's fresh verification was Maven-based.

### F06 handoff

A raw PatternView contains patternId, runId, Product IDs, exact frozen weighted utility, normalized integer utility, pattern size, and nullable supportCount. Request/run records retain Store, snapshot, algorithm, threshold, version, input hash, and run metrics. This is sufficient for a later F06 Rule Engine to consume raw patterns while keeping business approval separate. Support is honestly reported as unavailable. No automatic bundle approval or storefront modification occurs.

## H. Issues and Risks

| Priority | Finding | Effect |
|---|---|---|
| BLOCKER | Fresh MySQL deployment is stopped by V7's unquoted row_number column | A clean MySQL database cannot reach V9/F05. H2 and prepared-baseline V9 evidence do not eliminate this blocker. |
| HIGH | External SPMF JAR is absent from the backend repo/artifact and needs Java 25 | A new deployment has no mining until the exact hash-pinned JAR and Java 25 child runtime are provisioned. Java 17 alone cannot run this binary. |
| HIGH | F05 caps input at 20,000 rows and 256 Products | Foodmart and Ecommerce public datasets in the workspace exceed the Product cap; Ecommerce exceeds both caps. The primary research datasets cannot be run through F05 API. |
| HIGH | Benchmark evidence is insufficient for the research comparison | One tiny synthetic fixture and one cold run per algorithm do not support choosing a winner. Memory is sampled Java heap, not RSS. |
| MEDIUM | No scheduled mining jobs | The original document describes weekly/monthly scheduled runs. Current F05 handoff explicitly defers scheduler as P1. |
| MEDIUM | Public dataset scripts/data/results are outside the BE repository | Workspace artifacts are useful but a clean clone cannot reproduce the research run from the feature branch alone. |
| MEDIUM | No F06 consumer exists yet | F05 provides raw patterns and a handoff contract; end-to-end recommendation remains unverified until F06 exists. |
| LOW | Legacy matrixHash is retained as provenance but is not recomputed from historical DTO serialization | F05 checks its shape, matrix totals, and transaction sums, then hashes normalized mining bytes. Full reauthentication of legacy matrix serialization is not performed. |

## I. Prioritized Remediation Plan — Proposal Only

No code was changed during this audit. The following changes need approval before implementation.

### P0 — Make a clean MySQL deployment possible

- **Issue/root cause:** V7 declares row_number unquoted, which MySQL 8.4 parses as a reserved keyword.
- **Affected code:** src/main/resources/db/migration/V7__f04_transaction_import_pipeline.sql:95 and fresh-install migration path.
- **Suggested correction:** Do not edit an applied migration in-place. Prepare a forward-compatible repair strategy for existing and fresh databases, with a versioned follow-up migration or supported migration baseline plan; validate against real isolated MySQL.
- **Expected tests:** Fresh MySQL V1–latest migration, upgrade from a database already at V7/V8, Flyway checksum/validation, startup and F05 schema queries.

### P0 — Define supported SPMF runtime provisioning

- **Issue/root cause:** F05 requires an out-of-repository pinned binary and Java 25 child JVM despite Java 17 backend target.
- **Affected code:** SpmfMiningEngine configuration and developer/deployment setup.
- **Suggested correction:** Approve a reproducible dependency distribution/setup method, upstream provenance/licensing documentation, and explicit runtime checks. Do not silently replace the binary or lower class-file versions.
- **Expected tests:** Clean-machine setup; missing/wrong-hash/wrong-JVM behavior; real HUI-Miner and EFIM execution with the documented configuration.

### P1 — Make research datasets usable through the F03 → F05 path

- **Issue/root cause:** Current limits reject the actual dataset sizes; the standalone scripts bypass F03 snapshots and the F05 API.
- **Affected code:** MiningInputBuilder.MAX_ROWS/MAX_ITEMS and dataset import/snapshot workflow.
- **Suggested correction:** Agree target dataset sizes and resource budgets, then set defensible configurable limits or a scalable input strategy. Keep memory/time bounds and prevent overload.
- **Expected tests:** End-to-end Foodmart and Ecommerce snapshot mining with identical input/minUtil for both algorithms; product mapping round trip; row/product limits; timeout and resource bounds.

### P1 — Establish a reproducible benchmark protocol

- **Issue/root cause:** Only one cold execution per algorithm is recorded for F05's tiny fixture; historical standalone results cover different datasets and thresholds.
- **Affected code:** Mining benchmark storage/API and research experiment scripts/report data.
- **Suggested correction:** Agree warmups, repetitions, threshold sweep, dataset versions/hashes, ordering/randomization, runtime boundary, memory metric, and export format. Preserve raw run records. Compare identical frozen input and effective integer minUtil.
- **Expected tests:** Repeated same-dataset runs, several thresholds, both algorithms per threshold, exact pattern-count/result verification, stable metric provenance, export/reproduction check.

### P2 — Add recurring scheduling if the original requirement remains in scope

- **Issue/root cause:** There is only a request queue; no weekly/monthly scheduler or admin policy endpoint.
- **Affected code:** MiningWorker and future scheduling/configuration contract.
- **Suggested correction:** Approve scheduler scope and idempotency/retry/retention behavior before implementation.
- **Expected tests:** Schedule authorization, duplicate suppression, Store isolation, misfire/restart behavior, retry and audit logs.

### P2 — Validate the raw result contract with F06

- **Issue/root cause:** F06 consumer is not present, and support is intentionally unavailable.
- **Affected code:** F05_F06_HANDOFF.md and future F06 integration.
- **Suggested correction:** Keep F05 patterns raw; have F06 confirm that utility, Product IDs, provenance and nullable support meet its rule-generation requirements before any contract changes.
- **Expected tests:** F06 reads results only for completed runs, cannot cross Stores, preserves request/snapshot provenance, and never treats patterns as pre-approved bundles.

## J. Files and Evidence Referenced

- Original requirements: /Users/khoinm/Downloads/Copy of FA26_SE048.docx (read directly for this audit).
- F05 source: src/main/java/vn/edu/fpt/paspm/mining/MiningController.java, MiningService.java, MiningInputBuilder.java, MiningWorker.java, MiningEngine.java, SpmfMiningEngine.java, MiningRepository.java.
- F03 source: src/main/java/vn/edu/fpt/paspm/utility/UtilitySnapshotService.java and UtilitySnapshotRepository.java.
- Migrations: src/main/resources/db/migration/V7__f04_transaction_import_pipeline.sql and V9__f05_mining_engine.sql.
- Tests: src/test/java/vn/edu/fpt/paspm/mining/MiningInputTest.java, SpmfMiningEngineTest.java, MiningIntegrationTest.java.
- API evidence: docs/api/openapi.json; docs/api/F05_MINING_API.md; docs/PASPM_F05_High_Utility_Mining/F05_VERIFICATION.md and its evidence/UI01–UI23 files.
- Handoff: docs/frontend_handoff/F05_HANDOFF.md and docs/PASPM_F05_High_Utility_Mining/F05_F06_HANDOFF.md.
- Local standalone research artifacts: ../spmf_run/README.md, train/, run_train.sh, results/. These are outside the BE Git repository.
- Fresh test output: target/surefire-reports/TEST-vn.edu.fpt.paspm.mining.SpfmMiningEngineTest.xml and target/surefire-reports/vn.edu.fpt.paspm.mining.*.txt.

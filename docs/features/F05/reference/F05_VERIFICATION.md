<!-- docs-reorganization:notice -->
> **HISTORICAL — documentation review 2026-10-26.** This verification includes an earlier V7 failure; current recovery runbooks and later evidence are separate. See [current navigation](../../../testing/README.md). Original results and business-rule text below are retained; this notice is not a new execution or approval.
<!-- /docs-reorganization:notice -->

# F05 Implementation & Verification Report

Execution date:2026-10-19, timezone Asia/Ho_Chi_Minh. Backend P0 functionality implemented and verified; clean MySQL provisioning remains **PARTIAL/BLOCKED by existing V7**, described below. No clean-deployment DONE claim. P1 scheduling deliberately deferred. No commit/push/merge performed for this task.

## A. Task status and evidence

| Task | Result | Status | Evidence |
|---|---|---|---|
| F05-01 | Four existing ERD entities implemented in V9; conceptual/logical pages updated | PASS for V9; clean MySQL chain blocked at V7 | evidence/erd-schema-check.json; evidence/mysql-v9-persistence.txt |
| F05-02 | Store-scoped frozen snapshot validator/capture; old/new metadata support | PASS | MiningInputTest; MiningIntegrationTest; evidence/mining-fixture.json |
| F05-03 | Exact scale6 + lossless GCD/int32 conversion; percentage/absolute ceil | PASS | MiningInputTest; UI11/UI21 |
| F05-04 | Actual HUI-Miner2.67a external process | PASS | SpmfMiningEngineTest; UI03/UI05 |
| F05-05 | Actual EFIM2.67a, exact canonical set+utility equality | PASS | SpmfMiningEngineTest; UI22 |
| F05-06 | Durable async states, bounds, idempotency, failures/timeout/recovery | PASS, single instance MVP | MiningIntegrationTest; UI09/UI10/UI23 |
| F05-07 | Atomic full pattern persistence, real Product mapping, bounded pages | PASS | rollback/composite-FK tests; native32patterns104items |
| F05-08 | Eight JWT/RBAC/Store-scoped operations | PASS | UI01–08, UI13–20; runtime OpenAPI |
| F05-09 | Same input/threshold/environment, labeled time/heap, exact comparison | PASS | UI03/UI06/UI22; frozen benchmark JSON |
| F05-10 |113 automated tests (23 F05 +90 existing), real algorithms included | PASS | evidence/automated-test-results.json; Maven summary |
| F05-11 | API, React handoff, examples, runtime OpenAPI, F06 contract | PASS | docs/api/F05_MINING_API.md; frontend_handoff/F05_HANDOFF.md |
| F05-12 | Recurring scheduling backlog recorded, no scheduler implementation | DEFERRED P1 by scope | F05_F06_HANDOFF.md |

## B. Architecture and implementation files

Controller → Service → Repository/Worker → MiningEngine → isolated SPMF process. MiningInputBuilder reads stored weighted utilities only, sorts transactions/Products, losslessly normalizes integers and hashes exact input bytes. A repeatable-read capture freezes the input/mapping and original SnapshotView before enqueueing. The worker never consults current Product weights/expiry/prices or reloads an altered F03 matrix. F04 ingestion and F03 calculations are unchanged.

Modified boundary: `src/main/java/vn/edu/fpt/paspm/mining/MiningEngine.java` (previously unused placeholder). Added in that same package: MiningContracts.java, MiningController.java, MiningInputBuilder.java, MiningRepository.java, MiningService.java, MiningWorker.java, SpmfMiningEngine.java. Added test files in `src/test/java/vn/edu/fpt/paspm/mining/`: MiningInputTest.java, SpmfMiningEngineTest.java, MiningIntegrationTest.java. Added `V9__f05_mining_engine.sql`. No Frontend source changed; preexisting application.yml and v1.2 source changes preserved.

Worker concurrency1, waiting queue8; algorithms for one request run sequentially. Request UUID + normalized payload hash prevents accidental duplicate submissions, including concurrent requests. CAS claims protect durable requests/runs. On startup, unfinished records become FAILED/INTERRUPTED; deliberate retry requires a new UUID. Single application instance is required; no distributed leases or automatic retries. Queue rejection is durable FAILED/QUEUE_FULL. Flyway-disabled F01-only tests do not attempt mining table recovery; production mining requires Flyway-enabled schema.

Raw patterns are not recommendations. F06 uses run/request/snapshot provenance and Store-scoped raw Product IDs/utilities, then separately applies business rules/current inventory/push batches and Merchandiser review. See F05_F06_HANDOFF.md.

## C. Database / ERD verification

V9 creates MINING_REQUEST, MINING_RUN, HIGH_UTILITY_PATTERN and PATTERN_ITEM, preserving approved entity relationships. Adds unique `(store_id,snapshot_id)` to UTILITY_SNAPSHOT for a composite FK; no historical snapshot content is changed. Requests freeze input/mapping, snapshot metadata, threshold, provenance/idempotency and status. Runs preserve algorithm/library/conversion/measurement methods, lifecycle/metrics/failures. Patterns preserve canonical key/hash, size and original/normalized utility; pattern items reference own-Store Products. Support stays NULL.

Store-composite FKs connect snapshot→request→run→pattern→item→Product. Per-Store requestKey, per-request algorithm attempt and per-run canonical key uniqueness prevent duplicates. Check constraints enforce allowed statuses, positive thresholds, nonnegative metrics/utilities and valid sizes. Complete result batch and run COMPLETED status use one transaction; injected partial-write failure rolled back both pattern and pattern_item rows.

Latest editable ERD: `docs/ERD_Diagram/PASPM_Complete_ERD_F03_Three_Weights_v4.drawio`. Complete Conceptual and Logical pages now identify F05 P0/V9; logical mining fields match actual migration columns. Existing F03 zoom request table aligned too. Entity IDs and relationship endpoints preserved; manual requested_by is required (USER→request1:N). Existing other feature entities remain intact. Automated XML/field inventory checks passed; no separate diagrams.net visual rendering was executed.

**H2 full migrations V1–V9: PASS. Native MySQL8.4.11 V9 upgrade: PASS** on a separately prepared isolated baseline8 QA schema. This is distinct from a clean install.

**Fresh MySQL startup: BLOCKED at preexisting V7** (`row_number INT NOT NULL` is rejected as reserved syntax, SQLState42000/MySQL1064). Original V7 was not edited. To isolate V9, QA setup applied existing V1–V8 definitions into a new QA-only schema with row_number quoted only in the setup copy, then Flyway baselined8 and executed original V9. Never apply this baseline procedure blindly to a shared DB. Resolve existing migration provisioning policy separately before a fresh deployment. Evidence files contain sanitized results, no connection credentials.

Native persistence readback after Swagger: two completed MATCH requests, four completed runs,32patterns and104pattern items; snapshot totals and matrix sums both96. UI23 read the same original benchmark after actual process restart without changed runtime/hash/conversion metadata.

## D. Implemented APIs

All use F01 Bearer JWT. MERCHANDISER creates/reads own Store; STORE_ADMIN reads own Store only; SYSTEM_ADMIN/END_CUSTOMER forbidden. Missing/invalid token401, forbidden403, foreign detail404; no client storeId accepted.

| Method | Endpoint | Verification |
|---|---|---|
| GET | /api/mining/snapshots | UI08 200 |
| POST | /api/mining/requests | UI01 202 |
| GET | /api/mining/requests | UI07 200 |
| GET | /api/mining/requests/{id} | UI02 200 |
| GET | /api/mining/requests/{id}/runs | UI03 200 |
| GET | /api/mining/runs/{id} | UI04 200 |
| GET | /api/mining/runs/{id}/patterns | UI05/UI22 200 |
| GET | /api/mining/runs/{id}/benchmark | UI06/UI14/UI23 200 |

Actual OpenAPI52paths/65operations, including7F05paths/8operations. Final runtime export validates minItems1 for algorithms and nullable support/metrics; explicit runtime validators enforce all additional numerical rules. DTO field tables/examples in F05_MINING_API.md.

## E. Actual algorithm correctness

SPMF existing JAR version2.67a, SHA256 `4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a`. No invented Maven coordinate, copied JAR or mock-only integration claim. Verified APIs use int32, and class major69 requires Java25 for child execution. Backend compiled release17; standalone Backend execution on a Java17 runtime was NOT RUN.

Five historical transactions, seven Products, frozen total96. F03 snapshot2 in the native Swagger fixture; analysisDate2026-10-19, F04_DATABASE_V1, scoringVersionEXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1, criteria disabled/Weight1. Actual input text hash `8ce22f131d59e8486c6974733fcc81de0c3caadaf13765399311cdfe3dc32626`,100bytes, scale6/HALF_UP, divisor1000000. minUtil30 (or31.25%) gives final integer30. Official small-fixture ordering was normalized without changing its values.

| Dense source Product IDs | Utility | Native real Product IDs |
|---|---:|---|
| 2,4 | 30 | 9,11 |
| 2,5 | 31 | 9,12 |
| 1,3,5 | 31 | 8,10,12 |
| 2,3,4 | 34 | 9,10,11 |
| 2,3,5 | 37 | 9,10,12 |
| 2,4,5 | 36 | 9,11,12 |
| 2,3,4,5 | 40 | 9,10,11,12 |
| 1,2,3,4,5,6 | 30 | 8,9,10,11,12,13 |

HUI-Miner8patterns, EFIM8patterns; exact canonical Product sets AND utilities match. UI22 independently compared response Product arrays/utilities. Known expected values asserted in real algorithm test. Zero utility and all-zero input with positive absolute threshold actually executed; empty results are valid. Percentage0-total is rejected. Negative/finer-than-scale/overflow inputs rejected before process execution.

Historical linear scoring metadata remains usable without recomputation. Tests changed live Product.expiryPushDays/scores/prices and lambda/coefficients: new live F03 preview changed from96 to137.76 while mining the old snapshot input hash/results remained unchanged. Another test deliberately changed the source matrix after request capture and confirmed the worker still consumed its frozen96-unit copy.

## F. Actual measured performance

Primary native Swagger request1, same input/threshold/default256MiB heap, sequential cold child JVMs on macOS/aarch64 with Java25.0.1:

| Algorithm | Patterns | Runtime ms | SPMF sampled Java heap MiB |
|---|---:|---:|---:|
| HUI-Miner |8|98|10.622848510742188|
| EFIM |8|90|11.122848510742188|

The percentage request separately recorded111ms/92ms with the same heap metrics. These are actual single executions, not a statistically robust speed ranking. Runtime includes JVM startup/mining IO, excludes common input preparation and Backend verification/persistence. Memory is sampled Java heap, NOT process peak RSS. No warmup/repetition suite or production-scale benchmark was run. Environment/binary fingerprint, input size/counts, threshold, conversion and measurement methods are stored.

## G. Automated and interactive verification

Executed: `mvn -Dpaspm.avatar.directory=/tmp/paspm-test-avatars -Dpaspm.f05.export-dir=/tmp/paspm-f05-examples verify` — BUILD SUCCESS,113tests,0failures,0errors,0skips. Maven3.9.12 / Java25.0.1, compiler release17.23new F05 test methods plus90existing F01–F04/v1.2 tests. Full sanitized case inventory: evidence/automated-test-results.json. No dependency-required test was skipped or marked PASS without execution.

F05 groups: MiningInputTest3 (deterministic scale/mapping/hash; rejection/precision/overflow; thresholds/zero policy), SpmfMiningEngineTest5 (real known outputs; real zero/all-zero/empty outputs; real timeout/missing/failed executable; malformed output; interrupted cleanup), MiningIntegrationTest15 (real F03→SPMF→DB/API; RBAC/isolation/strict DTOs; legacy metadata; OpenAPI; capacity/concurrent idempotency; persisted provenance; recovery/CAS; frozen copy; atomic rollback; failures/timeouts; comparison mismatch; invalid snapshots; composite FKs; valid empty results). Detailed exact method names are in the evidence JSON.

Initially full-suite startup exposed the mining recovery listener querying tables in Flyway-disabled F01-only tests. Fixed by honoring that explicitly disabled migration context; all22F01 tests pass without F01 source/test edits. Independent review found cleanup-interruption and historical-provenance issues; regression tests reproduced them, fixes passed and followup review reported no remaining actionable defects. Also corrected SpringDoc algorithm minItems/nullable metadata against runtime schema assertions.

Interactive Swagger: **23PASS,0FAIL,0BLOCKED,0NOT RUN among the23 executed cases below**. Mechanism is Authorize→Try it out→Execute in the actual Swagger UI, not curl/Postman. Tokens came from synthetic isolated QA users through existing F01 token issuance; password/OTP login UI itself was not tested in this F05 run. Credentials/tokens are excluded from evidence. Additional failure/timeout/corrupt-input permutations were automated Backend tests, not all repeated through UI.

| ID | Case | Method / endpoint | Expected / actual HTTP | Result |
|---|---|---|---|---|
| UI01-create-both | MERCHANDISER | POST /api/mining/requests | 202 / 202 | PASS |
| UI02-request-completed | MERCHANDISER | GET /api/mining/requests/{id} | 200 / 200 | PASS |
| UI03-runs | MERCHANDISER | GET /api/mining/requests/{id}/runs | 200 / 200 | PASS |
| UI04-run-detail | MERCHANDISER | GET /api/mining/runs/{id} | 200 / 200 | PASS |
| UI05-patterns | MERCHANDISER | GET /api/mining/runs/{id}/patterns | 200 / 200 | PASS |
| UI06-benchmark | MERCHANDISER | GET /api/mining/runs/{id}/benchmark | 200 / 200 | PASS |
| UI07-request-list | MERCHANDISER | GET /api/mining/requests | 200 / 200 | PASS |
| UI08-snapshot-list | MERCHANDISER | GET /api/mining/snapshots | 200 / 200 | PASS |
| UI09-idempotency | MERCHANDISER | POST /api/mining/requests | 202 / 202 | PASS |
| UI10-key-conflict | MERCHANDISER | POST /api/mining/requests | 409 / 409 | PASS |
| UI11-invalid-percentage | MERCHANDISER | POST /api/mining/requests | 400 / 400 | PASS |
| UI12-forged-store | MERCHANDISER | POST /api/mining/requests | 400 / 400 | PASS |
| UI13-admin-cannot-create | MERCHANDISER | POST /api/mining/requests | 403 / 403 | PASS |
| UI14-admin-read-benchmark | MERCHANDISER | GET /api/mining/runs/{id}/benchmark | 200 / 200 | PASS |
| UI15-cross-store-patterns | MERCHANDISER | GET /api/mining/runs/{id}/patterns | 404 / 404 | PASS |
| UI16-cross-store-list | MERCHANDISER | GET /api/mining/requests | 200 / 200 | PASS |
| UI17-customer-forbidden | MERCHANDISER | GET /api/mining/requests | 403 / 403 | PASS |
| UI18-system-admin-forbidden | MERCHANDISER | GET /api/mining/requests | 403 / 403 | PASS |
| UI19-invalid-token | MERCHANDISER | GET /api/mining/requests | 401 / 401 | PASS |
| UI20-no-token | MERCHANDISER | GET /api/mining/requests | 401 / 401 | PASS |
| UI21-percentage-threshold | MERCHANDISER | POST /api/mining/requests | 202 / 202 | PASS |
| UI22-efim-patterns | MERCHANDISER | GET /api/mining/runs/{id}/patterns | 200 / 200 | PASS |
| UI23-history-after-restart | MERCHANDISER | GET /api/mining/runs/{id}/benchmark | 200 / 200 | PASS |

Every case has sanitized request inputs, actual body, date/time/timezone and status in evidence/swagger-ui-results.json; sanitized Swagger rendered text in UI*.txt. POST persistence verified through subsequent GETs, native SQL readback and idempotency counts. The response screenshot evidence/swagger-efim-patterns-200.jpg shows HTTP200 and actual normalized patterns without Authorization headers.

## H. Documents and handoff

- F05_GAP_ANALYSIS.md: initial source audit, numerical/execution decisions and dependency verification.
- `docs/superpowers/plans/2026-10-19-f05-mining.md`: Superpowers implementation/test plan.
- `docs/api/F05_MINING_API.md`: all real endpoint/DTO/type/validation/error/role contracts.
- `docs/frontend_handoff/F05_HANDOFF.md`: React integration order, screen mapping, polling, forms, states/errors, limitations.
- `docs/frontend_handoff/F05_examples/`: actual sanitized UI request/response JSON, including percentage/errors.
- `docs/api/openapi.json`: complete final runtime OpenAPI, not manually fabricated.
- `docs/api/API_CHANGELOG.md`: F05 addition and deployment caveats; existing entries preserved.
- F05_F06_HANDOFF.md: raw-pattern consumer contract and deferred F05-12 backlog.
- F05_CODEGRAPH_REPORT.md: executed architecture exploration/index synchronization.
- Updated existing F03/F05 boundary documents to reference the implemented adapter, preserving v1.2 rules.
- Existing editable ERD updated in place; no duplicate mining entities.

## I. Remaining limitations / blockers

1. **Fresh MySQL provisioning blocker**: preexisting V7 reserved identifier. V9 itself passes native MySQL, but full new MySQL deployment is not declared DONE. Existing applied migrations were not rewritten.
2. Pinned external SPMF JAR needs Java25 child runtime and the exact trusted fingerprint. Missing/unverified dependency503; wrong executable compatibility produces a failed run. Java17-only algorithm execution is incompatible with this JAR, and a Java17 Backend runtime execution was not performed (release17 build passed).
3. Single-instance bounded executor; no distributed leasing, recurring schedules, cancellation or auto-retry. Crash recovery is deterministic failure plus explicit resubmission, not durable queue continuation.
4. Legacy matrixHash remains original version-specific provenance; F05 validates totals/metadata and freezes/hashes normalized bytes, but does not cryptographically reconstruct old DTO serialization.
5. Support remains unmeasured/null; memory is sampled heap. No native-memory/RSS or repeated production-scale performance claim.
6. F06 recommendations and Frontend implementation are out of scope. Raw output and independent expiry-push data are ready for a later approved F06 consumer.

These limitations are also included in the API/Frontend handoff so integration does not rely on unsupported behavior.

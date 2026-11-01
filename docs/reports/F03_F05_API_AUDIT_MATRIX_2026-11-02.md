# F03 / F05 API audit matrix — 2026-11-02

> **Post-merge note (2026-11-02).** This document describes the state at the Core release candidate. The branch was merged into `dev` as PR #11 (`11973a0`); the Backend source tree on `dev` is identical to the verified commit `a4f1115`. Statements below such as "not pushed" or "GitHub CI NOT_RUN" describe the time of writing; the GitHub results of PR #11 were not inspected afterwards.

Scope: every F03 and F05 HTTP operation on branch `Khoi/feat/f03-f05-admin-mining` at `0a2d27e` plus the new role-matrix test (uncommitted at the time of writing). Sources read: `docs/FA26_SE048.pdf` requirements as summarised in the [PO package](../decisions/PO_DECISION_PACKAGE_D1_D10_2026-11-01.md), the controllers and `SecurityConfig`, the tests named below, and the verification run recorded in "Evidence" at the end. Nothing here approves a business policy.

Requirement basis: `FA26_SE048.pdf` gives the Data/System Administrator three F05 duties — **R-F05-1** configure `minUtil` and the active algorithm, **R-F05-2** scheduled mining that re-scans transactions weekly or monthly, **R-F05-3** a benchmark view comparing HUI-Miner and EFIM (run time, RAM). F03 follows the Weight Rules v1.2 as documented in [F03_UTILITY_API.md](../api/F03_UTILITY_API.md); the stand-alone `F03_WEIGHT_RULES_v1_2.md` is **not tracked in the repository** (documented gap in `docs/requirements/README.md`), so the written API contract is the only available v1.2 source. The PDF contains no sentence about System Admin reading Store results, choosing a Store, time zones, lookback, catch-up or retry; these are decisions D1–D10.

Result vocabulary: **PASS** = exercised by an executed test in the evidence run; **NOT RUN** = no executed test in this evidence; **PENDING-PO** = behaviour exists but its business policy is unapproved.

## F03 — Utility profiling

| # | Operation (method, route) | Business requirement | Actor | Request → response DTO | Implementation | Authorization (enforced) | Tests | Result | Missing / unresolved |
|---|---|---|---|---|---|---|---|---|---|
| F03-1 | GET `/api/system/utility/lambda-policy` | Global λ bounds | SYSTEM_ADMIN | – → `LambdaPolicy` | `UtilityConfigurationController` → `UtilityConfigurationService` → `UtilitySettingsRepository` | `@PreAuthorize hasRole('SYSTEM_ADMIN')` | `UtilityIntegrationTest.separatesPolicy…`, `F03F05RbacMatrixTest` | PASS | – |
| F03-2 | PUT `/api/system/utility/lambda-policy` | Change bounds, version increments, no clamping of Store values | SYSTEM_ADMIN | `PolicyRequest` → `LambdaPolicy` | same | SYSTEM_ADMIN | `UtilityIntegrationTest.rejectsInvalidBounds…`, `snapshotFreezes…AndRejectsStaleLambda` | PASS | – |
| F03-3 | GET `/api/utility/config` | Store weight configuration read | STORE_ADMIN, MERCHANDISER | – → `ConfigView` | same service | `hasAnyRole` | `UtilityIntegrationTest`, `F03F05RbacMatrixTest` | PASS | – |
| F03-4 | PUT `/api/utility/config` | α/β/γ, λ, enable flags; coefficients sum to 1 | MERCHANDISER | `WeightRequest` → `ConfigView` | same | `hasRole('MERCHANDISER')` | `UtilityIntegrationTest.rejectsInvalid…`, `…separatesPolicy…` | PASS | – |
| F03-5 | PUT `/api/utility/config/expiry-window` | Legacy Store window (deprecated, no scoring effect) | STORE_ADMIN | `ExpiryWindowRequest` → `ConfigView` | same | `hasRole('STORE_ADMIN')` | `UtilityIntegrationTest`, `WeightRulesV12IntegrationTest.storeAdminCanConfigureThreshold…` | PASS | Deprecated by design; Frontend must use F02 `expiryPushDays` |
| F03-6 | GET `/api/utility/products/{id}/scores` | Current S/M, E and W preview | STORE_ADMIN, MERCHANDISER | – → `Scores` | `ProductScoreController` → `ProductScoreService` → `ProductScoreRepository`, `ExpiryScoreCalculator`, `UtilityMath` | `hasAnyRole` | `UtilityIntegrationTest.scorePatch…`, `WeightRulesV12IntegrationTest` | PASS | – |
| F03-7 | PATCH `/api/utility/products/{id}/scores` | Stock Priority Score and Seasonal Priority Score ∈ [0,1], partial update | MERCHANDISER | `ScorePatch` → `Scores` | same | MERCHANDISER; foreign Product → 404 | `UtilityIntegrationTest.scorePatchIsPartialValidatedAndStoreScoped` | PASS | – |
| F03-8 | POST `/api/utility/preview` | Utility Preview without persistence | MERCHANDISER | `PreviewRequest` → `MatrixPage` | `UtilitySnapshotController` → `UtilitySnapshotService` → `UtilityMatrixBuilder`, `TransactionSource` (F04) | class-level MERCHANDISER | `UtilityIntegrationTest.weightReportExamples…`, `previewRejects…`, `UtilityCalculationTest`, `ExpiryBinaryRulesTest` | PASS | – |
| F03-9 | POST `/api/utility/snapshots` | Immutable Utility Snapshot with frozen config, evidence, hashes | MERCHANDISER | `RangeRequest` → `SnapshotView` (201) | same + `UtilitySnapshotRepository` | MERCHANDISER | `UtilityIntegrationTest.snapshotFreezes…`, `persistenceFailureRollsBackSnapshotHeader`, `WeightRulesV12IntegrationTest.snapshotFreezesEvidence…` | PASS | MySQL variants are opt-in (`RetailCatalogUtilityMySqlIntegrationTest`, `mysql` group, ran in the evidence run) |
| F03-10 | GET `/api/utility/snapshots/{id}` | Read frozen settings without recomputation | MERCHANDISER | – → `SnapshotView` | same | MERCHANDISER; foreign → 404 | `UtilityIntegrationTest.expirySnapshotsKeepOriginal…`, `WeightRulesV12IntegrationTest.oldLinearSnapshotsStayReadable…` | PASS | No list/update/delete snapshot endpoint (by design); Store snapshot *listing* is `GET /api/mining/snapshots` (F05) |
| F03-11 | GET `/api/utility/snapshots/{id}/matrix` | Frozen matrix items, paged | MERCHANDISER | `page`,`pageSize` → `MatrixPage` | same | MERCHANDISER | `UtilityIntegrationTest.matrixHashIgnoresPreparedSourceOrdering`, `UtilitySnapshotRoutingTest` | PASS | – |
| F03-12 | GET `/api/utility/expiry-push-batches` | FEFO push batches (F06 input) | STORE_ADMIN, MERCHANDISER | optional `productId`,`asOfDate`,`page`,`pageSize` → `PushPage` | `ExpiryPushController` → `ExpiryPushService` | `hasAnyRole` | `WeightRulesV12IntegrationTest.independentPushQueryUsesFefo…`, `RecommendationExpiryIntegrationTest` | PASS | – |

F03 rules re-checked against source in this audit:

| Rule | Where | Evidence |
|---|---|---|
| FEFO = `expiry_date`, then `received_at`, then `batch_id` | `UtilityProductRepository` line 28: `ORDER BY b.expiry_date,b.received_at,b.batch_id` | `ExpiryFefoOrderingTest.sameExpiryUsesOldestReceiptThenBatchIdWithinTheStore`, `ExpiryBinaryRulesTest` |
| Binary E: `E=1` iff `T>0` and earliest daysRemaining ≤ T; quantity ratios never dilute | `ExpiryScoreCalculator` | `ExpiryBinaryRulesTest` (boundary inclusive, T=0/null, negative invalid) |
| Product-level `expiryPushDays`, no Store fallback | `UtilityProductRepository`, `ExpiryScoreCalculator` | `WeightRulesV12IntegrationTest.nullAndOmittedThresholdHaveEffectiveZero` |
| `W = 1 + λ(αS + βE + γM)`, scale 6 `HALF_UP` | `UtilityMath` | `UtilityCalculationTest`, `UtilityFixtureTest` |
| Immutable snapshots; historical scoring versions preserved | `UtilitySnapshotRepository`, snapshot storage tables | `oldLinearSnapshotsStayReadableAndTheirStoredValuesNeverChange` |
| Rollback on failed persistence | `UtilitySnapshotService` (transaction) | `persistenceFailureRollsBackSnapshotHeader` |

## F05 — Mining

| # | Operation | Business requirement | Actor | DTO | Implementation | Authorization (enforced) | Tests | Result | Missing / unresolved |
|---|---|---|---|---|---|---|---|---|---|
| F05-1 | GET `/api/system/mining/config` | R-F05-1 read active algorithm/minUtil | SYSTEM_ADMIN | → `MiningAdministrationController.View` | `MiningAdministrationController` (JDBC: `mining_config_head`, `mining_config_version`) | URL rule + class `@PreAuthorize` | `MiningAdminConfigurationTest.systemAdminVersionsConfig…`, `F03F05RbacMatrixTest` | PASS | – |
| F05-2 | PUT `/api/system/mining/config` | R-F05-1 versioned config with optimistic locking | SYSTEM_ADMIN | `Request{algorithm,minUtilMode,minUtilValue,expectedVersion}` → `View` | same; `SELECT … FOR UPDATE` on the head row, 409 on stale version | same | `MiningAdminConfigurationTest` (409 and success path) | PASS | – |
| F05-3 | POST `/api/system/mining/requests` | Manual Store-targeted mining using the pinned policy | SYSTEM_ADMIN | `ManualRequest{storeId,snapshotId,requestKey,compareAlgorithms}` → `RequestView` (202) | `MiningSystemController` → `MiningService.createAdministrative` → `MiningRepository`, `MiningInputBuilder` | SYSTEM_ADMIN | `MiningIntegrationTest.systemAdminSubmitsFrozenPolicyForAnExplicitStore…`, `concurrentDuplicateSubmissions…`, `MiningHttpMySqlTest` | PASS | Body shape was undocumented in `F05_MINING_API.md` (fixed in this change) |
| F05-4 | GET `/api/system/mining/stores/{storeId}/runs/{runId}/benchmark` | R-F05-3 benchmark | SYSTEM_ADMIN | → `BenchmarkView` | `MiningService.benchmarkForStore` | SYSTEM_ADMIN (not gated by D1) | `MiningIntegrationTest.realSnapshotToBothAlgorithms…`, `exactResultMismatch…` | PASS | `runId` is only discoverable through the D1-gated routes (see D1) |
| F05-5 | GET `/api/system/mining/stores/{storeId}/snapshots` | Pick a snapshot for a manual run | SYSTEM_ADMIN | `Page<SnapshotView>` | `MiningSystemController` + `SystemStoreReadPolicy.SNAPSHOTS` | SYSTEM_ADMIN + D1 group | `SystemStoreReadPolicyTest`, `MiningSystemStoreReadGateTest`, `MiningSystemStoreReadGranularGateTest`, `MiningIntegrationTest.systemAdminReads…` | PASS (gate) | **PENDING-PO D1**: refused (403) by default |
| F05-6 | GET `…/stores/{storeId}/requests`, `…/requests/{requestId}` | Poll status | SYSTEM_ADMIN | `Page<RequestView>`, `RequestView` | same, group `REQUESTS` | same | same tests | PASS (gate) | PENDING-PO D1 |
| F05-7 | GET `…/stores/{storeId}/requests/{requestId}/runs` | Discover `runId` | SYSTEM_ADMIN | `RunView[]` | same, group `RUNS` | same | same tests | PASS (gate) | PENDING-PO D1 |
| F05-8 | GET `…/stores/{storeId}/runs/{runId}/patterns` | Inspect patterns | SYSTEM_ADMIN | `Page<PatternView>` | same, group `PATTERNS` | same | same tests | PASS (gate) | PENDING-PO D1 |
| F05-9 | GET `/api/system/mining/stores/…` Store list | Store picker | SYSTEM_ADMIN | – | **does not exist** | – | – | NOT IMPLEMENTED | **PENDING-PO D2** (no sentence in the requirements; not built before approval) |
| F05-10 | GET `/api/mining/snapshots` | Store user chooses READY snapshot | MERCHANDISER, STORE_ADMIN | `Page<SnapshotView>` | `MiningController` → `MiningService` | class `hasAnyRole` | `MiningIntegrationTest.enforcesRolesOwnershipPaginationAndStrictInput`, `F03F05RbacMatrixTest` | PASS | – |
| F05-11 | GET `/api/mining/requests`, `/requests/{id}`, `/requests/{id}/runs` | Store-scoped status | MERCHANDISER, STORE_ADMIN | `Page<RequestView>`, `RequestView`, `RunView[]` | same | same | same | PASS | foreign ids → 404 |
| F05-12 | GET `/api/mining/runs/{id}`, `/runs/{id}/patterns` | Store-scoped results | MERCHANDISER, STORE_ADMIN | `RunView`, `Page<PatternView>` | same | same | same | PASS | – |
| F05-13 | POST `/api/mining/requests` | Legacy Merchandiser submit | nobody | `CreateRequest` | kept mapped only to emit an explicit 403 | `denyAll()` + URL rule | `F03F05RbacMatrixTest` (all four roles 403, anonymous 401) | PASS | Forbidden by design |
| F05-14 | GET `/api/mining/runs/{id}/benchmark` | Legacy Store benchmark | nobody | – | same | `denyAll()` + URL rule | `F03F05RbacMatrixTest`, `MiningIntegrationTest` (Merchandiser → 403) | PASS | Forbidden by design |
| F05-15 | POST/GET `/api/system/mining/schedules`, GET `/{id}` | R-F05-2 schedule administration | SYSTEM_ADMIN | `MiningScheduleController.Request/View` | `MiningScheduleController` → `MiningScheduleService` | SYSTEM_ADMIN | `MiningAdminConfigurationTest.schedulesRequireSystemAdmin…`, `MiningScheduleTimeTest`, `F03F05RbacMatrixTest` | PASS | schedules are created disabled |
| F05-16 | PUT `/api/system/mining/schedules/{id}` | Edit / disable; enable | SYSTEM_ADMIN | `Update{schedule,enabled,expectedVersion}` → `View` | `MiningScheduleService.update` | SYSTEM_ADMIN | `MiningAdminConfigurationTest`, `MiningOccurrenceDispatcherFlagTest`, `MiningAutomaticSchedulerMySqlTest` | PASS for disable/edit; **enable = HTTP 503 by design** | **PENDING-PO D3–D10** |
| F05-17 | GET `…/schedules/{id}/occurrences`, `…/{occurrenceId}` | History | SYSTEM_ADMIN | `Page<OccurrenceView>`, `OccurrenceView` | `MiningScheduleService` | SYSTEM_ADMIN | `MiningAdminConfigurationTest`, `MiningScheduleReservationMySqlTest`, `F03F05RbacMatrixTest` | PASS | – |

Internal (non-HTTP) F05 components, all covered by executed tests in the evidence run: `MiningWorker` / `MiningDispatchLease` (lease, heartbeat, fencing token — `MiningDispatchLeaseTest`, `MiningDispatchLeaseMySqlTest`, `MiningIntegrationTest.reclaimedRequestRejectsStaleRunAndPatternWrites`), bounded queue (`MiningWorkerQueueRejectionTest`, `boundedQueueLeavesExcessWorkForDurableRetry`), committed-request rediscovery (`committedRequestIsDiscoveredWithoutEnqueue`), `LinuxParentDeathLauncher` (`LinuxParentDeathLauncherTest`), `SpmfMiningEngine`/`SpmfRuntime` (`SpmfMiningEngineTest`, `SpmfRuntimeTest`), `MiningScheduleReservation` / `MiningOccurrenceLease` / `MiningOccurrenceProcessor` / `MiningOccurrenceDispatcher` (reservation, lease, reconciliation, flag tests), legacy reconciliation (`F05LegacyReconciliationTest`).

## Unresolved decisions that gate behaviour

| Decision | Engineering state | Effect today |
|---|---|---|
| D1 read access | Per-group gate implemented (`SystemStoreReadPolicy`), default none | Routes F05-5…F05-8 refuse with 403 |
| D2 Store list | Not implemented | Admin UI cannot list Stores |
| D3–D9 scheduler policies | Current behaviour documented in the package as "CURRENT", unapproved | Enable returns 503, dispatcher off |
| D10 activation acceptance | Not accepted | 503 stays |

No D1–D10 approval evidence (signed or otherwise explicit) exists in the repository. Searched: `docs/decisions/*`, `docs/reports/F05_SCHEDULER_POLICY_DECISIONS.md`, the register's DECISION boxes (all blank).

## Evidence

* Exact-HEAD full run: `scripts/verify-option-b-macos.sh --run` on `0a2d27e`, exit 0, **23/23 PASS** — `mvn.verify` 447 run / 0 failed / 155 skipped (H2 suites; skipped are opt-in classes), `test.flag` 6, `test.mysql_group` 51, `test.full_optin` 457 run / 0 failed / 10 skipped, `jpa.validate`, `openapi.export`, Flyway V1–V17 fresh, V11 Java migration, restart-identical history, upgrade-from-dev. Evidence directory: `outputs/verification-evidence/final-0a2d27e/` (outside the repository).
* New this change: `F03F05RbacMatrixTest` — 3 tests, 26 routes × {anonymous, SYSTEM_ADMIN, STORE_ADMIN, MERCHANDISER, END_CUSTOMER}. Red/green checked: a deliberately widened `lambda-policy` role set made two of the three tests fail, restored and passing.
* The 10 skipped tests of `test.full_optin` are the multi-JVM (5) and clone-API (5) classes, which require extra opt-in properties; they were executed separately on `d0522f1` (macOS 6/6 and 5/5, Linux 6/6) and are **not re-run in this pass**; skipped tests are not counted as executed.

## Update — 2026-11-02, HEAD `45dbd80`

No operation row above changed. Additions since `0a2d27e`: `ScheduledMiningDisabledByDefaultTest` (an enabled, due schedule stays inert with the shipped configuration; enable over HTTP stays 503), the RBAC matrix and the D1 gate suites joined the CI `backend` group, and the full script was re-run on `45dbd80` (23/23, 461 full opt-in tests, 0 failures). The 10 opt-in tests were executed on the exported `45dbd80`: macOS multi-JVM 6/6 and clone-API 5/5, Linux Docker multi-JVM 6/6 with kernel parent-death evidence. See the [Core release readiness report](F03_F05_CORE_RELEASE_READINESS_2026-11-02.md) for the classification of every category (PASS / SKIPPED / NOT RUN).

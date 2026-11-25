# ROC-20261116-008 — F05 EFIM-only mining and benchmark removal

| Trường | Giá trị |
|---|---|
| Record ID | ROC-20261116-008 |
| Date / timezone | 2026-11-16 / Asia/Ho_Chi_Minh |
| Agent | AI assistant using Copilot SDK in VS Code |
| Type | Implementation, Bugfix, Documentation |
| Features | F05 Mining; F06 compatibility reads |
| Branch / base HEAD | `dev` / `3e438f8` (base; local changes are not committed) |
| Execution status | COMPLETED within requested local scope |
| Release status | UNRELEASED |
| Daily summary | [README](README.md) |

## 1. Request and scope

The user asked to fix EFIM as the algorithm for mining and remove benchmark behavior. Scope is new manual and scheduled mining only: no new request may select HUI-Miner or request comparison, and no benchmark endpoint remains. Historical request/run/comparison data remains readable; no schema migration or historical rewrite is requested. This is an implementation record, not a release or Product Owner approval.

## 2. Change details

### Changed

- New configured and manual mining requests resolve to EFIM; the System Admin configuration no longer accepts an algorithm.
- New scheduled requests always create one EFIM run and do not compare algorithms. Legacy stored algorithm/compare metadata remains available to historical readers.
- Run-level runtime and memory fields remain execution telemetry.

### Fixed

- Both benchmark routes and their dedicated security rule were removed.
- OpenAPI previously conflated the configuration and schedule `Request`/`View` components. Configuration schemas now have distinct names; configuration response advertises only `EFIM`, while compare metadata appears only on legacy schedule and occurrence responses.
- Added OpenAPI assertions for fixed EFIM, absent request comparison fields, retained history fields, and removed benchmark paths.

### Security

No new roles, permissions, or Store-access paths. Removed benchmark routes are no longer reachable; the existing mining access controls remain.

### Database

No migration, table change, or historical data rewrite. HUI-Miner engine support remains for legacy work.

### Documentation

Updated the F05 API contract/changelog, frontend handoff, feature README, runtime OpenAPI export, and this Record of Changes. Historical benchmark diagrams/evidence are retained as historical references, not current API behavior.

## 3. Files changed

| Path | Added / Modified / Moved / Deleted | Nội dung / lý do |
|---|---|---|
| [MiningAdministrationController.java](../../../src/main/java/vn/edu/fpt/paspm/mining/MiningAdministrationController.java) | Modified | Remove algorithm from config request; report EFIM and define unique OpenAPI schema names. |
| [MiningContracts.java](../../../src/main/java/vn/edu/fpt/paspm/mining/MiningContracts.java) | Modified | Make new request contract EFIM-only while retaining legacy read shapes. |
| [MiningController.java](../../../src/main/java/vn/edu/fpt/paspm/mining/MiningController.java) | Modified | Remove public benchmark operation. |
| [MiningScheduleController.java](../../../src/main/java/vn/edu/fpt/paspm/mining/MiningScheduleController.java) | Modified | Drop comparison input; retain legacy response metadata. |
| [MiningScheduleReservation.java](../../../src/main/java/vn/edu/fpt/paspm/mining/MiningScheduleReservation.java) | Modified | Ensure newly reserved occurrences do not compare algorithms. |
| [MiningScheduleService.java](../../../src/main/java/vn/edu/fpt/paspm/mining/MiningScheduleService.java) | Modified | Persist comparison disabled for new/updated schedules. |
| [MiningScheduledRequest.java](../../../src/main/java/vn/edu/fpt/paspm/mining/MiningScheduledRequest.java) | Modified | Ignore historical compare/algorithm values for new scheduled work. |
| [MiningService.java](../../../src/main/java/vn/edu/fpt/paspm/mining/MiningService.java) | Modified | Pin configured mining to EFIM. |
| [MiningSystemController.java](../../../src/main/java/vn/edu/fpt/paspm/mining/MiningSystemController.java) | Modified | Remove System Admin benchmark operation. |
| [SecurityConfig.java](../../../src/main/java/vn/edu/fpt/paspm/security/SecurityConfig.java) | Modified | Remove benchmark-specific security rule. |
| [MiningAdminConfigurationTest.java](../../../src/test/java/vn/edu/fpt/paspm/mining/MiningAdminConfigurationTest.java) | Modified | Verify fixed-algorithm config behavior. |
| [MiningHttpMySqlTest.java](../../../src/test/java/vn/edu/fpt/paspm/mining/MiningHttpMySqlTest.java) | Modified | Align HTTP expectations with EFIM-only submission. |
| [MiningIntegrationTest.java](../../../src/test/java/vn/edu/fpt/paspm/mining/MiningIntegrationTest.java) | Modified | Cover EFIM-only behavior, retired routes, and OpenAPI request/response schemas. |
| [MiningMultiJvmMySqlTest.java](../../../src/test/java/vn/edu/fpt/paspm/mining/MiningMultiJvmMySqlTest.java) | Modified | Update multi-JVM mining expectations. |
| [MiningScheduleAdministrationHttpTest.java](../../../src/test/java/vn/edu/fpt/paspm/mining/MiningScheduleAdministrationHttpTest.java) | Modified | Verify new schedule comparison behavior. |
| [MiningScheduledActivationE2EMySqlTest.java](../../../src/test/java/vn/edu/fpt/paspm/mining/MiningScheduledActivationE2EMySqlTest.java) | Modified | Align scheduled activation expectations. |
| [MiningScheduledDatabaseOutageMySqlTest.java](../../../src/test/java/vn/edu/fpt/paspm/mining/MiningScheduledDatabaseOutageMySqlTest.java) | Modified | Align outage-path expectations. |
| [MiningSchedulerMultiJvmMySqlTest.java](../../../src/test/java/vn/edu/fpt/paspm/mining/MiningSchedulerMultiJvmMySqlTest.java) | Modified | Align scheduler multi-JVM expectations. |
| [MiningSchedulerUpgradeCompatibilityMySqlTest.java](../../../src/test/java/vn/edu/fpt/paspm/mining/MiningSchedulerUpgradeCompatibilityMySqlTest.java) | Modified | Verify legacy schema compatibility. |
| [MiningSystemStoreReadGateTest.java](../../../src/test/java/vn/edu/fpt/paspm/mining/MiningSystemStoreReadGateTest.java) | Modified | Keep System Admin read-gate expectations consistent. |
| [MiningSystemStoreReadGranularGateTest.java](../../../src/test/java/vn/edu/fpt/paspm/mining/MiningSystemStoreReadGranularGateTest.java) | Modified | Keep granular read-gate expectations consistent. |
| [RecommendationIntegrationTest.java](../../../src/test/java/vn/edu/fpt/paspm/recommendation/RecommendationIntegrationTest.java) | Modified | Align recommendation integration fixture with EFIM-only mining. |
| [F03F05RbacMatrixTest.java](../../../src/test/java/vn/edu/fpt/paspm/utility/F03F05RbacMatrixTest.java) | Modified | Update F05 authorization matrix for removed benchmark routes. |
| [F05_MINING_API.md](../../api/F05_MINING_API.md) | Modified | State current EFIM-only contract and retain older contract as history. |
| [API_CHANGELOG.md](../../api/API_CHANGELOG.md) | Modified | Record F05 API retirement and schema changes. |
| [openapi.json](../../api/openapi.json) | Modified | Export routes and distinct EFIM-only configuration schemas from Springdoc. |
| [F05 README](../../features/F05/README.md) | Modified | Describe current policy and mark benchmark diagram material historical. |
| [F05_HANDOFF.md](../../frontend_handoff/F05_HANDOFF.md) | Modified | Document EFIM-only request flow and removed benchmark routes. |
| [This record](08-f05-efim-only-and-benchmark-removal.md) | Added | Record this session's changes and evidence. |
| [Daily README](README.md) | Modified | Add Record 008 to daily summary. |
| [Record index](../README.md) | Modified | Update day index and session count. |

## 4. API and compatibility impact

- `PUT /api/system/mining/config` no longer accepts `algorithm`; GET reports effective `EFIM`.
- New manual and scheduled requests create one EFIM run. New request bodies do not accept `compareAlgorithms`.
- Both mining benchmark routes are removed. Runtime/memory telemetry remains on run reads.
- Existing HUI-Miner runs, comparison results, config history, and schedule/occurrence comparison metadata remain readable. Legacy flags do not affect new runs.
- No migration or client changes are needed for historical reads. Clients creating new requests must omit algorithm/comparison inputs.

## 5. Verification executed in this session

| Command / method | Environment | Actual result | Evidence |
|---|---|---|---|
| `.\mvnw.cmd '-Dtest=MiningIntegrationTest#openApiIncludesEveryMiningRoute' test` | Windows, Java 17, H2 | PASS, 1/1 | OpenAPI test in [MiningIntegrationTest.java](../../../src/test/java/vn/edu/fpt/paspm/mining/MiningIntegrationTest.java) |
| `.\mvnw.cmd '-Dtest=MiningIntegrationTest#openApiIncludesEveryMiningRoute' '-Dpaspm.test.openapi-export=docs\api\openapi.json' test` | Windows, Java 17, H2 | PASS, 1/1; refreshed export | [openapi.json](../../api/openapi.json) |
| OpenAPI schema inspection | Exported runtime JSON | PASS: config request excludes algorithm; config response enum is EFIM; manual/schedule requests exclude comparison; legacy schedule/occurrence responses retain it; no benchmark paths | Assertions in `MiningIntegrationTest` and [openapi.json](../../api/openapi.json) |

## 6. Previous evidence referenced

The preceding implementation work reported `mvnw clean test-compile` PASS (148 main sources and 104 test sources), the OpenAPI test 1/1 PASS, and targeted configuration/scheduling/RBAC/EFIM-stub tests 20/20 PASS. A broader integration run could not complete because the expected SPMF JAR was missing and engine-dependent tests returned `SPMF_JAR_NOT_FOUND`. These are referenced from prior session context, not re-executed here.

## 7. Limitations and next actions

- Real SPMF-backed mining and the complete suite remain unverified in this environment until the configured SPMF JAR is supplied.
- Historical benchmark documentation/evidence and the AD04 diagram are intentionally retained as historical references; current API documentation and runtime OpenAPI contain no benchmark routes.
- Changes are local and uncommitted. No push, pull request, merge, or deployment was performed.

## 8. Delivery

Source and documentation are present in the worktree on `dev`, based on `3e438f8`; not committed. The requested behavior and Record of Changes are documented, with historical data preserved. No unrelated files were intentionally reverted.

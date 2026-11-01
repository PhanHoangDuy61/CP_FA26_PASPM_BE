# F03/F05 documentation continuation — PARTIALLY SYNCHRONIZED

2026-10-31, Asia/Ho_Chi_Minh. Branch `Khoi/feat/f03-f05-admin-mining`, HEAD/base `c06d43ec59269b8190f5920b5db526ee4e7f19d7`; changes are uncommitted and not deployable. No implementation commit exists.

## Changes and status

- `docs/api/F03_UTILITY_API.md`: current source FEFO order expiry, received_at, batch_id, including push ordering; historical snapshots are unchanged. `docs/frontend_handoff/F03_HANDOFF.md` adds a branch note.
- `docs/features/F05/README.md`, `docs/api/F05_MINING_API.md`, `docs/frontend_handoff/F05_HANDOFF.md`: prominent work-in-progress notices warn that old role/endpoint descriptions are historical and must not be treated as this branch's current contract.
- Approved historical requirements have not been rewritten; independent approval/provenance of the v1.2 PDF remains unverified.
- `docs/api/openapi.json` was not regenerated. **OPENAPI_REGENERATION_BLOCKED / PENDING** until the scheduler and complete authorization/DTO contracts are verified. Existing exported schema describes the previous baseline.

## Implemented branch routes and permissions (partial; not final acceptance)

| Method / route | SYSTEM_ADMIN | STORE_ADMIN | MERCHANDISER | END_CUSTOMER | Evidence |
|---|---|---|---|---|---|
| GET, PUT `/api/system/mining/config` | allowed | forbidden | forbidden | forbidden | `MiningAdministrationController`, `MiningAdminConfigurationTest` |
| POST `/api/system/mining/requests` | allowed with explicit Store | forbidden | forbidden | forbidden | `MiningSystemController`, `MiningIntegrationTest` |
| GET `/api/system/mining/stores/{storeId}/runs/{runId}/benchmark` | allowed for explicit Store | forbidden | forbidden | forbidden | `MiningSystemController`, `MiningIntegrationTest` |
| POST `/api/system/mining/schedules` | allowed, creates disabled only | forbidden | forbidden | forbidden | `MiningScheduleController`, `MiningAdminConfigurationTest` |
| GET, PUT `/api/system/mining/schedules/{id}` | allowed; enable returns 503 | forbidden | forbidden | forbidden | `MiningScheduleService`, `MiningAdminConfigurationTest` |
| POST `/api/mining/requests` | forbidden | forbidden | forbidden | forbidden | `SecurityConfig` |
| GET `/api/mining/runs/{id}/benchmark` | forbidden | forbidden | forbidden | forbidden | `SecurityConfig` |
| Store-scoped mining request/run/pattern GET | forbidden | own Store | own Store | forbidden | `MiningController` |

Administrative execution uses versioned global policy and an existing Store-owned snapshot; it is not scheduled execution. Existing old requests retain their frozen data and may have a NULL policy version. F06 reads valid completed runs through Store-scoped workflow.

## Frontend and diagrams

The old Merchandiser mining submission and Store benchmark UI are breaking integration changes. `FE` and `FE_Kien` were not modified or runtime-tested in this task. Previously inspected FE_Kien contains F03 expiry push; no assertion is made about deployed frontend.

Diagram update backlog (not executed): Use Case System Admin owns config/run/schedules/benchmark; AD03 and sequence AD03 distinguish admin store selection, policy and async request; AD04/sequence AD04 benchmark; AD01/sequence AD01 distinguish Merchandiser snapshot creation from System Admin execution and scheduled fresh snapshot; mining state machine document request/run/occurrence and failure/recovery; logical architecture show scheduler and isolation only once implemented. The latest approved editable source has not been confirmed; no diagram modified.

## Outstanding

Scheduling is NOT COMPLETE: internal occurrence reservation, fresh snapshot, pinned request submission and a paginated occurrence-history GET exist, but there is no automatic due-job dispatcher, robust post-commit dispatch recovery or multi-instance worker fencing. Existing disabled schedule CRUD does not perform mining. Security and idempotency need additional review; MySQL, Java runtime, full regression and exact test results are reported in the implementation report. API fields, examples, schema export, cross-feature traceability, handoffs and links must be regenerated/reviewed after completion. This document is not a full API contract or an approval to merge.

## Scheduler continuation — 2026-10-31

- `GET /api/system/mining/schedules/{id}/occurrences` is System Admin-only and paginated; returns frozen context, snapshot/request IDs and status where present. History visibility was tested using H2 MockMvc; it is not proof of an automatic scheduler.
- V14 adds nullable frozen occurrence context (actor, analysis/range dates, timezone and comparison flag); legacy rows remain nullable, not backfilled with invented facts. Internal reservation, snapshot and request services are not externally triggerable endpoints.
- Policy used by the internal reservation is previous `lookbackDays` complete calendar days ending before the scheduled analysis date; at most one due slot is reserved per call; monthly overflow clamps to month end, DST gaps move forward and overlaps choose the earlier offset. These are engineering assumptions for PO review, not accepted operational policy. F04 currently interprets dates using the configured Store timezone; cross-timezone Store modeling requires review.
- Enable remains 503. `MiningWorker.recover()` can mark other instances' live jobs interrupted; no distributed lease/fencing exists. Crash after request commit before enqueue can strand a request; occurrence status is not automatically reconciled. Polling, retries, crash recovery, multi-instance fencing and all required failure paths remain NOT_IMPLEMENTED. No claim of production safety or `IMPLEMENTED_AND_TESTED`.
- `docs/api/openapi.json`, full contract/handoff and diagram files remain pending; diagram-change backlog above remains a proposal.
 
## Lease prototype continuation — 2026-10-31

**NOT_READY.** V15 and `MiningDispatchLease` are an unwired database-lease prototype; they do not change the scheduler API or make worker recovery safe. Enablement stays blocked with 503. The implementation report records the Java 17 full-suite result, isolated MySQL tests, initial helper failures and remaining critical unexecuted acceptance scenarios. Do not present lease ownership, retry, automatic dispatch or scheduler activation as implemented. Final OpenAPI, RBAC, handoff, lifecycle and diagram-backlog synchronization remain pending until the real execution flow is implemented and verified. Existing unrelated diagram changes were preserved.

## Worker integration continuation — 2026-10-31

This later entry supersedes the preceding prototype status: the real mining worker now uses V15 ownership, fenced repository writes and database polling. `docs/architecture/F05_WORKER_DISPATCH.md` records actual behavior and limits; the implementation report records final test evidence. The scheduler itself remains disabled and 503-protected. P0-1 and P0-2 remain **OPEN** pending independent JVM crash/recovery acceptance and fault tests. Do not regenerate final scheduler OpenAPI or describe scheduler dispatch as operational. Existing diagram changes remain untouched.

## Real multi-JVM verification — 2026-11-01

The later evidence supersedes the prior P0-2 status: durable dispatch for new eligible committed requests passed real multi-JVM pre-/post-commit crash and queue tests. P0-1 stays OPEN because a real Java 25 SPMF child survived the forced death of its owning Backend, even though the second Backend safely recovered the database request. Updated `docs/architecture/F05_WORKER_DISPATCH.md` and the implementation report document both observations. Test evidence is under `docs/testing/evidence/`; no scheduler API contract or diagram was changed. Scheduler and branch remain NOT_READY.

## Orphan-process follow-up — 2026-11-01

Updated `docs/architecture/F05_WORKER_DISPATCH.md` and `docs/testing/evidence/README_F05_MULTIJVM.md` to state that no crash-cleanup mechanism or target-platform verification exists yet. Added `docs/architecture/F05_LEGACY_RUNNING_RECONCILIATION.md`, a read-only, review-required inventory procedure; its query was exercised against synthetic H2 by `F05LegacyReconciliationTest`. The implementation report records the current full Maven counts and skipped-test classification. P0-1 remains OPEN; scheduler and branch remain NOT_READY. Do not infer production approval or automatic cleanup from these documents.

## Cross-Platform Developer Onboarding and Local Setup

- Updated root `README.md`, `docs/setup/README.md`, `docs/setup/LOCAL_DEVELOPMENT.md` and `docs/setup/F05_SPMF_RUNTIME_SETUP.md`. Added `LOCAL_SETUP_COMMON.md`, `LOCAL_SETUP_WINDOWS.md`, `LOCAL_SETUP_MACOS.md` and `LOCAL_SETUP_TROUBLESHOOTING.md` in `docs/setup/`. They describe actual Compose services, Maven Wrapper, application variables, bootstrap and dev accounts, Store-scoped F03/F05 workflow, migration safeguards, Linux process requirements, and the intentionally disabled scheduler.
- Backend/Maven use JDK 17; SPMF uses a separately configured Java 25+ executable and the approved, hash-pinned 2.67a JAR not committed in this Backend repository. `.env` is ignored; Compose reads it while Spring Boot does not automatically import it. No local secret or machine-specific runtime installation was added to Git.
- **Windows: NOT VERIFIED ON TARGET OS.** PowerShell commands were compared with repository configuration; no Windows host or PowerShell runtime was available. **macOS: PARTIALLY VERIFIED; fresh-clone walkthrough NOT VERIFIED.** The local host was Apple Silicon. Executed `sh ./mvnw -version` with a temporary wrapper home: Maven 3.9.9 and Java 17.0.21. The first sandboxed attempt could not write to the user's normal wrapper cache; the successful retry used the temporary home. All seven documented zsh blocks passed `zsh -n`. Neither Intel macOS nor a pristine machine was available.
- Executed `docker compose config --services`, which returned `mailpit` and `mysql`; the guide's complete Compose startup/stop sequence was not executed against a fresh clone. Isolated Linux Docker/MySQL 8.4.13 tests applied Flyway V1–V15 and verified real Java 25 SPMF, including HUI-Miner and EFIM. The final Java 17 `mvn -q -Dpaspm.test.spmf-java=<java-25-executable> verify` returned 362 tests, 0 failures/errors and 127 skipped; see the implementation report for separately executed opt-in tests and remaining skips.
- Local Markdown target validation checked README, setup index, runtime guide and all four new guides: no missing relative file links. PowerShell syntax and a fresh Windows/macOS checkout remain unverified. V12–V15 and other current feature files are still uncommitted in this working tree; a new clone cannot reproduce those changes until they are published. The approved SPMF artifact must be obtained separately from an approved source; no immutable hosting URL was invented. Source database and unrelated diagrams were not modified, and no commit/push/merge/deploy was performed.
- A subsequent repository-wide scan examined 163 Markdown files and found one missing relative target: `docs/diagrams/README.md` references the already-deleted `PASPM_Complete_ERD_F02_Inventory_Batch_v3.drawio`. That deletion predates this task and is explicitly protected; it was not restored or modified. The new/updated onboarding guides had no missing local file targets.

## Scheduler / Linux Docker continuation — 2026-11-01

Updated F05 API, handoff, feature status, worker architecture, setup and root README to describe V16, the default-disabled poller, occurrence lifecycle, and remaining 503 gate. Exported `docs/api/openapi.json` from `/v3/api-docs` served by the built Backend container and checked the scheduler list/detail paths and occurrence schema. Its generated server URL reflects the isolated test port, not a deployment endpoint. The evidence file records the normal two-container execution and one test-only SIGKILL/recovery scenario; it identifies the remaining untested crash/recovery cases.

The Windows and macOS setup instructions were not tested on clean target machines. The Docker image was built and its normal path tested under Linux/amd64 emulation on macOS ARM, not on a native Linux host. Approved SPMF binary is still obtained separately; no artifact hosting or redistribution was added. See the latest implementation report for the final test results and NOT_READY status.

### Diagram synchronization backlog

- Unified Draw.io and HTML Review 2: show SYSTEM_ADMIN as the schedule configuration actor; do not show MERCHANDISER/STORE_ADMIN/END_CUSTOMER administering schedules.
- Add the intended occurrence sequence: reserve and pin config, create a fresh Store-scoped snapshot, link a mining request, leased worker execution, terminal reconciliation, and bounded retry/fencing.
- Mark schedule activation and full multi-instance crash acceptance as pending; do not depict scheduling as production-ready. Review timezone, catch-up, retry and lookback policies with the Product Owner before presenting them as approved rules.
- The diagrams themselves were not edited in this task.

Latest verification: the final-source macOS Java 17 Maven verify completed with 369 tests, zero failures/errors and 12 skips; the opt-in Linux two-JVM group ran separately with six tests and zero failures/errors/skips. See the implementation report for environments, the failed earlier run, and outstanding acceptance scenarios. Local link-target validation checked 12 changed README/setup/API/architecture/report documents with zero missing relative file targets.

After the full verify, the skipped opt-in groups were executed separately: six Linux two-JVM cases (including the repeated crash case), five synthetic restricted-clone API cases, one real Java 17 HTTP/SPMF case, and one Java 17 SPMF rejection case passed. An initial clone setup failed because binary logging restricted stored function creation; the successful retest used a newly created disposable schema after configuring the test MySQL instance only. See the implementation report; this does not close the outstanding final-image scheduler crash-acceptance cases.

## Scheduler crash acceptance continuation — 2026-11-01

Updated F05 feature, API, handoff and worker architecture documents to describe the current disabled activation and tested scheduler lifecycle. Added [the acceptance matrix](F05_SCHEDULER_ACCEPTANCE_MATRIX.md), [Product Owner policy proposals](F05_SCHEDULER_POLICY_DECISIONS.md), [two-container crash evidence](../testing/evidence/F05_SCHEDULER_CRASH_ACCEPTANCE_2026-11-01.txt), and [final Maven evidence](../testing/evidence/F05_FINAL_VERIFY_2026-11-01.txt). Existing OpenAPI was verified against all four scheduler paths; no production API/schema source changed in this continuation. The diagram backlog above remains open; no diagram was edited. Windows/macOS fresh-machine onboarding remains unverified. Scheduler remains PARTIALLY_IMPLEMENTED / NOT_READY with 503 retained.

The final-source Maven verification reports 370 tests with 0 failures/errors and 12 skipped. All skipped categories were exercised in subsequent separate runs: the final-source Linux multi-JVM Surefire XML reports 6 passes including one repetition (shell exit unavailable after interruption), and seven clone/Java/HTTP cases passed on the disposable schema. The natural 300-second occurrence lease test also passed without changing the lease timestamp. The acceptance matrix and evidence retain all environment and fault-injection limits.

## Final scheduler gap verification — 2026-11-01

Updated the existing acceptance matrix and worker/feature documentation, and added [final-gap evidence](../testing/evidence/F05_FINAL_GAPS_2026-11-01.txt) for actual queue rejection and recovery, MySQL TCP disconnect/reconnect, simultaneous startup, natural retry/exhaustion and SPMF isolation/startup race. The final-source full Maven verify passed 371 tests with 12 skipped; seven opt-ins and six Linux multi-JVM executions passed separately. The DEBUG log is default-off and requires an operational log-volume/retention decision. Policies remain pending, enablement remains 503, and no diagrams were edited.

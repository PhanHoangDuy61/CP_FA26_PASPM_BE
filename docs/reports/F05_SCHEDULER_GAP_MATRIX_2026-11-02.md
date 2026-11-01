# F05 Scheduled Mining — gap matrix, approval boundary and exact change sets (2026-11-02)

Baseline: `origin/dev` @ `5c9d3dc` (Core merged by PR #11, documentation and 27-page pack by PR #12). Branch `Khoi/feat/f05-scheduled-mining-option-b-completion`.

## 1. Approval boundary (read first)

Every DECISION box of [the D1–D10 package](../decisions/PO_DECISION_PACKAGE_D1_D10_2026-11-01.md) is blank on `dev`; the [register](../decisions/PO_DECISION_REGISTER_F05_2026-11-01.md) records no approver or date; no other document in the repository contains an approval. **D1–D10 are therefore all PENDING_PO.** Consequences kept in force by this branch:

* schedule activation answers HTTP 503, the dispatcher flag defaults off, the five System Admin Store-read routes answer 403;
* no `SKIPPED` occurrence status, no service account or migration, no cancel-on-disable status, no Store list endpoint were added;
* the work below is approval-independent: tests that pin CURRENT behaviour (named "currentBehaviour…" or described as such) and review findings. Pinning a behaviour is **not** approving it.

## 2. Gap matrix

Status vocabulary: ALREADY_VERIFIED · NEEDS_IMPLEMENTATION · NEEDS_REGRESSION · PENDING_PO · BLOCKED · COMPLETE.

| # | Requirement | Current behaviour | Missing | Decision | Tests | Status |
|---|---|---|---|---|---|---|
| 1 | System Admin schedule administration: create, list, read, edit, disable, occurrence list/detail | All routes under `/api/system/mining/schedules`, class-level SYSTEM_ADMIN; schedules are created disabled; optimistic `expectedVersion` | – | – | `MiningAdminConfigurationTest`, `F03F05RbacMatrixTest`, new `MiningScheduleAdministrationHttpTest` (validation matrix, stale version 409, two concurrent edits → one 200 and one 409, store cannot change, 404s, pagination bounds, deactivated admin 401, disable keeps history) | COMPLETE |
| 2 | Activation (`enabled=true`) | `MiningScheduleService.update` throws 503 before any write; the fire-time computation (`next(...)`) already exists but is unreachable | remove the guard behind an approved acceptance gate | D10 (needs D3–D9 first) | `ScheduledMiningDisabledByDefaultTest`, new `enablingIsRefusedWithoutAnySideEffect…` | PENDING_PO, BLOCKED |
| 3 | Recurrence (weekly, monthly, zones, DST) | `MiningScheduleTime`: month-end clamp, DST gap forward, overlap earlier offset | – | D4, D5 pending (behaviour pinned as CURRENT) | `MiningScheduleTimeTest`, new `MiningScheduleTimeEdgeCasesTest` (day 29/30/31, leap and non-leap, 2100, no drift after a clamp, ±13 h, +05:30/+05:45/Lord Howe, spring gap monthly, fall overlap fires once, 3-year monotonic sequences) | COMPLETE (technical); policy PENDING_PO |
| 4 | Lookback window | window = `lookbackDays` complete days ending the day before the analysis date in the schedule's zone; 1–3660 | min-data rule, presets, maximum | D3 | new `MiningScheduleReservationScenariosTest` (1 day, 3660, Auckland near-midnight analysis date) | PENDING_PO |
| 5 | Insufficient data | occurrence fails once with `HTTP_400` (no eligible transaction items / incomplete scores); no snapshot, no request, no retry | maybe a distinct status | D3 | new `MiningOccurrenceFailurePathsTest` | ALREADY_VERIFIED; policy PENDING_PO |
| 6 | Reservation: durable, idempotent, multi-instance | `reserveOne` locks the due schedule row `FOR UPDATE`; unique `(schedule_id, scheduled_at)`; deterministic request key `PASPM-SCHEDULE-V1\|id\|instant`; pins config and schedule version | – | – | `MiningScheduleReservationTest` (2 threads), `MiningScheduleReservationMySqlTest`, new scenarios (re-reserve never duplicates, unique constraint, deterministic key, pinned config version) | COMPLETE |
| 7 | Catch-up after downtime | every missed fire becomes its own occurrence, in order, until none is due; the dispatcher reserves at most 5 per 5-second poll; no age limit, no merge, no skip | skipped status, latest-only, max age | D6 | new `currentBehaviourCatchUp…` (5 missed fires → 5 occurrences, then stops) | PENDING_PO |
| 8 | Retry and backoff | retryable = 5xx `ApiException` or transient DB error; 3 attempts; delays 15 s then 30 s; third failure terminal; expired lease after the last attempt → `FAILED`/`RETRY_EXHAUSTED` | alerting on terminal failure | D7 | `MiningOccurrenceLeaseTest`, new failure-path test through the real pipeline (SPMF unavailable → PENDING with `next_retry_at`, no attempt before it is due, attempt 3 terminal, no 4th) | COMPLETE (mechanism); alerting PENDING_PO |
| 9 | Config/schedule edits and disable | occurrence pins `schedule_version` and `config_version` at reservation. **Precise current behaviour:** if the schedule is disabled or edited after reservation but **before** the occurrence is prepared, the occurrence ends `FAILED`/`HTTP_409` with no snapshot; once the request exists (`DISPATCHED`) it runs to completion regardless. There is no `CANCELLED` status | explicit cancel semantics | D8 | new failure-path tests for edit and disable | PENDING_PO (see section 4: the package text was imprecise) |
| 10 | Actor of automatic runs | `mining_schedule.updated_by` must be an ACTIVE `SYSTEM_ADMIN` at preparation and at submission; otherwise `FAILED`/`HTTP_403`, nothing created | service account / creator | D9 | new failure-path test (inactive owner) | PENDING_PO |
| 11 | Fresh immutable F03 snapshot, FEFO, frozen config, no orphan | `MiningScheduledSnapshot` runs the real F03 builder in the occurrence transaction; a later failure rolls the snapshot back | – | – | `MiningAutomaticSchedulerMySqlTest` (real SPMF, both algorithms), new failure-path test asserts zero `utility_snapshot` rows after a late failure | ALREADY_VERIFIED |
| 12 | Mining request, worker, lease/fencing, SPMF, recovery | unchanged Core worker; scheduled request is created in the same transaction as the snapshot and the occurrence link | – | – | `MiningMultiJvmMySqlTest`, `MiningDispatchLeaseTest`, queue tests, Linux parent-death evidence in the Core readiness report | ALREADY_VERIFIED |
| 13 | Reconciliation of finished requests | `reconcile()` copies COMPLETED/FAILED request status to a DISPATCHED occurrence, independent of the schedule row | – | – | `MiningOccurrenceReconciliationTest` | ALREADY_VERIFIED |
| 14 | Disabled by default; inert enabled rows | dispatcher thread never starts without the flag | – | – | `ScheduledMiningDisabledByDefaultTest`, `MiningOccurrenceDispatcherFlagTest` | COMPLETE |
| 15 | System Admin Store-read routes | per-group gate, default none | opening a group is a configuration change after approval | D1 | gate suites | PENDING_PO |
| 16 | System Admin Store list | no endpoint | `GET /api/system/stores` (SYSTEM_ADMIN, id and name, paginated) | D2 | – | PENDING_PO |
| 17 | Public activation E2E over HTTP with real SPMF on MySQL 8.4 | cannot run: activation is 503 | all of it | D10 | – | BLOCKED |
| 18 | Crash points "after snapshot commit before link" and "after request commit before link" | not independent commit points (single transaction) | not to be invented | – | acceptance matrix rows stay BLOCKED by design | COMPLETE (documented) |
| 19 | Alerting / monitoring of terminal failures | log lines only; occurrence history shows status and failure code | exporter / notification | D7 | – | PENDING_PO |

## 3. Exact smallest change per decision (prepared, not applied)

| Decision | Option → smallest change | Tests to add with it |
|---|---|---|
| D1 | (b): set `paspm.mining.system-store-read.routes=REQUESTS,RUNS` in the approved environment — configuration only; code default stays empty until the PO decides to change the shipped default | extend `MiningSystemStoreReadGranularGateTest` to the approved set |
| D2 | (a): `GET /api/system/stores` in a new `StoreController` (SYSTEM_ADMIN), `SELECT store_id,name FROM store ORDER BY name LIMIT ? OFFSET ?`, page bounds 1..100 | RBAC row in `F03F05RbacMatrixTest`, pagination, no other fields |
| D3 | presets are UI only; a min-data rule needs a count check in `MiningScheduledSnapshot` before `snapshots.create` and a distinct `failure_code` | boundary tests with N−1 / N eligible transactions |
| D4 | (i) one Store calendar zone: validate `timeZone` equals `paspm.store-time-zone` in `MiningScheduleService.validate`; no migration | 400 for another zone on create and update |
| D5 | clamp is current; "skip" would change `MiningScheduleTime.nextMonthly` to jump a month when `day > lengthOfMonth`; "1–28 only" is a validation change | adjust `MiningScheduleTimeEdgeCasesTest` expectations |
| D6 | "latest only, record the rest as skipped": new status `SKIPPED` (V18 widens `ck_occurrence_status`), `MiningScheduleReservation` marks all but the last due fire `SKIPPED` in one transaction, OccurrenceView unchanged (status is a string) | catch-up test expecting N−1 skipped plus one PENDING; migration test; docs |
| D7 | attempt count and delays are constants in `MiningOccurrenceLease` (`MAX_ATTEMPTS=3`, `15L*(1L<<(count-1))`); alerting needs an agreed channel (log-based alert, metric or notification) — no code until the channel is chosen | terminal-failure signal test |
| D8 | cancel-on-disable: new status `CANCELLED`, `MiningScheduleService.update` marks PENDING occurrences of the schedule `CANCELLED` in the same transaction (V18 widens the check); `FAILED`/409 stays only for edits | disable test expecting `CANCELLED`, race test with the dispatcher |
| D9 | service account: seeded `SYSTEM_ADMIN`-role technical user (migration V18/V19 or config property naming an existing user) and `MiningScheduleReservation` using it instead of `updated_by`; the inactive-owner rule then guards that account | actor test, migration and rollback notes |
| D10 | remove the 503 in `MiningScheduleService.update` and compute `next_fire_at` (already coded); set `paspm.mining.schedule-dispatch-enabled=true` only in the approved environment; run the five acceptance items of the package | public HTTP E2E with real SPMF on MySQL 8.4, multi-container crash/concurrency rerun |

## 4. Documentation findings corrected by this work

* The D8 "CURRENT" text of the PO package and register said an already reserved occurrence "still runs" while also saying a disabled schedule is rejected at preparation. The code and the new tests show: reserved but not yet prepared → `FAILED`/`HTTP_409`; request already created → runs to completion. Both documents now say so.
* The F05 acceptance matrix keeps its two BLOCKED crash rows: they are not separate commit points and are deliberately not simulated.

## 5. What was not done, and why

No activation, no policy implementation, no migration, no new endpoint: each depends on a PENDING_PO decision above. Real HTTP activation, a public scheduled E2E and `FULL_F05_VERIFIED` stay unavailable until D10 is approved.

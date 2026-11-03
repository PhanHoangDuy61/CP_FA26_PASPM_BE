# F05 Scheduled Mining — gap matrix (2026-11-02, updated after the D1–D10 confirmations)

Baseline: `origin/dev` @ `5c9d3dc`. Branch `Khoi/feat/f05-scheduled-mining-option-b-completion`. The first version of this document (commit `bb9451f`) was written while D1–D10 were unconfirmed and listed the smallest change per decision; **all ten business choices were then confirmed on 2026-11-02** (record: [register](../decisions/PO_DECISION_REGISTER_F05_2026-11-01.md)) and this version records what was built. The earlier audit finding on D8 (a reserved but unprepared occurrence of a disabled/edited schedule ends `FAILED`/`HTTP_409`; once its request exists it runs to completion; no `CANCELLED` status) is unchanged and is now the confirmed behaviour.

Status vocabulary: `IMPLEMENTED` · `LOCAL_VERIFIED` · `E2E_VERIFIED` (real HTTP, MySQL 8.4, real SPMF) · `CI_NOT_RUN` · `RELEASE_SIGNOFF_PENDING`. Nothing is `RELEASE_ACCEPTED`.

## Pipeline map (controller → result)

`MiningScheduleController` → `MiningScheduleService` (validation, activation gate) → `MiningScheduleReservation.reserve` (row lock, latest-only catch-up, `SKIPPED` rows, `PENDING` occurrence, cursor) → `MiningOccurrenceDispatcher.poll` (flag-gated, 5 s) → `MiningOccurrenceLease` (claim, fence, retry) → `MiningOccurrenceProcessor` (REPEATABLE_READ) → `MiningScheduledSnapshot` (fresh F03 snapshot) → `MiningScheduledRequest` (Mining Request, same transaction) → `MiningWorker` → SPMF (HUI-Miner / EFIM) → `reconcile()` copies the final request status and cause to the occurrence.

## Gap matrix

| # | Requirement | Decision | Behaviour now | Evidence | Status |
|---|---|---|---|---|---|
| 1 | System Admin schedule administration (create/list/read/edit/disable/history) | – | unchanged; created disabled; optimistic `expectedVersion` | `MiningScheduleAdministrationHttpTest`, `F03F05RbacMatrixTest` | IMPLEMENTED · LOCAL_VERIFIED |
| 2 | System Admin reads of snapshots / requests / runs; patterns closed | D1 | default `SNAPSHOTS,REQUESTS,RUNS`; patterns always 403 and rejected as config; audit log line per read | `SystemStoreReadPolicyTest`, `MiningSystemStoreReadGateTest`, `…GranularGateTest`, E2E | IMPLEMENTED · E2E_VERIFIED |
| 3 | Store picker | D2 | `GET /api/system/stores` (SYSTEM_ADMIN, `{storeId,name}`, paged, name/id order) | `SystemStoreListHttpTest`, RBAC matrix, E2E | IMPLEMENTED · E2E_VERIFIED |
| 4 | Lookback 7–365, defaults 30 / 90, presets 30/60/90/180 | D3 (amended) | validated on create and update; legacy rows kept, never reserved, repaired only by a valid edit | admin HTTP, reservation scenarios, `MiningMigrationMySqlTest` | IMPLEMENTED · LOCAL_VERIFIED |
| 5 | Insufficient data → explicit failure | D3 | `FAILED/HTTP_400` with a stored reason; no snapshot, request or retry | `MiningOccurrenceFailurePathsTest`, E2E | IMPLEMENTED · E2E_VERIFIED |
| 6 | Store calendar zone `Asia/Ho_Chi_Minh` | D4 | `timeZone` must equal `paspm.store-time-zone`; legacy-zone schedules inert; legacy-zone occurrences `FAILED/HTTP_409`; startup guard with the dispatcher on | admin HTTP, reservation scenarios, failure paths, `ScheduledMiningZoneGuardTest` | IMPLEMENTED · LOCAL_VERIFIED |
| 7 | Monthly day 29/30/31 clamp | D5 | last valid day, no drift, unique fire times | `MiningScheduleTimeEdgeCasesTest`, reservation scenarios | IMPLEMENTED · LOCAL_VERIFIED |
| 8 | Catch-up: only the latest runs, older `SKIPPED` | D6 | V18; atomic with the cursor; ≤500 per transaction, resumable; never claimed | `MiningScheduleCatchUpTest`, E2E (monthly), multi-process | IMPLEMENTED · E2E_VERIFIED |
| 9 | Retry 3× (15 s, 30 s) and visible final error | D7 | `RETRY_EXHAUSTED`, `failure_message`, ERROR log; reconcile copies the failed run's cause | failure paths, lease test, reconciliation test, E2E | IMPLEMENTED · E2E_VERIFIED |
| 10 | Config / schedule changes and disable | D8 | frozen versions; disable stops reservations; committed requests finish; unprepared occurrence → `HTTP_409` | `MiningScheduleChangeSemanticsTest`, `MiningIntegrationTest`, multi-process | IMPLEMENTED · LOCAL_VERIFIED |
| 11 | Execution identity | D9 | scheduler machine principal (V19), hidden and immutable through account management | `SchedulerPrincipalHttpTest`, failure paths, E2E | IMPLEMENTED · E2E_VERIFIED |
| 12 | Gated activation | D10 | `enabled=true` only with `paspm.mining.schedule-dispatch-enabled=true`; otherwise 503 before any write | `ScheduledMiningActivationGateTest`, `ScheduledMiningDisabledByDefaultTest`, admin HTTP, E2E | IMPLEMENTED · E2E_VERIFIED |
| 13 | Multi-instance reservation / processing | D10 | row lock + unique `(schedule_id, scheduled_at)` + fenced lease; two processes produce one request and 3 skipped records | `MiningSchedulerMultiJvmMySqlTest` (macOS) | E2E_VERIFIED (macOS); Linux see matrix |
| 14 | Crash recovery (inside the occurrence transaction; after request commit) | D10 | no orphan snapshot after `kill -9`; lease not stolen before expiry; fence 2 after recovery; recovered request completes | `MiningSchedulerMultiJvmMySqlTest` | E2E_VERIFIED (macOS; the 300 s occurrence lease expiry is simulated) |
| 15 | Fresh immutable F03 snapshot, FEFO, frozen config | – | unchanged | `MiningIntegrationTest`, E2E | LOCAL_VERIFIED |
| 16 | Worker lease / fencing / SPMF recovery | – | unchanged Core | `MiningMultiJvmMySqlTest`, queue tests | LOCAL_VERIFIED |
| 17 | Alerting beyond the recorded error | D7 | none, by decision (no e-mail/SMS/webhook) | – | NOT BUILT (by decision) |
| 18 | GitHub CI on the Scheduler branch | – | not pushed, never run | – | CI_NOT_RUN |
| 19 | Native Linux run of the new suites | – | see the acceptance matrix for what ran under Docker amd64 emulation | – | see matrix |
| 20 | Release sign-off, formal PO identity | D1–D10 | outstanding | – | RELEASE_SIGNOFF_PENDING |

## What changed in the code (summary)
`SystemStoreReadPolicy`, `MiningSystemController` (D1); `SystemStoreController` (D2); `MiningScheduleController`/`Service` (D3, D4, D10 gate); `MiningScheduleReservation` (D4, D6, D9); `MiningScheduledSnapshot`/`MiningScheduledRequest` (D4 check, D9 check); `MiningOccurrenceLease`/`Dispatcher` (D7); `SchedulerPrincipal`, `UserRepository`, `UserController` (D9); `ScheduledMiningZoneGuard` (D4); Flyway V18, V19. A transactional method calling another on `this` lost its proxy during the rewrite (reservation ran without a transaction); the existing two-instance reservation test caught it and `reserveOne` is now itself transactional.

## What was deliberately not built
No cancel status on disable (D8 keeps `FAILED/HTTP_409`); no maximum catch-up age (D6); no e-mail/SMS/webhook notification (D7); no change to the database `CHECK (lookback_days BETWEEN 1 AND 3660)` because narrowing it would rewrite stored history (D3); no Frontend code.

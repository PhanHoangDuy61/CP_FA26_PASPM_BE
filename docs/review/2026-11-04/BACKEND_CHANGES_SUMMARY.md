# Backend — major changes summary (for the 2026-11-04 review)

Baseline: `origin/dev` `5c9d3dc` → branch `Khoi/feat/f05-scheduled-mining-option-b-completion` @ `c65daf2` (27 commits ahead of `dev`, pushed to `origin`, **not merged**). Production code is 16 files (migrations included in the count); everything else is tests, scripts and documentation. Scheduling is **implemented but off by default**.

## 1. What the branch adds (decisions D1–D10, confirmed in the working conversation on 2026-11-02)

| ID | Behaviour now in the Backend | Where to see it |
|---|---|---|
| D1 | System Admin may read a Store's **snapshots, requests and runs**; raw **patterns stay closed** (always 403; naming `PATTERNS` in configuration stops startup); every cross-Store read is audit-logged | `SystemStoreReadPolicy`, `MiningSystemController`; UC07; AD01 roles note |
| D2 | `GET /api/system/stores` — SYSTEM_ADMIN only, `{storeId, name}`, paged | `SystemStoreController`; UC33 |
| D3 | schedule `lookbackDays` 7–365 (defaults 30 weekly / 90 monthly); legacy out-of-range rows are kept but never run; no empty "successful" snapshot — insufficient data is an explicit failure | `MiningScheduleService`; AD03 |
| D4 | schedule zone must equal the Store calendar zone `Asia/Ho_Chi_Minh`; with the dispatcher on, a different `paspm.store-time-zone` makes the process refuse to start | `ScheduledMiningZoneGuard` |
| D5 | monthly day 29/30/31 clamps to the month's last valid day; each logical fire time exists once | `MiningScheduleTime`, unique `(schedule_id, scheduled_at)` |
| D6 | after downtime only the **latest** missed fire runs; older ones are stored as `SKIPPED` with a reason (Flyway V18) | `MiningScheduleReservation`; R2-06.03b |
| D7 | up to 3 attempts (retry after 15 s, then 30 s); the final cause is stored and returned (`failureMessage`); no e-mail/SMS/webhook | `MiningOccurrenceLease`; AD03 note |
| D8 | schedule and configuration versions are frozen at reservation; disabling stops new fires; a **committed** Mining Request finishes; a reserved occurrence that was disabled/edited **before preparation** ends `FAILED/HTTP_409` | `MiningScheduleChangeSemanticsTest`; R2-06.03b note |
| D9 | scheduled runs execute as a dedicated machine principal `SYSTEM_SCHEDULER` (Flyway V19): no password, `INACTIVE`, hidden from `/api/users` and `/api/roles`, not assignable, not the last human editor | `SchedulerPrincipal`; R2-04 |
| D10 | activation (`PUT … enabled=true`) is accepted only where `paspm.mining.schedule-dispatch-enabled=true`; default `false` → HTTP 503 before any write, no dispatcher thread | `MiningScheduleService`, `MiningOccurrenceDispatcher` |

Pipeline (unchanged architecture, extended): schedule row (row lock) → reservation (latest due fire, `SKIPPED` for older) → occurrence claim with lease + fencing token → **fresh immutable F03 snapshot** + Mining Request + link in **one transaction** → existing async worker → SPMF (HUI-Miner / EFIM, Java 25 child) → reconciliation of the occurrence.

## 2. Schema and API

- Flyway **V18**: status `SKIPPED`, `skip_reason`, `failure_message`, `ck_occurrence_skip_reason`. **V19**: role `SYSTEM_SCHEDULER` and one machine user. V1–V17 byte-identical to `dev`. No V20 was needed. Reference: [`F05_SCHEDULER_SCHEMA_V14_V19.md`](../../architecture/F05_SCHEDULER_SCHEMA_V14_V19.md), ERD [`PASPM_F05_Scheduler_ERD_V14_V19.drawio`](../../diagrams/PASPM_F05_Scheduler_ERD_V14_V19.drawio).
- OpenAPI 88 → **89** operations (`GET /api/system/stores`); occurrence view gains `failureMessage`, `skipReason`, status `SKIPPED`; `lookbackDays` optional 7–365. `docs/api/openapi.json` equals the runtime document (checked).
- Roll-forward only: the old executable starts on the V19 schema but would expose the principal and run schedules as a human, so **rollback of the executable is unsafe**. See [V18/V19 report](../../reports/F05_V18_V19_SCHEMA_ROLLFORWARD_2026-11-02.md).

## 3. Test evidence and the commit it was run on

All local; **GitHub CI NOT RUN, native Linux NOT RUN, release sign-off PENDING.** Skipped tests are not counted as passes.

| Evidence | Result | Tested source |
|---|---|---|
| `scripts/verify-option-b-macos.sh --run` (23 stages: H2 suite, MySQL group, full opt-in, Flyway fresh/upgrade/rerun, JPA validate, OpenAPI) | **PASS 23/23**; 552 run / 0 failed / 179 skipped (opt-in classes) | `501f980` |
| Nine real `kill -9` crash points + stale owner + D8 update-while-preparing (`MiningSchedulerMultiJvmMySqlTest`) | **12/12**, 0 duplicates, 0 orphans | `501f980` (macOS) |
| Lease expiry **by itself** (no timestamp edited) | recovery 309 s after the kill; stalled owner resumed after 385 s was refused by the fence | `501f980` (macOS) |
| V17 upgrade with in-flight rows, simultaneous first start, default-off and zone-guard in a real process, old executable on V19 (`MiningSchedulerUpgradeCompatibilityMySqlTest`) | **5/5** | `501f980` |
| Real HTTP E2E, MySQL 8.4, SPMF 2.67a on Java 25: weekly and monthly activation, `SKIPPED`, failure visibility, RBAC, Store isolation | 3/3 | `501f980` |
| Linux amd64 **emulated**, non-root uid 10001 | 197 run / 0 failed / 2 opt-in skipped; SPMF child killed by the kernel on Backend SIGKILL | `501f980` (not native) |

The 300 s lease-recovery tests were **not repeated** for this review package: production code is unchanged since `28fac97` (a log statement), so the evidence above stays valid and is cited with its commit. Full detail: [acceptance matrix](../../reports/F05_SCHEDULER_ACCEPTANCE_MATRIX.md) (update 2026-11-02 (c)), [readiness report](../../reports/F05_D8_D9_D10_RELEASE_READINESS_2026-11-02.md), [native Linux report](../../reports/F05_NATIVE_LINUX_ACCEPTANCE_2026-11-02.md).

## 4. Where F01–F06 stand (for the integration question)

F01 auth/users/roles (4 business roles + the hidden machine principal) · F02 products, categories, inventory batches · F03 utility snapshots (frozen, hashed) · F04 CSV transaction import (Excel pending) · F05 mining requests/runs, HUI-Miner and EFIM through pinned SPMF, benchmark comparison, **scheduled mode (gated)** · F06 recommendation candidates from completed F05 runs, review with audit history, approval never publishes anything. F07–F11 (layout, A/B, storefront, dashboards) are **not implemented**.

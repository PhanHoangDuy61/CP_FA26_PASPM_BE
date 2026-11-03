# F05 — HUI-Miner and EFIM Mining

> **Release status (2026-11-02): the F03/F05 Core is merged into `dev`** by [PR #11](https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/pull/11), merge commit `11973a0`. Backend tree at `11973a0` is identical to `a4f1115`, whose code passed the full verification script (23/23) on `45dbd80`. Evidence: [Core release readiness](../../reports/F03_F05_CORE_RELEASE_READINESS_2026-11-02.md), [audit matrix](../../reports/F03_F05_API_AUDIT_MATRIX_2026-11-02.md).
>
> **Scheduler branch status (2026-11-02, `Khoi/feat/f05-scheduled-mining-option-b-completion`, not yet on `dev`, nothing pushed):** the business decisions D1–D10 are **confirmed** by the project owner in conversation (no approver name recorded; PO identity verification outstanding) and **implemented** with Flyway V18–V19. Scheduled mining is **locally verified** (default suite, MySQL 8.4 + real SPMF over HTTP, two-process crash recovery) but **not release accepted**: GitHub CI has not run, native Linux evidence is missing and final review / release sign-off are pending. Activation still answers HTTP 503 and the dispatcher stays off unless an instance sets `paspm.mining.schedule-dispatch-enabled=true`.

## 1. Purpose and scope
Mines a Store's frozen F03 utility snapshot with HUI-Miner and EFIM (external SPMF 2.67a, Java 25 child), stores patterns, compares the two algorithms and exposes results. The approved requirements give the System Admin three duties: configure `minUtil` and the active algorithm, run scheduled weekly/monthly mining, and view a benchmark comparing HUI-Miner and EFIM.

## 2. Actors and effective RBAC
| Role | Capability |
|---|---|
| `SYSTEM_ADMIN` | mining configuration, manual Store-targeted request, benchmark, schedule administration and history |
| `MERCHANDISER`, `STORE_ADMIN` | read-only, own Store: snapshots, requests, runs, patterns. **Cannot** submit or read benchmarks |
| `END_CUSTOMER` | none |

System Admin has **no** access to the Store-scoped `/api/mining/**` routes (403). Matrix: [RBAC matrix](../../api/F01_F02_RBAC_MATRIX.md) (23 F05 rows).

## 3. Status by area
| Area | State |
|---|---|
| **Merged and verified (Core)** | System Admin configuration (versioned, optimistic lock); manual Store-targeted request (202, idempotent by `requestKey`); HUI-Miner and EFIM; durable DB dispatch; lease, heartbeat and fencing; Linux parent-death launcher; benchmark; Store-scoped results; F06 consumption |
| **Implemented, default-enabled (D1, branch)** | System Admin reads of Store **snapshots, requests and runs** (`/api/system/mining/stores/{storeId}/…`); an operator may only remove groups via `paspm.mining.system-store-read.routes`. Every such read is audit-logged. **Raw patterns stay closed to System Admin** (always 403; Store Admin / Merchandiser read them Store-scoped) |
| **Implemented (D2, branch)** | `GET /api/system/stores` — read-only, paginated Store picker `{storeId,name}` for System Admin |
| **Implemented, default-disabled (D3–D10, branch)** | schedules (weekly/monthly, lookback 7–365, zone `Asia/Ho_Chi_Minh`, month-end clamp), latest-only catch-up with `SKIPPED` history, 3 attempts (15 s / 30 s) with a stored final error, frozen versions, execution as the scheduler machine principal. `PUT … enabled=true` is accepted only where `paspm.mining.schedule-dispatch-enabled=true`; otherwise **503** and nothing is written |
| **Forbidden, unchanged** | `POST /api/mining/requests` and `GET /api/mining/runs/{id}/benchmark` answer 403 for every role (`deprecated` in OpenAPI) |

## 4. Business rules
- The algorithm, minUtil mode (`PERCENTAGE` or `ABSOLUTE`) and value come from the active configuration version, pinned into each request when captured; later changes affect only new requests.
- Only a READY, frozen F03 snapshot of the chosen Store can be mined; F05 never recalculates F03 values. Input is converted exactly (scale 6, GCD normalisation, int32 limits); both algorithms receive identical bytes; exact itemset and utility equality gives `MATCH`.
- Request states `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`; run states add `TIMED_OUT`. A full queue leaves the request `PENDING` for the next database poll; an expired lease lets another instance reclaim it with a higher fence; stale writes are rejected. No transition `PENDING → FAILED` exists.
- Store isolation is enforced by Store-qualified queries and composite foreign keys; foreign ids answer 404.
- `supportCount` is always null (SPMF output has no support); utility is frozen weighted utility, not revenue.

## 5. Main workflows
Configuration (System Admin) → F03 snapshot (Merchandiser) → manual request (System Admin) → asynchronous worker → status and patterns read by Store users → benchmark (System Admin) → F06 generation. Core example without any PO decision: [Option B handoff §10](../../frontend_handoff/OPTION_B_BACKEND_API_HANDOFF.md).

## 6. API documentation
[F05 API contract](../../api/F05_MINING_API.md) · [Frontend handoff](../../frontend_handoff/F05_HANDOFF.md) · [Backend handoff for all Core flows](../../frontend_handoff/OPTION_B_BACKEND_API_HANDOFF.md) · [runtime OpenAPI](../../api/openapi.json) · [worker architecture](../../architecture/F05_WORKER_DISPATCH.md) · [SPMF runtime setup](../../setup/F05_SPMF_RUNTIME_SETUP.md).

## 7. Entities and migrations
V9 `mining_request`, `mining_run`, `high_utility_pattern`, `pattern_item`; V13 `mining_config_head`, `mining_config_version`; V14 `mining_schedule`, `mining_schedule_occurrence`; V15 frozen occurrence context; V16 request leases and fencing; V17 occurrence recovery; **V18** `SKIPPED` status, `skip_reason`, `failure_message`; **V19** `SYSTEM_SCHEDULER` role and its machine user. V12 belongs to F04. V1–V12 are unchanged by F05.

## 8. Dependencies
F01 (roles), F02 (Products), F03 (frozen snapshots), F04 (via F03), external SPMF jar and Java 25. Feeds F06.

## 9. Tests and evidence
Mining suites under `src/test/java/.../mining` (configuration, gate policy, scheduler-off, lease, worker queue, multi-JVM, SPMF engine/runtime, MySQL integration), `F03F05RbacMatrixTest`, `RecommendationIntegrationTest` (F03→F05→F06). Results on the Scheduler branch (`90ceba1`): script 23/23 (534 H2 tests; opt-in 544 run / 0 failed); macOS multi-JVM 9/9 and clone-API 5/5; Linux Docker (amd64 under emulation, non-root) 179/179. Results on `dev` (PR #11): script 23/23, macOS multi-JVM 6/6. Scheduler crash and recovery evidence: [acceptance matrix](../../reports/F05_SCHEDULER_ACCEPTANCE_MATRIX.md), [policy proposals](../../reports/F05_SCHEDULER_POLICY_DECISIONS.md). The GitHub CI results of PR #11 were not inspected in this documentation task.

**Scheduler completion 2026-11-02:** tests per decision (catch-up and skips, change semantics, failure paths, principal, administration, activation gate, Store picker), an opt-in real-HTTP acceptance on MySQL 8.4 with the pinned SPMF (`MiningScheduledActivationE2EMySqlTest`) and a two-process crash/concurrency acceptance (`MiningSchedulerMultiJvmMySqlTest`). Results and the NOT RUN list: [acceptance matrix](../../reports/F05_SCHEDULER_ACCEPTANCE_MATRIX.md); exact status per decision: [register](../../decisions/PO_DECISION_REGISTER_F05_2026-11-01.md); gap analysis: [gap matrix](../../reports/F05_SCHEDULER_GAP_MATRIX_2026-11-02.md).

**Release readiness 2026-11-02 (c)** (final tree `501f980`: script 23/23, opt-in multi-process 12/12 + 5/5 + 6/6, clone API 5/5, Linux emulated non-root 197 run / 0 failed / 2 opt-in skipped; native Linux and CI not run): [native Linux acceptance](../../reports/F05_NATIVE_LINUX_ACCEPTANCE_2026-11-02.md) (`NATIVE_LINUX_NOT_RUN`; nine real `kill -9` crash points, natural lease expiry, stale-owner fencing, SPMF child cleanup), [V18/V19 review, compatibility matrix and roll-forward plan](../../reports/F05_V18_V19_SCHEMA_ROLLFORWARD_2026-11-02.md), [D8/D9/D10 review and sign-off record](../../reports/F05_D8_D9_D10_RELEASE_READINESS_2026-11-02.md), schema reference [F05_SCHEDULER_SCHEMA_V14_V19.md](../../architecture/F05_SCHEDULER_SCHEMA_V14_V19.md).

## 10. Known limitations
System Admin has no pattern access (by decision D1); an edit to an enabled schedule recomputes its next fire time from the moment of the edit; a legacy schedule outside 7–365 days or the Store zone is kept but never runs until edited; one worker per instance with eight queued; at most 256 products and 20,000 matrix rows per input; kernel-level cleanup of the SPMF child exists on Linux only; the SPMF jar is external (GPLv3, pinned by SHA-256) and not bundled.

## 11. Product Owner decisions D1–D10
All ten business choices are `BUSINESS_CHOICE_CONFIRMED` (2026-11-02; D3 amended the same day to 7–365 days). See the [package](../../decisions/PO_DECISION_PACKAGE_D1_D10_2026-11-01.md) and the [register](../../decisions/PO_DECISION_REGISTER_F05_2026-11-01.md), which track `IMPLEMENTED`, `TESTED` and `RELEASE_ACCEPTED` separately. `RELEASE_ACCEPTED` is granted for none; verifying the confirmer's formal Product Owner identity is outstanding.

## 12. Related diagrams
[Diagram index](../../diagrams/README.md) and the editable [27-page Draw.io pack](../../diagrams/PASPM_Unified_27_Diagrams_UML2_Editable.drawio) (QA: [UML27 report](../../reports/UML27_DIAGRAM_QA_2026-11-02.md)); relevant pages: Use Case UC04–UC08, AD03 (mining), AD04 (benchmark), State R2-06.03 (request) and R2-06.04 (run), Sequence AD03 and AD04, System Architecture.

## 13. Historical references
[Gap analysis](reference/F05_GAP_ANALYSIS.md), [original verification](reference/F05_VERIFICATION.md) (retired Merchandiser flow, dated), [F05→F06 handoff](reference/F05_F06_HANDOFF.md), [P0 deployment fix report](../../audits/F05_P0_DEPLOYMENT_FIX_REPORT.md), [legacy RUNNING reconciliation](../../architecture/F05_LEGACY_RUNNING_RECONCILIATION.md).

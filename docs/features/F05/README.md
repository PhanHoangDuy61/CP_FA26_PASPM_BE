# F05 — EFIM Mining (legacy HUI-Miner history)

## Current policy — 2026-11-16

The current local change on `dev` (base `3e438f8`, not committed) fixes all new manual and scheduled mining requests to one EFIM run. The minUtil configuration no longer accepts an algorithm. Comparison input and both benchmark routes are removed. Existing HUI-Miner runs, comparison results, runtime metrics and stored schedule metadata remain readable; historical schedule comparison flags do not affect new occurrences. No database migration or historical rewrite is planned. See the [current API contract](../../api/F05_MINING_API.md#current-contract-2026-11-16).

> **Release status (2026-11-02): the F03/F05 Core is merged into `dev`** by [PR #11](https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/pull/11), merge commit `11973a0`. Backend tree at `11973a0` is identical to `a4f1115`, whose code passed the full verification script (23/23) on `45dbd80`. Evidence: [Core release readiness](../../reports/F03_F05_CORE_RELEASE_READINESS_2026-11-02.md), [audit matrix](../../reports/F03_F05_API_AUDIT_MATRIX_2026-11-02.md).
>
> **Scheduler branch status (2026-11-02, `Khoi/feat/f05-scheduled-mining-option-b-completion`, not yet on `dev`, nothing pushed):** the business decisions D1–D10 are **confirmed** by the project owner in conversation (no approver name recorded; PO identity verification outstanding) and **implemented** with Flyway V18–V19. Scheduled mining is **locally verified** (default suite, MySQL 8.4 + real SPMF over HTTP, two-process crash recovery) but **not release accepted**: GitHub CI has not run, native Linux evidence is missing and final review / release sign-off are pending. Activation still answers HTTP 503 and the dispatcher stays off unless an instance sets `paspm.mining.schedule-dispatch-enabled=true`.

> **Superseded 2026-11-16 ([DEC-20261116-04](../../decisions/2026-11-16/DEC-20261116-04-f05-efim-only-mining.md), EFIM-only, `cd90e58` merged into `dev` by `2a944c5`).** The "Current policy" section above is no longer an uncommitted local change; it is on `dev`. In the table below, "per algorithm", "`COMPARING` (two algorithms only)" and "`+1 when comparing`" describe two-algorithm requests, which new requests can no longer create. `COMPARING` remains only for legacy two-run requests reclaimed after the upgrade (`mining/MiningWorker.java:84`).

## Historical status — branch `Khoi/feat/f05-progress-f03-weight-v2` (2026-11-15)

Commits `3bdfee3`, `bd60c75`, `982094c` (Checkpoint 1) and `e38b55f` (F03 Weight v2 consumers). Code paths are relative to `src/main/java/vn/edu/fpt/paspm/` on `c8c837a`.

| Item | Behaviour | Evidence |
|---|---|---|
| Progress routes | `GET /api/mining/requests/{id}/progress` (Merchandiser, Store Admin, own Store); `GET /api/system/mining/stores/{storeId}/requests/{requestId}/progress` (System Admin, `REQUESTS` gate, audited) | `mining/MiningProgressController.java:22-29`; `security/SecurityConfig.java:93` |
| DTO | `ProgressView {requestId, storeId, status, stage, stageStartedAt, currentRunId, algorithm, attemptNo, completedSteps, totalSteps, percent, elapsedMs, timeoutMs, comparisonStatus}` | `mining/MiningContracts.java:25-30` |
| Stages | `QUEUED` at claim → `RUNNING` → `SAVING` per algorithm → `COMPARING` (two algorithms only) → `COMPLETED`/`FAILED`; rows before V24 read `LEGACY` | `mining/MiningDispatchLease.java:21`, `:41-43`; `mining/MiningWorker.java:66`, `:79`, `:84`, `:86`; V24 |
| Fencing | Stage writes go through `guarded(...)` (lease owner + fence must still match) | `mining/MiningDispatchLease.java:41-43` |
| Steps | `totalSteps = 2 × algorithms (+1 when comparing)`; completed run = 2, saving run = 1; computed at read time | `mining/MiningProgressReader.java:22-32` |
| `attemptNo` | = `mining_request.lease_fence`; increases by 1 on every claim, including recovery after a lease loss | `mining/MiningProgressReader.java:37`; `mining/MiningDispatchLease.java:21` |
| `elapsedMs` / `timeoutMs` | Database clock since `stage_started_at`; engine timeout only while `RUNNING` | `mining/MiningProgressReader.java:35-38` |
| Merchandiser submission | `POST /api/mining/requests {requestKey, snapshotId}`; Store from the JWT; algorithm and minUtil from the current System Admin configuration (no overrides) | `mining/MiningController.java:24-26`; `mining/MiningContracts.java:23-24`; `mining/MiningService.java:52-53`; `security/SecurityConfig.java:94` |
| λ_max block | New Merchandiser request → 409 when the snapshot's frozen λ is above the current λ_max; idempotent replay returns the existing request | `mining/MiningService.java:83-84` |
| Scheduler | Enabling a schedule still returns 503 unless dispatch is enabled | `mining/MiningScheduleService.java:67` |
| Scheduled snapshot (W2) | Same freezer as manual snapshots; seasons active on the occurrence date copied by value | `mining/MiningScheduledSnapshot.java:44-45`; `utility/UtilityFreezer.java:23-25` |

**Superseded 2026-11-15:** "System Admin is the only mining submitter" (requirements and the 2026-11-02 text below) — replaced by owner decision P1 (Merchandiser submits for its own Store). The System Admin route is unchanged.

**Verification:** Phase 1 gate 632 tests, 627 PASS, 5 SKIP plus JPA 5/5 ([record 003](../../record-of-changes/2026-11-15/03-opt-in-gate-and-weight-v2.md)); final branch gate 645 tests, 640 PASS, 5 SKIP plus JPA 5/5 ([record 004](../../record-of-changes/2026-11-15/04-f03-weight-v2-expiry-season.md)). **NOT VERIFIED:** Linux SPMF orphan cleanup (macOS evidence only: surviving children were cleaned up by the tests), GitHub CI, interactive Swagger UI.

**Known gaps:** progress `percent` is completed work, not time, and may be FAILED at 100% or terminate below 100%; `attemptNo` can increase after recovery, so clients must not treat it as a retry counter from the user; `mining_run.attempt_no` is exposed in `RunView` (`mining/MiningRepository.java:46`) but never incremented (V9 default 1); it is not the progress `attemptNo`.

Earlier dated release evidence below is historical, not evidence for this feature branch.

## 1. Purpose and scope
Mines a Store's frozen F03 utility snapshot with EFIM (external SPMF 2.67a, Java 25 child), stores patterns and exposes results. HUI-Miner execution and comparison data are retained only for legacy requests/results. There is no benchmark endpoint.

## FoodMart-scale input bound — 2026-11-16
The old 256-distinct-Product guard was an application validation in `MiningInputBuilder.MAX_ITEMS`, not an EFIM or database limitation. It rejected a valid snapshot before enqueueing. The bound is now **1,600 distinct Products** and remains **20,000 `utility_matrix_item` rows**. The 1,600 bound is based on a measured 1,559-Product FoodMart reference input plus 41 Products of headroom; the 20,000-row bound is unchanged. The shared constant is also used by F06's `FrozenEvidenceReader`, so F06 can validate frozen evidence up to the same item bound. No migration, frozen JSON rewrite, or OpenAPI schema change was required.

The public FoodMart SPMF reference file measured 4,141 transactions, 18,319 transaction-Product rows, 1,559 Products and 179,510 source bytes. A separate, explicitly synthetic PASPM fixture with 1,559 Products passed the persisted F04 → F03 → F05 → F06 lifecycle on disposable MySQL with real SPMF EFIM. It produced 1,759 utility-matrix rows and 1,560 persisted patterns. These are synthetic acceptance dimensions, not measurements of an authentic FoodMart F03 snapshot. The original FoodMart extract lacks canonical order IDs and approved batch expiry/push-day/season evidence, so authentic-data acceptance remains blocked. See [the benchmark and acceptance report](../../testing/F05_FOODMART_SCALABILITY_BENCHMARK.md).

## 2. Actors and effective RBAC
| Role | Capability |
|---|---|
| `SYSTEM_ADMIN` | minUtil configuration (EFIM fixed), manual Store-targeted request, schedule administration and history; no benchmark endpoint |
| `MERCHANDISER` | own-Store reads and submission using System Admin minUtil policy |
| `STORE_ADMIN` | own-Store reads only; cannot submit |
| `END_CUSTOMER` | none |

System Admin has **no** access to the Store-scoped `/api/mining/**` routes (403). Matrix: [RBAC matrix](../../api/F01_F02_RBAC_MATRIX.md) (23 F05 rows).

## 3. Status by area
| Area | State |
|---|---|
| **Core** | Versioned minUtil policy; new requests are EFIM-only; durable DB dispatch; lease, heartbeat and fencing; Linux parent-death launcher; Store-scoped results; F06 consumption. HUI-Miner and comparisons are legacy-read/dispatch compatibility only |
| **Implemented, default-enabled (D1, branch)** | System Admin reads of Store **snapshots, requests and runs** (`/api/system/mining/stores/{storeId}/…`); an operator may only remove groups via `paspm.mining.system-store-read.routes`. Every such read is audit-logged. **Raw patterns stay closed to System Admin** (always 403; Store Admin / Merchandiser read them Store-scoped) |
| **Implemented (D2, branch)** | `GET /api/system/stores` — read-only, paginated Store picker `{storeId,name}` for System Admin |
| **Implemented, default-disabled (D3–D10, branch)** | schedules (weekly/monthly, lookback 7–365, zone `Asia/Ho_Chi_Minh`, month-end clamp), latest-only catch-up with `SKIPPED` history, 3 attempts (15 s / 30 s) with a stored final error, frozen versions, execution as the scheduler machine principal. `PUT … enabled=true` is accepted only where `paspm.mining.schedule-dispatch-enabled=true`; otherwise **503** and nothing is written |
| **Current API change** | Both benchmark routes are removed (404); new request/schedule comparison inputs are rejected. Merchandiser submission is active; Store Admin cannot submit |
| **Branch 2026-11-15** | V24 progress stages + two progress routes; Merchandiser submission (P1); W2 snapshots (see status table above) |

## 4. Business rules
- EFIM is fixed for all new requests. The minUtil mode (`PERCENTAGE` or `ABSOLUTE`) and value come from the active configuration version and are pinned into each request; legacy algorithm values are not rewritten.
- Only a READY, frozen F03 snapshot of the chosen Store can be mined; F05 never recalculates F03 values. Input is converted exactly (scale 6, GCD normalisation, int32 limits). Historical multi-algorithm runs/comparisons remain readable.
- Request states `PENDING`, `RUNNING`, `COMPLETED`, `FAILED`; run states add `TIMED_OUT`. A full queue leaves the request `PENDING` for the next database poll; an expired lease lets another instance reclaim it with a higher fence; stale writes are rejected. No transition `PENDING → FAILED` exists.
- Store isolation is enforced by Store-qualified queries and composite foreign keys; foreign ids answer 404.
- `supportCount` is always null (SPMF output has no support); utility is frozen weighted utility, not revenue.

## 5. Main workflows
Configuration (System Admin) → F03 snapshot (Merchandiser) → EFIM request (System Admin, or Merchandiser for their own Store) → asynchronous worker → status and patterns read by Store users → F06 generation. There is no benchmark step. Core example without any PO decision: [Option B handoff §10](../../frontend_handoff/OPTION_B_BACKEND_API_HANDOFF.md).

## 6. API documentation
[F05 API contract](../../api/F05_MINING_API.md) · [Frontend handoff](../../frontend_handoff/F05_HANDOFF.md) · [Backend handoff for all Core flows](../../frontend_handoff/OPTION_B_BACKEND_API_HANDOFF.md) · [runtime OpenAPI](../../api/openapi.json) · [worker architecture](../../architecture/F05_WORKER_DISPATCH.md) · [SPMF runtime setup](../../setup/F05_SPMF_RUNTIME_SETUP.md).

## 7. Entities and migrations
V9 `mining_request`, `mining_run`, `high_utility_pattern`, `pattern_item`; V13 `mining_config_head`, `mining_config_version`; V14 `mining_schedule`, `mining_schedule_occurrence`; V15 frozen occurrence context; V16 request leases and fencing; V17 occurrence recovery; **V18** `SKIPPED` status, `skip_reason`, `failure_message`; **V19** `SYSTEM_SCHEDULER` role and its machine user. V12 belongs to F04. V1–V12 are unchanged by F05.

## 8. Dependencies
F01 (roles), F02 (Products), F03 (frozen snapshots), F04 (via F03), external SPMF jar and Java 25. Feeds F06.

F01 session reconciliation (2026-11-12, dev V20 retained + additive V21): HTTP admin/read routes use valid Bearer sessions and existing role/Store rules. Internal scheduled execution does not obtain browser refresh cookies. This update changes no scheduler policy, activation gate, lease/fencing or SPMF runtime. See [shared session impact and evidence](../F01/F01_SESSION_MANAGEMENT.md#5-tác-động-tới-feature-khác).

## 9. Tests and evidence
Mining suites under `src/test/java/.../mining` (configuration, gate policy, scheduler-off, lease, worker queue, multi-JVM, SPMF engine/runtime, MySQL integration), `F03F05RbacMatrixTest`, `RecommendationIntegrationTest` (F03→F05→F06). Results on the Scheduler branch (`90ceba1`): script 23/23 (534 H2 tests; opt-in 544 run / 0 failed); macOS multi-JVM 9/9 and clone-API 5/5; Linux Docker (amd64 under emulation, non-root) 179/179. Results on `dev` (PR #11): script 23/23, macOS multi-JVM 6/6. Scheduler crash and recovery evidence: [acceptance matrix](../../reports/F05_SCHEDULER_ACCEPTANCE_MATRIX.md), [policy proposals](../../reports/F05_SCHEDULER_POLICY_DECISIONS.md). The GitHub CI results of PR #11 were not inspected in this documentation task.

**Scheduler completion 2026-11-02:** tests per decision (catch-up and skips, change semantics, failure paths, principal, administration, activation gate, Store picker), an opt-in real-HTTP acceptance on MySQL 8.4 with the pinned SPMF (`MiningScheduledActivationE2EMySqlTest`) and a two-process crash/concurrency acceptance (`MiningSchedulerMultiJvmMySqlTest`). Results and the NOT RUN list: [acceptance matrix](../../reports/F05_SCHEDULER_ACCEPTANCE_MATRIX.md); exact status per decision: [register](../../decisions/PO_DECISION_REGISTER_F05_2026-11-01.md); gap analysis: [gap matrix](../../reports/F05_SCHEDULER_GAP_MATRIX_2026-11-02.md).

**Release readiness 2026-11-02 (c)** (final tree `501f980`: script 23/23, opt-in multi-process 12/12 + 5/5 + 6/6, clone API 5/5, Linux emulated non-root 197 run / 0 failed / 2 opt-in skipped; native Linux and CI not run): [native Linux acceptance](../../reports/F05_NATIVE_LINUX_ACCEPTANCE_2026-11-02.md) (`NATIVE_LINUX_NOT_RUN`; nine real `kill -9` crash points, natural lease expiry, stale-owner fencing, SPMF child cleanup), [V18/V19 review, compatibility matrix and roll-forward plan](../../reports/F05_V18_V19_SCHEMA_ROLLFORWARD_2026-11-02.md), [D8/D9/D10 review and sign-off record](../../reports/F05_D8_D9_D10_RELEASE_READINESS_2026-11-02.md), schema reference [F05_SCHEDULER_SCHEMA_V14_V19.md](../../architecture/F05_SCHEDULER_SCHEMA_V14_V19.md).

## 10. Known limitations
System Admin has no pattern access (by decision D1); an edit to an enabled schedule recomputes its next fire time from the moment of the edit; a legacy schedule outside 7–365 days or the Store zone is kept but never runs until edited; one worker per instance with eight queued; at most 1,600 products and 20,000 matrix rows per input; kernel-level cleanup of the SPMF child exists on Linux only; the SPMF jar is external (GPLv3, pinned by SHA-256) and not bundled. Lower `minUtil` can still exceed the 2,000-pattern or 4 MiB output guard; the FoodMart low-threshold probe hit the output guard before timeout. Synthetic 1,559-Product persisted F03 → F05 → F06 acceptance passed on disposable MySQL; authentic FoodMart snapshot acceptance remains NOT RUN because source grouping and F03 inventory evidence are missing. Hosted CI and production Linux acceptance remain NOT RUN.

## 11. Product Owner decisions D1–D10
All ten business choices are `BUSINESS_CHOICE_CONFIRMED` (2026-11-02; D3 amended the same day to 7–365 days). See the [package](../../decisions/PO_DECISION_PACKAGE_D1_D10_2026-11-01.md) and the [register](../../decisions/PO_DECISION_REGISTER_F05_2026-11-01.md), which track `IMPLEMENTED`, `TESTED` and `RELEASE_ACCEPTED` separately. `RELEASE_ACCEPTED` is granted for none; verifying the confirmer's formal Product Owner identity is outstanding.

## 12. Related diagrams
[Diagram index](../../diagrams/README.md) and the editable [27-page Draw.io pack](../../diagrams/PASPM_Unified_27_Diagrams_UML2_Editable.drawio) (QA: [UML27 report](../../reports/UML27_DIAGRAM_QA_2026-11-02.md)); relevant pages: Use Case UC04–UC08, AD03 (mining), State R2-06.03 (request) and R2-06.04 (run), Sequence AD03 and System Architecture. AD04 benchmark material is historical; benchmark routes were removed on 2026-11-16.

## 13. Historical references
[Gap analysis](reference/F05_GAP_ANALYSIS.md), [original verification](reference/F05_VERIFICATION.md) (retired Merchandiser flow, dated), [F05→F06 handoff](reference/F05_F06_HANDOFF.md), [P0 deployment fix report](../../audits/F05_P0_DEPLOYMENT_FIX_REPORT.md), [legacy RUNNING reconciliation](../../architecture/F05_LEGACY_RUNNING_RECONCILIATION.md).

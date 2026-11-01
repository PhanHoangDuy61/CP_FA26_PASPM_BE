# F05 — HUI-Miner and EFIM Mining

> **Release status (2026-11-02): the F03/F05 Core is merged into `dev`** by [PR #11](https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/pull/11), merge commit `11973a0`. Backend tree at `11973a0` is identical to `a4f1115`, whose code passed the full verification script (23/23) on `45dbd80`. Evidence: [Core release readiness](../../reports/F03_F05_CORE_RELEASE_READINESS_2026-11-02.md), [audit matrix](../../reports/F03_F05_API_AUDIT_MATRIX_2026-11-02.md).
>
> **Not complete:** scheduled mining is **not operational** (activation returns HTTP 503, dispatcher off by default) and decisions D1–D10 are **unapproved**. The existing scheduler infrastructure must not be read as a finished feature.

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
| **Merged but gated: pending D1** | five System Admin Store-read routes (`/api/system/mining/stores/{storeId}/…` snapshots, requests, requests/{id}, runs, patterns) answer 403 unless `paspm.mining.system-store-read.routes` lists the group (`SNAPSHOTS`, `REQUESTS`, `RUNS`, `PATTERNS`); default none |
| **Absent: pending D2** | no endpoint lists Stores for the System Admin |
| **Merged but disabled: pending D3–D10** | schedule create/list/read/edit/disable and occurrence history work; `PUT … enabled=true` returns **503**; `paspm.mining.schedule-dispatch-enabled` defaults `false` (an enabled schedule in the database stays inert) |
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
V9 `mining_request`, `mining_run`, `high_utility_pattern`, `pattern_item`; V13 `mining_config_head`, `mining_config_version`; V14 `mining_schedule`, `mining_schedule_occurrence`; V15 frozen occurrence context; V16 request leases and fencing; V17 occurrence recovery. V12 belongs to F04. V1–V12 are unchanged by F05.

## 8. Dependencies
F01 (roles), F02 (Products), F03 (frozen snapshots), F04 (via F03), external SPMF jar and Java 25. Feeds F06.

## 9. Tests and evidence
Mining suites under `src/test/java/.../mining` (configuration, gate policy, scheduler-off, lease, worker queue, multi-JVM, SPMF engine/runtime, MySQL integration), `F03F05RbacMatrixTest`, `RecommendationIntegrationTest` (F03→F05→F06). Results: script 23/23; macOS multi-JVM 6/6 and clone-API 5/5; Linux Docker 71/72 (the one failure is a root-user artefact that passes as non-root). Scheduler crash and recovery evidence: [acceptance matrix](../../reports/F05_SCHEDULER_ACCEPTANCE_MATRIX.md), [policy proposals](../../reports/F05_SCHEDULER_POLICY_DECISIONS.md). The GitHub CI results of PR #11 were not inspected in this documentation task.

## 10. Known limitations
System Admin cannot discover run ids through its own routes until D1 opens `REQUESTS,RUNS`; one worker per instance with eight queued; at most 256 products and 20,000 matrix rows per input; kernel-level cleanup of the SPMF child exists on Linux only; the SPMF jar is external (GPLv3, pinned by SHA-256) and not bundled.

## 11. Pending Product Owner decisions
[D1–D10 package](../../decisions/PO_DECISION_PACKAGE_D1_D10_2026-11-01.md) and [register](../../decisions/PO_DECISION_REGISTER_F05_2026-11-01.md): D1 System Admin read access, D2 Store selection, D3 lookback, D4 time zone, D5 monthly overflow, D6 catch-up, D7 retry/backoff, D8 effect of configuration/schedule changes, D9 actor of automatic runs, D10 activation acceptance. All DECISION boxes are blank; nothing is approved.

## 12. Related diagrams
`docs/diagrams/README.md` indexes the 27-page Draw.io pack: Use Case UC04–UC08, AD03 (mining), AD04 (benchmark), State R2-06.03 (request) and R2-06.04 (run), Sequence AD03 and AD04, System Architecture.

## 13. Historical references
[Gap analysis](reference/F05_GAP_ANALYSIS.md), [original verification](reference/F05_VERIFICATION.md) (retired Merchandiser flow, dated), [F05→F06 handoff](reference/F05_F06_HANDOFF.md), [P0 deployment fix report](../../audits/F05_P0_DEPLOYMENT_FIX_REPORT.md), [legacy RUNNING reconciliation](../../architecture/F05_LEGACY_RUNNING_RECONCILIATION.md).

> **WORK IN PROGRESS — 2026-10-31, NOT MERGE READY.** The F05 administrative RBAC and API are being changed on this branch. The historical endpoint descriptions below are not the current branch contract. `POST /api/mining/requests` and the former Store benchmark route are forbidden; System Admin endpoints are under verification. Scheduling is partial: schedules are created disabled, the dispatcher defaults off, and activation returns 503 pending acceptance and policy approval. See [implementation report](../../reports/PASPM_F03_F05_Implementation_Report.md).

# F05 — HUI-Miner and EFIM Mining

## Current scope

8 mapped async request/run/pattern/benchmark operations; actual SPMF child-process adapter exists. Local runtime configuration is distinct from public hosting and from research/production acceptance.

- [Canonical API](../../api/F05_MINING_API.md) · [Frontend handoff](../../frontend_handoff/F05_HANDOFF.md).
- [Architecture](../../architecture/README.md) · [diagrams](../../diagrams/README.md) · [documentation map](../../DOCUMENTATION_MAP.md).

## Requirements and authority

[Original gap/scope](reference/F05_GAP_ANALYSIS.md), [runtime setup](../../setup/F05_SPMF_RUNTIME_SETUP.md), [current contract](../../api/F05_MINING_API.md). Keep threshold/benchmark semantics in the actual contract; no algorithm or scope change here.

## Workflow and implementation

Follow the endpoint/use-case sequence in the canonical contract and handoff; no API tables are duplicated here. Source availability is separate from tested/accepted status. [Review register](../../requirements/README.md#documentation-review-register) records unresolved authority and packaging gaps.

## Testing and evidence

[Historical verification](reference/F05_VERIFICATION.md), [captured evidence](reference/evidence), [P0 fix report](../../audits/F05_P0_DEPLOYMENT_FIX_REPORT.md). Old V7 clean-start failure is a dated result, not a fresh current failure.

## Historical references and known issues

[Original F05→F06 handoff](reference/F05_F06_HANDOFF.md) needs review against implemented F06. [F05 manifest](reference/F05_FILE_MANIFEST.json) is frozen:40 recorded old paths move; use the path map, do not rewrite it.


## Scheduler working-tree update — 2026-11-01

An internal, default-disabled dispatcher and occurrence lease/retry/reconciliation have been added with V17 (Flyway renumbered 2026-11-01; formerly V16). Public schedule activation remains 503. Fresh-snapshot automatic execution was exercised against disposable MySQL and real SPMF in one JVM and in a normal two-container run; two-container scheduler crash recovery remains unverified. See [worker architecture](../../architecture/F05_WORKER_DISPATCH.md) and the implementation report for the current acceptance status.

### Scheduler requirement traceability and access

| Requirement | Current source / evidence | Status |
| --- | --- | --- |
| System Admin schedule administration | `MiningScheduleController`: POST/GET collection, GET/PUT item, GET occurrence list/detail; controller requires SYSTEM_ADMIN | Implemented; targeted RBAC tests executed; activation returns 503 |
| Occurrence durability and fresh F03 snapshot | V14–V17, `MiningScheduleReservation`, `MiningOccurrenceLease`, `MiningOccurrenceProcessor`; synthetic MySQL automatic and two-container normal tests | Partial acceptance; crash at each preparation point not yet verified |
| Request dispatch and real SPMF | `MiningWorker`, `MiningDispatchLease`, Linux parent-death launcher; normal two-container and one SIGKILL recovery experiment | Partial acceptance; startup-race and lease-loss tests on final image outstanding |
| Other roles | MERCHANDISER, STORE_ADMIN and END_CUSTOMER do not have schedule administration access | Authorization enforced by controller; not every negative case was independently tested on the final image |

The latest deterministic crash and concurrency evidence is [here](../../testing/evidence/F05_SCHEDULER_CRASH_ACCEPTANCE_2026-11-01.txt). The scheduler remains **PARTIALLY_IMPLEMENTED / NOT_READY**, and the separate [business policy register](../../reports/F05_SCHEDULER_POLICY_DECISIONS.md) is pending PO approval. Do not present the 503 enable guard as removed.

A 2026-11-01 final-gap run observed actual queue rejections and later redispatch, MySQL TCP disconnection/reconnection, concurrent startup, natural retry exhaustion and SPMF process isolation. See [evidence](../../testing/evidence/F05_FINAL_GAPS_2026-11-01.txt) and the [acceptance matrix](../../reports/F05_SCHEDULER_ACCEPTANCE_MATRIX.md) for precise boundaries. Activation remains 503 and PO decisions remain pending.

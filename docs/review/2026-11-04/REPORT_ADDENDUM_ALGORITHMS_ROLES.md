# Report wording adopted for algorithms, roles and status (decision of 2026-11-02, supplied by the project owner)

The project owner chose to keep the report and bring it in line with the implemented Backend through this wording (resolves R1–R4 and R10 of the [consistency review](PROJECT_REPORT_CONSISTENCY_REVIEW.md) as "addendum"). The text below is exactly as supplied. **It has not been inserted into any `.docx`**; the Word files in `sprint_2/` are unchanged.

## Wording as supplied

### Algorithms
The implemented PASPM system uses HUI-Miner and EFIM, integrated through the SPMF library, to discover high-utility itemsets from immutable utility snapshots produced by F03.
F05 supports configurable mining execution, stores the resulting patterns, and provides benchmarking capabilities to compare HUI-Miner and EFIM.
Earlier report versions referenced TKQ/FEACP. Those references describe an earlier design and must not be presented as algorithms implemented or verified in the current Backend.

### User Roles and Access Control
PASPM defines four business user roles:

* System Administrator (SYSTEM_ADMIN): Manages mining configurations, manual mining requests, mining schedules, and algorithm benchmarks. Can inspect Store mining snapshots, requests, and runs, but cannot access raw mining patterns.
* Store Administrator (STORE_ADMIN): Manages Store operations and has read-only access to mining results and patterns belonging to their Store.
* Merchandiser (MERCHANDISER): Manages utility-related workflows, prepares F03 snapshots, reviews Store-scoped mining patterns, and uses mining results for merchandise recommendations and layouts.
* End Customer (END_CUSTOMER): Uses customer-facing storefront features and has no administrative F05 mining access.

The Scheduler also uses an internal SYSTEM_SCHEDULER machine identity for automated execution and auditing. This identity is not an interactive business user role and cannot log in.

### Implementation Status
The F05 Scheduled Mining implementation has passed local verification. GitHub CI, native Linux acceptance, and formal release sign-off remain pending. Scheduled dispatch is disabled by default.

## Check against the Backend at `c65daf2`

| Statement | Result | Evidence |
|---|---|---|
| HUI-Miner and EFIM through SPMF; immutable F03 snapshots as input; patterns stored; HUI-Miner vs EFIM comparison | **agrees** | `MiningEngine`/`SpmfMiningEngine`, `comparisonStatus`; `docs/api/F05_MINING_API.md` |
| TKQ/FEACP not implemented or verified | **agrees** | no TKQ/FEACP in `src/` or `docs/` |
| SYSTEM_ADMIN: configuration, manual requests, schedules, benchmarks; sees snapshots/requests/runs; **no raw patterns** | **agrees** | `MiningSystemController` (`hasRole('SYSTEM_ADMIN')`), patterns route always 403 (D1) |
| STORE_ADMIN read-only for own-Store results and patterns | **agrees** | `MiningController` (`hasAnyRole('MERCHANDISER','STORE_ADMIN')`, Store-scoped) |
| MERCHANDISER prepares F03 snapshots, reviews Store-scoped patterns | **agrees** | `UtilitySnapshotController` (`hasRole('MERCHANDISER')`); F06 generate/approve/reject are Merchandiser-only |
| MERCHANDISER "uses mining results for … **layouts**" | **needs a qualifier** | F06 produces reviewable recommendation **candidates**; layout management (F07) is **PLANNED**, nothing publishes a layout |
| END_CUSTOMER "uses customer-facing **storefront** features" | **needs a qualifier** | the role is created by self-registration / Google sign-up and has no F05 access, but **no implemented endpoint is customer-facing**: the storefront (UC26–UC29, AD08) is PLANNED |
| SYSTEM_SCHEDULER: internal machine identity, not an interactive role, cannot log in | **agrees** | V19: `INACTIVE`, no password, hidden from `/api/users`/`/api/roles`; login and password-reset flows give it no credential (`SchedulerPrincipalHttpTest`) |
| Local verification passed; CI, native Linux, sign-off pending; dispatch off by default | **agrees** | [readiness report](../../reports/F05_D8_D9_D10_RELEASE_READINESS_2026-11-02.md) |

## Two precision edits recommended before the wording goes into the report

Both keep your text and only prevent a reviewer from reading planned work as delivered:

1. Merchandiser: "…and uses mining results for merchandise recommendations (F06 candidates; layout management is planned)."
2. End Customer: "End Customer (END_CUSTOMER): Registered through self-registration; customer-facing storefront features are planned, and the role has no administrative F05 mining access."

The UML pack already draws it this way (UC12–UC16 and UC23–UC29 tagged `[PLANNED]`, End Customer actor on the planned storefront only).

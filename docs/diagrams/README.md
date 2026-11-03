# Diagrams and Database Models

## Review 2 Mermaid pack — final diagrams (`mermaid/`)

The files in [`mermaid/`](mermaid/) are transcribed from the **final clean** draw.io diagrams (UML: branch `Khoi/docs/diagrams-final-clean`, 36 pages; ERD: `PASPM_ERD_V1_V19_clean.drawio`, SHA-256 `64f9c7e712b8dfa2e3d81ac8f2c07167807f982470fa33be175330f300014c3a`). Names, states, transitions, guards, actors and steps follow the draw.io pages; nothing was added or dropped. SVG renders are in [`mermaid/rendered/`](mermaid/rendered/) (Mermaid CLI 12.2.0). The draw.io files remain the master; the figure numbers are those of Report 4 (SDD) and Report 3 (SRS) in `docs/review-2/`.

| Mermaid source | draw.io page | Report figure |
|---|---|---|
| `architecture.mmd` | R2-04 — System Architecture | SDD Figure 1 |
| `conceptual-erd.mmd` | Conceptual ERD (ERD file) | SDD Figure 3, SRS Figure 4 |
| `logical-erd.mmd` | Logical ERD (ERD file); generated from the migrated schema (31 tables, 304 columns, 59 foreign keys) | SDD Figure 4, SRS Figure 5 |
| `state-import-batch.mmd` | R2-06.01 — Import Batch State | SDD Figure 11 |
| `state-utility-snapshot.mmd` | R2-06.02 — Utility Snapshot State | SDD Figure 13 |
| `state-mining-request-and-schedule-occurrence.mmd` | R2-06.03 — Mining Request State, with the Schedule Occurrence State drawn on the same page | SDD Figure 14 |
| `state-mining-run.mmd` | R2-06.04 — Mining Run State | SDD Figure 15 |
| `state-recommendation-review.mmd` | R2-06.05 — Recommendation Review State | SDD Figure 16 |
| `state-staging-row.mmd` | R2-06.06 — Staging Row State | SDD Figure 12 |
| `activity-ad01-overall-system-workflow.mmd` | AD01 - Overall System Workflow | SDD Figure 19 |
| `activity-ad02-transaction-data-processing.mmd` | AD02 - Transaction Data Processing | SDD Figure 22 |
| `activity-ad03-mining-execution-configuration.mmd` | AD03 - Mining Execution & Configuration | SDD Figure 25 |
| `activity-ad04-algorithm-benchmarking.mmd` | AD04 - Algorithm Benchmarking | SDD Figure 28 |
| `activity-ad05-product-recommendation-approval.mmd` | AD05 - Product Recommendation & Approval | SDD Figure 31 |
| `activity-ad06-layout-campaign-management.mmd` | AD06 - Layout & Campaign Management | SDD Figure 34 |
| `activity-ad07-ab-testing-performance-evaluation.mmd` | AD07 - A/B Testing & Performance Evaluation | SDD Figure 37 |
| `activity-ad08-customer-shopping-bundle.mmd` | AD08 - Customer Shopping & Bundle | SDD Figure 40 |

Activity diagrams use one Mermaid `subgraph` per swimlane exactly as drawn (AD01 has one lane). Use cases, context, screen flow, package diagram, class and sequence diagrams are not part of this pack; they exist only as draw.io pages.

Re-render with `PUPPETEER_SKIP_DOWNLOAD=1 npx -y @mermaid-js/mermaid-cli@12.2.0 -i mermaid/<file>.mmd -o mermaid/rendered/<file>.svg` (a Chrome executable can be passed with `-p <puppeteer-config.json>`).

## Superseded legacy Mermaid files (top level `*.mmd`, `rendered/`)

Kept unchanged as history; **do not use them for Review 2**. Comparison with the final draw.io pages (2026-11-04):

| Legacy file | Final page | Status |
|---|---|---|
| `01-system-context.mmd` | Context | Differs: adds a "Store staff / data operator" actor and an identity-provider node that the final page does not have |
| `02-use-cases.mmd` | Use Case | Differs: no use-case numbering, Store roles create mining requests (System Admin only now), operator actor |
| `03-system-architecture.mmd` | R2-04 | Differs: says Flyway V1–V11, "JDBC repositories" only (final: hybrid JPA/JDBC), no scheduler component, feature codes in labels, dashed demo edge |
| `04-logical-erd-v1-v11.mmd` | Logical ERD | Superseded: 26 tables (V1–V11); the final schema has 31 tables and 59 foreign keys |
| `05a-import-batch-state.mmd` | R2-06.01 | Same states and transitions; guard wording differs |
| `05b-utility-snapshot-state.mmd` | R2-06.02 | Differs: extra Capture/Rejected states and notes |
| `05c-mining-request-state.mmd` | R2-06.03 | Differs: no lease-reclaim transition, no Schedule Occurrence machine |
| `05d-mining-run-state.mmd` | R2-06.04 | Differs: no RUNNING → PENDING (lease reclaimed) transition |
| `05e-recommendation-review-state.mmd` | R2-06.05 | Same states and transitions; guards differ, extra note |
| `05f-staging-row-state.mmd` | R2-06.06 | Same states; the VALID → REJECTED reason differs (order-level validation vs conflicting content) |
| `06-activity-workflows.mmd` | AD01–AD08 | Different set of eight diagrams (authentication, product, import, utility, mining, recommendation, review, expiry push) |
| `07a/07b/07c-*-sequence.mmd` | R2-08.02, R2-08.03, R2-08.05 | Different participants (layers vs. the named classes of the final pages) |
| `08-screen-flow.mmd` | R2-03 — Screen Flow | Same screens and paths; the legacy file adds explanatory notes and an `F03` label prefix |
| `09-f05-scheduler-erd-v14-v19.mmd` | Mining & Scheduling (ERD file) | Subset of the final schema (7 tables) |

## Editable UML 2.0 pack (27 pages) — current

[`PASPM_Unified_27_Diagrams_UML2_Editable.drawio`](PASPM_Unified_27_Diagrams_UML2_Editable.drawio) is the maintained, editable source (open with diagrams.net). It covers requirements, context, use cases, eight activity diagrams, screen flow, architecture, six state machines and eight sequence diagrams (F07–F11 pages are marked PLANNED). It was audited page by page against the Backend on `dev` @ `11973a0`: see the [27-page audit](../reports/UML27_DIAGRAM_QA_2026-11-02.md) for per-page results, the pages that are informal rather than UML, and the known gaps. Edit the `.drawio` file; never replace it with exported images.

## Legacy Mermaid pack (Review 2) — not synchronised after PR #11

The `.mmd` files and `rendered/*.svg` below predate the F05 administration work. They are kept unchanged as history and are **stale in places**: `02-use-cases.mmd` lets Store roles create mining requests (System Admin owns it now), `03-system-architecture.mmd` says Flyway V1–V11 (now V1–V17), `04-logical-erd-v1-v11.mmd` has no V12–V17 tables, `05c`/`05d` lack the lease-reclaim transitions. Use the Draw.io pack above for F05 behaviour.

## Existing editable models

| Model | Status | Scope |
|---|---|---|
| [F03-named editable model](PASPM_Complete_ERD_F03_Three_Weights_v4.drawio) | NEEDS_REVIEW | Actual pages include final weights v1.2/F05/F06 V10 despite filename; V11 archive/routing is missing |
| [Original F02 v3 model](PASPM_Complete_ERD_F02_Inventory_Batch_v3.drawio) | SUPERSEDED for later schema | Original iteration reference, retained unchanged |

The [second F02 copy](../features/F02/reference_v3/PASPM_Complete_ERD_F02_Inventory_Batch_v3.drawio) has identical bytes and separate original-backlog provenance; it is intentionally preserved. No diagram contents or hashes are changed.

## PASPM Review 2 diagram pack

Mermaid source diagrams are derived from backend code and migrations in this repository and the sibling frontend checkout `D:\Capstone\FE-FA26`. Render these `.mmd` files with Mermaid-compatible tooling and insert SVG/PNG exports into Report 3 or Report 4.

| File | Purpose |
| --- | --- |
| `01-system-context.mmd` | System boundary and external actors/dependencies |
| `02-use-cases.mmd` | FE/BE use cases, with prototype-only functions labeled |
| `03-system-architecture.mmd` | Runtime components and main data paths |
| `04-logical-erd-v1-v11.mmd` | Logical schema represented by migrations V1–V11, including the historical matrix archive and routing registry |
| `09-f05-scheduler-erd-v14-v19.mmd` | Focused F05 scheduler schema after V14–V19 (current; not part of the stale Review 2 pack). Reference: [F05_SCHEDULER_SCHEMA_V14_V19.md](../architecture/F05_SCHEDULER_SCHEMA_V14_V19.md) |
| `05a-import-batch-state.mmd` – `05f-staging-row-state.mmd` | Persisted entity state machines |
| `06-activity-workflows.mmd` | Eight principal backend workflows |
| `07a-import-sequence.mmd` – `07c-recommendation-sequence.mmd` | Import, mining, and recommendation sequences |
| `08-screen-flow.mmd` | Frontend screen routes and principal navigation paths |

### Source and accuracy notes

- The current repository has Flyway migrations V1–V11. V11 is a Java migration at `src/main/java/db/migration/V11__historical_utility_matrix_storage.java`; it installs/validates the `utility_matrix_item_legacy_v5` archive and `utility_snapshot_storage` registry. The ERD includes these alongside F04 transaction, F05 mining, and F06 recommendation tables. Archived legacy utility data is not treated as valid F04 transaction provenance.
- State names are copied from database constraints and implementation. Recommendation states stop at `APPROVED` or `REJECTED`: F06 stores reviewable business candidates and does not implement campaign publication or storefront activation. Campaign state is omitted because no campaign entity or F08 implementation is present here.
- The utility snapshot schema permits `READY` or `FAILED`; the current create service persists `READY` only after successful validation and returns an error before creating a record when validation fails. A snapshot is captured with frozen policy/input values; there is no persisted `DRAFT` or `BUILDING` state.
- F04 import records `PROCESSING`, `COMPLETED`, `COMPLETED_WITH_ERRORS`, or `FAILED`; staging rows separately use `PENDING`, `VALID`, `REJECTED`, and `PROCESSED`. CSV import is idempotent by `(store_id, source_system, source_order_id)` and does not write inventory batches.
- Utility base is `(unit_price - unit_cost) * quantity` unless a source utility is supplied. Enabled stock, expiry, and seasonal scores contribute according to the frozen weighting configuration. E is derived from sellable inventory batch expiry metadata; S and M are product priority scores. F05 consumes frozen weighted matrix values.
- Expiry Push is a recommendation-generation path based on live expiry eligibility; it is not a published campaign. Transaction-backed candidates use frozen mining evidence and live eligibility checks.
- Screen-flow routes are sourced from the sibling frontend checkout `D:\Capstone\FE-FA26\src\utils\navigationRouter.ts` and screen components. The FE source labels only S01, S02, S05, S06, and S07. Other screen IDs in a putative S01–S18 inventory are not assigned here because the FE source does not identify them.
- `forgot-password` is rendered by the FE but is absent from its explicit `SCREEN_TO_PATH` map; the app falls back to `/forgot-password`, which is not added to the reverse path map.
- FE routes and rendered prototype screens do not imply BE integration. Authentication, account management, F02 catalog/inventory, and F03 utility use `/api`. Mining UI actions, Merchandiser recommendations/campaign/simulation pages, and customer storefront purchase flow currently use local/demo state without matching API calls. F04 CSV import has a backend endpoint but no matching CSV import screen in the FE route map.
- A role mismatch is present in FE source: `SCREEN_OWNER_MAP` assigns recommendation/campaign routes to Merchandiser, but both `ROLE_PERMISSIONS` and post-login `MERCHANDISER` mapping grant only F03 routes, and the Merchandiser sidebar is disabled in `AppLayout`. In BE, mining and recommendation APIs allow Store Admin/Merchandiser, while the FE mining console is grouped under System Admin. Resolve this before presenting end-to-end authorization as verified.
- Context and role use cases are inferred from the backend API/authorization and FE route/screen sources. The repository tree contains no Report 3/Report 4 files, so exported diagram insertion into those reports remains outstanding.

### Update process

When the database schema or workflow changes, update the corresponding Mermaid source in the same change. Do not hand-edit exported images as the source of truth.

Physical schema authority: [Flyway SQL](../../src/main/resources/db/migration), [Java V11](../../src/main/java/db/migration/V11__historical_utility_matrix_storage.java), [legacy compatibility](../architecture/LEGACY_UTILITY_MATRIX_COMPATIBILITY.md). [Path map](../DOCUMENTATION_MAP.md) resolves old `ERD_Diagram` locations; old names/recorded manifests are not evidence of a newer physical model.

# Diagrams and Database Models

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

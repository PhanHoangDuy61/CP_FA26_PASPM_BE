# API Reference

Canonical contract locations stay stable in this directory. `CURRENT` describes present documentation scope, not successful execution of every endpoint; `NEEDS_REVIEW` flags unresolved prose. Source/controllers and the checked-in export remain separately traceable.

| Feature | Contract | Status / caveat | Handoff |
|---|---|---|---|
| F01 | [Authentication/User](F01_AUTH_USER_API.md) | Local session update: csrfToken, refresh/logout; 202/409 reconciled; uncommitted | [F01](../frontend_handoff/F01_HANDOFF.md) |
| F01 | [Store management](F01_STORE_MANAGEMENT_API.md) | 2026-11-23 (DEC-20261123-01, V27): Store administration, own Store profile, icon/banner, public image GET, INACTIVE guard; branch `Khoi/feat/store-management` | [F01](../frontend_handoff/F01_HANDOFF.md#store-management-2026-11-23) |
| F02 | [Product/Inventory](F02_PRODUCT_INVENTORY_API.md) | CURRENT scoped contract + batch `pushDay`; product `expiryPushDays` **removed 2026-11-16** (400 if sent; V26); old v3 role/expiry clauses differ | [F02](../frontend_handoff/F02_HANDOFF.md) |
| F03 | [Utility](F03_UTILITY_API.md) | CURRENT: two routes deleted 2026-11-16 (expiry-window, score PATCH); P2–P6 resolved. Weight v2 since 2026-11-15: Weight v2 section first; v1.2 section kept as legacy for pre-V25 data. Requirements are derived, pending formal approval ([F03 W2 requirements](../requirements/F03_WEIGHT_V2_REQUIREMENTS.md)) | [F03](../frontend_handoff/F03_HANDOFF.md) |
| F04 | [Transaction](F04_TRANSACTION_API.md) | CURRENT CSV P0 contract (sync and async) + XLS/XLSX upload under the approved Excel contract of 2026-10-26; broader workflow acceptance separate | [F04](../frontend_handoff/F04_HANDOFF.md) |
| F05 | [Mining](F05_MINING_API.md) | Current EFIM numeric contract includes lossless per-TU/per-Product-TWU/minUtil int32 checks and an int64 global total; this change is local/unpublished | [F05](../frontend_handoff/F05_HANDOFF.md) |
| F06 | [Recommendation](F06_RECOMMENDATION_API.md) | Current Backend v1.1 contract + W2; frozen evidence reader validates exact int64 mining totals; historical test statements retain dates | [F06](../frontend_handoff/F06_HANDOFF.md) |
| F07/F08 | [Layout and Campaign](F07_F08_LAYOUT_CAMPAIGN_API.md) | Merged to dev at `8f94f94` (V23); `priorityCoverage` still null (priority = W > 1 not wired) | [F07/F08](../frontend_handoff/F07_F08_HANDOFF.md) |
| F09 | [Public Storefront](F09_STOREFRONT_API.md) | MVP public projection; ACTIVE Stores only, by `store.slug`; active homepage needs configured layout caps | [F09](../frontend_handoff/F09_HANDOFF.md) |

[OpenAPI export](openapi.json) (historical counts: 96 paths / 114 operations at the F07/F08 merge, 98 / 116 at F05 Checkpoint 1; current counts below). The export's server entry is the local test origin; configure the target origin in the integration environment. The session changes are uncommitted. Earlier operation counts are historical evidence, not the current export. Role prose still needs source-based interpretation; use the [RBAC matrix](F01_F02_RBAC_MATRIX.md). [RBAC matrix](F01_F02_RBAC_MATRIX.md) and [integration notes](F03_V12_F05_F06_INTEGRATION.md) have review notices. [API changelog](API_CHANGELOG.md) remains append-only history.

## 2026-11-25 F09 Storefront merge export

Branch `Khoi/merge/f09-storefront-be` (F09 branch `Duy/Feature/F09_Storefront` + `dev` `82b0ced`): **111 paths / 138 operations** (+4 paths, +4 operations: the anonymous F09 GETs `/api/storefront/stores`, `/{storeSlug}/home`, `/{storeSlug}/products`, `/{storeSlug}/products/{productId}`).
- Added schemas: `PublicStore`, `StoreSummary`, `ProductCard`, `ProductCategory`, `StorefrontProductPage`, `CatalogCategory`, `HomePlacement`, `HomeZone`, `HomeView`. Changed (additive): `ProductRequest` and `ProductView` gain `imageUrl` and `description`.
- The branch's `PUT /api/system/stores/{storeId}/storefront-visibility` is **not** part of the merge (DEC-20261125-01).
- The runtime `/v3/api-docs` was taken through `-Dpaspm.test.export-openapi=true` on `TransactionExcelImportIntegrationTest`, and only these F09 paths and schema changes were inserted; the rest of the file is byte-identical to `dev`. Pre-existing runtime-vs-file differences (`PlacementView.sku` description, `/api/transactions/imports/csv/async`) were left as they are.

## 2026-11-24 F02 expiry / push-day / product-delete export

Branch `Khoi/feat/f02-expiry-pushday-product-delete` (base `dev` `3d51f5b`): **107 paths / 134 operations** (+1 operation: `DELETE /api/products/{id}`; no new path).
- Changed schemas (all additive): `BatchView` (`effectiveStatus`, `daysToExpiry`, `warnings`), `InventorySummary` (`batchesMissingPushDay`), `SeasonProduct` (`deleted`). Changed descriptions: the batch routes, `PATCH /api/products/{id}/status` and the inventory summary.
- The runtime `/v3/api-docs` was taken through `-Dpaspm.test.export-openapi=true` on `TransactionExcelImportIntegrationTest`, and only these changes were merged into the checked-in file. The runtime export still differs from the file, before this change, in `servers`, one CSV async parameter list and the `PlacementView.sku` description; those were left alone.

## 2026-11-23 Store management export

Branch `Khoi/feat/store-management` (base `dev` `6d2a877`): **107 paths / 133 operations**.
- That is the 102 / 123 file on `dev` plus 5 Store paths and 10 operations ([F01_STORE_MANAGEMENT_API.md](F01_STORE_MANAGEMENT_API.md)), and the changed picker `GET /api/system/stores`.
- The runtime `/v3/api-docs` was taken through `-Dpaspm.test.export-openapi=true` on `TransactionExcelImportIntegrationTest`, and only the Store paths and schemas were inserted. The rest of the file is byte-identical.

## API Inventory provenance

`API_INVENTORY_BY_FEATURE.md`, `.csv` and `API_FEATURE_GAP_REPORT.md` are absent from current dev. They exist only in local branch `Khoi/docs/backend-api-inventory-audit` at `39a4918`; no remote publication or merge is assumed. This reorganization does not cherry-pick/copy them. They are not presented as live links until a separately reviewed integration supplies them.

F10–F11 contracts are not defined here. F07/F08 routes are documented in the linked contract. The documented unavailable F03 `GET /api/utility/snapshots` is not an implemented route; F05 `GET /api/mining/snapshots` is a distinct selector. Framework Swagger/OpenAPI paths are not business APIs.

Current F01 reconciliation (2026-11-12): preserves published dev V20/AuthSessionService and adds V21 binding; [current evidence](../testing/reports/F01_SESSION_RECONCILIATION_2026-11-12.md). Earlier local-variant details are dated history; no source database repair/deployment was performed.

2026-11-15 feature branch, Checkpoint 1 (historical): 98 paths / 116 operations with the two F05 progress routes and Merchandiser Store submission.

**Branch `Khoi/feat/f05-progress-f03-weight-v2` @ `c8c837a` (historical): 102 paths, 124 operations** (F01 27, F02 15, F03 20, F04 8, F05 26, F06 8, F07/F08 20), regenerated through the MockMvc export hook of `RecommendationIntegrationTest`; adds the 8 F03 season operations. Every one of the 124 operations is named in a contract in this folder; the only documented route absent from the export is `GET /api/utility/snapshots`, listed as NOT IMPLEMENTED. This is not an interactive Swagger UI execution.

## 2026-11-20 F09 feature-branch export (historical)

The unmerged branch `Duy/Feature/F09_Storefront` exported 105 paths / 126 operations on its older base, including five F09 operations (four public GETs and the SYSTEM_ADMIN visibility PUT, removed in the merge below).

## 2026-11-16 export update
The follow-up export has **103 paths / 125 operations**, adding DELETE `/api/inventory-batches/{id}/push-day` to the 102/124 baseline. Recommendation views include `seasonEnded` and `endedSeasons`; all local schema references resolve.

**Branch `Khoi/feat/f03-remove-legacy-product-fields` (2026-11-16, not merged): 100 paths, 121 operations** (F01 27, F02 16, F03 18, F04 8, F05 24, F06 8, F07/F08 20). Before: 101 paths / 123 operations in the export committed on `dev` `2a944c5`. Removed: `PUT /api/utility/config/expiry-window`, `PATCH /api/utility/products/{productId}/scores`. Regenerated through the MockMvc export hook of `RecommendationIntegrationTest` (`-Dpaspm.f06.export-dir`); this is not an interactive Swagger UI execution. Besides the two removals, the regenerated file renumbers two springdoc `operationId`s and picks up the current EFIM-only summary of `POST /api/mining/requests`.

## 2026-11-17 F05 numeric contract update

The current local export remains **100 paths / 121 operations**. No route was added or removed. `RequestView.totalScaledUtility` is now `integer/int64`; transaction utility, Product TWU and converted positive `minUtil` remain int32-bounded for EFIM. The export matches the captured runtime OpenAPI document. See [DEC-20261117-01](../decisions/2026-11-17/DEC-20261117-01-efim-aware-lossless-int32-guard.md) and [Record 015](../record-of-changes/2026-11-17/15-f05-efim-aware-int32-guard.md).

## 2026-11-19 F04 Excel export update

Branch `Khoi/feat/f04-excel-import-v26` (on `dev` `530651b`, PR to `dev`): **101 paths / 122 operations** — the 100 / 121 export plus `POST /api/transactions/imports/excel`. The path was taken from the runtime `/v3/api-docs` export (`-Dpaspm.test.export-openapi=true` on `TransactionExcelImportIntegrationTest`) and inserted into the checked-in file; a semantic comparison shows no other path, schema or server change.

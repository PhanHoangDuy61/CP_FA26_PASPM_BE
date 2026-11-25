# DEC-20261124-03 — Product soft delete (F02)

| Field | Value |
|---|---|
| Date | 2026-11-24 (Asia/Ho_Chi_Minh) |
| Source | Product Owner requirement R3 and the PO answers at Checkpoint 1 of the F02 expiry / push-day / product-delete task (2026-11-24) |
| Status | OWNER_DECISION, implemented on branch `Khoi/feat/f02-expiry-pushday-product-delete`; PR to `dev` pending |
| Feature | F02 (product), with effects on F03 seasons, F04 import, F06, F07/F08 layouts |
| Related | F02 backlog F02-06 ("no hard delete of Product or Batch; keep historical transaction references"), DEC-20261116-02, DEC-20261124-01 |

## Context
There was no product delete: only `PATCH /api/products/{id}/status` (ACTIVE / INACTIVE). A hard delete is impossible, because eight tables reference `product` with RESTRICT foreign keys: `inventory_batch`, `transaction_item`, `import_staging_row`, `utility_matrix_item`, `pattern_item`, `recommendation`, `layout_placement` and `product_season`.

## Decision

### 1. Endpoint
`DELETE /api/products/{id}` → **204**. Only a **STORE_ADMIN of the owning Store** may call it. Order of checks: 401, 403 (MERCHANDISER, SYSTEM_ADMIN, END_CUSTOMER), 404 (unknown, **another Store's**, or already deleted product), 409, then delete. There is no restore endpoint and no restore UI.

### 2. Refusals: 409 with `code` and `reason`
Body: `{code, error, reason, sellableQuantity, campaigns:[{campaignId, status}]}`; both `sellableQuantity` and `campaigns` are always present.
| `code` | When |
|---|---|
| `PRODUCT_HAS_SELLABLE_STOCK` | stored ACTIVE batches with `remaining > 0` that are **not expired** hold units (`sellableQuantity` > 0). A batch expiring today counts (DEC-20261124-01). Expired, BLOCKED and empty batches do not block. |
| `PRODUCT_IN_LIVE_CAMPAIGN` | the product has a placement in **layout A or B** of a campaign whose status is **PUBLISHED or ACTIVE**. DRAFT and ENDED campaigns do not block. |
If both apply the code is `PRODUCT_HAS_SELLABLE_STOCK` and `campaigns` is filled in the same body.

### 3. What a delete does
In one transaction, after locking the product row and its batches: set `deleted_at`, `deleted_by` (the acting user) and **`status = 'INACTIVE'`**. Nothing else is touched. Migration **V28** adds the two nullable columns (a CHECK keeps them paired, `deleted_by` is an FK to `app_user`) and an index; no existing row changes.

### 4. Effect on other data
| Area | Effect |
|---|---|
| Catalogue reads (`GET /api/products`, `GET /api/products/{id}`, PUT, PATCH status, batch routes, inventory summary, similar-name check) | Deleted products are **excluded in the WHERE shared by the `COUNT` and the `LIMIT/OFFSET` query, i.e. before pagination**; by id they are 404. `PATCH status` cannot re-activate one. |
| Batches and stock | Kept in the database, unreachable through the API. |
| F03 snapshots, matrices, F05 mining results, F06 recommendations, frozen JSON | Unchanged. History joins (`UtilityProductRepository.product`, layout placements, transactions) keep resolving the row. |
| F03 / F06 / F07 new work | The product is INACTIVE, so it is already not sellable, `INELIGIBLE` for recommendations and not `readyForConsumption`. |
| F04 import | The SKU still resolves (history). **Each imported row of a deleted product gets a warning** in `import_staging_row.error_details`: `[{"rowNumber":n,"field":"sku","message":"PRODUCT_DELETED: …"}]`, listed by `GET /api/transactions/imports/{id}/errors` with `status: "PROCESSED"`. The batch status stays `COMPLETED`; there is no new column. |
| F03 seasons | Existing assignments are kept. `GET /api/utility/seasons/{id}/products` returns `deleted: true` for the product. A new assignment (including an idempotent re-assign) of a deleted product is **409 `PRODUCT_DELETED`**; unassigning still works. |
| F07/F08 layouts, campaigns | DRAFT/ENDED campaign layouts keep their placements and still show the deleted product's name and SKU. PUBLISHED/ACTIVE ones block the delete. |
| F09 storefront, F10 bundle cart | Not coded yet (PLANNED). When built they must exclude `deleted_at IS NOT NULL` products and keep historical cart/order lines. |
| SKU | **Never reused.** The SKU is generated from the product ID and unique per Store, and the row stays. |

## Known limitations
- **Concurrency is covered by row locks only.** The delete locks the product row and its batches (`SELECT … FOR UPDATE`) before it checks stock and campaigns, so a concurrent receive (which locks the product row) waits. A concurrent batch `PATCH`, a campaign publish or a layout write is **not** serialised against the delete by a dedicated guard, and **no concurrent test exists**. A rare race can therefore leave a product that was deleted a moment after a batch was edited or a campaign was published. Accepted by the PO at Checkpoint 2 (2026-11-24); not tested now.
- The `PRODUCT_DELETED` code for a season assignment of a deleted product is HTTP 409, accepted by the PO at Checkpoint 2.

## Consequences
- **API (additive):** `DELETE /api/products/{id}`; `SeasonProduct.deleted`; season assign `409 PRODUCT_DELETED`; an import warning on deleted-product rows.
- **Migration:** V28 `V28__f02_product_soft_delete.sql`.
- Tests: `ProductSoftDeleteIntegrationTest` (role, cross-Store 404, stock, live campaign, pagination, similar names, SKU, seasons), `TransactionImportIntegrationTest` (import warning), `ProductSoftDeleteV28MigrationTest` and `ProductSoftDeleteV28MySqlTest`.

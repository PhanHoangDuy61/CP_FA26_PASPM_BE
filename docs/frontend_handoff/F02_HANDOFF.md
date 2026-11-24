# F02 Product and Inventory Frontend Handoff

## Backend update — 2026-11-24 (expiry boundary, push-day warning, product delete) — read this first

Rules: [DEC-20261124-01](../decisions/2026-11-24/DEC-20261124-01-batch-expiry-boundary-effective-status.md), [DEC-20261124-02](../decisions/2026-11-24/DEC-20261124-02-push-day-missing-warning.md), [DEC-20261124-03](../decisions/2026-11-24/DEC-20261124-03-product-soft-delete.md). Exact DTOs: [F02 API](../api/F02_PRODUCT_INVENTORY_API.md). **All response changes are additive; no request field changed.** This repository contains backend code only.

### 1. Stop comparing dates in the frontend
A batch is expired only when today (`Asia/Ho_Chi_Minh`) is **after** its expiry date, so it is still active **on** the expiry date. The backend computes everything; remove `isExpired`, `isExpiringSoon` and the date part of `batchPushState` from `features/store/f02.forms.ts` (they used `expiryDate <= today`) and read these read-only fields of every `BatchView` (list, get, create, PATCH, both DELETE-clear routes):

| Field | Type | Meaning |
|---|---|---|
| `status` | `"ACTIVE" \| "BLOCKED"` | the **stored** status; the only one PATCH accepts. Sending `EXPIRED` or `INACTIVE` → 400 |
| `effectiveStatus` | `"ACTIVE" \| "BLOCKED" \| "EXPIRED"` | what to display. EXPIRED when today is after `expiryDate` (wins over BLOCKED), else the stored status |
| `daysToExpiry` | `number \| null` | `expiryDate − today` in days: **0 on the expiry date**, negative when expired, `null` without an expiry date |
| `warnings` | `"MISSING_PUSH_DAY"[]` | always present, `[]` when nothing to warn about |

- **Expiring soon** = `effectiveStatus !== "EXPIRED"` and `daysToExpiry !== null` and `0 <= daysToExpiry <= 7`.
- An expired batch can still be edited (`expiryDate`, `remainingQuantity`, `BLOCKED`). Moving `expiryDate` into the future un-expires it; nothing is stored, so refresh from the response.
- **Summary** (`GET /api/products/{id}/inventory-summary?expiringWithinDays=7`): buckets no longer overlap. `expiredQuantity` = expired batches of any stored status (a BLOCKED expired batch is here, not in `blockedQuantity`); `blockedQuantity` = BLOCKED and not expired; `sellableQuantity` = ACTIVE and not expired (a batch expiring today is sellable); `expiringSoonQuantity` = ACTIVE with `0 <= daysToExpiry <= expiringWithinDays`. New field `batchesMissingPushDay`.

### 2. Push day: no default, warn instead
The backend never filled a default push day (no "expiry − 7 days"), and the batch form must not either. Leave the push day empty if the user does not enter it and save normally. Show a non-blocking warning when `warnings` contains `MISSING_PUSH_DAY`: the product requires expiry, `pushDay` is empty and the batch is not expired. This replaces the local `batchPushState === 'missing'` rule; it applies whatever the stored status or remaining quantity is. `batchesMissingPushDay` in the summary gives the count for a product banner. (F03 uses narrower conditions for its snapshot; the F02 warning is only a prompt to enter a push day.)

### 3. Delete a product — `DELETE /api/products/{id}`
Store Admin only (hide the control for Merchandiser). Send Bearer, no body. There is **no restore**: do not build a restore or "deleted products" screen.

| Status | Body | UI |
|---|---|---|
| 204 | none | remove the row, refetch the list (totals already exclude it) |
| 401 / 403 | `{error}` | session / role |
| 404 | `{error}` | already deleted, unknown, or another Store's product; refresh the list |
| 409 | `{code:"PRODUCT_HAS_SELLABLE_STOCK", error, reason, sellableQuantity, campaigns:[{campaignId,status}]}` | explain with `reason`; `sellableQuantity` > 0; list `campaigns` if any |
| 409 | `{code:"PRODUCT_IN_LIVE_CAMPAIGN", error, reason, sellableQuantity:0, campaigns:[{campaignId,status:"PUBLISHED"\|"ACTIVE"}]}` | tell the user to end the campaign first |

- Sellable stock = ACTIVE, remaining > 0, not expired (a batch expiring today counts). Expired, BLOCKED and empty batches do not block.
- Live campaign = the product is placed in layout A or B of a PUBLISHED or ACTIVE campaign. If both apply you get `PRODUCT_HAS_SELLABLE_STOCK` with `campaigns` filled.
- A deleted product disappears from `GET /api/products` before pagination (`totalElements`, `totalPages` and pages ignore it) and every product/batch route answers 404. Its SKU is never reused.
- Seasons: the season product list marks it `deleted: true`; a new assignment is `409 PRODUCT_DELETED`; removing the assignment still works. F04: rows of the deleted product are still imported and listed by `GET …/imports/{id}/errors` as `PROCESSED` rows with a `PRODUCT_DELETED` message (a warning).

```ts
export type BatchStatus = 'ACTIVE' | 'BLOCKED';                         // stored, writable
export type BatchEffectiveStatus = 'ACTIVE' | 'BLOCKED' | 'EXPIRED';   // computed, read-only
export interface InventoryBatch { /* …existing… */ effectiveStatus: BatchEffectiveStatus; daysToExpiry: number | null; warnings: 'MISSING_PUSH_DAY'[] }
export interface InventorySummary { /* …existing… */ batchesMissingPushDay: number }
export interface DeleteProductConflict {
  code: 'PRODUCT_HAS_SELLABLE_STOCK' | 'PRODUCT_IN_LIVE_CAMPAIGN'; error: string; reason: string;
  sellableQuantity: number; campaigns: { campaignId: number; status: 'PUBLISHED' | 'ACTIVE' }[];
}
```

---

## Backend update — 2026-11-20

This repository contains backend code only; no frontend UI was changed. Product and Batch IDs are generated by the database. Show IDs read-only on detail screens; never include them in create requests or expose editable ID controls on create/edit forms. Use the ID from the 201 response for detail navigation.

Product list sorting supports `name`, `sku`, `category`, `unit`, `costPrice`, `sellingPrice`, `status`, `requiresExpiry`, and `stockQuantity`. Keep the selected column/direction in query state; each header should toggle `asc`/`desc` and indicate the current direction. Pagination ties are stable by product ID ascending.

On the product form, present **Giá nhập (`costPrice`) before Giá bán (`sellingPrice`)**. Show `requiresExpiry` explicitly as “Có hạn sử dụng” / “Không yêu cầu hạn sử dụng” in forms, lists and detail, with text labels (not color alone). When `requiresExpiry=true`, require the batch expiry date; do not silently omit or generate it.

SKU is generated by the backend when a Product is created, using `SKU-` plus the database Product ID padded to 11 digits (for example, ID 42 produces `SKU-00000000042`). Show it read-only after creation. Omit `sku` from POST and PUT bodies; attempting to send it returns 400. Existing Product SKUs are preserved, and PUT cannot change them. Similar product names are warnings, not unique constraints. If POST/PUT returns `409` with `code: "PRODUCT_NAME_CONFIRMATION_REQUIRED"`, show a confirmation prompt and retry the same request with `"confirmSimilarName": true`; the rejected attempt has not written data. Similarity currently means normalized exact match or a one-character edit for names whose normalized length is at least four.

Batch create no longer accepts `batchCode`; it is generated in the backend and returned in `BatchView.batchCode` as `BATCH-YYYYMMDD-NNN`. The date comes from `receivedAt` in the configured Store time zone; NNN increases for each Product across dates and is padded to at least three digits. Keep `batchCode` and batch ID read-only in details. Remove `batchCode` from POST payloads; older clients that send it receive 400. Routes and response fields are unchanged.

Batch PATCH may update `expiryDate`. Omitted or JSON `null` means **keep the existing date**; use `DELETE /api/inventory-batches/{id}/expiry-date` to clear it. Do not offer clearing when `requiresExpiry=true`. `batchCode`, `receivedAt`, and `initialQuantity` are immutable; edits to original receipt facts require a separate audited workflow. Batch APIs remain `STORE_ADMIN` only.

> **2026-11-16 — product form:** remove the `expiryPushDays` input. The field is no longer accepted (sending it → **400**) or returned, and V26 drops the column ([breaking change 15](F03_W2_BREAKING_CHANGES.md), [DEC-20261116-02](../decisions/2026-11-16/DEC-20261116-02-p5-remove-legacy-product-fields.md)). The push day lives on the **batch form**: optional `pushDay` on create and PATCH (Store Admin only, ≤ expiryDate), cleared with `DELETE /api/inventory-batches/{id}/push-day`.
>
> *2026-11-15 (history):* inventory batches accept an optional `pushDay`; product `expiryPushDays` was deprecated and ignored by scoring.

## Legacy v1.2 Product expiry priority update (2026-10-19; removed 2026-11-16)

**Legacy (v1.2, deprecated 2026-11-15, removed 2026-11-16):** the existing Product POST/PUT request accepts optional `expiryPushDays: integer|null`; absent/null is stored as0, values must be >=0, and fractional/string/boolean values are rejected400. Full PUT must send the desired threshold (omitting resets it to0). Product GET/list/create/update/status responses include `expiryPushDays: integer`. Store Admin owns this field in their Store; Merchandiser and System Admin cannot modify it through F02, and cross-Store records return404. No duplicate configuration endpoint was added. `requiresExpiry=false` remains NOT_APPLICABLE in F03 even if a threshold is configured. Inventory quantities are unchanged by threshold updates.

F03 uses only this Product threshold: no Store fallback. The legacy Store expiry-window endpoint is deprecated. New E is binary from the earliest-expiring ACTIVE positive-quantity batch with expiry strictly after the analysis date. *(Superseded 2026-11-24: a batch expiring today IS sellable.)* `GET /api/utility/expiry-push-batches` provides a Store Admin/Merchandiser current push view independently of mining; see the F03 contract.


For React + TypeScript. Exact DTOs and validation are in [`../api/F02_PRODUCT_INVENTORY_API.md`](../api/F02_PRODUCT_INVENTORY_API.md); synthetic payloads are in [`F02_examples/`](F02_examples). Full runtime contract: [`../api/openapi.json`](../api/openapi.json).

## Base URL and authentication

Set `VITE_PASPM_API_BASE_URL` to the backend origin (default local `http://localhost:8080`; do not bake credentials into it). GET `/api/products`, `/api/products/{id}`, `/api/categories` and `/api/categories/{id}` permit a `STORE_ADMIN` or `MERCHANDISER` bearer token. All other F02 calls remain `STORE_ADMIN` only; Merchandiser must not see Product/Category/inventory mutation controls. SystemAdmin/EndCustomer have no F02 management access. The server derives Store from the account; never send `storeId` to select scope. Foreign/absent resources return 404. Swagger is `/swagger`; OpenAPI is `/v3/api-docs`.

## Screen mapping and integration order

Recommended sequence, adapted to actual routes:

1. Fetch `GET /api/categories` for category selectors.
2. Fetch `GET /api/products` with search/category/status/sorting and pagination.
3. Load `GET /api/products/{id}` and `GET /api/products/{id}/inventory-summary?expiringWithinDays=7` for detail, stock and expiry indicators.
4. Create/update product with POST/PUT `/api/products`; status uses PATCH `/api/products/{id}/status`.
5. Fetch batches from `GET /api/products/{id}/batches`.
6. Add batches with POST to that path; edit expiry date, remaining quantity/status/push day with PATCH `/api/inventory-batches/{id}`; detail GET by batch ID. Clear an optional expiry date with DELETE `/api/inventory-batches/{id}/expiry-date`.
7. Display derived summary and refresh after any batch edit. No separate stock-adjustment or movement-history API exists.

| Screen | Endpoint(s) |
|---|---|
| Category Management | GET list/detail, POST create, PUT update, PATCH status `/api/categories...` |
| Product List | GET `/api/products` |
| Product Details | GET `/api/products/{id}` plus batches and summary endpoints |
| Product Creation / Editing | POST `/api/products`, PUT `/api/products/{id}` |
| Product activation | PATCH `/api/products/{id}/status` |
| Inventory Batch List | GET `/api/products/{id}/batches` |
| Add Inventory Batch | POST `/api/products/{id}/batches` |
| Edit Batch | GET/PATCH `/api/inventory-batches/{id}` |
| Inventory Summary | GET `/api/products/{id}/inventory-summary` |
| Delete Product | `DELETE /api/products/{id}` (2026-11-24, Store Admin, soft delete, no restore) |
| Delete Category, stock adjustment/movement history | Not implemented; optional batch expiry and push day can be cleared with their DELETE routes |

## Data types and form validation

```ts
export type CatalogStatus = 'ACTIVE' | 'INACTIVE';
export type BatchStatus = 'ACTIVE' | 'BLOCKED';
export interface Category { id: number; name: string; status: CatalogStatus }
export interface CategoryRequest { name: string; status: CatalogStatus }
export interface ProductRequest {
  name: string; categoryId?: number | null; unit: string;
  requiresExpiry: boolean; sellingPrice: number; costPrice: number; status: CatalogStatus;
  confirmSimilarName?: boolean; // true only after confirming the 409 warning
  imageUrl?: string | null;     // F09 display, absolute http(s) URL (V29)
  description?: string | null;  // F09 display, max 2000 (V29)
}
export interface Product {
  readonly id: number; readonly sku: string; name: string; unit: string; requiresExpiry: boolean;
  category: Category | null; sellingPrice: number; costPrice: number; status: CatalogStatus; stockQuantity: number;
  imageUrl: string | null; description: string | null;
}
export interface ProductPage { content: Product[]; page: number; pageSize: number; totalElements: number; totalPages: number }
export interface InventoryBatchCreateRequest {
  receivedAt: string; expiryDate?: string | null; initialQuantity: number;
  pushDay?: string | null;
}
export interface InventoryBatchPatchRequest {
  remainingQuantity?: number; status?: BatchStatus; pushDay?: string | null; expiryDate?: string | null;
}
export interface InventoryBatch {
  readonly id: number; readonly productId: number; readonly batchCode: string;
  receivedAt: string; expiryDate: string | null; initialQuantity: number;
  remainingQuantity: number; status: BatchStatus; pushDay: string | null;
  createdAt: string; updatedAt: string;
}
export interface InventorySummary {
  productId: number; totalQuantity: number; sellableQuantity: number;
  expiredQuantity: number; blockedQuantity: number; expiringSoonQuantity: number;
}
export interface ApiError { error: string; code?: string; confirmWith?: string }
```

Category name is required/nonblank/max150; category status required and active/inactive. Product SKU is generated by the backend and read-only; omit it from create/update payloads. Product name max180, unit max40; prices required and nonnegative; `requiresExpiry` required; status required active/inactive; categoryId may be omitted/null. A category must belong to the current Store. There is no category-name uniqueness rule in the current schema. `confirmSimilarName` is optional and should only be true on a retry after the user confirms the similar-name warning.

Do not submit batchCode; it is generated from receivedAt's Store-local date and the next Product-scoped sequence, then returned read-only. receivedAt must be an ISO date-time; initialQuantity must be positive integer. If Product requires expiry, expiryDate is required. A batch starts ACTIVE with remainingQuantity=initialQuantity. Patch accepts expiryDate, remainingQuantity (integer 0..initialQuantity), status ACTIVE/BLOCKED and/or pushDay; at least one non-null field is required. Null/omitted `expiryDate` or `pushDay` is unchanged. Use DELETE `/api/inventory-batches/{id}/expiry-date` to clear expiry only for products that do not require it; use DELETE `/api/inventory-batches/{id}/push-day` to clear pushDay. `batchCode`, `receivedAt` and `initialQuantity` are immutable through PATCH.

`stockQuantity` on Product is read-only derived total remaining stock across batches. Never put it in Product request payloads. Place cost price before selling price in the form while preserving both API field names and values. Show expiry requirement using text in the product form, list and detail. `stock_priority_score` and `seasonal_priority_score` belong to F03 endpoints, not F02 forms. F02 owns `requiresExpiry` and batch expiry dates.

## Inventory display rules

The summary's total includes every remaining unit, including blocked and expired batches. Blocked counts BLOCKED batches that are not expired. Expired counts batches with an expiry date before today, whatever their stored status. Sellable counts ACTIVE batches that have no expiry or expire today or later (2026-11-24: expiry today is sellable). Expiring soon counts ACTIVE batches expiring from today (inclusive) through the inclusive lookahead date; default lookahead is 7 days. These buckets overlap only in that expiringSoon is a subset of sellable; blocked and expired are disjoint. No batches yield zeros. The current query does not filter by Product status, so show an inactive Product state separately. Time zone defaults to `Asia/Ho_Chi_Minh` and is application-configured.

## Local sample data

The dev seeder creates demo Stores A/B, categories/products and batches, including expiring, expired, blocked, and non-expiring examples; IDs may differ. Do not rely on fixed IDs. It creates development credentials in source; obtain them from the team's approved local setup and never copy them into Frontend code or these docs. Safer synthetic requests/responses are in `F02_examples/`.

Product `category` may be null even when its generated OpenAPI schema is not nullable. Batch `expiryDate` and `pushDay` are nullable; model them as nullable in TypeScript.

## UI states and errors

Use loading state for every list/detail request; empty arrays mean empty list, and `ProductPage.totalElements=0` means no matches. Save 201/200 should display returned resource and use its ID. Handle 400 with the `error` message/field form feedback, 401 by clearing session and signing in again, 403 as insufficient role, 404 as missing or another Store's resource, 409 as a similar-name confirmation warning, network errors as connectivity issue, and 5xx as server failure. A `409` with code `PRODUCT_NAME_CONFIRMATION_REQUIRED` is a non-unique name warning: ask for confirmation, then retry with `confirmSimilarName:true`. Handled error JSON is `{error:string}` except that name confirmation also includes `code` and `confirmWith`; framework auth responses may not have a body. Refresh batch/product/summary reads after successful mutation.

There is no CORS setup in the Backend source. Use same-origin local proxy or deployment CORS configuration when Frontend and API have different origins.

## 2026-11-16 — Optional clear-push-day action
New non-breaking route: `DELETE /api/inventory-batches/{id}/push-day` (Store Admin). Send Bearer, no body; 200 returns `InventoryBatch` with nullable `pushDay`. Update local batch state from the response, then refresh scores/preview. Already-null succeeds. 404 covers unknown and foreign Store records; 403 wrong role. PATCH null/omitted keeps the old push day. Add `pushDay?: string | null` to create/PATCH types and nullable `pushDay` to the response type.

## 2026-11-25 — F09 display metadata (additive)

Product create/update may include `imageUrl` (absolute HTTP(S); upload not supported; anything else is 400) and `description` (≤ 2,000 characters). Both are optional. Because PUT replaces the whole product, an edit form must send back the current `imageUrl` and `description` (from the last `Product` read) or they are cleared. For public rendering use the [F09 storefront handoff](F09_HANDOFF.md), never F02 `Product`.

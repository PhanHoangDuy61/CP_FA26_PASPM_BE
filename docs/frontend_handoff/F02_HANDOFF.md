# F02 Product and Inventory Frontend Handoff

> **2026-11-16 — product form:** remove the `expiryPushDays` input. The field is no longer accepted (sending it → **400**) or returned, and V26 drops the column ([breaking change 15](F03_W2_BREAKING_CHANGES.md), [DEC-20261116-02](../decisions/2026-11-16/DEC-20261116-02-p5-remove-legacy-product-fields.md)). The push day lives on the **batch form**: optional `pushDay` on create and PATCH (Store Admin only, ≤ expiryDate), cleared with `DELETE /api/inventory-batches/{id}/push-day`.
>
> *2026-11-15 (history):* inventory batches accept an optional `pushDay`; product `expiryPushDays` was deprecated and ignored by scoring.
>
> **2026-11-20 — display metadata:** Product create/update may include `imageUrl` (absolute HTTP(S), upload not supported) and `description` (≤2,000 characters). These are optional; F02 remains authenticated and continues returning its administrative cost/stock fields. For public rendering use the separate [F09 storefront handoff](F09_HANDOFF.md), never F02 `Product`.

## Historical v1.2 Product expiry priority (superseded 2026-11-16)

The former Product `expiryPushDays` field and the related score-column fields were removed by V26. F02 rejects `expiryPushDays` as an unknown request field; it is no longer returned. F03 Weight v2 uses batch `pushDay`; see the current [F02 API contract](../api/F02_PRODUCT_INVENTORY_API.md) and [F03 API contract](../api/F03_UTILITY_API.md). The historical threshold behavior below no longer applies.


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
6. Add batches with POST to that path; edit remaining quantity/status with PATCH `/api/inventory-batches/{id}`; detail GET by batch id.
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
| Delete Category/Product/Batch, stock adjustment history | Not implemented |

## Data types and form validation

```ts
export type CatalogStatus = 'ACTIVE' | 'INACTIVE';
export type BatchStatus = 'ACTIVE' | 'BLOCKED';
export interface Category { id: number; name: string; status: CatalogStatus }
export interface CategoryRequest { name: string; status: CatalogStatus }
export interface ProductRequest {
  sku: string; name: string; categoryId?: number | null; unit: string;
  requiresExpiry: boolean; sellingPrice: number; costPrice: number; status: CatalogStatus;
  imageUrl?: string | null; description?: string | null;
}
export interface Product {
  id: number; sku: string; name: string; unit: string; requiresExpiry: boolean;
  category: Category | null; sellingPrice: number; costPrice: number; status: CatalogStatus; stockQuantity: number;
  imageUrl: string | null; description: string | null;
}
export interface ProductPage { content: Product[]; page: number; pageSize: number; totalElements: number; totalPages: number }
export interface InventoryBatchCreateRequest {
  batchCode: string; receivedAt: string; expiryDate?: string | null; initialQuantity: number;
}
export interface InventoryBatchPatchRequest { remainingQuantity?: number; status?: BatchStatus }
export interface InventoryBatch extends InventoryBatchCreateRequest {
  id: number; productId: number; remainingQuantity: number; status: BatchStatus;
  createdAt: string; updatedAt: string;
}
export interface InventorySummary {
  productId: number; totalQuantity: number; sellableQuantity: number;
  expiredQuantity: number; blockedQuantity: number; expiringSoonQuantity: number;
}
export interface ApiError { error: string }
```

Category name is required/nonblank/max150; category status required and active/inactive. Product SKU required/max80, name max180, unit max40; prices required and nonnegative; `requiresExpiry` required; status required active/inactive; categoryId may be omitted/null. SKU is unique inside a Store. A category must belong to the current Store. Duplicate SKU is a database conflict (409). There is no category-name uniqueness rule in the current schema.

Batch code required/max80 and unique per Product; receivedAt must be an ISO date-time; initialQuantity must be positive integer. If Product requires expiry, expiryDate is required. A batch starts ACTIVE with remainingQuantity=initialQuantity. Patch accepts remainingQuantity (integer 0..initialQuantity) and/or status ACTIVE/BLOCKED; at least one non-null field is required. `expiryDate` and `initialQuantity` cannot be edited through PATCH.

`stockQuantity` on Product is read-only derived total remaining stock across batches. Never put it in Product request payloads. `stock_priority_score` and `seasonal_priority_score` belong to F03 endpoints, not F02 forms. F02 owns `requiresExpiry` and batch expiry dates.

## Inventory display rules

The summary's total includes every remaining unit, including blocked and expired batches. Blocked counts BLOCKED batches. Expired counts ACTIVE batches with expiry on or before today's date. Sellable counts ACTIVE batches that have no expiry or expire strictly after today. Expiry today is excluded under v1.2. Expiring soon counts ACTIVE batches expiring strictly after today through the inclusive lookahead date; default lookahead is 7 days. These buckets overlap only in that expiringSoon is a subset of sellable; blocked and expired are disjoint. No batches yield zeros. The current query does not filter by Product status, so show an inactive Product state separately. Time zone defaults to `Asia/Ho_Chi_Minh` and is application-configured.

## Local sample data

The dev seeder creates demo Stores A/B, categories/products and batches, including expiring, expired, blocked, and non-expiring examples; IDs may differ. Do not rely on fixed IDs. It creates development credentials in source; obtain them from the team's approved local setup and never copy them into Frontend code or these docs. Safer synthetic requests/responses are in `F02_examples/`.

OpenAPI currently does not mark nullable response members as nullable: Product `category` and Batch `expiryDate` may be null. Model them as nullable in TypeScript as shown by the source/database lifecycle.

## UI states and errors

Use loading state for every list/detail request; empty arrays mean empty list, and `ProductPage.totalElements=0` means no matches. Save 201/200 should display returned resource and use its ID. Handle 400 with the `error` message/field form feedback, 401 by clearing session and signing in again, 403 as insufficient role, 404 as missing or another Store's resource, 409 as duplicate/constraint conflict, network errors as connectivity issue, and 5xx as server failure. Handled error JSON is `{error:string}`; framework auth responses may not have a body. Refresh batch/product/summary reads after successful mutation.

There is no CORS setup in the Backend source. Use same-origin local proxy or deployment CORS configuration when Frontend and API have different origins.

## 2026-11-16 — Optional clear-push-day action
New non-breaking route: `DELETE /api/inventory-batches/{id}/push-day` (Store Admin). Send Bearer, no body; 200 returns `InventoryBatch` with nullable `pushDay`. Update local batch state from the response, then refresh scores/preview. Already-null succeeds. 404 covers unknown and foreign Store records; 403 wrong role. PATCH null/omitted keeps the old push day. Add `pushDay?: string | null` to create/PATCH types and nullable `pushDay` to the response type.

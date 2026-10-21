# F02 Product and Inventory API Contract

## v1.2 Product expiry priority update (2026-10-19)

The existing Product POST/PUT request accepts optional `expiryPushDays: integer|null`; absent/null is stored as0, values must be >=0, and fractional/string/boolean values are rejected400. Full PUT must send the desired threshold (omitting resets it to0). Product GET/list/create/update/status responses include `expiryPushDays: integer`. Store Admin owns this field in their Store; Merchandiser and System Admin cannot modify it through F02, and cross-Store records return404. No duplicate configuration endpoint was added. `requiresExpiry=false` remains NOT_APPLICABLE in F03 even if a threshold is configured. Inventory quantities are unchanged by threshold updates.

F03 uses only this Product threshold: no Store fallback. The legacy Store expiry-window endpoint is deprecated. New E is binary from the earliest-expiring ACTIVE positive-quantity batch with expiry strictly after the analysis date. Batch expiring today is not sellable in the current summary. `GET /api/utility/expiry-push-batches` provides a Store Admin/Merchandiser current push view independently of mining; see the F03 contract.


Contract checked against the running OpenAPI export, F02 ERD and current Backend source on 2026-10-17. Each endpoint below is PASS for source/spec contract correspondence; this handoff did not individually invoke all endpoints in a live Swagger UI session. Full shared OpenAPI: [`openapi.json`](openapi.json). Every route requires a bearer JWT. From2026-10-21, GET Product list/detail and GET Category list/detail permit `STORE_ADMIN` or `MERCHANDISER`; all other F02 operations remain `STORE_ADMIN` only. Resources are scoped to the authenticated Store; request bodies cannot choose a Store. Cross-Store product/category/batch access is returned as 404. F02 does not expose delete or stock-movement APIs.

The approved ERD is `docs/ERD_Diagram/PASPM_Complete_ERD_F02_Inventory_Batch_v3.drawio` (capitalized directory in this checkout). The schema uses `INVENTORY_BATCH`, not `PRODUCT_LOT`. Product stock is derived from batch remaining quantities.

## DTOs

- `CategoryRequest`: required nonblank `name:string` max150 and `status:string` in `ACTIVE|INACTIVE`.
- `CategoryView`: `{id:int32,name:string,status:string}`.
- `ProductRequest`: required nonblank `sku:string` max80, `name:string` max180, `unit:string` max40, `requiresExpiry:boolean`, `sellingPrice:number >= 0`, `costPrice:number >= 0`, `status:ACTIVE|INACTIVE`; nullable/optional `categoryId:int32` and `expiryPushDays:int32 >=0` (null/omitted→0, strict integer). No stock or F03 score fields are accepted.
- `ProductView`: `{id:int32,sku:string,name:string,unit:string,requiresExpiry:boolean,category:CategoryView|null,sellingPrice:number,costPrice:number,status:string,stockQuantity:int64,expiryPushDays:int32}`. `stockQuantity` is derived from all batch remaining quantities and read-only.
- `ProductPage`: `{content:ProductView[],page:int32,pageSize:int32,totalElements:int64,totalPages:int32}`.
- `BatchCreateRequest`: required `batchCode:string` nonblank max80, `receivedAt:string` ISO date-time, `initialQuantity:int32 > 0`; nullable/optional `expiryDate:string` ISO date. Expiry required if Product `requiresExpiry=true`.
- `BatchPatchRequest`: optional `remainingQuantity:int32 >= 0`, optional `status:ACTIVE|BLOCKED`; at least one must be supplied. Remaining may not exceed initial quantity.
- `BatchView`: `{id:int32,productId:int32,batchCode:string,receivedAt:date-time,expiryDate:date|null,initialQuantity:int32,remainingQuantity:int32,status:string,createdAt:date-time,updatedAt:date-time}`. New batches start `remainingQuantity=initialQuantity`, `status=ACTIVE`.
- `InventorySummary`: `{productId:int32,totalQuantity:int64,sellableQuantity:int64,expiredQuantity:int64,blockedQuantity:int64,expiringSoonQuantity:int64}`.

## Endpoint reference

| Method and path | Purpose / parameters | Request → response | Success and key errors |
|---|---|---|---|
| `GET /api/categories` | list Store categories; raw `CategoryView[]` | none → array | 200, 401, 403 |
| `GET /api/categories/{id}` | detail; path id int32 | none → `CategoryView` | 200, 404 foreign/absent, 401, 403 |
| `POST /api/categories` | create | `CategoryRequest` → `CategoryView` | 201, 400 invalid, 401, 403 |
| `PUT /api/categories/{id}` | replace name/status fields | `CategoryRequest` → `CategoryView` | 200, 400 invalid status/name, 404, 401, 403 |
| `PATCH /api/categories/{id}/status` | activate/deactivate | `{status:"ACTIVE"|"INACTIVE"}` → `CategoryView` | 200, 400 invalid, 404, 401, 403 |
| `GET /api/products` | search/filter/sort/page; see query list below | none → `ProductPage` | 200, 400 invalid filter/sort/page, 404 foreign category, 401, 403 |
| `GET /api/products/{id}` | product detail and derived stock | none → `ProductView` | 200, 404 foreign/absent, 401, 403 |
| `POST /api/products` | create; stock remains batch-derived | `ProductRequest` → `ProductView` | 201, 400 invalid, 404 category outside Store, 409 duplicate Store SKU, 401, 403 |
| `PUT /api/products/{id}` | update all Product fields; no stock mutation | `ProductRequest` → `ProductView` | 200, 400 invalid or enabling expiry while an existing batch has no expiry, 404 Product/category, 409 duplicate Store SKU, 401, 403 |
| `PATCH /api/products/{id}/status` | activate/deactivate Product | `{status:"ACTIVE"|"INACTIVE"}` → `ProductView` | 200, 400 invalid, 404, 401, 403 |
| `GET /api/products/{id}/batches` | list batches, newest received first | none → raw `BatchView[]` | 200, 404 product foreign/absent, 401, 403 |
| `POST /api/products/{id}/batches` | receive batch | `BatchCreateRequest` → `BatchView` | 201, 400 validation/missing required expiry, 404 product foreign/absent, 409 duplicate product+batchCode, 401, 403 |
| `GET /api/inventory-batches/{id}` | batch detail | none → `BatchView` | 200, 404 foreign/absent, 401, 403 |
| `PATCH /api/inventory-batches/{id}` | change remaining quantity and/or block state | `BatchPatchRequest` → `BatchView` | 200, 400 empty patch/negative/over-initial quantity/invalid status, 404 foreign/absent, 401, 403 |
| `GET /api/products/{id}/inventory-summary?expiringWithinDays=7` | derived inventory totals; lookahead defaults7, minimum0 | none → `InventorySummary` | 200, 400 negative/too-large lookahead, 404 Product foreign/absent, 401, 403 |

Product list query parameters: `search` default empty; `categoryId` optional int32; `status` default empty (`ACTIVE|INACTIVE` when nonempty); `page` default0, >=0; `pageSize` default20, 1..100; `sortBy` default `name`, supported `name|sku|sellingPrice|status`; `sortDirection` default `asc`, supported `asc|desc`. Search matches name or SKU without case sensitivity. Invalid sort gives 400. Page is zero-based. Unlike category and batch lists, product list returns pagination metadata in `ProductPage`.

SKU is unique per Store (database constraint); same SKU may exist in another Store. SKU, category name, unit and batch code are trimmed before storage. Category names have no uniqueness constraint in the current migration. A Product category is checked for Store ownership, but the service does not require that Category status is ACTIVE. Product status and category status are `ACTIVE|INACTIVE`; batch status is `ACTIVE|BLOCKED`. Category update is a full request replacement; Product PUT also requires the full `ProductRequest` body.

## Inventory rules

- `totalQuantity` sums every batch's remaining quantity, including blocked and expired stock. No batch yields zero.
- `blockedQuantity` sums batches whose status is `BLOCKED`.
- `expiredQuantity` sums ACTIVE batches with non-null expiry date on or before the Store-local current date.
- `sellableQuantity` sums ACTIVE batches with no expiry date or expiry date strictly after today. Expiry today is excluded under v1.2.
- `expiringSoonQuantity` sums ACTIVE batches with expiry date strictly after today through today + `expiringWithinDays`, upper boundary inclusive. It is part of sellable quantity and is not an additional disjoint bucket.
- Blocked and expired quantities do not overlap: expired calculation only considers ACTIVE batches.
- Time zone defaults to `Asia/Ho_Chi_Minh` (`paspm.store-time-zone`), configured at application level in current source, not per-Store.
- Summary SQL does not check Product status, so a deactivated Product's batches can still be counted as sellable by this summary. The frontend should display Product status separately.
- Batch quantity constraints: initial >0; remaining >=0 and <=initial. Receive sets remaining equal to initial. Patch cannot change initial quantity. There is no adjustment history/stock-movement endpoint.

## F02 / F03 ownership

F02 Product create/update owns `expiryPushDays`, `requiresExpiry`, SKU, category, unit, prices, and Product status. F02 batch APIs own batch expiry and quantities. `stockQuantity` is derived and cannot be edited. The F03 API owns `stock_priority_score` and `seasonal_priority_score` through `/api/utility/products/{productId}/scores`; ProductRequest does not accept these fields. F03 derives expiry priority using inventory batches and a fixed analysis date. Do not add F03 score fields to F02 Product forms/contracts.

## Request and response examples

Synthetic JSON examples are available under [`../frontend_handoff/F02_examples/`](../frontend_handoff/F02_examples/). Example Product response:

```json
{"id":21,"sku":"MILK-01","name":"Fresh Milk","unit":"bottle","requiresExpiry":true,"category":{"id":4,"name":"Dairy","status":"ACTIVE"},"sellingPrice":2.5,"costPrice":1.25,"status":"ACTIVE","stockQuantity":12,"expiryPushDays":7}
```

Example batch response:

```json
{"id":31,"productId":21,"batchCode":"B-001","receivedAt":"2026-10-17T02:00:00Z","expiryDate":"2026-10-24","initialQuantity":12,"remainingQuantity":12,"status":"ACTIVE","createdAt":"2026-10-17T02:00:00Z","updatedAt":"2026-10-17T02:00:00Z"}
```

Handled validation response:

```json
{"error":"Invalid request fields"}
```

A more specific service validation may return e.g. `{"error":"Remaining quantity cannot exceed initial quantity"}`. Missing/foreign Store resources are reported as 404.

## Verification status and ERD differences

All 15 F02 method/path operations appear in the runtime OpenAPI document. `CatalogIntegrationTest` exercises authentication/role denial, category/product CRUD/status, Store isolation, batch CRUD, required expiry, and quantity/status patch. The document is source/spec verified; not every route was separately exercised through a live browser during this handoff. See `F02_SWAGGER_TEST_REPORT.md` for the prior actual F02 Swagger run.

OpenAPI does not encode nullable response fields: `ProductView.category` can be null when no category is assigned and `BatchView.expiryDate` can be null for a non-expiring Product, although its schema shows nonnullable object/date types. Treat these as nullable in TypeScript. The ERD labels the account table `USER`; the implemented schema uses `app_user` to avoid reserved-word issues. The ERD uses `CATEGORY` / `PRODUCT` / `INVENTORY_BATCH`, and migrations V1/V3 implement these entities, including Store-scoped unique SKU, composite Store/category ownership, and unique `(product_id,batch_code)`. No stored Product stock column exists. F03 migration V4 adds score columns separately; this is consistent with field ownership in the F03 utility API.

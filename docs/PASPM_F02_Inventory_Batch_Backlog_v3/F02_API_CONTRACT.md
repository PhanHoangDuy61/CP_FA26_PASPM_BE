# F02 API Contract

All routes require a bearer JWT and `STORE_ADMIN` role. The Store ID is derived from the authenticated F01 account. Requests do not accept a Store ID. A resource outside the caller's Store returns `404` to avoid exposing its existence; a valid token with a role other than `STORE_ADMIN` returns `403`.

Errors use the existing format `{ "error": "message" }`.

## Categories

| Method and path | Request | Success |
|---|---|---|
| `GET /api/categories` | — | `200`: `CategoryView[]` for the caller's Store |
| `GET /api/categories/{id}` | — | `200`: `CategoryView` |
| `POST /api/categories` | `CategoryRequest` | `201`: created `CategoryView` |
| `PUT /api/categories/{id}` | `CategoryRequest` | `200`: updated `CategoryView` |
| `PATCH /api/categories/{id}/status` | `StatusRequest` | `200`: updated `CategoryView` |

`CategoryRequest`: `name` (required, 1–150 characters), `status` (`ACTIVE` or `INACTIVE`). Categories are deactivated rather than deleted when referenced by products.

## Products

| Method and path | Request/query | Success |
|---|---|---|
| `GET /api/products` | `search`, `categoryId`, `status`, `page` (default 0), `pageSize` (default 20, maximum 100), `sortBy` (`name`, `sku`, `sellingPrice`, `status`), `sortDirection` (`asc`/`desc`) | `200`: `ProductPage` |
| `GET /api/products/{id}` | — | `200`: `ProductView` |
| `POST /api/products` | `ProductRequest` | `201`: created `ProductView` |
| `PUT /api/products/{id}` | `ProductRequest` | `200`: updated `ProductView` |
| `PATCH /api/products/{id}/status` | `StatusRequest` | `200`: updated `ProductView` |
| `GET /api/products/{id}/inventory-summary` | `expiringWithinDays` (default 7, nonnegative) | `200`: `InventorySummary` |

`ProductRequest`: `sku` (required, max 80), `name` (required, max 180), optional `categoryId`, `unit` (required, max 40), `requiresExpiry` (required boolean), `sellingPrice` and `costPrice` (required, nonnegative decimals), `status` (`ACTIVE` or `INACTIVE`). It contains no writable stock or Store field.

`ProductView` returns `id`, `sku`, `name`, `unit`, `requiresExpiry`, optional `category`, `sellingPrice`, `costPrice`, `status`, and `stockQuantity`. `stockQuantity` is the sum of batch `remainingQuantity` and is read-only.

`ProductPage` returns `content`, `page`, `pageSize`, `totalElements`, and `totalPages`. Search matches name or SKU without case sensitivity. An invalid sort field/direction or pagination value returns `400`.

Changing `requiresExpiry` to true is rejected with `400` if any existing batch has a null expiry date. Category IDs must belong to the caller's Store. Duplicate SKU in a Store returns `409`.

## Inventory batches

| Method and path | Request | Success |
|---|---|---|
| `GET /api/products/{id}/batches` | — | `200`: `BatchView[]` |
| `POST /api/products/{id}/batches` | `BatchCreateRequest` | `201`: created `BatchView` |
| `GET /api/inventory-batches/{id}` | — | `200`: `BatchView` |
| `PATCH /api/inventory-batches/{id}` | `BatchPatchRequest` | `200`: updated `BatchView` |

`BatchCreateRequest`: `batchCode` (required, max 80), `receivedAt` (required ISO-8601 instant), optional `expiryDate` (ISO date), `initialQuantity` (required positive integer). `remainingQuantity` starts equal to `initialQuantity`; status starts as `ACTIVE`. A product with `requiresExpiry=true` requires an expiry date. Duplicate batch code for a product returns `409`.

`BatchPatchRequest` must contain `remainingQuantity` (nonnegative and no greater than initial quantity), `status` (`ACTIVE` or `BLOCKED`), or both. Updates lock the batch row in a transaction before validating and writing. Inventory history/stock movements are not created.

`BatchView` returns `id`, `productId`, `batchCode`, `receivedAt`, optional `expiryDate`, `initialQuantity`, `remainingQuantity`, `status`, `createdAt`, and `updatedAt`.

## Inventory summary

`InventorySummary` returns `productId`, `totalQuantity`, `sellableQuantity`, `expiredQuantity`, `blockedQuantity`, and `expiringSoonQuantity`.

- `totalQuantity` sums remaining units from all batches.
- `blockedQuantity` sums all `BLOCKED` batches, regardless of expiry.
- `expiredQuantity` sums `ACTIVE` batches whose expiry date is before the Store's current local date.
- `sellableQuantity` sums `ACTIVE` batches with no expiry date or an expiry date on/after that local date.
- `expiringSoonQuantity` sums sellable batches with expiry dates from today through today + `expiringWithinDays`, inclusive.
- These categories do not overlap. A product with no batches returns zero quantities.
- The local date uses `paspm.store-time-zone`, defaulting to the approved MVP timezone `Asia/Ho_Chi_Minh`. Expiry is inclusive through the batch's expiry date; a batch becomes expired on the following local calendar date.

## Status codes

`400` invalid fields, status, quantity, date range, or pagination; `401` missing/invalid authentication; `403` authenticated role is not allowed; `404` resource is missing or belongs to another Store; `409` duplicate key or database constraint conflict.

# F04 Transaction Data API

This contract describes the F04 P0 implementation in the Spring Boot backend. The runtime OpenAPI document is [`openapi.json`](openapi.json), generated from `/v3/api-docs` after Flyway V7 started successfully.

## Authentication and Store scope

Send `Authorization: Bearer <access-token>` for every endpoint below. The Store is taken from the authenticated F01 identity; request parameters and CSV fields cannot choose a Store.

| Role | Import CSV | Read imports/errors | Read transactions |
|---|---|---|---|
| `STORE_ADMIN` | Yes | Yes | Yes |
| `MERCHANDISER` | No | Yes | Yes |
| Other roles / anonymous | No | No | No |

An authenticated user outside a record's Store receives `404`; an authenticated user without the endpoint role receives `403`; missing/invalid tokens receive `401`.

## Endpoints

### `POST /api/transactions/imports/csv`

Uploads one CSV file as multipart field `file`. Role: `STORE_ADMIN`. Success: `201 Created` with `ImportSummary`.

CSV must be UTF-8 and use this exact header order:

```csv
source_order_id,occurred_at,sku,quantity,unit_price,unit_cost,status
```

| Field | Type | Validation |
|---|---|---|
| `source_order_id` | string | Required; max 150 characters. Idempotency key is Store + `CSV_UPLOAD` + order ID. |
| `occurred_at` | ISO-8601 date-time string | Required with an explicit UTC offset, e.g. `2026-10-19T09:00:00+07:00`; stored as an instant. All lines in an order must match. |
| `sku` | string | Required; max 80 characters; must resolve to a Product in the authenticated Store. |
| `quantity` | positive integer | Must be at least 1. Repeated SKU lines aggregate only when unit price, cost and status match. |
| `unit_price` | decimal | Required, nonnegative, at most 2 fraction digits and within the DECIMAL(12,2) range. |
| `unit_cost` | decimal | Same numeric rules as `unit_price`. |
| `status` | string enum | `COMPLETED`, `CANCELLED`, `REFUNDED`, or `PARTIAL_REFUND`; all lines in an order must match. |

Default limits are 5 MiB per file and 10,000 nonblank data rows. Operators can override `paspm.transaction.import.max-file-bytes` and `paspm.transaction.import.max-rows`. A valid order is persisted atomically. Same order ID + same normalized content is a duplicate; the same ID with different content is a conflict and historical data is retained. Only `COMPLETED` orders with every unit price at least its unit cost are eligible for F03. Other valid statuses and below-cost sales remain historical transaction data. Imports never change inventory batches.

Example response:

```json
{
  "importBatchId": 12,
  "status": "COMPLETED_WITH_ERRORS",
  "filename": "transactions.csv",
  "startedAt": "2026-10-19T02:00:00Z",
  "finishedAt": "2026-10-19T02:00:01Z",
  "totalCount": 4,
  "validCount": 3,
  "rejectedCount": 1,
  "totalOrderCount": 2,
  "acceptedOrderCount": 1,
  "rejectedOrderCount": 1,
  "duplicateOrderCount": 0,
  "conflictOrderCount": 0,
  "errorSummary": null
}
```

`totalCount`, `validCount` and `rejectedCount` count CSV rows. The separately named `*OrderCount` fields count grouped orders. Import status is `PROCESSING`, `COMPLETED`, `COMPLETED_WITH_ERRORS`, or `FAILED`.

### `GET /api/transactions/imports`

Lists import batches for the authenticated Store. Roles: `STORE_ADMIN`, `MERCHANDISER`. Query fields: `page` (integer, default 0, minimum 0) and `pageSize` (integer, default 20, range 1–100). Returns `Page<ImportSummary>`.

### `GET /api/transactions/imports/{id}`

Returns one Store-owned import batch as `ImportSummary`. Roles: `STORE_ADMIN`, `MERCHANDISER`. A missing or other-Store ID returns `404`.

### `GET /api/transactions/imports/{id}/errors`

Lists rejected/conflicted staging rows for one Store-owned batch. Roles: `STORE_ADMIN`, `MERCHANDISER`. Query fields: `page` (default 0) and `pageSize` (default 100; range 1–100). Returns `Page<ImportError>`.

`ImportError` fields: `rowNumber` (integer), `sourceOrderId` (nullable string), `status` (string), `errorDetails` (nullable string containing serialized validation details).

### `GET /api/transactions`

Lists transactions for the authenticated Store. Roles: `STORE_ADMIN`, `MERCHANDISER`. Optional query fields: `status` (`COMPLETED`, `CANCELLED`, `REFUNDED`, `PARTIAL_REFUND`), `from` and `to` (ISO-8601 instants, inclusive), `page` (default 0), `pageSize` (default 20, range 1–100). Returns `Page<TransactionView>`.

### `GET /api/transactions/{id}`

Returns one Store-owned transaction with its historical lines as `TransactionView`; roles: `STORE_ADMIN`, `MERCHANDISER`. A missing or other-Store ID returns `404`.

`TransactionView` fields: `transactionId` (integer), `sourceSystem` (string), `sourceOrderId` (string), `occurredAt` (ISO-8601 UTC instant), `status` (string), `f03Eligible` (boolean), `totalAmount` (decimal), `items` (array of `TransactionLine`). Each line has `transactionItemId`, `productId`, `sku`, `productName`, `quantity`, `unitPrice`, and `unitCost`.

Example transaction detail:

```json
{
  "transactionId": 31,
  "sourceSystem": "CSV_UPLOAD",
  "sourceOrderId": "demo-2026-01",
  "occurredAt": "2026-10-19T02:00:00Z",
  "status": "COMPLETED",
  "f03Eligible": true,
  "totalAmount": 155000.00,
  "items": [
    {"transactionItemId": 44, "productId": 5, "sku": "MILK", "productName": "Milk", "quantity": 2, "unitPrice": 45000.00, "unitCost": 28000.00},
    {"transactionItemId": 45, "productId": 8, "sku": "COFFEE", "productName": "Coffee", "quantity": 1, "unitPrice": 65000.00, "unitCost": 33000.00}
  ]
}
```

## HTTP status and error examples

- `200`: successful query.
- `201`: CSV import request processed; individual invalid orders are reflected in counters/errors rather than failing the entire file.
- `400`: missing/empty file, non-CSV filename/media type, invalid UTF-8/CSV/header/columns, invalid filter or pagination. Example: `{"error":"CSV header must match the approved F04 template"}`.
- `401`: missing or invalid bearer token.
- `403`: authenticated role cannot perform the operation.
- `404`: unknown import/transaction or a record owned by another Store.
- `409`: uniqueness/content conflict where raised by the API.
- `413`: file exceeds configured size limit.

Validation errors for parsed orders are returned through the import batch error endpoint, not as one HTTP failure for the whole CSV. The runtime OpenAPI JSON declares the shared error responses and request/response schemas.

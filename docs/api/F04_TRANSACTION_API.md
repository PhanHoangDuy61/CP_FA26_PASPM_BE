# F04 Transaction Data API

This contract describes the current F04 file-import implementation in the Spring Boot backend. F04 requires file-based CSV/Excel imports; external transaction APIs, POS integration and webhooks are outside the approved scope. CSV is available; the Excel format contract is approved; endpoint implementation is delivered on a separate stacked branch. The runtime OpenAPI document is [`openapi.json`](openapi.json).

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

Uploads one CSV file as multipart field `file`. Role: `STORE_ADMIN`. Success: `201 Created` with `ImportSummary`. This existing endpoint remains synchronous and retains its prior contract and processing behavior.

### `POST /api/transactions/imports/csv/async`

Uploads one CSV file as multipart field `file` for durable background processing. Role: `STORE_ADMIN`. Send a required `Idempotency-Key` header (1–128 ASCII letters/digits or `. _ : -`; case-insensitive). Success: `202 Accepted` with an `ImportProgress` snapshot. The request validates the complete CSV structure and durably stages its parsed rows before returning; business validation and order persistence run in a background worker.

```http
POST /api/transactions/imports/csv/async
Idempotency-Key: 2fbf38dc-8dcc-4a14-8ce3-66dbe92f7125
Content-Type: multipart/form-data
```

Example accepted response:

```json
{
  "importBatchId": 18,
  "status": "QUEUED",
  "stage": "QUEUED",
  "processedCount": 0,
  "totalCount": 4,
  "progressPercent": 0,
  "createdAt": "2026-11-12T06:25:00Z",
  "startedAt": null,
  "updatedAt": "2026-11-12T06:25:00Z",
  "completedAt": null,
  "statistics": {
    "validCount": 0,
    "rejectedCount": 0,
    "totalOrderCount": 0,
    "acceptedOrderCount": 0,
    "rejectedOrderCount": 0,
    "duplicateOrderCount": 0,
    "conflictOrderCount": 0
  },
  "errorSummary": null
}
```

The same key and identical file bytes return the original batch/current progress; reusing the key with different bytes returns `409`. Use a new key for a deliberate new import. A new key with identical contents creates a new batch; existing Store + CSV source order ID + normalized content rules count matching orders as duplicates and conflicting content as conflicts without overwriting history. A structurally invalid CSV is retained as a `FAILED` batch with an error and no fabricated total or percentage. File extension/content-type/size/request errors remain request-level `400`/`413`.

### `GET /api/transactions/imports/{id}/progress`

Returns an `ImportProgress` for a batch owned by the authenticated Store. Roles: `STORE_ADMIN`, `MERCHANDISER`. A missing or other-Store ID returns `404`.

```json
{
  "importBatchId": 18,
  "status": "PROCESSING",
  "stage": "IMPORTING",
  "processedCount": 2,
  "totalCount": 4,
  "progressPercent": 50,
  "createdAt": "2026-11-12T06:25:00Z",
  "startedAt": "2026-11-12T06:25:01Z",
  "updatedAt": "2026-11-12T06:25:02Z",
  "completedAt": null,
  "statistics": {
    "validCount": 2,
    "rejectedCount": 0,
    "totalOrderCount": 3,
    "acceptedOrderCount": 1,
    "rejectedOrderCount": 0,
    "duplicateOrderCount": 0,
    "conflictOrderCount": 0
  },
  "errorSummary": null
}
```

`ImportProgress` contains `importBatchId`, `status`, `stage`, `processedCount`, nullable `totalCount`/`progressPercent`, `createdAt`, nullable `startedAt`/`completedAt`, `updatedAt`, `statistics` and nullable `errorSummary`. `processedCount` counts rows actually marked processed/rejected in persistent staging. A percentage is returned only when the exact parsed row total is known and nonzero; it is derived from `processedCount / totalCount`, never elapsed time or estimation. Async stages are `QUEUED`, `IMPORTING`, `COMPLETED`, `COMPLETED_WITH_ERRORS`, `FAILED`; historical synchronous batches retain `LEGACY`. Terminal statuses are `COMPLETED`, `COMPLETED_WITH_ERRORS`, `FAILED`.

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

`totalCount`, `validCount` and `rejectedCount` count CSV rows. The separately named `*OrderCount` fields count grouped orders. Synchronous import status remains `PROCESSING`, `COMPLETED`, `COMPLETED_WITH_ERRORS`, or `FAILED`. Async statuses are `QUEUED`, `PROCESSING`, `COMPLETED`, `COMPLETED_WITH_ERRORS`, or `FAILED`.

The lifecycle is `QUEUED → PROCESSING → COMPLETED | COMPLETED_WITH_ERRORS | FAILED`. A DB lease with fencing prevents concurrent workers and a worker interrupted by process/instance loss is reclaimed after lease expiry; staging state and per-order transactions make replay safe. A processing exception is recorded as terminal `FAILED`; submit a new request with a new idempotency key to retry. Existing unique order identity/hash rules remain authoritative.

Async imports do not write uploaded files to an application-owned local temporary directory. Parsed raw rows and progress metadata are durably stored in the database; servlet multipart temporary parts are container-managed and cleaned after the request. Import batches, staging rows, errors and transaction history are retained (no automatic purge). The file is bounded by the configured multipart byte limit and the parser rejects files exceeding the configured nonblank row limit.

Flyway V22 adds async batch metadata, idempotency/lease fields and staging result markers without changing prior migration files. The background import worker starts automatically when the Spring application is ready and Flyway is enabled; no separate process or new dependency is required.

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
- `202`: async CSV accepted (or structurally invalid content durably recorded as a failed batch); inspect/poll the returned batch.
- `400`: missing/empty file, non-CSV filename/media type, invalid metadata/idempotency key, or invalid filter/pagination. On the legacy synchronous route, malformed UTF-8/CSV/header/columns returns 400; on the async route, structurally invalid CSV is recorded as a `FAILED` batch with HTTP 202. Example sync error: `{"error":"CSV header must match the approved F04 template"}`.
- `401`: missing or invalid bearer token.
- `403`: authenticated role cannot perform the operation.
- `404`: unknown import/transaction or a record owned by another Store.
- `409`: idempotency key reused for different bytes or a uniqueness/content conflict where raised by the API.
- `413`: file exceeds configured size limit.

Validation errors for parsed orders are returned through the import batch error endpoint, not as one HTTP failure for the whole CSV. The runtime OpenAPI JSON declares the shared error responses and request/response schemas.

## CSV validation correction — 2026-10-26

The staging order-ID column is a nullable lookup projection. If the normalized ID is blank or exceeds the existing validator limit, that projection is NULL while `raw_data` retains the complete original parsed field values. IDs are never silently truncated. The existing validator uses `String.trim()` and a 150 UTF-16 code-unit limit; this correction does not change normalization or identity rules.

A file containing one valid order and an unrelated 151-unit ID returns 201 with `COMPLETED_WITH_ERRORS`, one accepted order and one rejected order. `ImportError.sourceOrderId` can be NULL for an unrepresentable ID; use its row number and error details. Database raw evidence remains retained; no new raw-data API is introduced. An infrastructure write failure still fails the batch and rolls back the affected order; previously committed independent orders retain their existing semantics.

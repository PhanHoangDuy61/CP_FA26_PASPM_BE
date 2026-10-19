# F04 Transaction Pipeline — Frontend Handoff

This handoff documents the implemented F04 P0 API from the Backend controllers and the live SpringDoc export. No Frontend source has been changed.

## Environment and setup

- Local default base URL: `http://localhost:8080` (the isolated verification run used port `18080`).
- OpenAPI 3.1 document: `GET /v3/api-docs`.
- Swagger UI: `/swagger-ui/index.html`.
- Send the F01 JWT as `Authorization: Bearer <token>`.
- F04 derives Store scope from the token. Do not send a Store ID with imports or transaction queries.
- A Store Admin account and Store Products with matching SKUs must exist before importing the sample CSV.

## Roles

- `STORE_ADMIN`: uploads CSV, reads own Store imports/errors and transactions.
- `MERCHANDISER`: reads own Store imports/errors and transactions; cannot upload CSV.
- Other roles: cannot access F04 endpoints.
- Anonymous or invalid token: `401`; wrong role: `403`; cross-Store detail access: `404`.

## Recommended integration order

1. Obtain a valid F01 access token and identify the user's role and Store from the authenticated account flow.
2. For Store Admin, upload `multipart/form-data` field `file` to `POST /api/transactions/imports/csv`.
3. Display the returned `ImportSummary`; row counts and order counts are distinct measures.
4. Poll/read `GET /api/transactions/imports/{id}` and show validation detail from `GET /api/transactions/imports/{id}/errors` when rejected/conflicted rows exist.
5. Read the Store's transaction list with `GET /api/transactions`; filters include status and inclusive ISO-8601 `from`/`to` instants. Use `page` and `pageSize` for pagination.
6. Open a transaction with `GET /api/transactions/{id}` to show persisted historical Product lines, units, prices and costs.

## Sample data

The synthetic CSV file is [`transactions.csv`](../PASPM_F04_Transaction_Data_Pipeline/examples/transactions.csv). It contains two completed lines grouped into one order and one cancelled order. `MILK` and `COFFEE` are illustrative SKUs; replace them with Product SKUs belonging to the target Store. The fixture date is `2026-10-19T09:00:00+07:00` and is sample-only, not a current-date rule.

The completed order with sale prices at or above costs is marked `f03Eligible=true`. Cancelled/refunded orders or completed orders with any below-cost line are stored as history but are not eligible for F03. F04 does not calculate F03 utility scores.

## Response handling

`ImportSummary` contains `importBatchId`, `status`, `filename`, UTC `startedAt`/`finishedAt`, row counts (`totalCount`, `validCount`, `rejectedCount`), order counts (`totalOrderCount`, `acceptedOrderCount`, `rejectedOrderCount`, `duplicateOrderCount`, `conflictOrderCount`) and nullable `errorSummary`.

`TransactionView` contains transaction identifiers, UTC `occurredAt`, source system/order ID, status, `f03Eligible`, `totalAmount` and `items`. An item includes Product identifiers/name/SKU, quantity and captured unit price/cost. Treat returned historical values as a transaction snapshot; do not recompute them from the current catalog.

Use the shared Backend error format for request-level errors. Parsed CSV validation errors are batch row errors and are retrieved from the batch errors endpoint. Do not interpret an HTTP `201` as every row accepted; inspect counters and errors.

## APIs ready for integration

- `POST /api/transactions/imports/csv`
- `GET /api/transactions/imports`
- `GET /api/transactions/imports/{id}`
- `GET /api/transactions/imports/{id}/errors`
- `GET /api/transactions`
- `GET /api/transactions/{id}`

## Limits and known limitations

- CSV only in this P0 scope; exact header order and UTF-8 are required.
- Default upload limit is 5 MiB and the row limit is 10,000 nonblank rows. Backend values are configurable.
- Import processing is synchronous and bounded; no background job progress endpoint exists.
- API connection records are metadata-only. External POS API synchronization and Excel import are deferred to P1.
- A duplicate order with identical normalized contents is counted as duplicate; a changed payload with the same Store/source/order ID is a conflict and does not overwrite history.
- Import does not mutate inventory and does not implement F03 scoring or F05 mining.
- Swagger UI can display and invoke endpoints, but authenticated end-to-end import requires an active Store Admin credential and matching Product SKUs in the target environment.

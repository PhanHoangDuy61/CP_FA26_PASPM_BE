# F02 Examples

All IDs and data are synthetic; actual IDs depend on the database. Examples use the implemented `INVENTORY_BATCH` contract. The backend generates Product SKU from the database Product ID; Product request examples omit `sku`, while response examples show it. Batch create omits `batchCode` because the backend generates it per Product. Prices are illustrative numeric values; no currency code is part of the DTO. See `docs/api/F02_PRODUCT_INVENTORY_API.md` for precise validation and status rules.

## 2026-11-16 — `expiryPushDays` removed (DEC-20261116-02)

`product_create_request.json` and `product_update_request.json` no longer contain `expiryPushDays`. Sending it now gives 400. Product response examples have been updated to the current schema without the field. Set push days per batch with `pushDay` instead. `product_name_confirmation_warning.json` and `product_name_confirmation_retry_request.json` demonstrate the confirm-and-retry flow for similar names.

## 2026-11-24 — expiry boundary, push-day warning, product delete (DEC-20261124-01..03)

Batch and summary examples now show a product read on 2026-10-17: `effectiveStatus`, `daysToExpiry` (0 on the expiry date, negative when expired), `warnings: ["MISSING_PUSH_DAY"]` (no push day on an expiry-tracked, non-expired batch) and the summary's `batchesMissingPushDay`. `product_delete_conflict_response.json` is the `409` body of `DELETE /api/products/{id}` (`PRODUCT_HAS_SELLABLE_STOCK`; the other code is `PRODUCT_IN_LIVE_CAMPAIGN`). A successful delete returns `204` with no body.

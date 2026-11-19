# F02 Examples

All IDs and data are synthetic; actual IDs depend on the database. Examples use the implemented `INVENTORY_BATCH` contract. SKUs follow `SKU-` plus 11 allowed characters; batch create omits `batchCode` because the backend generates it per Product. Prices are illustrative numeric values; no currency code is part of the DTO. See `docs/api/F02_PRODUCT_INVENTORY_API.md` for precise validation and status rules.

## 2026-11-16 — `expiryPushDays` removed (DEC-20261116-02)

`product_create_request.json` and `product_update_request.json` no longer contain `expiryPushDays`. Sending it now gives 400. Product response examples have been updated to the current schema without the field. Set push days per batch with `pushDay` instead. `product_name_confirmation_warning.json` and `product_name_confirmation_retry_request.json` demonstrate the confirm-and-retry flow for similar names.

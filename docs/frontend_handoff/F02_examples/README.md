# F02 Examples

All IDs and data are synthetic; actual IDs depend on the database. Examples use the implemented `INVENTORY_BATCH` contract. Prices are illustrative numeric values; no currency code is part of the DTO. See `docs/api/F02_PRODUCT_INVENTORY_API.md` for precise validation and status rules.

## 2026-11-16 — `expiryPushDays` removed (DEC-20261116-02)

`product_create_request.json` and `product_update_request.json` no longer contain `expiryPushDays`. Sending it now gives 400. `product_detail_response.json` and `product_list_response.json` are captured responses from before the removal; current responses do not contain the field. Set push days per batch with `pushDay` instead. See [F03_W2_BREAKING_CHANGES.md](../F03_W2_BREAKING_CHANGES.md) row 15.

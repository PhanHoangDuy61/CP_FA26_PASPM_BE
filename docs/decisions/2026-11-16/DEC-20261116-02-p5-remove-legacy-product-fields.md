# DEC-20261116-02 — P5 changed: remove the legacy product scoring fields (V26)

| Field | Value |
|---|---|
| Date | 2026-11-16 (Asia/Ho_Chi_Minh) |
| Source | Owner instruction of 2026-11-16 |
| Status | OWNER_DECISION — implemented on branch `Khoi/feat/f03-remove-legacy-product-fields` (not merged) |
| Supersedes | P5 of 2026-11-15 ("accept `expiryPushDays` but ignore it; deprecated in OpenAPI") |

## Decision
- Remove `expiryPushDays` from every live product request, response, service, repository and the JPA entity.
- Remove `stockPriorityScore` and `seasonalPriorityScore` from every live path where they remained.
- New migration **V26** drops `product.expiry_push_days` (added by V8) and `product.stock_priority_score`, `product.seasonal_priority_score` (added by V4), with their CHECK constraints.
- Frozen JSON (old snapshots, mining requests, recommendation eligibility v1/v2, F06 evidence) is not rewritten; the legacy reader records keep these field names so old payloads still deserialize.
- A client that still sends a removed field gets **400** (`fail-on-unknown-properties` stays enabled).

## Evidence
Listed in [record ROC-20261116-010](../../record-of-changes/2026-11-16/10-remove-legacy-product-fields-and-routes.md) (reference classification, V26 tests, gate counts).

# DEC-20261116-03 — P6 changed: delete the two obsolete F03 routes

| Field | Value |
|---|---|
| Date | 2026-11-16 (Asia/Ho_Chi_Minh) |
| Source | Owner instruction of 2026-11-16 |
| Status | OWNER_DECISION — implemented on branch `Khoi/feat/f03-remove-legacy-product-fields` (not merged) |
| Supersedes | P6 of 2026-11-15 for these two routes (they answered 410 Gone) |

## Decision
Delete outright (they now answer like any unknown route):
- `PUT /api/utility/config/expiry-window` — replacement: batch `pushDay` on create/PATCH and `DELETE /api/inventory-batches/{id}/push-day`.
- `PATCH /api/utility/products/{productId}/scores` — replacement: season CRUD and assignment under `/api/utility/seasons`.

`GET /api/utility/products/{productId}/scores` stays: it returns the current W2 preview (U_expiry, M, W, missing push days) and is still used under W2. Every other F03 route keeps its path; P6 "same paths, new bodies" still holds for them.

## Evidence
[Record ROC-20261116-010](../../record-of-changes/2026-11-16/10-remove-legacy-product-fields-and-routes.md).

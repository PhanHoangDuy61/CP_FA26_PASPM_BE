# ROC-20261124 — F01 Store management and F02 expiry / push day / product delete (backend)

2026-11-24 / Asia/Ho_Chi_Minh. Branch `Khoi/feat/store-management` from `dev` `6d2a877`. Committed and pushed; merged into `dev` as PR #27 (merge commit `3d51f5b`).

## Record 001 — Store management: profile, status, central guard, branding images

[Record 001](01-f01-store-management.md) implements [DEC-20261123-01](../../decisions/2026-11-23/DEC-20261123-01-store-management.md):
- migration V27;
- System Admin Store administration (UC34) and the own Store profile (UC35);
- the central INACTIVE guard (403 `STORE_INACTIVE`) and 409 for System Admin jobs and user assignment;
- the scheduler skip;
- Store icon/banner on the shared `MediaStorage` (avatars included), with a public image GET for ACTIVE Stores.

OpenAPI goes from 102/123 to 107/133. CI groups: backend and mysql (results in the record).

## Record 002 — F02 expiry boundary, push-day warning, product soft delete

[Record 002](02-f02-expiry-pushday-product-delete.md) implements [DEC-20261124-01](../../decisions/2026-11-24/DEC-20261124-01-batch-expiry-boundary-effective-status.md), [-02](../../decisions/2026-11-24/DEC-20261124-02-push-day-missing-warning.md) and [-03](../../decisions/2026-11-24/DEC-20261124-03-product-soft-delete.md) on branch `Khoi/feat/f02-expiry-pushday-product-delete` (base `origin/dev` `3d51f5b`), committed and pushed, PR not opened:
- a batch is expired only after its expiry date, computed on read (`effectiveStatus`, `daysToExpiry`), in the F02 summary, F03 and F06;
- `warnings: ["MISSING_PUSH_DAY"]` on every batch; the push day has no default;
- `DELETE /api/products/{id}` (soft delete) with migration V28, 409 `PRODUCT_HAS_SELLABLE_STOCK` / `PRODUCT_IN_LIVE_CAMPAIGN`, seasons and the F04 import warning;
- `scoringVersion` was not bumped (PO decision at Checkpoint 2); the effective date and the "regenerate, do not mix" rule are in DEC-20261124-01 §6.

OpenAPI goes from 107/133 to 107/134. CI groups: backend 441/0, mysql 101/0, full 864 tests with 1 failure and 12 skipped that are identical on clean `dev`.

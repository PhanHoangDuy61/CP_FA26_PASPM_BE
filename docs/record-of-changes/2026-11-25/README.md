# ROC-20261125 — F09 Storefront merged with dev

2026-11-25 / Asia/Ho_Chi_Minh. Delivered through a pull request into `dev` ([DEC-20261119-01](../../decisions/2026-11-19/DEC-20261119-01-dev-changes-only-through-pr.md)).

Record 001 of this day belongs to the SPMF CI artifact branch (`Khoi/chore/spmf-ci-artifact`, PR #29); this folder therefore starts at 002 on this branch.

## Record 002 — F09 Storefront MVP merged with dev

[Record 002](02-f09-storefront-merge-into-dev.md) merges Duy Phan's `Duy/Feature/F09_Storefront` with `dev` `82b0ced`: migration renumbered V27 → V29 (product `image_url`, `description` only), public Store = ACTIVE Store by `store.slug` ([DEC-20261125-01](../../decisions/2026-11-25/DEC-20261125-01-f09-public-store-follows-store-status.md)), soft-deleted products and the DEC-20261124-01 expiry boundary applied to every public read. Backend group 449 tests PASS on Java 17; fresh MySQL 8.4 migrate V1→V29 PASS.

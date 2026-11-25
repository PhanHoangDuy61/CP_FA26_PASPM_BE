# ROC-20261125 — Server-generated SKU: demo seed and FoodMart mapping

2026-11-25 / Asia/Ho_Chi_Minh. Delivered through a pull request into `dev` ([DEC-20261119-01](../../decisions/2026-11-19/DEC-20261119-01-dev-changes-only-through-pr.md)).

Records 001 (SPMF CI artifact, PR #29), 002 (F09 Storefront merge, `Khoi/merge/f09-storefront-be`) and 003 (source-of-truth rule, `Khoi/docs/source-of-truth-rule`) of this day live on their own branches; this branch adds record 004.

## Record 004 — Demo seed, FoodMart mapping and JpaPilotMigrationMySqlTest

[Record 004](04-sku-foodmart-seed.md) records [DEC-20261119-07](../../decisions/2026-11-19/DEC-20261119-07-server-generated-product-sku.md) (server-generated SKU), fixes the demo seed (products by name, `mapping.csv`, import-ready CSV copies, no `batchCode`, new push-day mix) and `JpaPilotMigrationMySqlTest`, and registers that test in the `mysql` group. Seed: two runs on a fresh MySQL 8.4, 0 rejected orders, second run idempotent. Backend group 441 PASS, mysql group 105 PASS on Java 17.

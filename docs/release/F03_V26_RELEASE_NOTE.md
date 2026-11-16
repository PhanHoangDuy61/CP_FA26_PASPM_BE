# F03 follow-up release note — V26 and two deleted routes (2026-11-16)

Status: **PROPOSED / NOT MERGED.** Branch `Khoi/feat/f03-remove-legacy-product-fields` from `origin/dev` `2a944c5`. Follows the closed [F03/F05 merge plan](F03_F05_MERGE_PLAN.md) (its branch was merged as PR #19). Record: [ROC-20261116-010](../record-of-changes/2026-11-16/10-remove-legacy-product-fields-and-routes.md).

## What ships
- Migration **V26** drops `product.expiry_push_days`, `product.stock_priority_score`, `product.seasonal_priority_score` ([DEC-20261116-02](../decisions/2026-11-16/DEC-20261116-02-p5-remove-legacy-product-fields.md)).
- `PUT /api/utility/config/expiry-window` and `PATCH /api/utility/products/{productId}/scores` are deleted ([DEC-20261116-03](../decisions/2026-11-16/DEC-20261116-03-p6-delete-obsolete-routes.md)).
- Clients that still send `expiryPushDays`, `stockPriorityScore` or `seasonalPriorityScore` get **400**.
- A separate test-only commit repairs ten test cases in five classes that the EFIM-only merge `2a944c5` left stale ([record 010 §3](../record-of-changes/2026-11-16/10-remove-legacy-product-fields-and-routes.md)).

## Before deploy (none done here)
- [ ] **Back up the database first.** V26 drops columns; the values cannot be recovered without that backup.
- [ ] Stop all old backend instances before V26 runs. An old instance still maps `product.expiry_push_days` in its JPA entity, so it is expected to fail on product reads and writes after V26 (inferred from the code; **NOT VERIFIED** by a test).
- [ ] Release the Frontend together with the backend: remove the `expiryPushDays` product field and the two calls ([breaking changes 15–18](../frontend_handoff/F03_W2_BREAKING_CHANGES.md)).
- [ ] After start-up, check `flyway_schema_history` shows V26 `success = 1` and the three columns are gone.
- [ ] Earlier V25 checklist items still apply ([merge plan §4](F03_F05_MERGE_PLAN.md#4-before-deploy)).

## Rollback
Before V26: keep the old application and the backup. After V26: restore the backup together with the old application; rolling the code back alone fails, because the old entity maps a dropped column.

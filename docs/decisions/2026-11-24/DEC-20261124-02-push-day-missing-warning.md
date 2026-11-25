# DEC-20261124-02 — Push day: no default, `MISSING_PUSH_DAY` warning on the batch API (F02)

| Field | Value |
|---|---|
| Date | 2026-11-24 (Asia/Ho_Chi_Minh) |
| Source | Product Owner requirement R2 and the PO answers at Checkpoint 1 of the F02 expiry / push-day / product-delete task (2026-11-24) |
| Status | OWNER_DECISION, implemented on branch `Khoi/feat/f02-expiry-pushday-product-delete`; PR to `dev` pending |
| Feature | F02 (batch push day); F03/F06 keep their own rule |
| Related | DEC-20261115-01 (push day per batch, a missing one only warns), DEC-20261124-01 |

## Context
The PO asked to remove a default of "`expiry_date` minus 7 days" for the push day.
Audit of 2026-11-24 (backend `dev` @ `3d51f5b`, frontend `dev` @ `7def287`): **neither side applies any default.**
- Backend: `InventoryBatchService.create` passes `pushDay` as sent, `InventoryBatchRepository.create` stores null as `NULL`, `BatchCreateRequest` has no default, `update` keeps the value with `COALESCE`, V25 only adds `push_day DATE NULL` plus a CHECK, `CatalogDevSeeder` inserts batches without a push day, and F04 has no batch import.
- Frontend: `emptyBatchForm()` starts with an empty push day and create sends `pushDay || null`.
- The only "expiry − 7..14 days" is the demo seed script `scripts/seed/weight_v2_plan.py` (demo data; its fixes are deferred and untouched).
The unrelated "7" values are the `expiringWithinDays` default of the inventory summary and the 7-day F05 schedule range.

## Decision
1. **No default, no auto-fill, no backfill.** A push day the user does not enter stays empty. Existing batch data is not migrated or changed. A regression test asserts that creating a batch without `pushDay` stores and returns `null`.
2. **Saving is still allowed.** The batch API warns instead of refusing.
3. **Response field.** Every `BatchView` (list, get, create, PATCH, both DELETE-clear routes) has `warnings: string[]`, always present, empty when there is nothing to warn about. The only code is `MISSING_PUSH_DAY` (the same constant F03 uses).
4. **When it applies (PO rule):** the product has `requiresExpiry = true` **and** `pushDay == null` **and** `effectiveStatus != EXPIRED`. The stored status (ACTIVE or BLOCKED) and the remaining quantity are **not** part of the rule; the frontend already behaves this way.
5. **The summary** adds `batchesMissingPushDay`: how many batches of the product meet the same rule (0 for a product without expiry tracking).
6. **F03 keeps its own snapshot conditions.** F03 evaluates only stored ACTIVE batches with remaining > 0 that are not expired, and records `MISSING_PUSH_DAY` for those (`ExpiryRiskCalculator`). So a BLOCKED or empty batch can show the F02 warning while F03 ignores it, and that is intended: F02 tells the user which batches need a push day, F03 decides what enters U_expiry.
7. `pushDay` must still be on or before `expiryDate` (400, and a DB CHECK). `DELETE /api/inventory-batches/{id}/push-day` is unchanged.

## Consequences
- **API (additive):** `BatchView.warnings`, `InventorySummary.batchesMissingPushDay`.
- No migration.
- Tests: `BatchExpiryRulesTest` (rule), `CatalogIntegrationTest` (`batchViewExposesEffectiveStatusDaysToExpiryAndMissingPushDayWarning`, `creatingABatchWithoutPushDayNeverAutoFillsIt`, summary count).

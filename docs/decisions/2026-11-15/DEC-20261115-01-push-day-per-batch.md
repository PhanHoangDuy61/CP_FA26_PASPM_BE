# DEC-20261115-01 — Push day per inventory batch; missing push day only warns

| Field | Value |
|---|---|
| Date | 2026-11-15 (Asia/Ho_Chi_Minh) |
| Source | Q1; recorded in `outputs/prompts/PROMPT_Codex_F03_F05.md` and the session instructions |
| Status | OWNER_DECISION — implemented on branch `Khoi/feat/f05-progress-f03-weight-v2` (not merged); formal approval of the written requirement pending |
| Register | [Owner decisions 2026-11-15](../OWNER_DECISIONS_F03_F05_2026-11-15.md) |

## Decision
The push day is stored per inventory batch (`inventory_batch.push_day`, DATE, nullable) and entered by the Store Admin. `product.expiry_push_days` is no longer used for calculation. A batch without a push day only produces a warning and is excluded from Q_risk and Q_eligible; there is no automatic backfill.

## Code evidence (HEAD `c8c837a`)
- `db/migration/V25__f03_weight_v2_expiry_season.java:21-22` (column + CHECK ≤ expiry)
- `src/main/java/vn/edu/fpt/paspm/catalog/inventory/InventoryBatchService.java:44`, `:52-53`, `:63-70`
- `src/main/java/vn/edu/fpt/paspm/catalog/inventory/InventoryBatchController.java:26` (Store Admin)
- `src/main/java/vn/edu/fpt/paspm/utility/ExpiryRiskCalculator.java:45`, `:84` (excluded, `MISSING_PUSH_DAY`)
- `src/main/java/vn/edu/fpt/paspm/utility/UtilityMatrixBuilder.java:66` (snapshot never blocked)

## Tests
`PushDayIntegrationTest`, `WeightV2CalculationTest.missingPushDayOnlyWarnsAndIsExcludedFromRiskAndEligible`, `WeightV2MigrationTest` — inside the final gate (645 tests, 640 PASS, 0 FAIL, 5 SKIP; [record 004](../../record-of-changes/2026-11-15/04-f03-weight-v2-expiry-season.md)).

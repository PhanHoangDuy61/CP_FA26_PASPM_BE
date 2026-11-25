# DEC-20261115-05 — N = 30 sales window; v = 0 gives U_expiry = 0 with a warning

| Field | Value |
|---|---|
| Date | 2026-11-15 (Asia/Ho_Chi_Minh) |
| Source | Q6, Q7; recorded in `outputs/prompts/PROMPT_Codex_F03_F05.md` and the session instructions |
| Status | OWNER_DECISION — implemented on branch `Khoi/feat/f05-progress-f03-weight-v2` (not merged); formal approval of the written requirement pending |
| Register | [Owner decisions 2026-11-15](../OWNER_DECISIONS_F03_F05_2026-11-15.md) |

## Decision
v(i) = units sold in the last N days ÷ N, N = 30 by default, stored in the Store config and editable by the Merchandiser. v(i) = 0 → U_expiry = 0 plus warning `SLOW_MOVING_NO_RECENT_SALES`. Implementation choice (flagged): the window includes the analysis date; N is validated to 1..365.

## Code evidence (HEAD `c8c837a`)
- `src/main/java/vn/edu/fpt/paspm/utility/WeightV2Calculator.java:30`
- `src/main/java/vn/edu/fpt/paspm/utility/UtilityVersionedContracts.java:17` (default N 30)
- `src/main/java/vn/edu/fpt/paspm/utility/UtilityContracts.java:42` (1..365)
- `src/main/java/vn/edu/fpt/paspm/utility/ExpiryRiskCalculator.java:60`

## Tests
`WeightV2CalculationTest.noRecentSalesGivesZeroAndSlowMovingWarning`, `RetailCatalogUtilityIntegrationTest.currentScoresUseTheW2FormulaWithSalesWindowAndActiveSeasons` — inside the final gate (645 tests, 640 PASS, 0 FAIL, 5 SKIP; [record 004](../../record-of-changes/2026-11-15/04-f03-weight-v2-expiry-season.md)).

# DEC-20261115-02 — Seasons with dates and weights, per Store

| Field | Value |
|---|---|
| Date | 2026-11-15 (Asia/Ho_Chi_Minh) |
| Source | Q2; recorded in `outputs/prompts/PROMPT_Codex_F03_F05.md` and the session instructions |
| Status | OWNER_DECISION — implemented on branch `Khoi/feat/f05-progress-f03-weight-v2` (not merged); formal approval of the written requirement pending |
| Register | [Owner decisions 2026-11-15](../OWNER_DECISIONS_F03_F05_2026-11-15.md) |

## Decision
Seasons are a new Store-scoped table: name, start_date, end_date, weight in [0,1]. The Merchandiser creates, updates and deletes seasons and assigns or unassigns products. M(i) = weight of an active season that product i is assigned to, else 0.

## Code evidence (HEAD `c8c837a`)
- `db/migration/V25__f03_weight_v2_expiry_season.java:24`, `:46`, `:59` (season, product_season, utility_snapshot_season)
- `src/main/java/vn/edu/fpt/paspm/utility/SeasonController.java:14-42`
- `src/main/java/vn/edu/fpt/paspm/utility/WeightV2Calculator.java:39-43`
- `src/main/java/vn/edu/fpt/paspm/utility/SeasonRepository.java:69` (active by analysis date)

## Tests
`UtilityIntegrationTest.seasonsAreStoreScopedMerchandiserWrittenAndProtectedWhileAssigned`, `WeightV2CalculationTest.productInSeveralActiveSeasonsGetsTheHighestWeightP2` — inside the final gate (645 tests, 640 PASS, 0 FAIL, 5 SKIP; [record 004](../../record-of-changes/2026-11-15/04-f03-weight-v2-expiry-season.md)).

# DEC-20261115-04 — W(i) rounded half-up to 2 decimals

| Field | Value |
|---|---|
| Date | 2026-11-15 (Asia/Ho_Chi_Minh) |
| Source | Q9; recorded in `outputs/prompts/PROMPT_Codex_F03_F05.md` and the session instructions |
| Status | OWNER_DECISION — implemented on branch `Khoi/feat/f05-progress-f03-weight-v2` (not merged); formal approval of the written requirement pending |
| Register | [Owner decisions 2026-11-15](../OWNER_DECISIONS_F03_F05_2026-11-15.md) |

## Decision
W(i) is computed exactly and rounded half-up to 2 decimals before it is applied (experiment assumption A1). WeightedUtility = BaseUtility × W(i), scale 6. Report 4's example 1.115 becomes 1.12.

## Code evidence (HEAD `c8c837a`)
- `src/main/java/vn/edu/fpt/paspm/utility/UtilityMath.java:31-39`
- `src/main/java/vn/edu/fpt/paspm/utility/UtilityFreezer.java:25` (frozen `weightScale` 2, `HALF_UP`)

## Tests
`WeightV2CalculationTest.weightIsRoundedHalfUpToTwoDecimalsAndBounded` (includes 1.115 → 1.12), `backendMatchesTheExperimentReferenceImplementation` — inside the final gate (645 tests, 640 PASS, 0 FAIL, 5 SKIP; [record 004](../../record-of-changes/2026-11-15/04-f03-weight-v2-expiry-season.md)).

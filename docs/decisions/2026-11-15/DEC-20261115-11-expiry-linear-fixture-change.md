# DEC-20261115-11 — Test fixture `EXPIRY_LINEAR_V1` → `F03_LINEAR_V1`

| Field | Value |
|---|---|
| Date | 2026-11-15 (Asia/Ho_Chi_Minh) |
| Source | Checkpoint 2 approval, 2026-11-15; recorded in `outputs/prompts/PROMPT_Codex_F03_F05.md` and the session instructions |
| Status | OWNER_DECISION — implemented on branch `Khoi/feat/f05-progress-f03-weight-v2` (not merged); formal approval of the written requirement pending |
| Register | [Owner decisions 2026-11-15](../OWNER_DECISIONS_F03_F05_2026-11-15.md) |

## Decision
`MiningIntegrationTest.oldScoringMetadataCanBeMinedWithoutRecalculation` used the synthetic version `EXPIRY_LINEAR_V1`, which no shipped code ever wrote. Because unknown versions are now rejected, the fixture uses the real legacy version `F03_LINEAR_V1`. Approved by the owner at Checkpoint 2. Before deploying, check real data for other versions (record 004 §6).

## Code evidence (HEAD `c8c837a`)
- `src/main/java/vn/edu/fpt/paspm/utility/UtilityJsonVersions.java:29-31`
- `src/test/java/vn/edu/fpt/paspm/mining/MiningIntegrationTest.java` (fixture)

## Tests
Final gate 645 tests, 640 PASS, 5 SKIP — inside the final gate (645 tests, 640 PASS, 0 FAIL, 5 SKIP; [record 004](../../record-of-changes/2026-11-15/04-f03-weight-v2-expiry-season.md)).

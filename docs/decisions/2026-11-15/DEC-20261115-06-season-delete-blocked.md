# DEC-20261115-06 — A season with assigned products cannot be deleted; snapshots keep their own copy

| Field | Value |
|---|---|
| Date | 2026-11-15 (Asia/Ho_Chi_Minh) |
| Source | Q8; recorded in `outputs/prompts/PROMPT_Codex_F03_F05.md` and the session instructions |
| Status | OWNER_DECISION — implemented on branch `Khoi/feat/f05-progress-f03-weight-v2` (not merged); formal approval of the written requirement pending |
| Register | [Owner decisions 2026-11-15](../OWNER_DECISIONS_F03_F05_2026-11-15.md) |

## Decision
Deleting a season that still has assigned products returns 409. Snapshots keep their own copy of the season data, by value.

## Code evidence (HEAD `c8c837a`)
- `src/main/java/vn/edu/fpt/paspm/utility/SeasonService.java:51`
- `src/main/java/vn/edu/fpt/paspm/utility/UtilitySnapshotRepository.java:43`
- `db/migration/V25__f03_weight_v2_expiry_season.java:59` (no FK to `season`)

## Tests
`UtilityIntegrationTest.seasonsAreStoreScopedMerchandiserWrittenAndProtectedWhileAssigned`, `snapshotFreezesWeightV2AndSeasonsByValueAndOnlyWarnsForMissingPushDays` — inside the final gate (645 tests, 640 PASS, 0 FAIL, 5 SKIP; [record 004](../../record-of-changes/2026-11-15/04-f03-weight-v2-expiry-season.md)).

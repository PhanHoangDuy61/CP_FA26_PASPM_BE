# DEC-20261115-10 — Accept a one-time `inventoryChanged = true` on old recommendations

| Field | Value |
|---|---|
| Date | 2026-11-15 (Asia/Ho_Chi_Minh) |
| Source | Q5 (resume instruction, Phase 5); recorded in `outputs/prompts/PROMPT_Codex_F03_F05.md` and the session instructions |
| Status | OWNER_DECISION — implemented on branch `Khoi/feat/f05-progress-f03-weight-v2` (not merged); formal approval of the written requirement pending |
| Register | [Owner decisions 2026-11-15](../OWNER_DECISIONS_F03_F05_2026-11-15.md) |

## Decision
The F06 eligibility fingerprint drops `expiryPushDays` and adds batch push days, so every recommendation created before W2 reports `inventoryChanged = true` once. This is accepted; stored eligibility JSON is not rewritten; F06 never re-runs mining or generation automatically.

## Code evidence (HEAD `c8c837a`)
- `src/main/java/vn/edu/fpt/paspm/recommendation/ProductEligibilityService.java:28`, `:39`
- `src/main/java/vn/edu/fpt/paspm/recommendation/RecommendationService.java:81`

## Tests
`RecommendationIntegrationTest`, `VersionedUtilityPersistenceTest.legacySnapshotMiningAndRecommendationReadsNeverRewriteFrozenJson` — inside the final gate (645 tests, 640 PASS, 0 FAIL, 5 SKIP; [record 004](../../record-of-changes/2026-11-15/04-f03-weight-v2-expiry-season.md)).

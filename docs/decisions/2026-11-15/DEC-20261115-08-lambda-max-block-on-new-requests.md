# DEC-20261115-08 — New Merchandiser mining requests blocked when the snapshot λ exceeds the current λ_max

| Field | Value |
|---|---|
| Date | 2026-11-15 (Asia/Ho_Chi_Minh) |
| Source | Checkpoint 1 approval, 2026-11-15; recorded in `outputs/prompts/PROMPT_Codex_F03_F05.md` and the session instructions |
| Status | OWNER_DECISION — implemented on branch `Khoi/feat/f05-progress-f03-weight-v2` (not merged); formal approval of the written requirement pending |
| Register | [Owner decisions 2026-11-15](../OWNER_DECISIONS_F03_F05_2026-11-15.md) |

## Decision
A new Merchandiser request whose snapshot's frozen λ is above the current λ_max is rejected with 409 and the message to create a new snapshot. Idempotent replay of an existing request still returns it; System Admin behaviour unchanged; old data never rewritten.

## Code evidence (HEAD `c8c837a`)
- `src/main/java/vn/edu/fpt/paspm/mining/MiningService.java:83-84`

## Tests
`MiningIntegrationTest.loweredLambdaMaximumBlocksOnlyNewMerchantRequestsAndPreservesSnapshots` — inside the final gate (645 tests, 640 PASS, 0 FAIL, 5 SKIP; [record 004](../../record-of-changes/2026-11-15/04-f03-weight-v2-expiry-season.md)).

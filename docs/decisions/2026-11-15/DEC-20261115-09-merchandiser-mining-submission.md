# DEC-20261115-09 — Merchandiser may submit mining for its own Store

| Field | Value |
|---|---|
| Date | 2026-11-15 (Asia/Ho_Chi_Minh) |
| Source | P1 (owner decision, 2026-11-15); recorded in `outputs/prompts/PROMPT_Codex_F03_F05.md` and the session instructions |
| Status | OWNER_DECISION — implemented on branch `Khoi/feat/f05-progress-f03-weight-v2` (not merged); formal approval of the written requirement pending |
| Register | [Owner decisions 2026-11-15](../OWNER_DECISIONS_F03_F05_2026-11-15.md) |

## Decision
**Supersedes** "System Admin is the only mining submitter". The Merchandiser submits `POST /api/mining/requests {requestKey, snapshotId}` for its own Store (from the JWT) using the current System Admin mining configuration; no overrides. Store Admin still cannot submit. The System Admin route is unchanged; scheduling stays System Admin only and enabling still returns 503.

## Code evidence (HEAD `c8c837a`)
- `src/main/java/vn/edu/fpt/paspm/security/SecurityConfig.java:94`
- `src/main/java/vn/edu/fpt/paspm/mining/MiningController.java:24-26`
- `src/main/java/vn/edu/fpt/paspm/mining/MiningService.java:52-53`
- `src/main/java/vn/edu/fpt/paspm/mining/MiningScheduleService.java:67`

## Tests
`MiningIntegrationTest.merchantSubmissionUsesCurrentPolicyAndRejectsOverrides`, `F03F05RbacMatrixTest` — inside the final gate (645 tests, 640 PASS, 0 FAIL, 5 SKIP; [record 004](../../record-of-changes/2026-11-15/04-f03-weight-v2-expiry-season.md)).

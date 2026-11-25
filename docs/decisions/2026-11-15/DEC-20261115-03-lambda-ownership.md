# DEC-20261115-03 — λ ownership: System Admin sets λ_max; Merchandiser sets λ, α, β

| Field | Value |
|---|---|
| Date | 2026-11-15 (Asia/Ho_Chi_Minh) |
| Source | Q3, Q10; recorded in `outputs/prompts/PROMPT_Codex_F03_F05.md` and the session instructions |
| Status | OWNER_DECISION — implemented on branch `Khoi/feat/f05-progress-f03-weight-v2` (not merged); formal approval of the written requirement pending |
| Register | [Owner decisions 2026-11-15](../OWNER_DECISIONS_F03_F05_2026-11-15.md) |

## Decision
The System Admin sets only the policy upper bound λ_max; the range is [0, λ_max] with the lower bound fixed at 0. The Merchandiser picks λ in that range and sets α and β (each in [0,1], α + β = 1). λ_max defaults to 0.5 (already seeded). **Supersedes** the legacy `[lambdaMin, lambdaMax]` policy and the three weights (2026-11-15).

## Code evidence (HEAD `c8c837a`)
- `src/main/java/vn/edu/fpt/paspm/utility/UtilityConfigurationController.java:17-26`
- `src/main/java/vn/edu/fpt/paspm/utility/UtilityConfigurationService.java:31-33`, `:38-42`
- `src/main/java/vn/edu/fpt/paspm/utility/UtilityContracts.java:33-42`

## Tests
`UtilityIntegrationTest.policyAndConfigUseTheTwoWeightContractWithRolesAndValidation`, `F03F05RbacMatrixTest` — inside the final gate (645 tests, 640 PASS, 0 FAIL, 5 SKIP; [record 004](../../record-of-changes/2026-11-15/04-f03-weight-v2-expiry-season.md)).

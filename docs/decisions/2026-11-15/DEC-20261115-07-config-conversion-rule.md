# DEC-20261115-07 — V25 conversion of live Store configs, λ clamped

| Field | Value |
|---|---|
| Date | 2026-11-15 (Asia/Ho_Chi_Minh) |
| Source | Owner answer of 2026-11-15 (resume instruction); recorded in `outputs/prompts/PROMPT_Codex_F03_F05.md` and the session instructions |
| Status | OWNER_DECISION — implemented on branch `Khoi/feat/f05-progress-f03-weight-v2` (not merged); formal approval of the written requirement pending |
| Register | [Owner decisions 2026-11-15](../OWNER_DECISIONS_F03_F05_2026-11-15.md) |

## Decision
(1) Keep the Store's λ, clamped into [0, current λ_max], recording clamped Stores. (2) α ← old expiry coefficient, β ← old seasonal coefficient, only when enabled; stock dropped; normalise to α + β = 1. (3) Both 0/disabled or no config → 0.5/0.5. (4) N = 30, version + 1, `scoringVersion = W2_EXPIRY_SEASON_V1`. (5) Never touch frozen JSON. (6) Record in the record of changes.

## Code evidence (HEAD `c8c837a`)
- `src/main/java/vn/edu/fpt/paspm/utility/WeightV2Conversion.java:14-16`, `:44-49`
- `db/migration/V25__f03_weight_v2_expiry_season.java:80` (evidence table), `:150` (policy row)

## Tests
`WeightV2MigrationTest` (H2 + MySQL): normalisation, stock-only → 0.5/0.5, no config, λ above λ_max clamped. No real Store converted. — inside the final gate (645 tests, 640 PASS, 0 FAIL, 5 SKIP; [record 004](../../record-of-changes/2026-11-15/04-f03-weight-v2-expiry-season.md)).

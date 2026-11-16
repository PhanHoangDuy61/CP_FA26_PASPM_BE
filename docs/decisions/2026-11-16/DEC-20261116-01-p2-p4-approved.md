# DEC-20261116-01 — P2, P3 and P4 approved as built

| Field | Value |
|---|---|
| Date | 2026-11-16 (Asia/Ho_Chi_Minh) |
| Source | Owner instruction of 2026-11-16 in the working session |
| Status | OWNER_DECISION — approved 2026-11-16; no code change |
| Supersedes | The "assumption pending owner confirmation" status of P2–P4 in [OWNER_DECISIONS_F03_F05_2026-11-15](../OWNER_DECISIONS_F03_F05_2026-11-15.md) |

## Decision
- **P2** — a product in several active seasons gets the highest weight; on a tie, the lowest `seasonId`.
- **P3** — a season is active when `start_date ≤ analysisDate ≤ end_date`, both ends included; preview, manual and scheduled snapshots use the same rule, and snapshots copy the season data by value.
- **P4** — a push day on or before the analysis date leaves zero selling days, so the whole remaining batch is at risk.

## Code evidence (`origin/dev` `2a944c5`)
- P2: `src/main/java/vn/edu/fpt/paspm/utility/WeightV2Calculator.java` (`seasonScores`, highest weight, lowest id on tie).
- P3: `src/main/java/vn/edu/fpt/paspm/utility/SeasonRepository.java` (`active`: `start_date<=? AND end_date>=?`); `src/main/java/vn/edu/fpt/paspm/utility/UtilityFreezer.java`; `src/main/java/vn/edu/fpt/paspm/utility/UtilitySnapshotRepository.java` (`saveSeasons`).
- P4: `src/main/java/vn/edu/fpt/paspm/utility/ExpiryRiskCalculator.java` (`Math.max(days,0)` selling days).

## Tests
`WeightV2CalculationTest` (`productInSeveralActiveSeasonsGetsTheHighestWeightP2`, `pushDayOnOrBeforeAnalysisDatePutsTheWholeBatchAtRisk`), `UtilityIntegrationTest.snapshotFreezesWeightV2AndSeasonsByValueAndOnlyWarnsForMissingPushDays` — unchanged by this decision.

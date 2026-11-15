# Owner decisions F03/F05 — 2026-11-15

Decisions stated by the project owner in the working conversation on 2026-11-15 and implemented on branch `Khoi/feat/f05-progress-f03-weight-v2` (HEAD `c8c837a`, not merged). No approver name or signature is recorded, and none is invented. Requirement text derived from them: [F03 Weight v2 requirements](../requirements/F03_WEIGHT_V2_REQUIREMENTS.md) (pending formal approval).

## Decisions

| ID | Decision | Source | Status |
|---|---|---|---|
| [DEC-20261115-01](2026-11-15/DEC-20261115-01-push-day-per-batch.md) | Push day per inventory batch; missing push day only warns | Q1 | Implemented, tested |
| [DEC-20261115-02](2026-11-15/DEC-20261115-02-seasons-with-dates-and-weights.md) | Seasons with dates and weights, per Store | Q2 | Implemented, tested |
| [DEC-20261115-03](2026-11-15/DEC-20261115-03-lambda-ownership.md) | λ ownership: System Admin sets λ_max; Merchandiser sets λ, α, β | Q3, Q10 | Implemented, tested |
| [DEC-20261115-04](2026-11-15/DEC-20261115-04-weight-rounding-two-decimals.md) | W(i) rounded half-up to 2 decimals | Q9 | Implemented, tested |
| [DEC-20261115-05](2026-11-15/DEC-20261115-05-sales-window-and-zero-sales.md) | N = 30 sales window; v = 0 gives U_expiry = 0 with a warning | Q6, Q7 | Implemented, tested |
| [DEC-20261115-06](2026-11-15/DEC-20261115-06-season-delete-blocked.md) | A season with assigned products cannot be deleted; snapshots keep their own copy | Q8 | Implemented, tested |
| [DEC-20261115-07](2026-11-15/DEC-20261115-07-config-conversion-rule.md) | V25 conversion of live Store configs, λ clamped | Owner answer of 2026-11-15 (resume instruction) | Implemented, tested |
| [DEC-20261115-08](2026-11-15/DEC-20261115-08-lambda-max-block-on-new-requests.md) | New Merchandiser mining requests blocked when the snapshot λ exceeds the current λ_max | Checkpoint 1 approval, 2026-11-15 | Implemented, tested |
| [DEC-20261115-09](2026-11-15/DEC-20261115-09-merchandiser-mining-submission.md) | Merchandiser may submit mining for its own Store | P1 (owner decision, 2026-11-15) | Implemented, tested |
| [DEC-20261115-10](2026-11-15/DEC-20261115-10-f06-one-time-inventory-changed.md) | Accept a one-time `inventoryChanged = true` on old recommendations | Q5 (resume instruction, Phase 5) | Implemented, tested |
| [DEC-20261115-11](2026-11-15/DEC-20261115-11-expiry-linear-fixture-change.md) | Test fixture `EXPIRY_LINEAR_V1` → `F03_LINEAR_V1` | Checkpoint 2 approval, 2026-11-15 | Implemented, tested |

## Assumptions pending owner confirmation

These defaults were applied because the prompt fixed them as defaults; each is **assumption pending owner confirmation**.

| ID | Assumption | Code evidence (paths relative to `src/main/java/vn/edu/fpt/paspm/`) |
|---|---|---|
| P2 | A product in several active seasons gets the highest weight (ties: lowest seasonId) | `utility/WeightV2Calculator.java:39-43` |
| P3 | A season is active when the analysis date lies in [start_date, end_date] inclusive; preview, manual and scheduled snapshots use the same rule | `utility/SeasonRepository.java:69`; `utility/UtilityFreezer.java:25`; `mining/MiningScheduledSnapshot.java:44-45` |
| P4 | A push day on or before the analysis date puts the whole remaining batch at risk | `utility/ExpiryRiskCalculator.java:74` |
| P5 | The Product API still accepts `expiryPushDays` but ignores it; deprecated in OpenAPI | `catalog/CatalogContracts.java:38` |
| P6 | Same route paths, new bodies; breaking changes documented for the Frontend | [breaking changes](../frontend_handoff/F03_W2_BREAKING_CHANGES.md) |

## Superseded by these decisions (kept as history)

- Weight Rules v1.2 binary expiry (`EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1`) and the three weights α (stock), β (expiry), γ (season) with `[lambdaMin, lambdaMax]` — legacy since 2026-11-15; still used to **read** pre-V25 data.
- "System Admin is the only mining submitter" (F05 requirements / D-register era) — superseded by DEC-20261115-09.
- Manual product stock/seasonal scores — replaced by seasons (DEC-20261115-02).

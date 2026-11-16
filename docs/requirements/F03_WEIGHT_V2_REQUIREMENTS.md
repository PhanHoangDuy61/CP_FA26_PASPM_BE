# F03 Weight v2 requirements (`W2_EXPIRY_SEASON_V1`)

> **Derived from owner decisions 2026-11-15, pending formal approval.** Built only from the [owner decision register](../decisions/OWNER_DECISIONS_F03_F05_2026-11-15.md) and the code on branch `Khoi/feat/f05-progress-f03-weight-v2` @ `c8c837a` (not merged). It is not an approved requirement document and does not replace the missing Weight Rules v1.2 source ([gap](README.md#f03-v12-source-gap)), which remains the legacy authority for pre-V25 data. Code paths are relative to `src/main/java/vn/edu/fpt/paspm/`. "P" items were assumptions on 2026-11-15; **all five were resolved on 2026-11-16**: P2, P3 and P4 approved as built ([DEC-20261116-01](../decisions/2026-11-16/DEC-20261116-01-p2-p4-approved.md)), P5 changed to full removal with V26 ([DEC-20261116-02](../decisions/2026-11-16/DEC-20261116-02-p5-remove-legacy-product-fields.md)), P6 changed for two routes that are now deleted ([DEC-20261116-03](../decisions/2026-11-16/DEC-20261116-03-p6-delete-obsolete-routes.md)). Line references point to `c8c837a`; the 2026-11-16 changes are listed in R4.4 and R9.

## R1 Formula
- R1.1 `W(i) = 1 + λ·[α·U_expiry(i) + β·M(i)]`, `1 ≤ W ≤ round(1 + λ, 2)` — DEC-04; `utility/UtilityMath.java:31-39`.
- R1.2 W is computed exactly and rounded half-up to 2 decimals before use; `WeightedUtility = BaseUtility × W`, scale 6 HALF_UP — DEC-04; `utility/UtilityMath.java:39`.

## R2 Expiry risk U_expiry
- R2.1 `U_expiry = Q_risk / Q_eligible`; `Q_eligible = 0 → 0` — DEC-01; `utility/ExpiryRiskCalculator.java`.
- R2.2 Eligible batch: ACTIVE, remaining > 0, not expired at the analysis date (expiry > analysis date), has a push day — `utility/ExpiryRiskCalculator.java:41`, `:45`.
- R2.3 FEFO order: push day ascending, then batch id as an integer — `utility/ExpiryRiskCalculator.java:59`.
- R2.4 Each batch sells at most `v × days_to_push − units sold from earlier batches`; the rest is Q_risk — `utility/ExpiryRiskCalculator.java:74-75`.
- R2.5 (P4, approved 2026-11-16) A push day on or before the analysis date gives zero selling days — `utility/ExpiryRiskCalculator.java:74`.
- R2.6 Golden values: 120 units, push in 3 days, 25/day → 0.375; 40 units at 2 days + 60 units at 5 days, 15/day → 0.25 — `WeightV2CalculationTest`.

## R3 Sales rate
- R3.1 `v(i)` = units of COMPLETED, F03-eligible transactions in the N days ending on and including the analysis date (Store time zone) ÷ N — DEC-05; `utility/WeightV2Calculator.java:30`.
- R3.2 N = 30 by default, stored in the Store config, editable by the Merchandiser (1..365) — `utility/UtilityVersionedContracts.java:17`; `utility/UtilityContracts.java:42`.
- R3.3 `v = 0 → U_expiry = 0`, status `SLOW_MOVING`, warning `SLOW_MOVING_NO_RECENT_SALES` — DEC-05; `utility/ExpiryRiskCalculator.java:60`.

## R4 Push day (F02)
- R4.1 `inventory_batch.push_day` DATE nullable, ≤ expiry date, entered by the Store Admin on batch create/PATCH — DEC-01; `catalog/inventory/InventoryBatchService.java:44`, `:63-70`; `catalog/inventory/InventoryBatchController.java:26`.
- R4.2 A batch without a push day only warns (`MISSING_PUSH_DAY`) and is excluded; snapshot creation is never blocked — `utility/ExpiryRiskCalculator.java:84`; `utility/UtilityMatrixBuilder.java:66`.
- R4.3 No automatic backfill — `db/migration/V25__f03_weight_v2_expiry_season.java:21`.
- R4.4 (P5, changed 2026-11-16) `expiryPushDays`, `stockPriorityScore` and `seasonalPriorityScore` are not part of any live product request or response; sending one returns 400 (unknown field). Migration V26 drops `product.expiry_push_days`, `product.stock_priority_score` and `product.seasonal_priority_score`. Frozen JSON that contains these names is still read by the legacy reader records. (Until 2026-11-16: accepted, stored, ignored, deprecated.)

## R5 Seasons
- R5.1 Store-scoped season: name (unique per Store), start_date ≤ end_date, weight in [0,1], version — DEC-02; V25 `:24`.
- R5.2 Merchandiser creates, updates (optimistic `expectedVersion`), deletes, assigns and unassigns; Store Admin reads; another Store's season or product → 404 — `utility/SeasonController.java:14-42`; `utility/SeasonService.java:38`, `:63`.
- R5.3 A season with assigned products cannot be deleted (409) — DEC-06; `utility/SeasonService.java:51`.
- R5.4 M(i) = weight of an active season the product is assigned to, else 0 — DEC-02; (P2, approved 2026-11-16) highest weight wins; (P3, approved 2026-11-16) active = analysis date in [start, end] inclusive, same rule for preview, manual and scheduled snapshots — `utility/WeightV2Calculator.java:39-43`; `utility/SeasonRepository.java:69`.
- R5.5 Snapshots copy the active seasons by value — DEC-06; `utility/UtilitySnapshotRepository.java:43`.

## R6 λ, α, β
- R6.1 The System Admin sets only λ_max; range [0, λ_max]; default λ_max 0.5 — DEC-03; `utility/UtilityConfigurationController.java:17-20`.
- R6.2 The Merchandiser sets λ ∈ [0, λ_max] and α, β ∈ [0,1] with α + β = 1; otherwise 400 — DEC-03; `utility/UtilityConfigurationService.java:31-32`, `:38-42`.
- R6.3 A stored λ above a lowered λ_max is not clamped; `lambdaValid=false`, new snapshots → 409 — `utility/UtilityFreezer.java:23`.

## R7 Snapshots, compatibility and migration
- R7.1 Snapshots freeze λ, α, β, N, λ_max, the active seasons, U_expiry, M, W and warnings — `utility/UtilityFreezer.java:25`; `utility/UtilitySnapshotRepository.java:37`.
- R7.2 Stored JSON (snapshots, mining requests, recommendations) is never rewritten and is read by `scoringVersion`; unknown versions are rejected; unknown fields stay rejected — `utility/UtilityJsonVersions.java:29-31`; `recommendation/RecommendationJsonVersions.java:21-22`.
- R7.3 Live Store configs and the λ policy are converted once by V25 with the owner rule (λ clamped, α ← expiry, β ← season, defaults 0.5/0.5, N = 30, version + 1) and evidence rows — DEC-07; `utility/WeightV2Conversion.java:44-49`; V25 `:80`, `:150`.

## R8 Consumers
- R8.1 F05: Merchandiser submission with the System Admin configuration (DEC-09); λ_max block on new requests (DEC-08) — `mining/MiningService.java:52-53`, `:83-84`.
- R8.2 F06: eligibility v2 without `expiryPushDays`, push-day expiry alerts, one-time `inventoryChanged` accepted (DEC-10), warning when a snapshot season has ended, no automatic re-run (Q5) — `recommendation/ProductEligibilityService.java:56`; `recommendation/RecommendationService.java:80-81`.
- R8.3 F07/F08: "priority item = W(i) > 1" is **not wired**; priority coverage stays null — `layout/LayoutCampaignRepository.java:344-345`.

## R9 Routes (P6, changed 2026-11-16)
- R9.1 `PUT /api/utility/config/expiry-window` and `PATCH /api/utility/products/{productId}/scores` do not exist; they answer like any unknown route (404 for the whole path, 405 for the PATCH because `GET .../scores` stays). Replacements: batch `pushDay` (create, PATCH, `DELETE /api/inventory-batches/{id}/push-day`) and season CRUD/assignment.
- R9.2 Every other F03 route keeps its path; bodies follow W2 (P6 of 2026-11-15).

## Removed / superseded (kept in history)
- 2026-11-15: three weights with stock score and `[lambdaMin, lambdaMax]`; binary expiry score from `Product.expiryPushDays`; manual product stock/seasonal scores (PATCH → 410); Store expiry window (→ 410); "System Admin is the only mining submitter".
- 2026-11-16: the two 410 routes are deleted; the three legacy product fields and columns are removed (V26).

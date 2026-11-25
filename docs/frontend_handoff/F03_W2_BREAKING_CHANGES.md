# F03 Weight v2 — breaking request/response changes for the Frontend (2026-11-15)

Branch `Khoi/feat/f05-progress-f03-weight-v2` (not merged). Rule P6: route paths stay the same, bodies change. Contract: [F03 API](../api/F03_UTILITY_API.md); schema: [V24/V25 notes](../architecture/F03_W2_SCHEMA_V24_V25.md). The backend rejects unknown JSON fields, so every removed request field below now returns **400**.

## Breaking

Code paths are relative to `src/main/java/vn/edu/fpt/paspm/` on `c8c837a`.

| # | Route | Old request/response | New request/response | Screen affected | Evidence (rows 1–14) / replacement to use (rows 15–18) |
|---|---|---|---|---|---|
| 1 | `PUT /api/system/utility/lambda-policy` | `{lambdaMin, lambdaMax}` | `{lambdaMax}` only; `lambdaMin` → 400 | System Admin — λ policy | `utility/UtilityContracts.java:33`; `utility/UtilityConfigurationController.java:20` |
| 2 | `GET/PUT /api/system/utility/lambda-policy` response | `{lambdaMin, lambdaMax, version}` | `{scoringVersion, lambdaMax, version}`; lower bound always 0 | System Admin — λ policy | `utility/UtilityVersionedContracts.java:27` |
| 3 | `PUT /api/utility/config` request | `{enableStock, enableExpiry, enableSeason, alpha, beta, gamma, lambda}` | `{alpha, beta, lambda, salesWindowDays?}`; α = expiry, β = season, α + β = 1; old fields → 400 | Merchandiser — weight settings | `utility/UtilityContracts.java:36-42`; `utility/UtilityConfigurationService.java:31-33` |
| 4 | `GET/PUT /api/utility/config` response | `config: WeightConfig` (flags, gamma, expiryWindowDays); `lambdaPolicy: LambdaPolicy` | `config: WeightV2 {alpha, beta, lambda, salesWindowDays, version, scoringVersion}`; `lambdaPolicy: LambdaV2` | Merchandiser + Store Admin — weight settings (read) | `utility/UtilityVersionedContracts.java:13`, `:27` |
| 5 | `PUT /api/utility/config/expiry-window` | 200 + `ConfigView` | **410 Gone** (superseded 2026-11-16 by row 16: route deleted) | Store Admin — expiry window form (remove); push day moves to the batch form | `utility/UtilityConfigurationController.java:30-33` |
| 6 | `PATCH /api/utility/products/{id}/scores` | `{stockPriorityScore, seasonalPriorityScore}` → `Scores` | **410 Gone** (superseded 2026-11-16 by row 17: route deleted) | Merchandiser — product score editor (remove); use season assignment | `utility/ProductScoreController.java:20`; `utility/ProductScoreService.java:46` |
| 7 | `GET /api/utility/products/{id}/scores` | `stockPriorityScore, seasonalPriorityScore, expiryScore, expiryPushDays, expiryEvidence (binary)` | `expiryRisk, seasonWeight, seasonId, weightValue (2 dp), expiryStatus, expiryEvidence (FefoEvidence), batchesMissingPushDay, activeSeasons, scoringVersion, …` | Merchandiser + Store Admin — product score detail | `utility/UtilityContracts.java:44`; `utility/ProductScoreService.java:27-41` |
| 8 | Snapshot `configuration` (new snapshots) | `FrozenConfiguration` | `FrozenV2` (`weightScale`, `weightRoundingMode`, `seasons[]`, coefficients `{alpha, beta}`); old snapshots still `FrozenConfiguration` — branch on `configuration.config.scoringVersion` | Merchandiser — snapshot detail; F05 request detail | `utility/UtilityVersionedContracts.java:38`; `utility/UtilityJsonVersions.java:29-31` |
| 9 | Matrix rows (new snapshots) | `stockScore`, `expiryScore`, `seasonalScore` filled | those three null; `expiryRisk`, `seasonWeight`, `seasonId`; `expiryEvidence` = `FefoEvidence` | Merchandiser — snapshot matrix table | `utility/UtilityMatrixBuilder.java:56`; `utility/UtilitySnapshotRepository.java:37` |
| 10 | `expiryStatus` | `AVAILABLE, NOT_APPLICABLE, NO_SELLABLE_BATCH, MISSING_METADATA` | adds `MISSING_PUSH_DAY`, `SLOW_MOVING` | Product score detail; matrix table; badges | `utility/ExpiryRiskCalculator.java:60`, `:84`; V25 `:130` |
| 11 | `GET /api/utility/expiry-push-batches` items | `PushBatch {expiryPushDays, daysRemaining, pushRequired, pushRequiredQuantity, asOfDate}` | `DueBatch {productId, batchId, pushDay, expiryDate, daysToPush, remainingQuantity}`; due = push day ≤ asOfDate | Store Admin + Merchandiser — expiry push list | `utility/PushDayRule.java:23`; `utility/ExpiryPushService.java:33` |
| 12 | `GET /api/recommendations/expiry-push` items | `{expiryDate, daysRemaining, expiryPushDays, status, …}` | `{productId, batchId, pushDay, expiryDate, daysToPush, remainingQuantity, pushRequired, checkedAt, warnings}`; ordered by pushDay | Merchandiser — recommendation expiry alerts | `recommendation/RecommendationExpiryService.java:43` |
| 13 | F06 `currentEligibility` (new `generationEligibility`, review `eligibilityCheck`) | `RecEligibility`; products with `expiryPushDays` | `EligibilityV2` with `scoringVersion`; no `expiryPushDays`; `expiryPriorityScore` = current U_expiry; old stored values still `RecEligibility` | Merchandiser — recommendation list/detail/review | `recommendation/ProductEligibilityService.java:56`; `recommendation/RecommendationJsonVersions.java:21-22` |
| 14 | F06 `inventoryChanged` | fingerprint compare | fingerprint format changed → `true` once on every pre-W2 recommendation (accepted) | Merchandiser — recommendation list badge | `recommendation/RecommendationService.java:81`; `recommendation/ProductEligibilityService.java:28`, `:39` |
| 15 | `POST /api/products`, `PUT /api/products/{id}` request and every product response (`GET /api/products`, `GET /api/products/{id}`) | `expiryPushDays` accepted (deprecated, ignored) and returned | **Removed (2026-11-16, DEC-20261116-02).** Sending `expiryPushDays` → **400** (unknown field); responses no longer contain it. V26 drops the column | Store Admin — product create/edit form; product list/detail | Batch `pushDay` on the batch form (create, PATCH, `DELETE /api/inventory-batches/{id}/push-day`) |
| 16 | `PUT /api/utility/config/expiry-window` | 410 Gone (row 5) | **Route deleted (2026-11-16, DEC-20261116-03)**: answers like any unknown path (404 for an authenticated caller, 401 without a token) | Store Admin — any leftover call to the old expiry-window form | Batch `pushDay` as in row 15 |
| 17 | `PATCH /api/utility/products/{productId}/scores` and the `stockPriorityScore` / `seasonalPriorityScore` fields | 410 Gone (row 6); fields absent from responses since W2 | **Route deleted (2026-11-16, DEC-20261116-03)**: `GET` on the same path stays, so `PATCH` answers 405 (unsupported method). Sending the score fields anywhere → 400; V26 drops the columns | Merchandiser — product score editor (remove the call) | Season CRUD and `PUT/DELETE /api/utility/seasons/{seasonId}/products/{productId}` |
| 18 | `expiryWindowDays` in `GET/PUT /api/utility/config` | Returned in `config` (deprecated Store setting, ignored since v1.2); set through `PUT /api/utility/config/expiry-window` | **Removed from the live contract (2026-11-16).** It has not been in the response since W2 (row 4: `config` is `WeightV2`), and a `PUT /api/utility/config` body that contains it gets **400**. Its route is deleted (row 16). Old F03_LINEAR_V1 and EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1 snapshots still show it inside their frozen `configuration.config`, which is read-only history and unchanged | Merchandiser and Store Admin — remove `expiryWindowDays` from the config type and form; keep it optional only when rendering a historical snapshot configuration | `utility/UtilityContracts.java:43` (`ConfigView` → `WeightV2`); legacy reader `utility/UtilityContracts.java:19-21` (`WeightConfig`, frozen JSON only); test `LegacyProductFieldsRemovedTest#utilityConfigHasNoExpiryWindowDays` |

## Additive

| Route | Change |
|---|---|
| `POST /api/products/{id}/batches`, `PATCH /api/inventory-batches/{id}`, batch views | optional `pushDay` (date, ≤ expiryDate, else 400); PATCH `{pushDay}` alone is a valid body. Store Admin only |
| `/api/utility/seasons…` (8 operations) | season CRUD + product assignment; Merchandiser writes, Store Admin reads |
| F06 recommendation `explanation` | a line when a season frozen in the recommendation's snapshot has ended (`end_date` before today); nothing re-runs automatically |
| Product API `expiryPushDays` | *(2026-11-15)* still accepted and stored, marked deprecated (P5). **Superseded 2026-11-16 by row 15: removed, 400 when sent** |

## UI checklist
- Merchandiser config form: two coefficients (expiry, season) that sum to 1, λ slider bounded by `lambdaMax`, sales window N.
- New season screens (list, create/edit with `expectedVersion`, delete blocked while products are assigned, assign/unassign products).
- Store Admin batch form: push day input; highlight `batchesMissingPushDay` and `MISSING_PUSH_DAY` warnings — **all existing batches start without a push day**.
- Snapshot/matrix views must render both the legacy and the W2 shapes.

> **F05 note — superseded 2026-11-16 ([DEC-20261116-04](../decisions/2026-11-16/DEC-20261116-04-f05-efim-only-mining.md)).** New requests are EFIM-only. The `COMPARING` stage and a second algorithm in the progress section below appear only on historical requests.

## F05 progress polling (Checkpoint 1, 2026-11-15)

- Routes: `GET /api/mining/requests/{id}/progress` (Merchandiser, Store Admin) and `GET /api/system/mining/stores/{storeId}/requests/{requestId}/progress` (System Admin) — `mining/MiningProgressController.java:22-29`.
- Poll every **1–2 seconds** after `202 Accepted`; **stop** when `status` is `COMPLETED` or `FAILED` (`mining/MiningDispatchLease.java:49`, `mining/MiningWorker.java:86`). `stage = LEGACY` (V24 default, `src/main/resources/db/migration/V24__f05_mining_progress.sql:2`) only means the request was created before V24; it is not terminal — a still-pending legacy request becomes `QUEUED` when claimed (`mining/MiningDispatchLease.java:21`).
- Show `stage` and `algorithm`; use `percent`/`completedSteps`/`totalSteps` from the response (`mining/MiningProgressReader.java:22-38`). Do not simulate a time-based bar. FAILED may stop below 100%; a comparison mismatch can be FAILED at 100%.
- `attemptNo` (= `lease_fence`) **may increase** after a lease loss and recovery (`mining/MiningDispatchLease.java:21`); completed steps can then drop. Treat a higher `attemptNo` as "restarted by the server", not an error.
- `timeoutMs` is present only while `RUNNING`; `elapsedMs` uses the database clock.
- Merchandiser submit: `POST /api/mining/requests {requestKey, snapshotId}`; 409 when the snapshot's λ is above the current λ_max (show the message: create a new snapshot) — `mining/MiningService.java:83-84`.

## 2026-11-16 — Additive follow-ups
Use the new Store-Admin DELETE `/api/inventory-batches/{id}/push-day` to clear a date (PATCH null keeps it). Display both missing-push-day and zero-sales warnings when both apply. Recommendation views expose `seasonEnded` plus `endedSeasons` for badges. Config/snapshot reads may return 409 with `code: UNSUPPORTED_SCORING_VERSION` and readable `error`; present the message and require administrator action rather than blind retry. These additions do not replace the 14 earlier breaking changes.

## 2026-11-16 — Legacy product fields, two routes and `expiryWindowDays` removed (rows 15–18)

Branch `Khoi/feat/f03-remove-legacy-product-fields` (not merged). Decisions: [DEC-20261116-02](../decisions/2026-11-16/DEC-20261116-02-p5-remove-legacy-product-fields.md), [DEC-20261116-03](../decisions/2026-11-16/DEC-20261116-03-p6-delete-obsolete-routes.md). The backend keeps `fail-on-unknown-properties`, so a client that still sends `expiryPushDays`, `stockPriorityScore` or `seasonalPriorityScore` gets **400**. Release the Frontend change together with the backend. Old snapshots, matrix rows and recommendations may still show `expiryPushDays` inside their frozen evidence; render it read-only, never send it back. Row 18: `expiryWindowDays` is not part of `GET/PUT /api/utility/config` (`config` is `WeightV2`), and a `PUT` body that contains it gets **400**. Old F03_LINEAR_V1/BINARY snapshots still show it inside their frozen `configuration.config`; read it, never send it.

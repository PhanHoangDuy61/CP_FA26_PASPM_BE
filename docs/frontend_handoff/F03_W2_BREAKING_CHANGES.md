# F03 Weight v2 — breaking request/response changes for the Frontend (2026-11-15)

Branch `Khoi/feat/f05-progress-f03-weight-v2` (not merged). Rule P6: route paths stay the same, bodies change. Contract: [F03 API](../api/F03_UTILITY_API.md); schema: [V24/V25 notes](../architecture/F03_W2_SCHEMA_V24_V25.md). The backend rejects unknown JSON fields, so every removed request field below now returns **400**.

## Breaking

| # | Route | Before | After |
|---|---|---|---|
| 1 | `PUT /api/system/utility/lambda-policy` | `{lambdaMin, lambdaMax}` | `{lambdaMax}` only; `lambdaMin` → 400 |
| 2 | `GET/PUT /api/system/utility/lambda-policy` response | `{lambdaMin, lambdaMax, version}` | `{scoringVersion, lambdaMax, version}`; lower bound is always 0 |
| 3 | `PUT /api/utility/config` request | `{enableStock, enableExpiry, enableSeason, alpha, beta, gamma, lambda}` | `{alpha, beta, lambda, salesWindowDays?}`; alpha = expiry, beta = season, `alpha + beta = 1`; the old fields → 400 |
| 4 | `GET/PUT /api/utility/config` response `config` | `WeightConfig` (enable flags, gamma, expiryWindowDays) | `WeightV2 {alpha, beta, lambda, salesWindowDays, version, scoringVersion}`; `lambdaPolicy` is `LambdaV2` |
| 5 | `PUT /api/utility/config/expiry-window` | 200 | **410 Gone** — remove the Store expiry-window form |
| 6 | `PATCH /api/utility/products/{id}/scores` | manual stock/seasonal scores | **410 Gone** — use season assignment |
| 7 | `GET /api/utility/products/{id}/scores` response | `stockPriorityScore, seasonalPriorityScore, expiryScore, expiryPushDays, expiryEvidence (binary)` | `expiryRisk, seasonWeight, seasonId, batchesMissingPushDay, activeSeasons, scoringVersion, expiryEvidence (FefoEvidence)`; `weightValue` now has 2 decimals |
| 8 | Snapshot `configuration` (new snapshots) | `FrozenConfiguration` | `FrozenV2` (`weightScale`, `weightRoundingMode`, `seasons[]`, coefficients `{alpha, beta}`); old snapshots still return `FrozenConfiguration` — branch on `configuration.config.scoringVersion` |
| 9 | Matrix rows (new snapshots) | `stockScore`, `expiryScore`, `seasonalScore` filled | those three are null; read `expiryRisk`, `seasonWeight`, `seasonId`; `expiryEvidence` is `FefoEvidence` (old rows keep the legacy `Evidence`) |
| 10 | `expiryStatus` | `AVAILABLE, NOT_APPLICABLE, NO_SELLABLE_BATCH, MISSING_METADATA` | adds `MISSING_PUSH_DAY`, `SLOW_MOVING` (legacy rows may still carry `MISSING_METADATA`) |
| 11 | `GET /api/utility/expiry-push-batches` items | `PushBatch {expiryPushDays, daysRemaining, pushRequired, pushRequiredQuantity, asOfDate}` | `DueBatch {productId, batchId, pushDay, expiryDate, daysToPush, remainingQuantity}`; due = push day ≤ asOfDate; missing push days only in `warnings` |
| 12 | `GET /api/recommendations/expiry-push` items | `{expiryDate, daysRemaining, expiryPushDays, status, …}` | `{productId, batchId, pushDay, expiryDate, daysToPush, remainingQuantity, pushRequired, checkedAt, warnings}`; ordered by pushDay |
| 13 | F06 `currentEligibility` (and new `generationEligibility`, review `eligibilityCheck`) | `RecEligibility`; products carry `expiryPushDays` | `EligibilityV2` with `scoringVersion`; products have no `expiryPushDays`; `expiryPriorityScore` is the current U_expiry. Old stored values still come back as `RecEligibility` |
| 14 | F06 `inventoryChanged` | fingerprint compare | the fingerprint format changed, so every recommendation created before W2 shows `inventoryChanged = true` once (accepted) |

## Additive

| Route | Change |
|---|---|
| `POST /api/products/{id}/batches`, `PATCH /api/inventory-batches/{id}`, batch views | optional `pushDay` (date, ≤ expiryDate, else 400); PATCH `{pushDay}` alone is a valid body. Store Admin only |
| `/api/utility/seasons…` (8 operations) | season CRUD + product assignment; Merchandiser writes, Store Admin reads |
| F06 recommendation `explanation` | a line when a season frozen in the recommendation's snapshot has ended (`end_date` before today); nothing re-runs automatically |
| Product API `expiryPushDays` | still accepted and stored, marked **deprecated**; ignored by every calculation (P5) |

## UI checklist
- Merchandiser config form: two coefficients (expiry, season) that sum to 1, λ slider bounded by `lambdaMax`, sales window N.
- New season screens (list, create/edit with `expectedVersion`, delete blocked while products are assigned, assign/unassign products).
- Store Admin batch form: push day input; highlight `batchesMissingPushDay` and `MISSING_PUSH_DAY` warnings — **all existing batches start without a push day**.
- Snapshot/matrix views must render both the legacy and the W2 shapes.

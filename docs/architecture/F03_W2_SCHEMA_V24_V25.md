# F03 Weight v2 and F05 progress — schema notes (V24, V25)

ERD notes for `W2_EXPIRY_SEASON_V1` and F05 mining progress. The editable ERD drawings in `docs/diagrams/` are **not** updated here (the change-record rules forbid editing diagrams in this pass); these notes are the delta to apply to them.

## V24 — F05 mining progress (`V24__f05_mining_progress.sql`)

| Table | Change |
|---|---|
| `mining_request` | `stage VARCHAR(16) NOT NULL DEFAULT 'LEGACY'` (CHECK `LEGACY, QUEUED, RUNNING, SAVING, COMPARING, COMPLETED, FAILED`), `stage_started_at TIMESTAMP NULL`, `current_run_id BIGINT NULL` |
| `mining_run` | `UNIQUE(store_id, request_id, run_id)` |
| FK | `mining_request(store_id, request_id, current_run_id) → mining_run(store_id, request_id, run_id)` |

`status` and its CHECK are unchanged. Rows that existed before V24 keep stage `LEGACY`.

## V25 — F03 Weight v2 (`db/migration/V25__f03_weight_v2_expiry_season.java`)

One Java migration (it converts JSON, so it must behave identically on H2 and MySQL).

### New tables

```
season (season_id PK, store_id FK store, name VARCHAR(100), start_date, end_date, weight DECIMAL(7,6),
        version BIGINT, created_by/updated_by FK app_user, created_at, updated_at)
  UNIQUE(store_id, name); UNIQUE(store_id, season_id); CHECK start_date <= end_date; CHECK 0 <= weight <= 1

product_season (store_id, season_id, product_id, assigned_by FK app_user, assigned_at)
  PK(season_id, product_id)
  FK(store_id, season_id)  → season(store_id, season_id)
  FK(store_id, product_id) → product(store_id, product_id)      -- a product of another Store cannot be assigned
  INDEX(store_id, product_id)

utility_snapshot_season (snapshot_id, store_id, season_id, name, start_date, end_date, weight, season_version)
  PK(snapshot_id, season_id); FK(store_id, snapshot_id) → utility_snapshot(store_id, snapshot_id)
  -- copy BY VALUE of every season active on the snapshot analysis date; no FK to season (Q8)

utility_weight_v2_conversion (conversion_id PK, subject 'STORE'|'POLICY', store_id NULL FK store, rule_version,
        had_config, old_value JSON NULL, new_value JSON, old_stock/expiry/season coefficients, new_alpha_expiry,
        new_beta_season, default_coefficients, old_lambda, new_lambda, lambda_max, lambda_clamped, converted_at)
  -- migration evidence of the owner conversion rule of 2026-11-15
```

### Changed tables

| Table | Change |
|---|---|
| `inventory_batch` | `push_day DATE NULL`, CHECK `push_day IS NULL OR expiry_date IS NULL OR push_day <= expiry_date`. No backfill: every existing batch has `push_day = NULL` |
| `utility_matrix_item` | `expiry_risk DECIMAL(24,18) NULL` (U_expiry, CHECK 0..1), `season_weight DECIMAL(7,6) NULL` (M, CHECK 0..1), `season_id INT NULL`, FK `(snapshot_id, season_id) → utility_snapshot_season` |
| `utility_matrix_item` CHECK | every existing CHECK covering `expiry_status` is located through the catalog and replaced by `ck_utility_matrix_expiry_status_w2`, which adds `MISSING_PUSH_DAY` and `SLOW_MOVING`. The V5 name is not reused: CHECK names are schema-wide in MySQL and, after the V11 legacy recovery, the archive table may still own `ck_utility_matrix_expiry_status` |
| `store.utility_config` (live) | rewritten to `WeightV2` JSON by the owner rule (see below) |
| `system_configuration['UTILITY_LAMBDA_POLICY']` | rewritten to `{"scoringVersion":"W2_EXPIRY_SEASON_V1","lambdaMax":<old max>}`, `config_version + 1`; lower bound fixed at 0 |

Unchanged on purpose: `product.stock_priority_score`, `product.seasonal_priority_score`, `product.expiry_push_days` (columns kept, unused by calculation); legacy matrix columns `stock_score`, `expiry_score`, `seasonal_score` (NULL for W2 rows). **Never touched:** `utility_snapshot.config_snapshot`, `utility_matrix_item.expiry_evidence`, `mining_request.utility_config_snapshot`, `mining_request.frozen_input`, `recommendation.generation_eligibility`, `recommendation_review_history.eligibility_check`.

### Live config conversion (owner decision 2026-11-15, `WeightV2Conversion.RULE = OWNER_2026_11_15_V1`)

1. λ kept, clamped into `[0, current λ_max]`; clamped Stores have `lambda_clamped = TRUE` in `utility_weight_v2_conversion`.
2. α (expiry) ← old expiry coefficient (`beta` when `enableExpiry`), β (season) ← old seasonal coefficient (`gamma` when `enableSeason`); the stock coefficient is dropped; α = a/(a+b) rounded to 6 decimals HALF_UP, β = 1 − α.
3. Both 0/disabled, or no config: α = β = 0.5.
4. `salesWindowDays = 30`, `version + 1` (a Store with no config counts as version 1 → 2), `scoringVersion = W2_EXPIRY_SEASON_V1`.
5. Invalid JSON, non-object JSON, non-boolean flags, non-numeric or negative coefficients, or a config already on W2 fail the migration (no guessing).

## Stored JSON versions (readers in `UtilityJsonVersions`, `RecommendationJsonVersions`)

| Stored JSON | Legacy record (kept) | W2 record |
|---|---|---|
| `store.utility_config` | `WeightConfig` (`F03_LINEAR_V1`, `EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1`) | `WeightV2` |
| λ policy row | `PolicyRequest` `{lambdaMin,lambdaMax}` | `PolicyPayloadV2` |
| `utility_snapshot.config_snapshot`, `mining_request.utility_config_snapshot` (inside `SnapshotView`) | `FrozenConfiguration` | `FrozenV2` (adds `weightScale`, `weightRoundingMode`, `seasons`) |
| `utility_matrix_item.expiry_evidence` | `ExpiryScoreCalculator.Evidence` | `FefoEvidence` |
| `recommendation.generation_eligibility`, `recommendation_review_history.eligibility_check` | `RecEligibility` (has `expiryPushDays`) | `EligibilityV2` |

Selection is by `scoringVersion` (missing → legacy). An unknown version is rejected; `fail-on-unknown-properties` stays enabled globally, so an unknown field in any version is still rejected.

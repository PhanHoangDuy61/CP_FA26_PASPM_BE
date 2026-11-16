# ROC-20261117-014 — Demo seed for F03 Weight v2 and F06

| Field | Value |
|---|---|
| Record ID | ROC-20261117-014 |
| Date / timezone | 2026-11-17 / Asia/Ho_Chi_Minh |
| Type | Seed script, test, documentation (no Backend source change) |
| Features | F02 batches, F03 Weight v2 (seasons, configuration, snapshots), F05 mining input, F06 eligibility |
| Branch / base | `Khoi/chore/seed-demo-weight-v2`, created from `origin/dev` `705e3d750a3daf416d35f19e67241435f1ab660b` after `git fetch` |
| Execution status | COMPLETED for the scope below |
| Release status | UNRELEASED; local commits only, no push, merge or deployment |
| Daily summary | [2026-11-17](README.md) |

## 1. Request and scope

The demo seed (`scripts/seed/seed_demo.py`) created one undated batch per product and no seasons or weight configuration, so every product had W = 1 and F03 showed nothing. Make the seed exercise Weight v2 (`W = 1 + λ·(α·U_expiry + β·M)`) and F06: expiry dates, push days, seasons, weight configuration; deterministic, idempotent, configurable, with a switch. Only the seed script, its helpers and documentation may change; `src/main`, migrations, `openapi.json`, validation limits and the database (except through the API) stay as they are. Verification only on a disposable MySQL 8.4 container with a backend on another port; the local databases `paspm` and `paspm_dev` were not touched.

## 2. Findings that shaped the design

1. **No seeded product requires expiry.** All 1,559 rows of `FoodMart_Product_Direct_Insert.sql` have `requires_expiry = 0`. The problem statement's "products with `requiresExpiry = true` but an undated batch become `NEEDS_RECHECK`" cannot happen with this data; W = 1 came from no expiry products and no seasons. The seed now creates a deterministic 35% of the products with `requiresExpiry = true` (`SEED_EXPIRY_PRODUCT_PCT`; `0` restores the old behaviour).
2. **Existing batches cannot be dated through the API.** `PATCH /api/inventory-batches/{id}` accepts only `remainingQuantity`, `status` and `pushDay` (`{"expiryDate":…}` → 400), and `PUT /api/products/{id}` with `requiresExpiry = true` → 400 "Cannot require expiry while a batch has no expiry date", also after the batch was blocked and its quantity set to 0 (run on a database seeded without Weight v2). A second `SEED-W2` batch would only help products that already require expiry, and there are none in an old database; on a non-perishable product it has no F03 effect (`NOT_APPLICABLE`) and just adds stock. **Decision:** no `SEED-W2` batch. An old database keeps its undated batches, gets seasons and the weight configuration, and the script warns with the number of products left undated (551). Recreate the database for the full demo. Stock effect of the supported path: perishable batches hold 20–994 units (total 134,397) instead of 1,000 each; total stock 1,142,397 instead of 1,559,000.
3. **The orders are old.** The CSV orders are dated 2026-10-01 to 2026-10-04 (the last day ends at 06:00). With N = 30 the sales window of any date after 2026-11-02 holds no orders: on 2026-11-17 every perishable product is `SLOW_MOVING` and U_expiry is 0 everywhere. N = 48 reaches 2026-10-01 but must grow daily. Snapshots take an explicit `asOfDate`, so 2026-10-05 with the default N = 30 works (see section 5).
4. **One category only.** Season groups cannot follow category names; they are SKU-position blocks (see [scripts/seed/README.md](../../../scripts/seed/README.md)).
5. **P2 needs two active seasons**, so a fourth season (Flash Sale Week, weight 0.8) was added to the three requested (active 0.7, future 0.9, ended 0.6).

## 3. Changes

### Added
- `scripts/seed/weight_v2_plan.py`: pure, deterministic plan (config from environment, per-SKU hash decisions, season definitions and membership). Standard library only.
- `scripts/seed/tests/test_weight_v2_plan.py`: 29 unit tests (determinism, distributions, push-day rules, seasons, configuration validation, forced expiry).
- `scripts/seed/README.md`: data contents, variables, reseeding, date guidance, recommended snapshot range.

### Changed
- `scripts/seed/seed_demo.py`: perishable flag on product creation; dated batches with push day; seasons and assignments (Merchandiser); weight configuration (Merchandiser; System Admin raises `lambdaMax` only when it is lower than λ); `SEED_WEIGHT_V2` switch; summary and record counts for `season` and `product_season`. Existing steps and skip-if-exists behaviour are unchanged.
- `scripts/seed/.env.seed.example`, `docs/setup/STAGING.md` §5: variables, link, corrected "3 days" to the real order period.

No migration, Backend source, `openapi.json`, validation limit or Frontend file changed.

## 4. Files changed

| Path | Change | Reason |
|---|---|---|
| [scripts/seed/seed_demo.py](../../../scripts/seed/seed_demo.py) | Modified | Weight v2 steps |
| [scripts/seed/weight_v2_plan.py](../../../scripts/seed/weight_v2_plan.py) | Added | Deterministic plan |
| [scripts/seed/tests/test_weight_v2_plan.py](../../../scripts/seed/tests/test_weight_v2_plan.py) | Added | Unit tests |
| [scripts/seed/README.md](../../../scripts/seed/README.md) | Added | Seed documentation |
| [scripts/seed/.env.seed.example](../../../scripts/seed/.env.seed.example) | Modified | Optional variables |
| [docs/setup/STAGING.md](../../setup/STAGING.md) | Modified | Seed section |
| [this record](14-demo-seed-weight-v2.md), [day README](README.md), [index](../README.md) | Added / Modified | Record |

## 5. Verification executed in this session

Environment: MySQL 8.4 container (`mysql:8.4`, host port 53307, schema `paspm_seed`), Mailpit container on 18025/11025, backend JAR built from this worktree (Java 17, `./mvnw -DskipTests package`) on port 18080, SPMF 2.67a (SHA-256 `4aa46838…dfe6a`, the pinned one) run by Java 25, throwaway random secrets. Flyway: 26 migrations applied. Seed as-of date 2026-11-17.

| # | Command / method | Actual result |
|---|---|---|
| 1 | `python3 -I -m unittest discover -s scripts/seed/tests -v` | **29 PASS**, 0 FAIL (the first run failed with `ModuleNotFoundError`, then 3 of the 29 failed until `force_expiry` and the Tết name existed) |
| 2 | `seed.sh`-equivalent run on the empty database | exit 0: 1,559 products (551 perishable), 551 dated + 1,008 undated batches, 4 seasons, 1,451 assignments, config version 2, 2 imports COMPLETED (4,141 orders) |
| 3 | Second run, fingerprints of batches, season assignments, seasons, products before and after | **identical**; nothing created |
| 4 | Scores of all 1,559 products, `N = 30` | W > 1: **624**; W ∈ {1.00 (935), 1.08 (421), 1.10 (203)}; `expiryRisk > 0`: **0**; 551 perishable: `SLOW_MOVING` 513, `MISSING_PUSH_DAY` 28, `NO_SELLABLE_BATCH` 10 |
| 5 | Seed rerun with `SEED_WEIGHT_SALES_WINDOW_DAYS=48`, scores again | only the config changed (version 3). W > 1: **853**; W min / median / mean / max 1.00 / 1.08 / 1.067 / 1.28; 706 at 1.00; `expiryRisk > 0`: 381 (148 also in a season); `AVAILABLE` 513; 91 `PUSH_DAY_REACHED`, 28 `MISSING_PUSH_DAY`, 10 `EXPIRED` batch warnings; P2 rule (M = highest active weight) holds for every product |
| 6 | Examples (N = 48 unless stated) | expiry-driven FOODMART-0632: W 1.14, U 0.7705 (192 units, 45 sold in 48 days, push in 47 days), no season. Season-driven FOODMART-0001: W 1.08, M 0.7, `NOT_APPLICABLE`. Two active seasons FOODMART-0422: seasons 0.7 and 0.8, M 0.8, W 1.10. `MISSING_PUSH_DAY` FOODMART-0050: batch without push day, W 1.08 (season only). `SLOW_MOVING` (N = 30) FOODMART-0002: 43 units eligible, 0 sold in the window, U 0. Reached push day FOODMART-0009: U 1.0, W 1.26. Maximum W FOODMART-0443: 1.28 |
| 7 | `GET /api/utility/expiry-push-batches` (as-of today) | **91** due batches (91 products, daysToPush −12…0), 28 `MISSING_PUSH_DAY` warnings |
| 8 | Read-only SQL | perishable products with an ACTIVE positive batch without expiry: **0** (so none can be `NEEDS_RECHECK` for that reason); `push_day > expiry_date`: 0 |
| 9 | Snapshot 2026-10-01..2026-10-04, as-of 2026-11-17, then mining | snapshot READY, total weighted utility 54,013,051.55; `POST /api/mining/requests` → 400 `Lossless converted dataset exceeds SPMF int32 capacity` (no limit changed) |
| 10 | All 10 sub-ranges, as-of 2026-11-17 and 2026-10-05 (System Admin policy `PERCENTAGE 0.2`) | every range of ≥ 2 days rejected (smallest total 22,550,346.15 / 23,313,162.23; limit 21,474,836.47); the four single days accepted |
| 11 | EFIM on the single days | 2026-10-01: COMPLETED, 1,546 patterns, 59,374 output bytes, 178 ms. 2026-10-03: COMPLETED, 69 patterns, 2,171 bytes, 187 ms. 2026-10-02 and 2026-10-04: **FAILED `OUTPUT_LIMIT`** at 0.2% |
| 12 | 2026-10-02 at other minUtil | 0.25%: COMPLETED, **179 patterns, 11,199 bytes, 165 ms**; 0.3%, 0.35%, 0.4%: 0 patterns (100 bytes) |
| 13 | F06 `POST /api/recommendations/generate` on run 7 (2026-10-02, as-of 2026-11-17, 179 patterns) | 324 recommendations, all `ELIGIBLE`; 1,244 perishable product slots, **0 `NEEDS_RECHECK`** |
| 14 | F06 on run 10 (2026-10-02, as-of 2026-10-05, 228 patterns) | 408 recommendations, all `ELIGIBLE`; **all 408 `seasonEnded`** (Back-to-School ended 2026-10-18) |
| 15 | Snapshot 2026-10-02, as-of 2026-10-05, default N = 30 | READY, total 17,919,927.61; W > 1 for 474 of 1,527 products, W 1.00–1.24, `expiryRisk > 0` for 221, 29 `MISSING_PUSH_DAY` |
| 16 | `SEED_WEIGHT_V2=0` on a second empty schema (backend 18081) | exit 0; SQL: 0 perishable products, 0 dated batches, 0 seasons; the summary says `weight v2: skipped`; 1,559 undated batches of 1,000 (old behaviour). The weight configuration was not queried on that schema |
| 17 | Same schema, new seed afterwards (old-style database) | seasons 4 and assignments 1,451 created, config v2 set, warning "551 products would be perishable … recreate the database"; API attempts to date the batches answered 400 as in section 2 |
| 18 | System Admin `lambdaMax` set to 0.2, seed with `SEED_WEIGHT_LAMBDA=0.4`, then default seed | policy raised to 0.4 and config λ = 0.4 (version 4); the default rerun restored λ = 0.3 (version 5) |

## 6. NOT RUN

- The Maven test suite and CI (no Backend source changed).
- The seed on the compose or staging stack, with a real SMTP server, or on Windows.
- Authentic FoodMart acceptance; any Frontend screen.
- `SEED_AS_OF` other than 2026-11-17 against a running backend (covered only by unit tests: dates keep their offsets).
- Percentages of the actual distribution against other random seeds; the unit tests check tolerances only.
- Whether EFIM accepts a 2-day range if λ is 0 (base utility of 2026-10-03..04 was not computed; it is estimated to stay above the limit).

## 7. Known limitations

- **int32 capacity (record [013](13-f05-dense-acceptance.md))**: only single-day snapshots (maximum 21,474,836.47 total weighted utility) can be mined; recommended range 2026-10-02 with minUtil `PERCENTAGE 0.25`, as-of 2026-10-05. The data is dense: 0.2% exceeds the 2,000-pattern cap and 0.3% returns nothing.
- The orders are fixed (2026-10-01..04); the live preview needs `SEED_WEIGHT_SALES_WINDOW_DAYS` ≥ days since 2026-10-01 + 1 to show expiry risk (48 on 2026-11-17). Re-dating the transaction files relative to `SEED_AS_OF` would remove this, but changes the shipped data and was not done.
- An old database cannot be upgraded to dated batches; recreate it.
- Product groups for seasons are SKU blocks, not business categories. Output bytes are not a field of the run view; they are read from `environment` (`outputBytes=…`).
- A seed run uses about 6–10 logins and the backend allows 30 per IP per 15 minutes.
- Running the unit tests creates `scripts/seed/__pycache__` locally; it was deleted before committing (`PYTHONDONTWRITEBYTECODE=1` avoids it).

## 8. Delivery

Two local commits on `Khoi/chore/seed-demo-weight-v2`; not pushed or merged. The disposable containers, backends, secrets file and scratch files were removed after verification.

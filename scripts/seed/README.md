# Demo seed

`seed_demo.py` fills an **empty** PASPM database through the public REST API (the only direct SQL is one `INSERT INTO store`, because no endpoint creates a store). `seed.sh` loads `.env.seed` and runs it. Setup steps for the staging stack are in [STAGING.md §5](../../docs/setup/STAGING.md); the variables are listed in [.env.seed.example](.env.seed.example).

```bash
cp scripts/seed/.env.seed.example scripts/seed/.env.seed && chmod 600 scripts/seed/.env.seed
$EDITOR scripts/seed/.env.seed     # SEED_SYSADMIN_* must equal BOOTSTRAP_*; set SEED_ACCOUNT_PASSWORD
scripts/seed/seed.sh
```

## What the demo data contains

| Part | Created through | Content |
|---|---|---|
| Accounts | Admin invitation, self-registration (OTP read from Mailpit) | System Admin (bootstrap account), Store Admin, Merchandiser, End Customer |
| Catalogue | Store Admin API | 1 category, 1,559 FoodMart products |
| Inventory | Store Admin API | one batch `SEED-INITIAL` per product; **551 products (35%) require expiry and carry an expiry date and usually a push day** |
| Transactions | Store Admin CSV import | 4,141 orders, 18,319 lines, dated **2026-10-01 to 2026-10-04** (the last day ends at 06:00) |
| Seasons | Merchandiser API | 4 seasons, 1,451 product assignments (below) |
| Weight configuration | Merchandiser API | α = 0.6, β = 0.4, λ = 0.3, N = 30 days |

Everything below "Weight v2" can be skipped with `SEED_WEIGHT_V2=0`; the seed then behaves as it did before Weight v2 (no product requires expiry, undated batches of 1,000, no seasons, no weight configuration).

### Why 35% of the products are marked as requiring expiry

`FoodMart_Product_Direct_Insert.sql` marks **all 1,559 products as not requiring expiry** (`requires_expiry = 0`), so F03 U_expiry is always 0 for them and F06 never evaluates expiry. The seed therefore creates a deterministic share (`SEED_EXPIRY_PRODUCT_PCT`, default 35) of the products with `requiresExpiry = true`, decided per SKU by a hash of the random seed. The SQL file itself is only parsed and never changed. Set `SEED_EXPIRY_PRODUCT_PCT=0` to keep every product non-perishable.

### Expiry dates, push days and quantities (perishable products only)

All dates are offsets from `SEED_AS_OF` (default: today in Asia/Ho_Chi_Minh). Observed on the verification database with `SEED_AS_OF=2026-11-17` (551 batches):

| Property | Rule (default) | Observed |
|---|---|---:|
| Expires in 0–7 days | 15% (`SEED_EXPIRY_SOON_PCT`) | 69 (12.5%) |
| Expires in 8–30 days | 25% (`SEED_EXPIRY_MID_PCT`) | 127 (23.0%) |
| Expires in 31–180 days | the rest | 355 (64.4%) |
| Push day = expiry − 7…14 days | most batches | 499 |
| Push day already reached (`pushDay` = as-of date − 0…10 days) | 5% (`SEED_PUSH_REACHED_PCT`); batches that expire within about two weeks reach it naturally as well | 99 batches have a push day ≤ as-of date |
| No push day (`MISSING_PUSH_DAY`) | 5% (`SEED_PUSH_MISSING_PCT`) | 30 |
| Initial quantity | log-uniform between `SEED_EXPIRY_QTY_MIN_PCT` (2%) and 100% of `SEED_BATCH_QUANTITY` (1,000) | 20 – 994, mean 244 |

`pushDay` is never after the expiry date (the API rejects it otherwise; the test suite checks it). Batches expiring **today** (expiry = `SEED_AS_OF`) are not sellable; they appear as `EXPIRED` in the score evidence and as `NO_SELLABLE_BATCH` (10 products). Non-perishable products keep an undated batch of `SEED_BATCH_QUANTITY`. The varied quantity is deliberate: with the same 1,000 units every batch would be at risk and U_expiry would be about 1 for all of them. Total stock is 1,142,397 units (perishable 134,397) instead of 1,559,000.

### Seasons and product groups

The FoodMart data has **one category** and generic product names ("FoodMart Item 0001"), so category names cannot define groups. A group is a block of products in SKU order (position `i/N`). Weights are configurable.

| Season | State on `SEED_AS_OF` | Dates (offset from `SEED_AS_OF`) | Weight | Product block | Products |
|---|---|---|---:|---|---:|
| Demo: Year-end Sale | active | −14 … +45 days | 0.7 (`SEED_SEASON_ACTIVE_WEIGHT`) | first 30% | 468 |
| Demo: Flash Sale Week | active, overlaps the one above | −3 … +10 days | 0.8 (`SEED_SEASON_OVERLAP_WEIGHT`) | 27% – 40% | 203 |
| Demo: Tết 2027 | future | +55 … +85 days (2027-01-11 … 2027-02-10 on 2026-11-17) | 0.9 (`SEED_SEASON_FUTURE_WEIGHT`) | 40% – 70% | 468 |
| Demo: Back-to-School (ended) | ended | −75 … −30 days | 0.6 (`SEED_SEASON_ENDED_WEIGHT`) | 65% – 85% | 312 |

47 products belong to both active seasons (rule P2: the highest weight, 0.8, applies; the lower season is still listed in `activeSeasons`). The fourth season exists because P2 needs two *active* seasons. Season dates are relative, so a season that already exists keeps its dates when `SEED_AS_OF` changes; the script reports that.

### Weight configuration

`PUT /api/utility/config` as Merchandiser: α = `SEED_WEIGHT_ALPHA` (0.6), β = `SEED_WEIGHT_BETA` (0.4), λ = `SEED_WEIGHT_LAMBDA` (0.3), N = `SEED_WEIGHT_SALES_WINDOW_DAYS` (30). α + β must equal 1. The System Admin λ policy (`lambdaMax`, default 0.5) is left alone unless it is lower than λ; then the seed raises it to λ and says so.

## Environment variables (Weight v2 part)

| Variable | Default | Meaning |
|---|---|---|
| `SEED_WEIGHT_V2` | `1` | `0`/`false` skips the whole Weight v2 part |
| `SEED_AS_OF` | today (Asia/Ho_Chi_Minh) | Reference date for expiry, push day and season dates (`YYYY-MM-DD`) |
| `SEED_RANDOM_SEED` | `paspm-demo-weight-v2` | Seed of the per-SKU hashes; same seed and date give identical data |
| `SEED_EXPIRY_PRODUCT_PCT` | 35 | Share of products with `requiresExpiry = true` |
| `SEED_EXPIRY_SOON_PCT` / `SEED_EXPIRY_MID_PCT` | 15 / 25 | Expiry within 0–7 / 8–30 days; the rest 31–180 (sum ≤ 100) |
| `SEED_PUSH_REACHED_PCT` / `SEED_PUSH_MISSING_PCT` | 5 / 5 | Push day already reached / no push day (sum ≤ 100) |
| `SEED_BATCH_QUANTITY` | 1000 | Quantity of undated batches; maximum of perishable batches |
| `SEED_EXPIRY_QTY_MIN_PCT` | 2 | Smallest perishable batch as % of `SEED_BATCH_QUANTITY` |
| `SEED_WEIGHT_ALPHA`, `SEED_WEIGHT_BETA` | 0.6, 0.4 | Expiry and season coefficients (sum 1) |
| `SEED_WEIGHT_LAMBDA` | 0.3 | λ; must not exceed `lambdaMax` unless the seed may raise it |
| `SEED_WEIGHT_SALES_WINDOW_DAYS` | 30 | N, 1–365 |
| `SEED_SEASON_ACTIVE_WEIGHT`, `…_OVERLAP_WEIGHT`, `…_FUTURE_WEIGHT`, `…_ENDED_WEIGHT` | 0.7, 0.8, 0.9, 0.6 | Season weights, 0–1 |

Invalid values stop the script before anything is created.

## Running it again, and reseeding

- **Running twice is safe.** Every step looks for its record first. A second run on the verification database created nothing; a fingerprint of batches, season assignments, seasons and products was identical before and after. Changing a Weight v2 setting and rerunning changes only the weight configuration (a new configuration version, no duplicates).
- **Reseeding for a clean demo: recreate the database** (for the staging stack, `docker compose … down -v` and start again, or drop and recreate the schema), start the backend so Flyway runs and the bootstrap account is created, then run `seed.sh`. This is the supported way to apply Weight v2 to a database seeded earlier.
- **A database seeded before Weight v2 cannot get expiry dates through the API.** The batch PATCH accepts only `remainingQuantity`, `status` and `pushDay` (no `expiryDate`), and `PUT /api/products/{id}` with `requiresExpiry = true` answers 400 "Cannot require expiry while a batch has no expiry date", even after the batch is blocked and emptied (checked on a real database). The seed therefore keeps the existing undated `SEED-INITIAL` batches, still creates the seasons and the weight configuration (seasons give W > 1 for 624 products), and prints a warning with the number of products that stay undated (551). It does **not** add a second `SEED-W2` batch: such a batch on a product that does not require expiry has no F03 effect (`NOT_APPLICABLE`) and would only add stock.
- A seed run uses roughly 6–10 logins; the backend limits logins to 30 per IP per 15 minutes, so several runs in a row can answer 429.

## Which date to analyse (important)

The seeded orders are from **2026-10-01 to 2026-10-04**, but the live preview (`GET /api/utility/products/{id}/scores`) and `SEED_AS_OF` use **today**. With the default N = 30 the sales window of a date after 2026-11-02 contains **no orders**, so every perishable product is `SLOW_MOVING` and U_expiry is 0 (only season weights show). Choose one:

- Create snapshots with an `asOfDate` inside the order period, for example **2026-10-05** (the 30-day window then contains all orders). Verified: 221 products with expiry risk, W up to 1.24.
- Or keep the live preview meaningful with `SEED_WEIGHT_SALES_WINDOW_DAYS=48` on 2026-11-17 (the window reaches 2026-10-01); this must grow by one each day, so it is only a stop-gap. Verified: 381 products with U_expiry > 0, W from 1.00 to 1.28, 706 products at W = 1.

## Recommended demo snapshot range for F03 → F05

F05 rejects a snapshot whose converted dataset does not fit SPMF int32: `Lossless converted dataset exceeds SPMF int32 capacity`. With this data the GCD divisor is 10,000, so the converted total is the weighted utility × 100 and must stay at or below 2,147,483,647, i.e. a total weighted utility of **at most 21,474,836.47**. No limit was changed. Every range of two or more days exceeded it (smallest: 2026-10-03 to 2026-10-04, 23,313,162.23 at as-of 2026-11-17 and 22,550,346.15 at as-of 2026-10-05); every single day fits.

| Range (rangeStart = rangeEnd) | Orders | Distinct products | Total weighted utility (as-of 2026-11-17 / 2026-10-05) |
|---|---:|---:|---:|
| 2026-10-01 | 900 | 1,444 | 12,078,358.62 / 11,706,710.28 |
| **2026-10-02** | 1,440 | 1,527 | 18,621,530.70 / 18,028,397.51 |
| 2026-10-03 | 1,440 | 1,530 | 18,499,160.89 / 17,900,432.42 |
| 2026-10-04 | 361 | 1,002 | 4,814,001.34 / 4,649,913.73 |

**Recommended: `rangeStart = rangeEnd = 2026-10-02`, `asOfDate = 2026-10-05`.** The System Admin minUtil policy matters because the data is dense: for 2026-10-02 at 0.2% EFIM exceeded the 2,000-pattern cap (`OUTPUT_LIMIT`), at 0.25% it completed with 179 patterns, at 0.3%, 0.35% and 0.4% it returned 0 patterns (higher values were not tried). A policy of `PERCENTAGE 0.25` works for 2026-10-02 (179 patterns, 11,199 output bytes, 165 ms); 2026-10-03 completed at 0.2% with 69 patterns. See [record 014](../../docs/record-of-changes/2026-11-17/14-demo-seed-weight-v2.md) for the complete run results.

## Tests

The date, push-day, quantity, season and configuration rules are pure functions in `weight_v2_plan.py`. Their unit tests need no backend:

```bash
python3 -I -m unittest discover -s scripts/seed/tests -v
```

The remaining script (HTTP calls) is covered by the verification runs in record 014, not by automated tests.

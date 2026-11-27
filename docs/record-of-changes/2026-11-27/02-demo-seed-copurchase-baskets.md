# ROC-20261127-002 — Demo seed: co-purchase baskets for F06 BUNDLE, CROSS_SELL and FBT

| Field | Value |
|---|---|
| Date / timezone | 2026-11-27 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Sonnet 5.5), requested by Nguyễn Minh Khôi |
| Type | Demo seed data (no product code, no scoring change, no migration, no API change) |
| Features | F05 mining and F06 recommendations (demo data); TDP-01 unchanged |
| Branch / base | `Khoi/fix/demo-seed-copurchase` / `origin/dev` @ `f1bc091` |
| Decision | None new: data only. The F06 rules (`F06_RULES_V1_1`, default policy) and the F05 conversion rules (DEC-20261119-05) are untouched. Source of the finding: live smoke test 2026-11-27, defect #1 |
| Release | UNRELEASED |
| Daily index | [2026-11-27](README.md) |

## Why

The demo seed imports 4,141 random FoodMart orders: no product pair is bought together more often than chance. At the documented demo policy (`PERCENTAGE 0.12`, full range 2026-10-01..04, as of 2026-10-05) EFIM returned 180 patterns, **all of size 1**, so F06 could only build BANNER candidates; BUNDLE, CROSS_SELL and FBT stayed empty and the demo of F06 review, F07 layout with bundle blocks and F10 bundle add had nothing to show. The black-box precondition DP-05 ("at least two BUNDLE approved") could not be met on the seed.

## What changed

- **New data file** `scripts/seed/data/Transactions_F04_Demo_Copurchase.csv`: 635 orders, 1,613 lines (ids `FMCOP-000001…`, status `COMPLETED`, catalogue prices, same per-day time window as the base data, spread over the four days in proportion to the base orders). Four disjoint product groups are bought together:

  | Group | FoodMart codes | Share of all orders (full range) | Candidates it gives |
  |---|---|---:|---|
  | F1 frequent pair | 0212, 1219 | 5.65% | BUNDLE, FBT, CROSS_SELL |
  | F2 frequent triple | 1557, 1513, 0750 | 5.59% | BUNDLE (pairs and the triple), FBT, CROSS_SELL |
  | O1 occasional quad | 0041, 0092, 0551, 0151 | 0.90% | BUNDLE (sizes 2, 3 and 4), CROSS_SELL |
  | O2 occasional pair | 1342, 1525 | 0.67% | BUNDLE, CROSS_SELL |

- **Why these shares.** FBT needs the whole pattern in at least 2 baskets and in at least **5% of all baskets of the snapshot**, so the frequent groups are in 5.2–5.8% of the orders of the full range and of every single day (checked per day by a unit test). CROSS_SELL needs confidence ≥ 0.50 per anchor: the group products appear in at most 10 base lines each, so confidence is 0.76–0.96. The occasional groups stay under 1%, so they correctly give no FBT. The groups are non-perishable and `ACTIVE`, so the candidates stay `ELIGIBLE`.
- **Matrix limit.** A snapshot may hold at most 20,000 matrix rows. The full range had 18,319 and now has 19,932, so the baskets hold only the group products (no filler orders), and a unit test fails if the total passes 19,950.
- **Generator** `scripts/seed/generate_copurchase_baskets.py` (stdlib, deterministic; `--check` fails when the committed file differs). The base files `Part1`/`Part2` are unchanged (a unit test pins their SHA-256).
- **Seed** `seed_demo.py`: `CSV_FILES` gets the third file. It is imported like the others: a copy with server SKUs, imported once (a rerun says "skipped, already imported"). A database seeded before this change gets only the new file on the next run.
- **Docs**: SRS §11 item 4, BRD_VISION (risk row and open question 9), TEST_PLAN (ENV-1 step 3, DP-03, DP-05), STAGING.md, and the seed README (new "Co-purchase baskets" section; the demo snapshot range and policy section is rewritten with the measured table; its old claim that every multi-day range exceeds int32 is gone, because the lossless-first rule of DEC-20261119-05 handles it).
- **Not changed:** no Java, no migration, no OpenAPI, no F06 policy, no `application.yml`. The seed does not set the minUtil policy (a System Admin setting) or the F06 policy.

## Files

| Path | Change |
|---|---|
| `scripts/seed/data/Transactions_F04_Demo_Copurchase.csv` | Added (generated) |
| `scripts/seed/generate_copurchase_baskets.py` | Added |
| `scripts/seed/tests/test_copurchase_baskets.py` | Added (15 tests) |
| `scripts/seed/seed_demo.py` | `CSV_FILES` + one file; docstring |
| `scripts/seed/README.md` | Baskets section, measured recipe table, counts |
| `docs/product/SRS.md` | §11 item 4 |
| `docs/product/BRD_VISION.md` | Risk row (minUtil) and open question 9 |
| `docs/testing/blackbox/TEST_PLAN.md` | ENV-1 step 3, DP-03, DP-05 |
| `docs/setup/STAGING.md` | Seeded order count |
| `docs/record-of-changes/2026-11-27/02-…`, `README.md`, `docs/record-of-changes/README.md` | This record and its indexes |

The earlier measurements in DEC-20261119-05, ROC-20261119-003, `docs/testing/F05_FOODMART_SCALABILITY_BENCHMARK.md` and `F08_LAYOUT_SIZE_SENSITIVITY_20261119.md` describe the seed as it was on their dates and are left as they are.

## Verification

**Seed unit tests (no backend):** `python3 -I -m unittest discover -s scripts/seed/tests` ran **64 tests, 0 failures** (49 existing + 15 new). The new module checks: the committed file equals the generator output; the base files' hashes; header, CRLF, ids, catalogue prices, one timestamp per order inside the base window of its day; the groups are disjoint, 2–4 products, `ACTIVE` and non-perishable; the exact F06 arithmetic (FBT ≥ 2 and ≥ 5% on the full range and on each day for F1 and F2; below 5% for O1 and O2; CROSS_SELL ≥ 0.50 for every anchor); at most 19,950 matrix rows.

**Isolated stack** (compose project `paspm_smoke`, MySQL 8.4 on 127.0.0.1:3307, Mailpit 1026/8026, backend jar built from this worktree on port 8080 with Java 17 and SPMF 2.67a on Java 25, throwaway secrets in `/tmp` outside the repository; the user's own databases and containers were not touched; the whole stack and its volume were removed afterwards). Raw logs: `~/paspm-verification-evidence/2026-11-27-seed-copurchase/` (outside the repository).

| Step | Result |
|---|---|
| Fresh database, `seed.sh` run 1 | exit 0: 1,559 products, 3 imports COMPLETED (9,999 + 8,320 + 1,613 rows; 2,272 + 1,869 + 635 orders, 0 rejected, 0 duplicate), `sales_transaction` **4,776**, `transaction_item` **19,932** |
| `seed.sh` run 2 | exit 0, creates nothing (all three imports "skipped, already imported"); the record counts are identical |
| TDP-01 | run 1 stopped on the first invitation with `503 Email delivery unavailable; retry later` (the cold-start e-mail defect from the smoke test, reproduced after a backend restart); run 2 created the 5 missing items; run 3: **0 created, 6 already present**. TDP-01 itself is unchanged and recovers from the interrupted run |
| Snapshot full range 2026-10-01..04, as of 2026-10-05 | READY, total weighted utility 54,626,020.11, 19,932 matrix rows |
| Mining at the System Admin policy **`PERCENTAGE 0.12`** | COMPLETED, **182 patterns** (size 1: 165, 2: 11, 3: 5, 4: 1), 520 ms, `LOSSLESS` |
| F06 generate (BUNDLE, CROSS_SELL, FBT, BANNER) on that run | **17 BUNDLE** (sizes 2: 11, 3: 5, 4: 1; at most **5 pairwise disjoint**), **41 CROSS_SELL**, **5 FBT**, 182 BANNER; all `ELIGIBLE` |

Sensitivity of the same snapshot (counts of BUNDLE / CROSS_SELL / FBT stay 17 / 41 / 5 from 0.14 to 0.10):

| minUtil | Patterns | Notes |
|---|---:|---|
| 0.25 | 22 | 16 / 39 / 5 |
| 0.14 | 100 | 17 / 41 / 5 |
| **0.12** | **182** | 17 / 41 / 5 |
| 0.10 | 316 | 17 / 41 / 5 |
| 0.09 | 419 | 18 / 41 / 5 (adds patterns of size 11–14 from the largest base orders; F06 skips them for BUNDLE) |
| 0.08 | 862 | 18 / 41 / 5, still below the 2,000 limit |

Other ranges (as of the range end or 2026-10-05): S1 = 2026-10-01..03 as of 2026-10-03 at 0.12: 183 patterns, 17 / 41 / 5; single day 2026-10-02 at 0.25: 111 patterns, 16 / 116 / 5; single day 2026-10-02 at 0.12: `FAILED OUTPUT_LIMIT` (as before the change).

**Before the change, same stack and method (2026-11-27, original seed):** full range at 0.12: 180 patterns, all of size 1 (BANNER only); at 0.10: 326 patterns; single day 2026-10-02 at 0.25: 240 patterns; at 0.12: `OUTPUT_LIMIT`. S1 at 0.12: 187 patterns, all of size 1.

**CI backend group:** `./mvnw -B clean verify -Dtest="$(python3 .github/scripts/test-groups.py backend)"` with Java 17 (Temurin 17.0.21) ran **496 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS (H2; the same count as ROC-20261127-001, because no Java test class was added or changed).

**NOT RUN:** the MySQL group and the integration job locally (they need a disposable MySQL 8.4 with client tools and SPMF on Java 25; the pull request's CI runs them, and no Java code changed). No frontend walk-through of the new candidates (layout seeding, bundle add) was done in this round.

## Limitations

- The 20,000-row matrix limit leaves about 70 rows of room on the full range; more demo orders need a smaller base or a different recipe.
- The demo policy `PERCENTAGE 0.12` still has to be set by the System Admin (owner action); the seed does not set it.
- The products have generic names ("FoodMart Item NNNN"), so the groups are not meaningful food pairings. The FBT 5% rule is the default policy of the F06 rules, not a seed choice.
- Product choice depends on the default Weight v2 plan (the groups are non-perishable under the default `SEED_RANDOM_SEED`); a different seed or `SEED_EXPIRY_PRODUCT_PCT` could make a group product perishable, which a unit test reports for the defaults only.

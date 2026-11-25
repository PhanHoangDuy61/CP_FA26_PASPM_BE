# ROC-20261125-004 — Server-generated SKU: demo seed, FoodMart mapping and JpaPilotMigrationMySqlTest

| Field | Value |
|---|---|
| Record ID | ROC-20261125-004 |
| Date / timezone | 2026-11-25 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Opus 5.5), requested by Nguyễn Minh Khôi |
| Type | Bugfix / Documentation |
| Features | F02 (SKU), F04 (import data), demo seed |
| Branch / base HEAD | `Khoi/fix/sku-foodmart-seed`, from `origin/dev` @ `82b0ced` |
| Execution status | COMPLETED |
| Release status | PR_OPEN pending (no PR opened by the agent) |
| Daily summary | [README](README.md) |

## 1. Request and scope

Fix the consequences of server-generated SKUs (`24b9b01`) so the demo seed and the FoodMart dataset work again and `JpaPilotMigrationMySqlTest` passes. PO answers at Checkpoint 1 (2026-11-25): option A (seed-side mapping, no schema change); keep server SKUs and record the 2026-11-19 decision ([DEC-20261119-07](../../decisions/2026-11-19/DEC-20261119-07-server-generated-product-sku.md)); keep existing `FOODMART-xxxx` SKUs without migration; fix the test and register it; new demo push-day mix.

## 2. Change details

### Fixed
- `scripts/seed/seed_demo.py`: products are created without `sku` and with `confirmSimilarName: true`, found again by exact name `FoodMart Item NNNN`; the server SKU is read back into `mapping.csv`; batches are created without `batchCode` and skipped when the product already has a batch; transaction CSVs are imported from import-ready copies; season groups follow the FoodMart code order from the mapping.
- `src/test/java/vn/edu/fpt/paspm/persistence/JpaPilotMigrationMySqlTest.java:82,87`: no `sku` in the payload; asserts `SKU-%011d` of the product ID.

### Added
- `scripts/seed/foodmart_mapping.py` (mapping file, CSV rewrite that changes only the `sku` column) and `scripts/seed/tests/test_foodmart_mapping.py`.
- `SEED_OUTPUT_DIR` (default `scripts/seed/out/`, git-ignored), `SEED_PUSH_LEAD_MIN_DAYS` / `SEED_PUSH_LEAD_MAX_DAYS`, `SEED_EXPIRY_EDGE_PCT`.
- [DEC-20261119-07](../../decisions/2026-11-19/DEC-20261119-07-server-generated-product-sku.md).

### Changed
- `scripts/seed/weight_v2_plan.py`: push days ~60% set (expiry − 3…21 days), ~15% already reached, ~25% missing; no `expiry − 7` rule; ~3% of perishable batches expire today or expired yesterday. Tests updated.
- `.github/scripts/test-groups.py`: `JpaPilotMigrationMySqlTest` added to the `mysql` group.

### Repository hygiene
- `scripts/seed/__pycache__/weight_v2_plan.cpython-314.pyc` removed from git (`git rm --cached`); `.gitignore` now ignores `__pycache__/` and `*.pyc`.

### Documentation
- Local setup: MySQL must run with `--log-bin-trust-function-creators=1` (V11 creates triggers; Error 1419 otherwise). New section in `docs/setup/LOCAL_SETUP_COMMON.md` with the `compose.override.yml` snippet and `docker run` snippets for macOS/Linux and Windows, pointers in `LOCAL_SETUP_MACOS.md`, `LOCAL_SETUP_WINDOWS.md`, a row in `LOCAL_SETUP_TROUBLESHOOTING.md`, and a note in `README.md`.
- `SRS.md` FR-02.1 and §11 items 5–6, `GLOSSARY.md` (Product), `BRD_VISION.md` §8 (`:158`) and §11 (`:214`), both `paspm-context` skill copies, `scripts/seed/README.md` (also the DEC-20261124-01 note), `scripts/seed/.env.seed.example`.

### Database, Security
N/A. No migration, no API change.

## 3. Files changed

| Path | Change | Reason |
|---|---|---|
| `scripts/seed/seed_demo.py`, `weight_v2_plan.py`, `foodmart_mapping.py` | Modified / Added | Seed against server SKUs; push-day mix |
| `scripts/seed/tests/*` | Modified / Added | Plan and mapping tests |
| `scripts/seed/README.md`, `.env.seed.example`, `.gitignore` | Modified | Output location, variables, observed data |
| `src/test/.../JpaPilotMigrationMySqlTest.java`, `.github/scripts/test-groups.py` | Modified | Test fix and CI registration |
| `docs/decisions/2026-11-19/DEC-20261119-07-server-generated-product-sku.md` | Added | Decision record |
| `.gitignore`, `scripts/seed/__pycache__/*.pyc` | Modified / Deleted from index | Stop tracking bytecode |
| `README.md`, `docs/setup/LOCAL_SETUP_{COMMON,MACOS,WINDOWS,TROUBLESHOOTING}.md` | Modified | MySQL trigger option for local setup |
| `docs/product/{SRS,GLOSSARY,BRD_VISION}.md`, `.claude`/`.agents/skills/paspm-context/SKILL.md` | Modified | Server-generated SKU |
| `docs/record-of-changes/2026-11-25/04-sku-foodmart-seed.md`, `README.md`, `../README.md` | Added / Modified | This record |

## 4. API and compatibility impact

No API, schema or business-rule change in code. The SKU rule in the product docs now matches the code (DEC-20261119-07). Databases seeded before 2026-11-20 keep `FOODMART-xxxx` SKUs; for them the mapping is the identity. `paspm-experiments` (RQ1) reads the SPMF file with numeric item IDs and is not affected; the original CSV/SQL files are unchanged.

## 5. Verification executed in this session

| Command / method | Environment | Actual result | Evidence |
|---|---|---|---|
| `python3 -I -m unittest discover -s scripts/seed/tests` before the change | macOS, Python 3 | FAIL as expected: 7 failures (push-day), then 1 error (`foodmart_mapping` missing) | local console |
| Same after the change | same | PASS — 41 tests | local console |
| `sh mvnw -B test -Dtest=JpaPilotMigrationMySqlTest -Dpaspm.test.mysql=true` on the original test | Temurin 17.0.21.1, disposable `mysql:8.4` (8.4.13) | FAIL as expected — `:84 expected 201 CREATED but was 400 BAD_REQUEST` | local console |
| Same after the fix | same | PASS — 4 tests | local console |
| Seed run 1 (`scripts/seed/seed.sh`) on an empty schema; backend jar of this branch; disposable `mysql:8.4` with `--log-bin-trust-function-creators=1` (as `compose.staging.yml`) and disposable Mailpit | macOS, Temurin 17, `SEED_AS_OF=2026-11-25` | PASS (81 s): Store, 3 accounts, 1 category, 1,559 products (all SKUs `SKU-%011d` of their ID), 1,559 batches (551 with expiry, 426 with push day, 125 without), import Part1 9,999 rows / 2,272 orders, Part2 8,320 rows / 1,869 orders, **0 rejected, 0 duplicate, 0 conflict**, 4 seasons, 1,451 assignments, weight config v2 | console summary |
| Batch API spot check | same | 123/123 non-expired batches without push day show `MISSING_PUSH_DAY`; 6 expired yesterday `EXPIRED`, `daysToExpiry -1`; 16 expiring today `ACTIVE`, `0` | console |
| Generated files | same | Only the `sku` column differs from the originals (`cut`/`cmp`); originals unchanged; `scripts/seed/out/` is git-ignored | console |
| Seed run 2 (idempotency) | same | PASS (2 s): nothing created, imports skipped; SHA-256 fingerprint of products, batches, seasons, assignments, imports, transactions, users, Store, category identical; `mapping.csv` identical | console |
| `sh mvnw -B clean verify -Dtest="$(python3 .github/scripts/test-groups.py backend)"` | Temurin 17.0.21.1, H2 | PASS — 441 tests, 0 failures, 0 errors, 0 skipped (65 classes) | local console |
| `sh mvnw -B clean verify -Dtest="$(python3 .github/scripts/test-groups.py mysql)" -Dpaspm.test.mysql=true` | Temurin 17.0.21.1, disposable `mysql:8.4` 8.4.13, host MySQL 8.4 client | PASS — 105 tests, 0 failures, 0 errors, 0 skipped (15 classes) | local console |
| `docker compose config` with a temporary `compose.override.yml` (removed afterwards) | local Docker Compose | PASS — the `mysql` service gets `--log-bin-trust-function-creators=1` and the guides' `docker compose up -d mysql mailpit` is unchanged | local console |
| `docker compose -f compose.yml -f compose.staging.yml config` | local Docker Compose | FAILS: `service "backend" has neither an image nor a build context` (the staging file needs `compose.backend.yml`), hence the override file in the docs | local console |
| `docker run … mysql:8.4 --log-bin-trust-function-creators=1` | MySQL 8.4.13 | Used for the seed run and the mysql group; V1→V28 migrated as user `paspm`. Without the flag V11 failed with Error 1419 | local console |
| Full `integration` suite | — | NOT RUN | — |

All containers used (`paspm-sku-mysqlgroup`, `paspm-sku-e2e-mysql`, `paspm-sku-e2e-mail`) were removed; no other container was touched.

## 6. Previous evidence referenced

Record 014 (2026-11-17) for the earlier seed design and demo minUtil results; not re-run here.

## 7. Limitations and next actions

- A teammate who already created a local database that failed at V11 may have a half-applied migration; the docs point to the disposable-database reset and say to ask otherwise. That recovery was not tested here.
- `compose.override.yml` is not git-ignored; the docs say not to commit it.
- `compose.yml` alone still needs `--log-bin-trust-function-creators=1` for V11 (known, see `docs/setup/STAGING.md`).
- The demo minUtil figures in the seed README were measured on 2026-11-17 data and not re-measured with the new push-day mix (pattern counts do not depend on push days, utilities may).
- `docs/record-of-changes/2026-11-25/README.md` is also created on other open branches (records 001–003); expect an add/add conflict, resolved by listing all records.

## 8. Delivery

Committed and pushed as branch `Khoi/fix/sku-foodmart-seed`; PR not opened. No merge, no deploy.

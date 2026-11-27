# ROC-20261127-005 — F05 request history: no more "Out of sort memory"

| Field | Value |
|---|---|
| Date / timezone | 2026-11-27 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Sonnet 5.5), requested by Nguyễn Minh Khôi |
| Type | Bugfix (HIGH, found in the pre-freeze walk-through). No migration, no API or contract change |
| Features | F05 mining request history (`GET /api/mining/requests`) |
| Branch / base | `Khoi/fix/f05-history-sort-memory` / `origin/dev` @ `c10acd0` |
| Decision | None (no business rule). Cause surfaced by the co-purchase seed, [ROC-20261127-002](02-demo-seed-copurchase-baskets.md) |
| Release | UNRELEASED |
| Daily index | [2026-11-27](README.md) |

## Root cause

`MiningRepository.requests()` ran `SELECT * FROM mining_request WHERE store_id=? ORDER BY request_id DESC LIMIT ? OFFSET ?`. MySQL sorts such rows in its sort buffer, and every row carries `frozen_input` (the whole SPMF input, JSON) and `utility_config_snapshot`. With the PR #50 seed a request's `frozen_input` is about 280,000 bytes, more than the default `sort_buffer_size` of 262,144, so the sort could not run: **MySQL error 1038 "Out of sort memory, consider increasing server sort buffer size"**, surfaced as HTTP 500 on the F05 history screen. H2 has no sort buffer, so none of the H2 suites could see it.

## What changed

- `MiningRepository.requests(...)` selects **only `request_id`** with `ORDER BY request_id DESC LIMIT ? OFFSET ?` and then loads each row by id with the existing `find(store,id)`, the same pattern as `snapshots()` in the same file. Only ids are sorted. The response, order, paging and total are the same; each item is built by the same code as `GET /api/mining/requests/{id}`.
- New MySQL test `MiningRequestListSortMemoryMySqlTest` (registered in the CI `mysql` group of `.github/scripts/test-groups.py`).
- `docs/api/API_CHANGELOG.md`: "no contract change" entry. No other doc states the old behaviour.

## Test first

`MiningRequestListSortMemoryMySqlTest` (3 tests) runs on a disposable MySQL 8.4 with the **default** `sort_buffer_size` (262,144; the test reads `@@session.sort_buffer_size` and inflates each stored `frozen_input` to that size plus 100,000 bytes, keeping valid JSON). It stores four requests through `MiningRepository.create` (no SPMF, no worker) and lists them through the HTTP endpoint:

1. newest first, `200`, `totalElements` 4, `page`/`pageSize` echoed;
2. paging: `pageSize=3` gives ids [4th, 3rd, 2nd] then [1st], the third page is empty, total stays 4;
3. every list item equals `GET /api/mining/requests/{id}`; another Store's Merchandiser sees 0 items.

- **Before the fix (RED):** 3 of 3 tests ERROR with `SQLException: Out of sort memory, consider increasing server sort buffer size`, error code 1038, SQL state HY001, on exactly `SELECT * FROM mining_request WHERE store_id=? ORDER BY request_id DESC LIMIT ? OFFSET ?`.
- **After the fix (GREEN):** 3 tests, 0 failures, 0 errors.

## Other list queries checked (all `ORDER BY` in `src/main/java`, 73 lines read)

Only a query that sorts a row with a large, growing column can hit this. The sort buffer must hold a number of rows of the sorted row size (about 15 rows, so rows above roughly 17 KB are at risk at the default size).

| Query | Sorted columns | Verdict |
|---|---|---|
| `MiningRepository.requests()` (mining_request: `frozen_input` ~280 KB, `utility_config_snapshot`) | whole row | **FIXED** (this record) |
| `MiningRepository.snapshots()` | `snapshot_id` only, then `find` | already the pattern used here |
| `MiningRepository.patterns()` (`SELECT * FROM high_utility_pattern`) | row of numbers plus `canonical_key` (at most 1,600 product ids, under 10 KB) | not changed: bounded below the limit |
| `MiningRepository.runs()` (`r.*`), `dispatchCandidates()` | small rows / ids | not at risk |
| `RecommendationRepository.list()` (`SELECT r.*`) and review history / sources / items | JSON columns are fixed templates (explanation, evidence, eligibility, policy snapshot), a few KB per row; on the MySQL stack of ROC-20261127-002 the recommendation list answered in pages of 100 (245 candidates) without error | not changed: bounded, but see the note below |
| Utility snapshots: `UtilitySnapshotRepository.page()` (matrix rows), `DashboardRepository` snapshot list | numeric columns / named small columns | not at risk |
| Layouts and campaigns: `storefront_layout` (`baseline_params` JSON, small), `campaign` (no JSON), `comparison_report` (`assumptions` JSON, fixed text), placements, revisions | small bounded JSON; `placements_json` is not in a list sort | not changed: bounded |
| F11 dashboard (`DashboardRepository`) | named columns only | not at risk |
| F04: `listBatches` (`SELECT * FROM import_batch`, no large column), `listErrors` (`error_details` one message per row), `stagingRows` (`raw_data` LONGTEXT; `EXPLAIN` shows the unique index `uq_import_staging_batch_row` gives the order, no filesort) | | not at risk |
| Mining schedules / occurrences (`SELECT *`), F01 users, stores, F02 products and batches, storefront and cart | small rows | not at risk |

`EXPLAIN` on the disposable schema: the old query had no filesort on an empty table (the planner walked the primary key backwards), which is why it only failed with real, large rows; the new id-only query uses a covering index with a filesort of ids only.

**Note (analysis, not a measurement):** the "bounded" verdicts come from reading the columns and the code that fills them, not from a MySQL run with maximum-size rows. If a future change makes `recommendation` or `high_utility_pattern` rows grow past about 17 KB, apply the same id-first pattern.

## Files

| Path | Change |
|---|---|
| `src/main/java/vn/edu/fpt/paspm/mining/MiningRepository.java` | `requests()` sorts ids only, loads by id |
| `src/test/java/vn/edu/fpt/paspm/mining/MiningRequestListSortMemoryMySqlTest.java` | Added (MySQL, 3 tests) |
| `.github/scripts/test-groups.py` | Test class added to the `mysql` group |
| `docs/api/API_CHANGELOG.md` | "No contract change" entry |
| `docs/record-of-changes/2026-11-27/05-…`, `README.md`, `docs/record-of-changes/README.md` | This record and its indexes |

## Verification

Java 17 (Temurin 17.0.21); a disposable MySQL 8.4.13 container on 127.0.0.1:3399 used only by the test (removed afterwards); no application stack was started.

- RED then GREEN as above: `./mvnw -B test -Dtest=MiningRequestListSortMemoryMySqlTest -Dpaspm.test.mysql=true` with `PASPM_TEST_MYSQL_SERVER=jdbc:mysql://127.0.0.1:3399/`.
- CI backend group: `./mvnw -B clean verify -Dtest="$(python3 .github/scripts/test-groups.py backend)"`: **496 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS (H2).
- Existing mining suite with the real SPMF 2.67a on Java 25 (H2): `-Dtest=MiningIntegrationTest`: **32 tests, 0 failures** (it lists, pages and detail-reads requests).
- **NOT RUN locally:** the full MySQL group and the integration job (the pull request's CI runs them).

## Limitations

- The test inflates the stored input with padding instead of mining a real 280 KB input; the size of the row, not its content, is what MySQL sorts.
- `requests()` now issues one `find` per listed row (at most 100 per page), like `snapshots()`; each `find` reads the large JSON once, as the old query did.

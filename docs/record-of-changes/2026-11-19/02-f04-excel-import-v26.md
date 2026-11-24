# ROC-20261119-002 — F04 Excel import brought onto current `dev`

| Field | Value |
|---|---|
| Record ID | ROC-20261119-002 |
| Date / timezone | 2026-11-19 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Opus 5.5) |
| Type | Implementation (rebase/adaptation), Documentation |
| Features | F04 Transaction Import (Excel XLS/XLSX upload) |
| Branch / base HEAD | `Khoi/feat/f04-excel-import-v26`, created from `origin/dev` `530651bad5b02d90bcb48ecffaf7fa5a0f6b0b7e` after `git fetch` |
| Execution status | COMPLETED for the scope below |
| Release status | PR_OPEN pending — branch pushed; the pull request into `dev` is opened on GitHub (`gh` was not available) |
| Daily summary | The 2026-11-19 day summary (`README.md`) is added by ROC-20261119-001 on branch `Khoi/docs/decisions-20261119`; this record is listed there after both PRs are merged |

## 1. Request and scope

Owner decision DEC-20261119-04 (`docs/decisions/2026-11-19/`, added on branch `Khoi/docs/decisions-20261119`): merge the F04 Excel import after bringing it onto current `dev` and green CI. Source: `origin/Khoi/feat/f04-excel-import`, 4 commits ahead of an older `dev` (merge base `4586926`), last commit 2026-10-26. The old branch is left unchanged (no force-push). The approved Excel contract of 2026-10-26 ([format contract](../../features/F04/F04_EXCEL_FORMAT_CONTRACT.md)) is kept as is.

## 2. Change details

### Added

The four commits were cherry-picked with `-x` (each message names its source commit):

| New | Source | Subject |
|---|---|---|
| `c868b47` | `f4c611c` | feat: add bounded XLS and XLSX transaction row parsers |
| `cbb60a2` | `c3c2055` | fix: enforce XLS blank-row bounds and reject orphan formulas |
| `577a98e` | `0f471aa` | feat: reuse F04 order pipeline for Store-scoped Excel uploads |
| `fcdfaa1` | `332a39b` | docs: record combined F04 F06 verification and stacked delivery |

Route: `POST /api/transactions/imports/excel`, `STORE_ADMIN`, multipart `file`, synchronous `201 ImportSummary`. New dependencies: Apache POI `poi-ooxml` 6.0.0 and `commons-io` 2.22.0 (`pom.xml`). No Flyway migration is added or changed, so no renumbering was needed.

### Changed

Conflicts while cherry-picking `0f471aa` and `332a39b`, all resolved without a business decision:

| File | Conflict | Resolution |
|---|---|---|
| `transaction/importing/TransactionImportController.java` | `dev` added the async CSV route where the branch added the Excel route | Kept both methods |
| `.github/scripts/test-groups.py` | `dev` added `MiningMigrationMySqlTest`, the branch added `TransactionExcelImportMySqlIntegrationTest` to the `mysql` group | Kept both |
| `docs/README.md`, `docs/api/README.md`, `docs/features/F04/README.md`, `docs/frontend_handoff/README.md`, `docs/requirements/README.md` | Status rows rewritten on both sides | Kept `dev`'s current text; rewrote only the F04 line to say CSV (sync and async) and XLS/XLSX (sync) |
| `docs/api/F04_TRANSACTION_API.md` | Intro sentence | Kept `dev`'s text; states CSV sync/async and Excel sync-only under the approved contract |
| `docs/frontend_handoff/F04_HANDOFF.md` | Route list, async notes, Excel section | Kept the async CSV route and notes, added the Excel route and the branch's "Excel upload integration" section |
| `docs/api/openapi.json` | Whole-file export | Kept `dev`'s file; the Excel path is inserted afterwards (below) |

Adaptation commit `74a97a0`: the Excel commit changed `validateFile(file)` to `validateFile(file, boolean excel)`, and the async CSV path added on `dev` after the branch was cut still used the old call, which did not compile. It now calls `validateFile(file, false)`; with `excel=false` the checks, messages and status codes equal `dev`'s CSV validator.

### Documentation

Commit `b513052`:
- `docs/api/openapi.json`: the `POST /api/transactions/imports/excel` path was taken from the runtime `/v3/api-docs` export (`-Dpaspm.test.export-openapi=true` on `TransactionExcelImportIntegrationTest`) and inserted into `dev`'s file in its own formatting. A semantic comparison with `dev` shows only that path added: **101 paths / 122 operations** (was 100 / 121), no existing path, component schema or server entry changed.
- `docs/api/F01_F02_RBAC_MATRIX.md`: Excel import row (`STORE_ADMIN`, own Store).
- `docs/api/API_CHANGELOG.md`, `docs/api/README.md`, `docs/README.md`: 2026-11-19 entry and the 101 / 122 count.

## 3. Files changed

`git diff --stat origin/dev...HEAD`: 42 files plus this record. Source: `pom.xml`, 6 new or changed classes under `src/main/java/vn/edu/fpt/paspm/transaction/importing/`, 5 test classes, `.github/scripts/test-groups.py`. Docs: the files above, the Excel format contract and two template workbooks under `docs/features/F04/`, the 2026-10-26 plan and evidence JSON under `docs/superpowers/plans/` and `docs/testing/evidence/f04_excel_import/` (historical, unchanged).

## 4. API and compatibility impact

Additive: one new route. CSV routes (sync and async), import history/error reads, Store isolation and RBAC are unchanged. CSV and Excel share the `CSV_UPLOAD` order identity, so the same order in either format is a duplicate and different content is a conflict. No migration; no change to frozen data.

## 5. Verification executed in this session

Java 17 (Temurin 17.0.21) for the backend, SPMF 2.67a (SHA-256 `4aa46838…dfe6a`) on Java 25 for the child process. MySQL: disposable `mysql:8.4` container (8.4.13) on `127.0.0.1:13319` with an empty root password, as in CI; removed afterwards.

| Command / method | Environment | Actual result | Evidence |
|---|---|---|---|
| `./mvnw -o -B test -Dtest=DatabaseTransactionSourceTest,TransactionCsvParserTest,TransactionExcelImportIntegrationTest,TransactionExcelParserTest,TransactionImportCorrectnessIntegrationTest,TransactionImportIntegrationTest,TransactionImportSchemaTest,TransactionMoneyBoundsTest,TransactionOrderIdentifierTest,TransactionOrderValidatorTest,TransactionEvidenceTest -Dpaspm.test.export-openapi=true -Dsurefire.failIfNoSpecifiedTests=false` | H2 | PASS — 129 tests, 0 failures, 0 errors, 0 skipped | Console |
| `./mvnw -o -B test -Dtest=TransactionImportMySqlIntegrationTest,TransactionImportCorrectnessMySqlIntegrationTest,TransactionRawEvidenceMigrationMySqlTest,TransactionExcelImportMySqlIntegrationTest -Dpaspm.test.mysql=true` (with `PASPM_CI_DISPOSABLE_MYSQL=true`, `PASPM_TEST_MYSQL_SERVER=jdbc:mysql://127.0.0.1:13319/`) | Disposable MySQL 8.4 | PASS — 52 tests, 0 failures, 0 errors, 0 skipped | Console |
| `./mvnw -o -B clean verify -DargLine=-Djava.io.tmpdir=<tmp> -Dpaspm.test.mysql=true -Dpaspm.test.java17-backend=true -Dpaspm.test.multijvm=true -Dpaspm.test.java17=<Java 17> -Dpaspm.test.spmf-jar=<spmf.jar> -Dpaspm.test.spmf-java=<Java 25>` (the CI `integration` opt-ins) | Disposable MySQL 8.4, real SPMF | FAIL on environment — 785 tests, 0 failures, 9 errors, 11 skipped (17:48 min). All 9 errors are `Cannot run program …/provision-restore.py: error=13, Permission denied` in `LegacyRecoveryMySqlTest` (7), `LegacyReaderMySqlTest` (1), `JpaPilotMigrationMySqlTest` (1): the script is tracked as `100644` and CI runs `chmod +x` before testing. The 11 skips are opt-in suites the CI command does not enable either (`paspm.test.foodmart-e2e` 3, `natural-lease` 2, `legacy-jar` 1, `jpa-clone` 5) | Console |
| Same opt-ins, `-Dtest=LegacyRecoveryMySqlTest,LegacyReaderMySqlTest,JpaPilotMigrationMySqlTest`, `PASPM_TEST_RESTORE_PROVISIONER` pointing to an executable copy outside the repository | Disposable MySQL 8.4 | PASS — 17 tests, 0 failures, 0 errors, 0 skipped. No single clean full run was repeated | Console |
| OpenAPI semantic comparison against `dev` (Python, read-only) | Worktree | Only `POST /api/transactions/imports/excel` added | — |
| Relative-link check over `docs/**/*.md`; private absolute-path scan; migration-path diff | Worktree | 0 broken links; 0 hits; no `db/migration` path | — |
| GitHub CI | — | NOT RUN (runs when the PR is opened) | — |

## 6. Previous evidence referenced

The evidence JSON under `docs/testing/evidence/f04_excel_import/` is the 2026-10-26 run on the old base (Flyway V1–V12); it is not re-executed evidence for this branch.

## 7. Limitations and next actions

- No async Excel route: the approved contract is synchronous; adding one would be a contract decision.
- The RBAC matrix has no row for the async CSV route; this gap exists on `dev` and is not fixed here.
- CI's `integration` job needs the repository variable `PASPM_CI_SPMF_ARTIFACT_URL`, recorded as missing on 2026-11-16.
- No Frontend change; the Frontend handoff describes the route.
- After the decisions PR and this PR are both merged, add record 002 to the 2026-11-19 day summary and the record index (this branch does not touch those files to avoid an add/add conflict).
- The old branch `Khoi/feat/f04-excel-import` is unchanged and can be deleted by its owner after merge.

## 8. Delivery

Seven commits on `Khoi/feat/f04-excel-import-v26` (four cherry-picks, one adaptation fix, one docs adaptation, this record), pushed to `origin`; no merge into `dev` from this session.

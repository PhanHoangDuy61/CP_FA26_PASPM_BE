# ROC-20261116-010 — Legacy product fields and two F03 routes removed (P5/P6)

| Field | Value |
|---|---|
| Record ID | ROC-20261116-010 |
| Date / timezone | 2026-11-16 / Asia/Ho_Chi_Minh |
| Type / features | Implementation, migration, documentation / F02, F03; F05 and F06 read compatibility |
| Branch / base | `Khoi/feat/f03-remove-legacy-product-fields` / `origin/dev` `2a944c5` |
| Commits | Test repair `21db51c`; V26/route removal `78a4106`; documentation is this record’s owning commit (`docs: record P2–P6 and EFIM-only owner decisions, team migration notes (record 010)`). |
| Status | COMPLETED within the requested local scope; production acceptance not claimed |
| Release | UNRELEASED — local commits only; no push, merge or deployment |
| Decisions | [DEC-20261116-01](../../decisions/2026-11-16/DEC-20261116-01-p2-p4-approved.md), [DEC-20261116-02](../../decisions/2026-11-16/DEC-20261116-02-p5-remove-legacy-product-fields.md), [DEC-20261116-03](../../decisions/2026-11-16/DEC-20261116-03-p6-delete-obsolete-routes.md) |
| Daily summary | [README](README.md) |

**Numbering.** The Flyway-checksum docs branch (`Khoi/docs/flyway-checksum-troubleshooting`) first committed its record as 008, which collides with [record 008](08-f05-efim-only-and-benchmark-removal.md) on `dev`. On the owner's instruction it was renumbered to **011**, the next number after this record (local commit `f6534a0` on that branch, not pushed). Number 009 is unused.

## 1. Owner decisions (2026-11-16)

- **P2, P3, P4: approved as built.** Their status changes from "assumption pending owner confirmation" to approved. There is no code change.
- **P5 changed.**
  - `expiryPushDays` is removed from every live product request, response, service, repository and the JPA entity.
  - `stockPriorityScore` and `seasonalPriorityScore` are removed from every live path.
  - Migration V26 drops the three columns.
- **P6 changed.**
  - `PUT /api/utility/config/expiry-window` and `PATCH /api/utility/products/{productId}/scores` are deleted. They used to answer 410 and now answer like any unknown route.
  - Replacements: batch `pushDay` (on create, on PATCH, and `DELETE /api/inventory-batches/{id}/push-day`) and season CRUD and assignment.
  - `GET /api/utility/products/{productId}/scores` stays, because it is the W2 preview of one product's U_expiry, season weight and W. It reads no dropped column.

The 2026-11-15 records ([owner decisions](../../decisions/OWNER_DECISIONS_F03_F05_2026-11-15.md), DEC-20261115-01 and -02) are marked approved or superseded, with links to these decisions.

## 2. Test repair before the P5/P6 change (separate commit)

On the new base `2a944c5`, the base itself was red: feature suites 469 tests, 4 FAIL, 3 ERROR. The EFIM-only commit `cd90e58` was written on a base without F03 W2 and F05 progress (PR #19), and the merge combined the two. Some tests now expected two algorithms, some used pre-W2 configuration types, and the route matrix had lost the W2 routes. As the owner instructed ("Repair tests first"), a **test-only** commit was made before any P5/P6 change. It touches 10 test cases in 5 classes and changes no production code.

| Class | Cases | Cause → repair |
|---|---:|---|
| `MiningIntegrationTest` | 5 | The tests submitted HUI-Miner + EFIM and expected 2 runs; they now submit EFIM only and expect 1 run. The frozen-input test used the removed v1.2 `WeightConfig`/`FrozenConfiguration` types and `EXPIRY_LINEAR_V1`. It now changes the live W2 configuration, adds an active season (W = 1.20 on the unchanged base utility 96), and writes the legacy frozen config as `F03_LINEAR_V1`. The OpenAPI test checked the internal `CreateRequest.algorithms` schema; it now checks the exposed `StoreCreateRequest` (no `algorithms`) and the progress route. |
| `F03F05RbacMatrixTest` | 1 | The route table had lost the W2 routes in the merge. The 8 season routes and the System Admin progress route were added back. The lambda-policy body no longer sends `lambdaMin`. The table now has 40 routes. |
| `ScheduledMiningDisabledByDefaultTest` | 1 | The System Admin mining-configuration body still sent `algorithm`, which is now rejected. |
| `LegacyReaderMySqlTest` (opt-in) | 1 | The recovery fixture submitted HUI-Miner + EFIM. It now submits EFIM only; historical patterns are still compared unchanged. |
| `MiningScheduledActivationE2EMySqlTest` (opt-in) | 2 | The tests expected two runs and `comparisonStatus = MATCH`. They now expect one EFIM run and `NOT_REQUESTED`. `MiningDispatchLease` is now injected as a `@MockitoSpyBean` instead of `@Autowired`. |

The opt-in cases were only found by the first full opt-in on the repaired base (see §7, invalid runs). No assertion about isolation, idempotency, status codes or historical reads was weakened.

## 3. Code change

### Removed from live contracts and persistence

| File | Change |
|---|---|
| `catalog/CatalogContracts.java` | `ProductRequest` and `ProductView` no longer have `expiryPushDays`. |
| `catalog/product/ProductRepository.java`, `ProductService.java` | The SELECT, row mapper, `create` and `update` no longer use `expiry_push_days`. |
| `catalog/product/persistence/ProductEntity.java`, `ProductMetadataPersistence.java` | The column mapping and parameter are removed. |
| `utility/UtilityProductRepository.java` | `Product(id, requiresExpiry, status)`. This was a **live read of all three columns**, although W2 never used the values. It is removed so that V26 cannot break it. |
| `utility/UtilityFixtureSource.java` (`dev`/`utility-fixture` profiles) | The fixture insert no longer writes the score columns. |
| `utility/ProductScoreController.java`, `ProductScoreService.java` | PATCH is deleted; GET is kept and documented. |
| `utility/UtilityConfigurationController.java` | The expiry-window route is deleted. |
| `recommendation/ProductEligibilityService.java` | Comment only. |
| `scripts/seed/seed_demo.py`, `scripts/seed/data/FoodMart_Product_Direct_Insert.sql` | The seed script no longer sends `expiryPushDays`, which would now get 400. The SQL no longer inserts the dropped columns. All 1,559 rows had `NULL, NULL, 0`, so no data is lost, and all 1,559 rows still parse. |

`fail-on-unknown-properties` is unchanged, so a client that still sends a removed field gets **400**. This is a documented breaking change ([breaking changes 15–18](../../frontend_handoff/F03_W2_BREAKING_CHANGES.md)).

### Kept (frozen or legacy readers; no live path)

`RecommendationContracts.RecProductEligibility` (stored v1 eligibility) and `ExpiryScoreCalculator.PushBatch`/`Evidence` (stored EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1 evidence) keep `expiryPushDays`. Old JSON therefore reads back byte-identically. These records are filled only from frozen JSON. No legacy reader recomputes from the live product columns, so the "stop and report" condition did not occur. `stockScore()`/`seasonalScore()` in the utility matrix are frozen matrix columns, not product columns. V4 and V8 are historical migrations and stay unchanged.

### Migration V26

`src/main/java/db/migration/V26__f03_drop_legacy_product_scoring_columns.java` (Java migration):

1. Finds every CHECK constraint on `product` whose clause mentions one of the three columns, through `INFORMATION_SCHEMA.TABLE_CONSTRAINTS` joined with `CHECK_CONSTRAINTS`. Constraint names are not assumed, following the V25 lesson. Names are validated against `[A-Za-z0-9_]+`.
2. Drops those constraints. MySQL and H2 both refuse to drop a column that a CHECK constraint references.
3. Runs `ALTER TABLE product DROP COLUMN` for `expiry_push_days`, `stock_priority_score` and `seasonal_priority_score`.

V1–V25 are unchanged. V26 is not reversible without a pre-migration backup ([release note](../../release/F03_V26_RELEASE_NOTE.md)).

## 4. Tests

### New

- **`LegacyProductColumnsV26MigrationTest`**: `h2`, `h2Fresh`, `mysql`, `mysqlFresh`; the MySQL cases are opt-in.
  - The upgrade cases migrate to V25, then seed data:
    - products with non-default legacy values;
    - frozen JSON for F03_LINEAR_V1, EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1 and W2_EXPIRY_SEASON_V1 snapshots;
    - a mining request view;
    - EXPIRY_V1 evidence;
    - eligibility v1 and v2;
    - review history;
    - F06 evidence.
  - They then apply V26 and assert:
    - the three columns are gone, and every other product column and value is unchanged;
    - every frozen JSON value is byte-identical, readable by the versioned readers, and serializes back to an equal value;
    - a plain product insert works;
    - a second migrate run applies nothing.
  - The fresh cases assert version 26 on an empty database.
- **`LegacyProductFieldsRemovedTest`** (H2, MockMvc):
  - no removed field appears in product or score responses;
  - each removed field gives 400 on create and update, and creates nothing;
  - the deleted PUT gives the same status as an unknown path (404);
  - the deleted PATCH gives the same status as an unsupported method on an existing path (405), and the GET on that path still returns 200;
  - anonymous callers get 401;
  - OpenAPI has no expiry-window path, no PATCH, and no removed property in `ProductRequest`, `ProductView` or `Scores`.
  - `configOmitsExpiryWindowDaysAndRejectsItOnPut`: GET config contains no `expiryWindowDays`; a valid W2 PUT with that unknown field gives 400 and a subsequent GET proves settings unchanged.

### Changed (P5/P6 commit)

| Test | Reason |
|---|---|
| `CategoryPersistenceIntegrationTest`, `ProductPersistenceIntegrationTest`, `ProductPersistenceMySqlIntegrationTest`, `JpaPilotMigrationMySqlTest` | The trailing `expiryPushDays` argument was removed. Score and push-day column assertions were removed, and fingerprints were recomputed without the dropped columns. Concurrency tests that wrote the dropped score columns now write a native price or batch quantity instead, which keeps the "native write survives stale JPA state" intent. The tests were renamed to match. |
| `PilotCloneApiIntegrationTest` | It asserts that the created product has no `expiryPushDays`. The test that used the deleted PATCH now checks metadata, status and batch inventory only. |
| `RecommendationExpiryIntegrationTest`, `RecommendationIntegrationTest`, `MiningIntegrationTest`, `RetailCatalogUtilityIntegrationTest` | Fixture UPDATE and INSERT statements no longer touch the dropped columns. One duplicate read was removed. The score-route test now expects 405 for every role. |
| `UtilityIntegrationTest` | expiry-window gives 404 and PATCH gives 405, for both admin and merchant, and OpenAPI has no expiry-window path. |
| `PushDayIntegrationTest` | Sending `expiryPushDays` (`3`, `null`, `0`, `-1`) gives 400, and scoring uses batch push days. The OpenAPI test now asserts that the removed fields and routes are absent. |
| `F03F05RbacMatrixTest` | Both deleted routes were removed from the matrix: 40 → 38 routes. |
| `LegacyReaderMySqlTest` (opt-in) | The test reads the `product` column list once with `SELECT *` on the old V5 database, then hashes the same columns again after migrating to the latest version. After V26 that query failed (see §7). The three columns V26 drops are now excluded, in the same way as `store.utility_config`, which V25 rewrites. After the migration the test asserts that they are gone, and every other product column must still hash identically. `LegacyDataset.hash` in `src/main` only runs the SQL it is given and reads no product column itself. The static grep missed this case because the column names come from the database at runtime. |

The golden expiry-risk values U_expiry = 0.375 and 0.25 and the F06 fingerprint tests were **not changed**, and they pass.

## 5. OpenAPI

`docs/api/openapi.json` was regenerated through `RecommendationIntegrationTest#openApiExposesActualRecommendationOperations` with `-Dpaspm.f06.export-dir`. The runner's `-Dpaspm.test.openapi-export` property has no consumer on this base.

- **Before → after:** 101 paths / 123 operations → **100 paths / 121 operations**.
- **Removed:** exactly `PUT /api/utility/config/expiry-window` and `PATCH /api/utility/products/{productId}/scores`.
- **Other differences:**
  - springdoc renumbers two `operationId`s (`get_6` → `get_5`, `get_7` → `get_6`);
  - the `POST /api/mining/requests` summary now shows the EFIM-only text, because the committed export was stale.
- **Per feature:** F01 27, F02 16, F03 18, F04 8, F05 24, F06 8, F07/F08 20.

## 6. Gate

The stages, Maven arguments and opt-in flags are the same as in records 004 and 006: feature suites, the MySQL group from `.github/scripts/test-groups.py mysql`, then the full opt-in `clean verify`. The full opt-in sets the MySQL, Java 17 backend, SPMF, multi-JVM, natural-lease and legacy-jar flags. After it, the JPA supplementary run (`PilotCloneApiIntegrationTest`) runs on a synthetic schema with `PASPM_JPA_PILOT_MUTATIONS_ALLOWED=true`. Each run used a disposable MySQL 8.4.13 container on tmpfs, and the final gate used a fresh container. No local or shared database was used.

| Stage | Baseline total / PASS / FAIL / ERROR / SKIP | Final total / PASS / FAIL / ERROR / SKIP | Exit |
|---|---|---|---|
| Feature suites | 469 / 323 / 0 / 0 / 146 | **478 / 330 / 0 / 0 / 148** | 0 |
| MySQL group | 72 / 72 / 0 / 0 / 0 | **72 / 72 / 0 / 0 / 0** | 0 |
| Full opt-in | 653 / 648 / 0 / 0 / 5 | **662 / 657 / 0 / 0 / 5** | 0 |
| JPA supplementary | 5 / 5 / 0 / 0 / 0 | **5 / 5 / 0 / 0 / 0** | 0 |

### Case-by-case baseline comparison

The final run is `final-approved`, using a new disposable MySQL 8.4.13 container. It includes the owner-requested config test. Baseline `baseline-repaired2` is the green test-repaired `2a944c5` tree. No runtime failure or error remains.

| New case | Feature suites | Full opt-in |
|---|---|---|
| `LegacyProductColumnsV26MigrationTest#h2` | PASS | PASS |
| `LegacyProductColumnsV26MigrationTest#h2Fresh` | PASS | PASS |
| `LegacyProductColumnsV26MigrationTest#mysql` | SKIP | PASS |
| `LegacyProductColumnsV26MigrationTest#mysqlFresh` | SKIP | PASS |
| `LegacyProductFieldsRemovedTest#productResponsesNoLongerCarryAnyRemovedField` | PASS | PASS |
| `LegacyProductFieldsRemovedTest#sendingARemovedFieldIsRejectedWith400` | PASS | PASS |
| `LegacyProductFieldsRemovedTest#configOmitsExpiryWindowDaysAndRejectsItOnPut` | PASS | PASS |
| `LegacyProductFieldsRemovedTest#theTwoObsoleteRoutesAnswerLikeAnyUnknownRoute` | PASS | PASS |
| `LegacyProductFieldsRemovedTest#openApiNoLongerListsTheRoutesOrTheFields` | PASS | PASS |

These **nine** additions explain every numerical difference: feature suites add seven PASS and two opt-in SKIP (469 → 478; 323 → 330 PASS; 146 → 148 SKIP); full opt-in adds nine PASS (653 → 662; 648 → 657 PASS, still five SKIP). MySQL remains 72/72 and JPA remains 5/5.

Existing case renames and changed assertions do not change counts:

| Class (including inherited MySQL variant where listed) | Baseline method → final method | Reason |
|---|---|---|
| `CategoryPersistenceIntegrationTest / CategoryPersistenceMySqlIntegrationTest` | `rollbackRestoresExistingCategoryProductInventoryAndScores` → `rollbackRestoresExistingCategoryProductAndInventory` | Dropped product scores are no longer valid write targets; rollback still preserves category/product/inventory. |
| `ProductPersistenceIntegrationTest / ProductPersistenceMySqlIntegrationTest` | `metadataUpdatesFlushBeforeJdbcReadbackAndPreserveNativeScoresAndInventory` → `metadataUpdatesFlushBeforeJdbcReadbackAndPreserveInventory` | JDBC readback and inventory preservation remain checked. |
| `ProductPersistenceIntegrationTest / ProductPersistenceMySqlIntegrationTest` | `repeatedConcurrentF03ScoreWritesSurviveJpaMetadataUpdates` → `repeatedConcurrentInventoryWritesSurviveJpaMetadataUpdates` | Concurrent native writes now target inventory, preserving the stale-JPA-state scenario. |
| `ProductPersistenceIntegrationTest / ProductPersistenceMySqlIntegrationTest` | `repeatedConcurrentNativeScoresAndPriceSurviveStatusWritesFromStaleJpaState` → `repeatedConcurrentNativePriceSurvivesStatusWritesFromStaleJpaState` | Native price remains the concurrent write target. |
| `PushDayIntegrationTest` | `openApiMarksDeprecatedFieldsAndPublishesPushDay` → `openApiDropsTheRemovedFieldsAndRoutesAndPublishesPushDay` | Removal replaces the superseded deprecation policy. |
| `PushDayIntegrationTest` | `productExpiryPushDaysIsStillAcceptedButIgnoredByScoring` → `productExpiryPushDaysIsRejectedAndScoringUsesBatchPushDays` | Owner now requires 400 for removed request fields. |
| `RetailCatalogUtilityIntegrationTest / RetailCatalogUtilityMySqlIntegrationTest` | `scoreReadsAreStoreScopedAndTheRemovedPatchIsGone` → `scoreReadsAreStoreScopedAndTheDeletedPatchIsAnUnsupportedMethod` | Deleted PATCH is 405 because GET still owns that path; Store scope remains checked. |
| `UtilityIntegrationTest` | `productScoresPreviewRiskSeasonAndMissingPushDaysAndThePatchIsGone` → `productScoresPreviewRiskSeasonAndMissingPushDaysAndThePatchIsDeleted` | Same preview coverage; deleted PATCH now 405. |
| `PilotCloneApiIntegrationTest` | `metadataAndStatusPreserveScoresAndBatchInventory` → `metadataAndStatusPreserveBatchInventory` | Five JPA cases remain; the renamed case PASSes in the supplementary run and is SKIP in full opt-in. |

Other modified cases keep their names and counts (§2 and §4), including EFIM-only fixtures, frozen-input invariants, RBAC, version readers, and V26-aware fingerprints. No baseline case silently changed PASS to SKIP or disappeared without a renamed counterpart.

### Skip coverage

| Feature-suite class | SKIP | Independent coverage in final full opt-in |
|---|---:|---|
| `CategoryPersistenceMySqlIntegrationTest` | 14 | All corresponding methods/invocations PASS |
| `LegacyProductColumnsV26MigrationTest` | 2 | All corresponding methods/invocations PASS |
| `ProductPersistenceMySqlIntegrationTest` | 18 | All corresponding methods/invocations PASS |
| `MiningDispatchLeaseMySqlTest` | 1 | All corresponding methods/invocations PASS |
| `MiningMigrationMySqlTest` | 5 | All corresponding methods/invocations PASS |
| `MiningMultiJvmMySqlTest` | 5 | All corresponding methods/invocations PASS |
| `MiningMySqlIntegrationTest` | 31 | All corresponding methods/invocations PASS |
| `MiningProgressMigrationTest` | 1 | All corresponding methods/invocations PASS |
| `MiningScheduleReservationMySqlTest` | 1 | All corresponding methods/invocations PASS |
| `MiningScheduledActivationE2EMySqlTest` | 3 | All corresponding methods/invocations PASS |
| `MiningScheduledDatabaseOutageMySqlTest` | 1 | All corresponding methods/invocations PASS |
| `MiningSchedulerMultiJvmMySqlTest` | 12 | All corresponding methods/invocations PASS |
| `MiningSchedulerUpgradeCompatibilityMySqlTest` | 5 | All corresponding methods/invocations PASS |
| `RecommendationExpiryMySqlIntegrationTest` | 5 | All corresponding methods/invocations PASS |
| `RecommendationHardeningMySqlIntegrationTest` | 13 | All corresponding methods/invocations PASS |
| `RecommendationMigrationTest` | 2 | All corresponding methods/invocations PASS |
| `RecommendationMySqlIntegrationTest` | 17 | All corresponding methods/invocations PASS |
| `RetailCatalogUtilityMySqlIntegrationTest` | 8 | All corresponding methods/invocations PASS |
| `VersionedUtilityPersistenceMySqlTest` | 3 | All corresponding methods/invocations PASS |
| `WeightV2MigrationTest` | 1 | All corresponding methods/invocations PASS |

All 148 feature-suite skipped entries are opt-in methods independently executed in the final full run. Disabled parameterized/repeated methods appear once in a skipped report and expand into multiple invocations when enabled; method names are normalized for this coverage match. In particular, two RecommendationHardening parameterized methods and the repeated MiningMultiJvm orphan-recovery method each expand to two PASS invocations. An initial exact-name-only documentation assembly assertion rejected those report-label differences; method-level matching was corrected, without changing test evidence or runtime code. The **five** full-opt-in skips are exactly `PilotCloneApiIntegrationTest`; its five cases PASS in the supplementary JPA run against a synthetic disposable schema. The stages overlap and must not be added as unique tests.

### Source identity and execution boundary

The runner captured SHA-256 for every `src/` file and `pom.xml` before the final gate. All three stage results report `sourceFilesChangedDuringRun = []`. After completion, the exact file set and hashes were rechecked with **zero differences**. Only documentation is changed during remediation, so no runtime rerun is required. Commit and merge tree IDs are verified separately before publication.

The original pre-extra-test gate (`final`) also completed successfully: feature 477 / 329 PASS / 148 SKIP; MySQL 72/72; full 661 / 656 PASS / 5 SKIP; JPA 5/5. It is superseded by `final-approved`, not counted as the final acceptance run. The interrupted chat did not interrupt either Maven gate.

Counts and exit codes are in `outputs/evidence/2026-11-16/p5-p6/P5_P6_GATE_COUNTS.json` in the project folder (**outside Git**). The tables above are the repository record. Private logs, source manifests and Surefire XML remain outside Git; no raw execution JSON is committed.

## 7. Invalid runs (not counted)

| Run | Result | Why not counted |
|---|---|---|
| Baseline on `2a944c5` as fetched | Feature suites 469 tests, 4 FAIL, 3 ERROR, exit 1. The runner stopped there. | The base itself was red because of tests left stale by `cd90e58`. I stopped and asked, and the owner chose "Repair tests first" (§2). |
| First baseline after repair | Feature suites and MySQL group green; full opt-in 653 tests, 2 FAIL, 1 ERROR, exit 1. | Three opt-in cases were also stale (§2). They were repaired and the baseline was rerun from the start. |
| First final gate on the P5/P6 tree | Feature suites 477 tests and MySQL group 72/72, both green; full opt-in 661 tests, 2 ERROR in `LegacyReaderMySqlTest` (`Unknown column 'stock_priority_score'`), exit 1. The JPA stage was not reached. | A test fingerprint still used the pre-V26 column list (§4). It was fixed and the whole gate was rerun from the start on a new disposable MySQL. |

No run was interrupted. Each counted stage has exit code 0, and the runner reported no changed source file for any counted run.

## 8. NOT RUN

- Linux orphan-process cleanup check; CI on GitHub; Swagger UI in a browser.
- A real-data or production-sized copy of the database. Only synthetic data was used, as required.
- A rolling upgrade with an old instance still running. The release note marks the expected failure as NOT VERIFIED.
- **NOT RUN:** `scripts/seed/seed_demo.py` against a running V26 server. Only its row parser was checked offline: all 1,559 rows of the updated seed SQL parse. The seed SQL itself was not executed against a V26 database.
- Frontend changes. These belong to the FE team.

## 9. Docs changed

- **Decisions:**
  - 2026-11-16: DEC-20261116-01/02/03.
  - 2026-11-15: owner decisions (P2–P6 status table), DEC-20261115-01 and -02 (update sections).
- **Requirements:** `F03_WEIGHT_V2_REQUIREMENTS.md` (P2–P6 resolved, new R9).
- **API:**
  - F02, F03 and F06 API docs;
  - RBAC matrix (two rows marked deleted);
  - `api/README.md` and `API_CHANGELOG.md`;
  - `openapi.json`.
- **Frontend:**
  - `F03_W2_BREAKING_CHANGES.md` (rows 15–18; rows 5 and 6 superseded);
  - `F02_HANDOFF.md`, `F03_HANDOFF.md`, OPTION_B handoff (banner);
  - F02 request examples (field removed);
  - F02 and F03 example READMEs (the deleted-route examples are relabelled history).
- **Features:** F02 and F03 READMEs.
- **Architecture:** `F03_W2_SCHEMA_V24_V25.md` (V26 section).
- **Setup:** `LOCAL_SETUP_COMMON.md`, where only the range line changes to "V1–V26". The other stale ranges in setup docs were left alone, because the docs branch also edits them: `F05_SPMF_RUNTIME_SETUP.md`, `LOCAL_SETUP_WINDOWS.md`, `LOCAL_SETUP_MACOS.md`, `STAGING.md`.
- **Release:**
  - new [`F03_V26_RELEASE_NOTE.md`](../../release/F03_V26_RELEASE_NOTE.md);
  - a pointer in the merge plan.
- **Records:**
  - 2026-11-15 records 02, 04, 05 and their README (addenda);
  - the day README, the record index, and `DOCUMENTATION_MAP.md`.

**Not edited (needs a manual change):**
- `PASPM_Complete_ERD_F03_Three_Weights_v4.drawio` and `PASPM_ERD_V1_V19_clean.drawio`, with its transcription `mermaid/logical-erd.mmd`: remove the three `product` columns.
- Word reports (SRS/SDD) that list `expiryPushDays`, the two deleted routes or the product score columns: update those mentions.

## 10. What team members must do

1. **Pull `dev`** once this branch is merged.
2. **Back up any local database you care about first.** V26 drops three columns and cannot be undone without the backup.
3. Start the backend. **Flyway applies V26 automatically**, and the schema becomes V1–V26.
4. **Frontend:**
   - remove `expiryPushDays`, `stockPriorityScore` and `seasonalPriorityScore`;
   - remove the calls to `PUT /api/utility/config/expiry-window` and `PATCH /api/utility/products/{id}/scores`;
   - use batch `pushDay` and seasons instead.
5. A client that still sends a removed field gets **400**.

## 11. Git

At record finalization, no push or merge has yet been performed; the owner authorized the subsequent normal pushes and detached-worktree merges subject to all gates. No rebase or force is authorized. The main checkout (`dev`, with its own uncommitted `application.yml` change) was not touched. The work was done in a separate worktree.

## 12. Evidence kept outside the repository

Location: `outputs/evidence/2026-11-16/p5-p6/` in the project folder (not in Git). Copies are SHA-256 verified. The `evidence-sha256.json` inventory covers the copied logs, run results and available Surefire XML. No credentials, dumps or machine-specific configuration are committed.

| File | SHA-256 |
|---|---|
| `P5_P6_GATE_COUNTS.json` | `7130f631535047201c7085e3c65d3a97ee8dea7e9a4bc4e521cada5f6c17b34b` |
| `baseline-repaired2/feature-suites-counts.json` | `9c78e50eaf948a1edb1a018f41251e7971698af2b7c1976834c7a767b3f62bf4` |
| `baseline-repaired2/mysql-group-counts.json` | `07daf21c1743ead313e3e3b470ca457d2cacbd0b2484bb977aeef9708e4ffaa9` |
| `baseline-repaired2/full-optin-counts.json` | `d5ef6f1cd2581e4d7a5a2cb29aaf1f084b729028ae703ef02c683bb1b0ebfd91` |
| `baseline-repaired2/jpa-status.json` | `efb12a9f49f86869e8dcf76a56dc83ec0936c797dd5a27c509e5dc3cfe848e93` |
| `final-approved/feature-suites-counts.json` | `09e2de12f1ca532572221285dfad80f975768cbf845e3e23dda76079ba6833a8` |
| `final-approved/mysql-group-counts.json` | `21cf4c33aa327ef535b7c303eece84f5b04bca8bd6141c733266d5f338c6f6ee` |
| `final-approved/full-optin-counts.json` | `d7c623545385ef42831a19326ed378d631b288f23a83e590945e5a75d9a26a3a` |
| `final-approved/jpa-status.json` | `c77fcea501f26a924ee457d3e808e11ea7002ff2a341f179803f3692a9898a56` |
| `final-approved/source-manifest.json` | `4f2ac8e8889372015d1c412cffa2df187cb5af0af2d6eb07f4a3c3cf0c7ef7b1` |
| `final-approved/feature-suites.log` | `44af498545ec6d1420c428a19424296866626dee91f710b5fb718f62bdecc90a` |
| `final-approved/mysql-group.log` | `430b9a36ae3cf4d392065ebe11eb05416c051025abd3aba1507fb64b52f6debe` |
| `final-approved/full-optin.log` | `6cfa2226b81114bb12462ec5ef6acb35c17d51759a22b4452cfd1fb881eb9aae` |
| `final-approved/jpa.log` | `48dc5f8f13f7b107cdeb7ea476d0c2f86f7a8d9a810f3231e091dc99398326dc` |

## 13. Documentation remediation and leftover-field audit

The owner authorized correcting the seven broken links and five EOF whitespace errors after the prior stop. Five decision links now use `../decisions/` from their respective document directories. The two absent JSON links are plain-text external evidence locations; no private JSON was added to Git. The historical stop remains recorded.

The leftover-field scan includes tracked and untracked, non-ignored text files. Every hit is classified in external `leftover-field-classification.json`; source hits are limited to V26 removal and the three named legacy version readers. Seed hits are removal comments only. Diagram/schema references remain the explicit manual-update backlog (§9), not silently changed.

| Classification | Hits |
|---|---:|
| Diagram/schema reference: known manual update backlog | 22 |
| Documentation: decision, historical contract, migration/removal guidance or rejection requirement | 531 |
| Frozen/legacy compatibility: legacy frozen recommendation eligibility DTO | 1 |
| Frozen/legacy compatibility: legacy versioned configuration DTO | 1 |
| Frozen/legacy compatibility: legacy versioned expiry evidence/calculation reader | 3 |
| Historical Flyway migration: unchanged | 6 |
| OpenAPI legacy versioned DTO, removed-field rejection description or frozen JSON schema | 4 |
| Seed comment: documents removal; no live insert uses dropped fields | 2 |
| Test fixture, historical compatibility, rejection or migration assertion | 55 |
| V26 explicit removal of legacy columns | 2 |

Audit SHA-256: `b85082c803cfca6d15a15a98f9908149c131fc611114b3eb7036664fe0f92979`. Classification was made before inserting this audit paragraph, so its own removal-related wording is not part of those counts. Final pre-commit grep is also checked and classified as documentation.

## 14. Verified source identity before documentation commit

The final-gate source manifest matches the feature source commit `78a4106` exactly, including the added config rejection test. Only documentation changed after the gate.

- Source tree: `7e8ef20b0c6080079cfb85de1801435a9939c6c0`.
- POM blob: `ddf14fdca04e3b3269fc32a4e063a43a9ed0de1f`.

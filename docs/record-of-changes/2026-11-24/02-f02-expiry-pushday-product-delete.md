# ROC-20261124-002 — F02 expiry boundary, push-day warning, product soft delete

| Field | Value |
|---|---|
| Record ID | ROC-20261124-002 |
| Date / timezone | 2026-11-24 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Sonnet 5.5) |
| Type | Implementation / Database / Documentation |
| Features | F02 (batches, summary, product delete), with effects on F03 (U_expiry boundary, seasons), F04 (import warning), F06 (eligibility, expiry-push lists) and F07/F08 (delete guard) |
| Branch / base HEAD | `Khoi/feat/f02-expiry-pushday-product-delete` / `origin/dev` `3d51f5b` (merge of PR #27) |
| Execution status | COMPLETED (backend). The PO reviewed it at Checkpoint 2 (2026-11-24). Frontend not started |
| Release status | BRANCH_PUSHED — committed and pushed to `origin/Khoi/feat/f02-expiry-pushday-product-delete`; PR not opened, not merged |
| Daily summary | [README of the day](README.md) |

## 1. Request and scope
- The Product Owner gave three requirements and answered the Checkpoint 1 questions on 2026-11-24:
  - R1: expired only when today (`Asia/Ho_Chi_Minh`) is after the expiry date; computed on read; `effectiveStatus` and `daysToExpiry`; BLOCKED + expired counts as expired.
  - R2: no default push day; a `MISSING_PUSH_DAY` warning in the batch API.
  - R3: product soft delete for the frontend.
- Rules: [DEC-20261124-01](../../decisions/2026-11-24/DEC-20261124-01-batch-expiry-boundary-effective-status.md), [-02](../../decisions/2026-11-24/DEC-20261124-02-push-day-missing-warning.md), [-03](../../decisions/2026-11-24/DEC-20261124-03-product-soft-delete.md).
- Gates checked first (2026-11-24): `git fetch --all --prune` and a scan of every local and remote branch (47 remote branches) found **no V28 or higher** migration, so V28 was free. The open PRs could not be listed (`gh` is not installed), only their branches.
- The PO also asked to bump `scoringVersion` unless that blocks comparing or mining across versions. It does, so it was **not** bumped; the PO confirmed this at Checkpoint 2 (keep `W2_EXPIRY_SEASON_V1`). DEC-20261124-01 §6 records the effective date, how `asOfDate` and the creation time identify the rule, and that experiment snapshots must be regenerated after the merge (no mixing).
- The main checkout has an uncommitted `src/main/resources/application.yml` (bootstrap password). It was never touched; all work is in a separate worktree.

## 2. Change details

### Added
- `catalog/inventory/BatchExpiryRules`: the one definition of "expired", `effectiveStatus`, `daysToExpiry` and the `MISSING_PUSH_DAY` warning.
- `BatchView.effectiveStatus`, `daysToExpiry`, `warnings`; `InventorySummary.batchesMissingPushDay`; `SeasonProduct.deleted`.
- `DELETE /api/products/{id}` with `ProductDeleteBlockedException` (409 `PRODUCT_HAS_SELLABLE_STOCK` / `PRODUCT_IN_LIVE_CAMPAIGN`, body `{code,error,reason,sellableQuantity,campaigns}`) and its handler in `ApiError`.
- Season assignment of a deleted product: 409 `PRODUCT_DELETED`.
- F04: a warning (`PRODUCT_DELETED` in `import_staging_row.error_details`) on each imported row of a deleted product.

### Changed
- The expiry boundary (`<=` → `<`) in `InventorySummaryRepository`, `ExpiryRiskCalculator`, `PushDayRule` and `ProductEligibilityService`. Summary buckets are disjoint: expired (any stored status) / blocked (not expired) / sellable / expiring soon.
- `InventoryBatchService` and `InventorySummaryService` use the shared `UtilityTimeZone` (`paspm.store-time-zone`) instead of their own `ZoneId` copies.
- `ProductRepository` excludes soft-deleted products in `find`, `search` (before pagination, in the WHERE shared by `COUNT` and `LIMIT/OFFSET`) and the similar-name check; `ProductService.updateStatus` now checks existence first, so a deleted product cannot be re-activated.
- Tests that pinned the old boundary or "latest migration is V27": `WeightV2CalculationTest`, `PushDayIntegrationTest`, `RetailCatalogUtilityIntegrationTest`, `RecommendationExpiryIntegrationTest`, `RecommendationIntegrationTest`, `CatalogIntegrationTest`, `StoreMigrationV27Test` (now filters checksums to versions below 27, so later migrations do not break it).

### Fixed
- N/A (the push day had no default to remove).

### Database
- `V28__f02_product_soft_delete.sql`: `product.deleted_at`, `product.deleted_by` (FK `app_user`), CHECK both-or-neither, index `(store_id, deleted_at)`. Nothing is backfilled; V1–V27 are unchanged.

### Documentation
- Three DEC files; `docs/api/F02_PRODUCT_INVENTORY_API.md`, `F03_UTILITY_API.md`, `F04_TRANSACTION_API.md`, `F06_RECOMMENDATION_API.md`, `F01_F02_RBAC_MATRIX.md`, `README.md` (counts), `API_CHANGELOG.md`, `openapi.json`.
- `docs/frontend_handoff/F02_HANDOFF.md` (exact fields, error codes, TypeScript types), F02 examples (new fields, a 409 body), `F03_HANDOFF.md`, `F04_HANDOFF.md`, `F06_HANDOFF.md`.
- `docs/features/F02/README.md`, `F03/README.md`, `F06/README.md`, `F02/reference_v3/F02_TECHNICAL_DECISIONS.md` (BLOCKED + expired), `docs/requirements/F03_WEIGHT_V2_REQUIREMENTS.md`.
- `docs/product/SRS.md` (FR-02.1/02.2/02.5, BR-14, BR-32, BR-33, UC20), `GLOSSARY.md`, `ARCHITECTURE_SDD.md` (V28, eligibility), `BRD_VISION.md` (decision log, open question 11); `scripts/seed/README.md` (one note; the seed itself is unchanged).

## 3. Files changed

| Path | Added / Modified | Content |
|---|---|---|
| `src/main/java/vn/edu/fpt/paspm/catalog/inventory/BatchExpiryRules.java` | Added | shared expiry / warning rules |
| `src/main/java/vn/edu/fpt/paspm/catalog/CatalogContracts.java` | Modified | `BatchView`, `InventorySummary` |
| `src/main/java/vn/edu/fpt/paspm/catalog/inventory/*` (`InventoryBatchService`, `InventorySummaryRepository`, `InventorySummaryService`, `InventoryBatchController`) | Modified | derive fields, buckets, descriptions |
| `src/main/java/vn/edu/fpt/paspm/catalog/product/*` (`ProductController`, `ProductService`, `ProductRepository`) and `ProductDeleteBlockedException.java` | Modified / Added | soft delete, catalogue filter |
| `src/main/java/vn/edu/fpt/paspm/common/ApiError.java` | Modified | 409 body for the delete refusal |
| `src/main/java/vn/edu/fpt/paspm/utility/{ExpiryRiskCalculator,PushDayRule,SeasonContracts,SeasonRepository,SeasonService}.java`, `recommendation/ProductEligibilityService.java` | Modified | boundary, season labelling and rejection |
| `src/main/java/vn/edu/fpt/paspm/transaction/importing/TransactionImportRepository.java` | Modified | import warning |
| `src/main/resources/db/migration/V28__f02_product_soft_delete.sql` | Added | schema |
| `src/test/java/.../BatchExpiryRulesTest`, `ExpiryBoundaryF03F06Test`, `ProductSoftDeleteIntegrationTest`, `ProductSoftDeleteV28MigrationTest`, `ProductSoftDeleteV28MySqlTest` | Added | new tests |
| the existing tests listed above | Modified | new boundary |
| `.github/scripts/test-groups.py` | Modified | four classes in `backend`, one in `mysql` |
| documentation | Modified / Added | see section 2 |

## 4. API and compatibility impact
- Additive: `DELETE /api/products/{id}`; `BatchView.effectiveStatus`, `daysToExpiry`, `warnings`; `InventorySummary.batchesMissingPushDay`; `SeasonProduct.deleted`. No request field changed, so no 400 "Invalid request fields" risk for existing clients.
- Behaviour changes: the expiry boundary (summary and F03/F06 on the expiry date), BLOCKED + expired moving to `expiredQuantity`, soft-deleted products hidden, season assignment 409 `PRODUCT_DELETED`, an import warning row. Stored frozen JSON, snapshots and history are not rewritten.
- RBAC: delete is STORE_ADMIN of the owning Store (401 → 403 → 404 → 409 → 204). OpenAPI 107 paths / 133 operations → 107 / 134.
- Frontend: must stop comparing dates and read the new fields (`F02_HANDOFF.md`).

## 5. Verification executed in this session
Java 17 (`JAVA_HOME=~/.sdkman/candidates/java/17.0.21.0+9-tem`), launched as `sh ./mvnw` (the wrapper is mode 100644). MySQL runs used a disposable `mysql:8.4` container on `127.0.0.1:56306` (not the default 3306, and not any teammate's database).

| Command / method | Environment | Actual result | Evidence |
|---|---|---|---|
| `sh ./mvnw -B test -Dtest=BatchExpiryRulesTest` (first run: compile error, no class; then green) | Java 17, no DB | PASS 4/4 after implementation | test-first red run observed |
| `sh ./mvnw -B test -Dtest=ExpiryBoundaryF03F06Test` | Java 17 | red: 3 failures (expiry-day batch treated as expired); green after the change, 4/4 | |
| `-Dtest=CatalogIntegrationTest` | H2 | red: missing `effectiveStatus` and summary 12 ≠ 16; green, 13/13 | |
| `-Dtest=ProductSoftDeleteIntegrationTest` | H2 | red: 7/7 failed with 405 (no route); green, 8/8 after the endpoint and the season test | |
| `-Dtest=TransactionImportIntegrationTest` | H2 | red: no warning row; green, 10/10 | |
| `-Dtest=ProductSoftDeleteV28MigrationTest` | H2 | PASS 1/1 (written after the migration; no red run) | |
| `sh ./mvnw -B clean verify -Dtest="$(python3 .github/scripts/test-groups.py backend)"` | H2 | **441 tests, 0 failures, 0 errors, 0 skipped**; BUILD SUCCESS. (An earlier run of the same command had 1 failure, `StoreMigrationV27Test`, because it assumed V27 was the latest migration; fixed in the test.) | `python3 .github/scripts/test-summary.py` |
| `PASPM_TEST_MYSQL_SERVER=jdbc:mysql://127.0.0.1:56306/ … sh ./mvnw -B clean verify -Dtest="$(python3 .github/scripts/test-groups.py mysql)" -DargLine="-Djava.io.tmpdir=…" -Dpaspm.test.mysql=true -Dlogging.level.root=ERROR` | MySQL 8.4 container | **101 tests, 0 failures, 0 errors, 0 skipped**; includes `ProductSoftDeleteV28MySqlTest` and `StoreMigrationV27MySqlTest` | |
| Full `sh ./mvnw -B clean verify` with all CI opt-ins (`-Dpaspm.test.mysql=true -Dpaspm.test.java17-backend=true -Dpaspm.test.multijvm=true -Dpaspm.test.java17 -Dpaspm.test.spmf-jar -Dpaspm.test.spmf-java -Dpaspm.avatar.directory`, `PASPM_MINING_SPMF_JAR`, `PASPM_MINING_JAVA_EXECUTABLE`) | MySQL 8.4 container; SPMF 2.67a (SHA-256 `4aa46838…dfe6a`, the approved artifact) on Java 25 | **864 tests, 1 failure, 0 errors, 12 skipped**; the failure and the 12 skips are the same on clean `dev` (below) | |
| Same opt-ins, `-Dtest=JpaPilotMigrationMySqlTest,MiningConversionStabilityTest,FoodMartScaleSyntheticE2EMySqlIntegrationTest,MiningSchedulerMultiJvmMySqlTest,MiningSchedulerUpgradeCompatibilityMySqlTest,PilotCloneApiIntegrationTest,RecommendationIntegrationTest` on a clean worktree of `origin/dev` `3d51f5b` | same | 47 tests, **1 failure, 12 skipped**: `JpaPilotMigrationMySqlTest.freshV1ToV17ValidatesJpaConstraintsAndActualCategoryProductHttpApis:84` (201 expected, 400 received: the test still sends `sku`, which F02 rejects since 2026-11-20); skips: `MiningConversionStabilityTest` 1, `FoodMartScaleSyntheticE2EMySqlIntegrationTest` 3, `MiningSchedulerMultiJvmMySqlTest` 2, `MiningSchedulerUpgradeCompatibilityMySqlTest` 1, `PilotCloneApiIntegrationTest` 5 | |
| `RecommendationIntegrationTest` without SPMF on clean `dev` | Java 17 only | 15 of 17 fail (HTTP 503: no SPMF/Java 25). With SPMF: 17/17 on both `dev` and this branch | |
| `python3 -I -m unittest discover -s .github/scripts/tests -p 'test_*.py'` | with `SPMF_JAR_PATH` set | 34 tests OK (2 errors without that variable, also on clean `dev`) | |
| Docker `mysql:8.4` on 56306 | local | created for this run and removed afterwards | |

## 6. Previous evidence referenced
- Record 001 (Store management) for the V27 baseline and the OpenAPI export hook.
- The frontend source was only read (`FE_Kien`, FE `dev` @ `7def287`) to confirm that it has no push-day default and repeats `expiryDate <= today`.

## 7. Limitations and next actions
- **Decided, no open question:** `scoringVersion` stays `W2_EXPIRY_SEASON_V1`. After the merge, regenerate experiment snapshots and do not mix results across the effective date (DEC-20261124-01 §6; BRD open question 11 is closed). The effective point is the merge of PR #28 into `dev`; regenerating the experiment snapshots after the merge is the only post-merge step.
- **Known limitation (accepted by the PO at Checkpoint 2, no new tests now):** delete versus concurrent work. The delete locks the product row and its batches before it checks stock and campaigns, so a concurrent receive waits. A concurrent batch `PATCH`, campaign publish or layout write is not serialised against it by a dedicated guard and no concurrent test exists, so a rare race can leave a product deleted just after a batch was edited or a campaign was published (also in DEC-20261124-03).
- **Follow-up task (separate, not fixed here):** OpenAPI drift. `docs/api/openapi.json` already differs from the runtime `/v3/api-docs` export, before this change, in `servers` (the export uses `http://localhost:8080`), the parameter list of `POST /api/transactions/imports/csv/async`, and the `PlacementView.sku` description and `readOnly` flag. This record merged only its own changes into the file. A later task should regenerate or reconcile those three spots.
- Season assignment of a deleted product answers HTTP 409 `PRODUCT_DELETED` (accepted by the PO at Checkpoint 2).
- **Frontend:** remove its own date comparisons and use the new fields; add the delete action and the 409 messages (F02 handoff).
- **Pre-existing, not changed:** `JpaPilotMigrationMySqlTest` (sends `sku`); the 12 skipped opt-in tests; `docs/api/openapi.json` already differs from the runtime export in `servers`, one CSV async parameter list and `PlacementView.sku` (left alone; follow-up task above); the demo seed `scripts/seed/seed_demo.py` (its fixes are deferred). Record 001 said PR_OPEN although PR #27 was merged; corrected in this change.
- The open PRs could not be listed (no `gh`); V28 was checked against branches only.
- F09/F10 are not coded: they must exclude deleted products when built.

## 8. Delivery
- Commit: one commit on `Khoi/feat/f02-expiry-pushday-product-delete` (hooks not bypassed), made at the PO's request after Checkpoint 2. Its hash is in `git log`; a record cannot contain the hash of its own commit.
- Push: `git push -u origin Khoi/feat/f02-expiry-pushday-product-delete` (explicit target, no force). The branch tracks its own remote branch, not `origin/dev` (the upstream inherited from the worktree creation was removed first).
- PR: **NOT OPENED, NOT MERGED**. `gh` is not installed; compare URL: `https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/compare/dev...Khoi/feat/f02-expiry-pushday-product-delete?expand=1`. The PR body draft is `outputs/pr-bodies/PR-f02-expiry-pushday-product-delete.md` (outside the repository).
- Source state: the work tree of `outputs/f02-expiry-wt` is clean after the commit. The main checkout and its uncommitted `src/main/resources/application.yml` were never touched or committed.
- Effective point: the merge of PR #28 into `dev` (DEC-20261124-01 §6). The only post-merge step is to regenerate the experiment snapshots.

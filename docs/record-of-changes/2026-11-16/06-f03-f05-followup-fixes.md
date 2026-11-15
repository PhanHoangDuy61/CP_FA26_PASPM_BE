# ROC-20261116-006 — Post-W2 follow-up fixes (series record 006)

| Field | Value |
|---|---|
| Date / timezone | 2026-11-16 / Asia/Ho_Chi_Minh |
| Type / features | Bug fixes / F02, F03, F05 regression, F06 |
| Branch / base | Khoi/feat/f05-progress-f03-weight-v2 / 369dba4 |
| Status | COMPLETED — scoped fixes and local verification; production acceptance not claimed |
| Release | UNRELEASED — local commit; not pushed |

## 1. Request and scope
Commit the approved docs sync, then resolve findings 2, 5, 6 and 1 without migrations or historical-data rewrites. This record continues the requested 001–006 series in the actual work-date folder. [Day summary](README.md).

## 2. Change details
- F2: additive DELETE `/api/inventory-batches/{id}/push-day`; Store Admin only, Store-scoped locked write, returns BatchView, idempotent. PATCH null/absent still retains the existing push day.
- F5: missing-push-day and no-recent-sales warnings both returned, including when all batches have missing push days and no batch is eligible. Legacy stored evidence is not recalculated.
- F6: recommendation view adds `seasonEnded` and `endedSeasons` (seasonId, name, endDate), derived from the snapshot's season copies and current Store date. Existing explanation text retained; no regeneration.
- F1: unsupported/non-text stored scoringVersion yields HTTP 409 with `{code: "UNSUPPORTED_SCORING_VERSION", error: "...stored value..."}`. This follows the existing persisted-state conflict convention (snapshot/policy conflicts use 409). Server logs keep diagnostics; response has no stack trace. Unknown JSON fields remain strict.

## 3. Files changed

- `docs/api/API_CHANGELOG.md`
- `docs/api/F01_F02_RBAC_MATRIX.md`
- `docs/api/F02_PRODUCT_INVENTORY_API.md`
- `docs/api/F03_UTILITY_API.md`
- `docs/api/F06_RECOMMENDATION_API.md`
- `docs/api/README.md`
- `docs/api/openapi.json`
- `docs/features/F02/README.md`
- `docs/features/F03/README.md`
- `docs/features/F06/README.md`
- `docs/frontend_handoff/F02_HANDOFF.md`
- `docs/frontend_handoff/F03_W2_BREAKING_CHANGES.md`
- `docs/frontend_handoff/F06_HANDOFF.md`
- `docs/record-of-changes/2026-11-15/04-f03-weight-v2-expiry-season.md`
- `docs/record-of-changes/2026-11-15/05-docs-sync-f03-w2-f05-progress.md`
- `docs/record-of-changes/2026-11-16/06-f03-f05-followup-fixes.md`
- `docs/record-of-changes/2026-11-16/README.md`
- `docs/record-of-changes/README.md`
- `src/main/java/vn/edu/fpt/paspm/catalog/inventory/InventoryBatchController.java`
- `src/main/java/vn/edu/fpt/paspm/catalog/inventory/InventoryBatchRepository.java`
- `src/main/java/vn/edu/fpt/paspm/catalog/inventory/InventoryBatchService.java`
- `src/main/java/vn/edu/fpt/paspm/common/ApiError.java`
- `src/main/java/vn/edu/fpt/paspm/common/UnsupportedScoringVersion.java`
- `src/main/java/vn/edu/fpt/paspm/recommendation/RecommendationContracts.java`
- `src/main/java/vn/edu/fpt/paspm/recommendation/RecommendationJsonVersions.java`
- `src/main/java/vn/edu/fpt/paspm/recommendation/RecommendationRepository.java`
- `src/main/java/vn/edu/fpt/paspm/recommendation/RecommendationService.java`
- `src/main/java/vn/edu/fpt/paspm/utility/ExpiryRiskCalculator.java`
- `src/main/java/vn/edu/fpt/paspm/utility/UtilityJsonVersions.java`
- `src/main/java/vn/edu/fpt/paspm/utility/UtilitySettingsRepository.java`
- `src/main/java/vn/edu/fpt/paspm/utility/WeightV2Calculator.java`
- `src/test/java/vn/edu/fpt/paspm/layout/LayoutCampaignIntegrationTest.java`
- `src/test/java/vn/edu/fpt/paspm/recommendation/RecommendationIntegrationTest.java`
- `src/test/java/vn/edu/fpt/paspm/utility/PushDayIntegrationTest.java`

`LayoutCampaignIntegrationTest` only updates its synthetic RecView constructor. No F07/F08 business or migration changes.

## 4. API and compatibility impact
Additive route and response fields. Frozen snapshot/config/request/recommendation JSON is never rewritten by these fixes. Store isolation and System Admin routes unchanged. Scheduler enable remains HTTP 503.

## 5. Verification executed in this session
Feature suites: 467 discovered, 322 PASS, 145 SKIP, zero FAIL/ERROR, Maven exit 0. Opt-in MySQL/multi-JVM groups intentionally skipped here and run separately. OpenAPI regenerated through Springdoc test execution: 102/124 paths/operations → 103/125, no dangling refs, route and season fields verified. Relative Markdown links: 1,039 checked, zero broken (intermediate check).

MySQL group: **72/72 PASS**, zero skipped/failures/errors, exit 0. Full opt-in completed; final results below. Raw logs are private under `<evidence-dir>/`; publish sanitized results only.
Initial RED runs deliberately exercised missing functionality (the first also has the preview-path test-authoring defect below). Detailed counts are in `development-attempts.json` (external evidence): red 26 discovered / 5 FAIL / 2 ERROR, corrected red 10 / 3 FAIL / 2 ERROR; two compilation failures; targeted green 33/33 PASS before the final additional assertions. None of these is substituted for final-source regression. A test-authoring mistake used `/api/utility/matrix/preview` instead of the implemented `/api/utility/preview`; corrected before the next run. Two intermediate compilation attempts caught a cache generic type and one test constructor requiring the new response fields. These attempts are not acceptance results.

## 6. Previous evidence referenced
Phase 6c: 645 discovered / 640 PASS / 5 SKIP, supplemental synthetic JPA 5/5. These are historical baseline counts, not this session's execution.

## 7. Limitations and next actions
Known gaps retained: F3 season-name H2 case sensitivity; F4 unused mining_run.attempt_no; F7 one F06 sales query per product; F8 stop old instances before V25; F9 progress may drop after recovery. Linux SPMF orphan cleanup, GitHub CI, interactive Swagger UI and real-data conversion remain NOT RUN.

### Before deploy / Frontend checks
- Integrate DELETE push-day action; use the returned BatchView and re-fetch scores/preview. Do not send null PATCH to clear.
- Display both warning codes independently; badge seasonEnded and endedSeasons; handle 409 UNSUPPORTED_SCORING_VERSION without blind retry.
- Existing V25 deployment checklist still applies, including stopping all old instances. No deployment performed.
- Diagram/report backlog remains: batch push-day clear action, combined warning display, recommendation season badge and coded version-conflict response in relevant sequences/screen contracts; SRS and Report 1/4 changes already listed in record 004. No Word/Draw.io edits.

## 8. Delivery
Approved docs sync committed locally: `369dba4` (43 docs files). Status was clean immediately after commit. Fix commit is proposed only; no push/merge/rebase/tag/force/deploy.

## Final verification and baseline comparison

All results below are local on macOS, Corretto 17.0.21 Backend, Java 25.0.1 child, pinned SPMF 2.67a (SHA-256 `4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a`) and a new disposable MySQL 8.4 container on loopback port 59584. The old `paspm-phase2-gate-20261115` was found Exited(0) and left unchanged. No source/shared/production database accessed.

| Run | Discovered | PASS | FAIL / ERROR | SKIPPED | Maven exit |
|---|---:|---:|---:|---:|---:|
| Targeted green (before final extra assertions) | 33 | 33 | 0 / 0 | 0 | 0 |
| F02/F03/F05/F06 package suites, final source | 467 | 322 | 0 / 0 | 145 | 0 |
| MySQL group, final source | 72 | 72 | 0 / 0 | 0 | 0 |
| Full opt-in, final source | 651 | 646 | 0 / 0 | 5 | 0 |
| Synthetic JPA supplementary | 5 | 5 | 0 / 0 | 0 | 0 |

No final regression run was interrupted or invalid. Source/resource/test fingerprints stayed unchanged during all three supervised stages. The package suite intentionally skips MySQL/multi-JVM opt-ins; those run in the subsequent stages. Test sets overlap and are NOT added together. The full suite's five JPA skips were subsequently executed successfully in a newly provisioned synthetic schema under a schema-only account; this is not a real-data clone.

| Metric | Phase 6c baseline | Follow-up | Difference |
|---|---:|---:|---:|
| Discovered tests | 645 | 651 | +6 |
| PASS | 640 | 646 | +6 |
| FAIL / ERROR | 0 / 0 | 0 / 0 | 0 |
| SKIP | 5 | 5 | 0 |
| Supplementary JPA PASS | 5 | 5 | 0 |

Against Phase 6c **645 / 640 PASS / 5 SKIP**: now **651 / 646 PASS / 5 SKIP**, exactly **+6** tests. PushDayIntegrationTest adds four (5→9); RecommendationIntegrationTest adds one ended-season case (16→17) and its MySQL subclass inherits that case (16→17). No other class count changed. The same five JPA cases are listed with reasons in `baseline-comparison.json` (external evidence), and all pass in `jpa-status.json` (external evidence).

Exact executed Maven arguments, all test identities, skip reasons, exit codes and source-change checks: `feature-suites-counts.json` (external evidence), `mysql-group-counts.json` (external evidence), `full-optin-counts.json` (external evidence). Runtime flags match the Phase 6c runner, including `paspm.test.mysql`, `paspm.test.java17-backend`, explicit Java17/Java25/SPMF paths, `paspm.test.multijvm`, `paspm.test.natural-lease` and the 8f94f94 legacy JAR. Only disposable port/evidence directories differ. Native MySQL CLI is used as in the earlier verified adaptation; no claim that the unchanged Option B shell script itself passed end-to-end.

The new DELETE HTTP cases execute on H2. The MySQL group and full suite cover MySQL migration, persistence and regression; the new ended-season integration case also runs on MySQL. Do not interpret the aggregate as every HTTP case having run on both databases.

Natural-expiry recovery passed after 309 seconds; a stalled owner resumed after 385 seconds and its stale commit was refused. Queue saturation, post-commit/pre-enqueue recovery, MySQL reconnect, concurrent startup, configuration pinning and real SPMF regression also passed. macOS orphan observations remain the existing limitation; Linux orphan cleanup is NOT RUN. GitHub CI, interactive Swagger UI, Windows testing and real-data conversion remain NOT RUN.

### Fix-to-test traceability
Paths below are relative to `src/main/java/vn/edu/fpt/paspm/`; test paths relative to `src/test/java/vn/edu/fpt/paspm/`.

| Fix | Source evidence | Executed test |
|---|---|---|
| F2 clear push day | `catalog/inventory/InventoryBatchController.java:64`, `InventoryBatchService.java:69`, `InventoryBatchRepository.java:73` | `utility/PushDayIntegrationTest.java:134` clear/repeat/roles/foreign/unknown/401/persistence/next preview; PATCH null retains value |
| F5 independent warnings | `utility/ExpiryRiskCalculator.java:53`, `WeightV2Calculator.java:52` | `utility/PushDayIntegrationTest.java:150` scores, preview, persisted snapshot evidence |
| F6 structured ended seasons | `recommendation/RecommendationContracts.java:63`, `RecommendationRepository.java:42`, `RecommendationService.java:80` | `recommendation/RecommendationIntegrationTest.java:59`, plus MySQL subclass; list/detail, text retained, frozen eligibility unchanged |
| F1 coded unsupported version | `common/ApiError.java:17`, `common/UnsupportedScoringVersion.java:6`, `utility/UtilityJsonVersions.java:27`, `UtilitySettingsRepository.java:53` | `utility/PushDayIntegrationTest.java:165` unknown config and `:169` non-text snapshot; unchanged stored JSON |

The existing legacy JSON round-trip/persistence, Store isolation, mining lambda-policy block, RBAC and scheduler activation regression tests passed in the final suite. V1–V25 and strict global unknown-property rejection remain unchanged.

### Delivery status
Only the approved docs commit `369dba4` was created. The owner authorized the local commit after coverage review; no push/merge/rebase/tag/force/deploy. Main BE checkout and diagram branch untouched. Authorized local commit message (hash recorded in Git log):

`fix(f02,f03,f06): clear batch push day, combine warnings and expose season/version errors`

Final QA: **1048 relative Markdown links checked, 0 broken**; OpenAPI **103/125 paths/operations**. Source fingerprint differences: **0**. `FINAL_QA.json` (external evidence). `git diff --check` passed; staged diff empty.

MySQL server version confirmed **8.4.13**; the newly created `paspm-followup-mysql-20261116` container was stopped after verification (not removed). The old container remained unchanged.
OpenAPI semantic comparison with HEAD found exactly four additions: the DELETE path, EndedSeason schema, RecView.seasonEnded and RecView.endedSeasons. No old paths or schemas removed. Only JSON key order was aligned to the baseline after exporting; document content unchanged.

## Pre-commit owner-requested coverage check

Source/test fingerprints were compared again with the final verified manifest and are identical. No required case is missing; no implementation/test changes or new Maven run were needed for this review. All methods below are new in this fix; existing tests were retained (the layout fixture constructor was changed for the additive DTO fields).

| Required case | Class#method |
|---|---|
| DELETE success | PushDayIntegrationTest#clearPushDayIsIdempotentScopedAndWarnsOnNextPreview |
| DELETE already empty | Same method, second DELETE iteration |
| Wrong role 403 | Same method, MERCHANDISER and SYSTEM_ADMIN loop |
| Other Store 404 | Same method, foreign Store Admin |
| Unknown batch 404 | Same method, unknown id 2147483647 |
| Next preview/snapshot missing-push warning | Same method, next preview's item expiryEvidence.warnings; snapshot persistence of missing warning additionally covered by the method below |
| Both warnings together | PushDayIntegrationTest#missingPushDayAndZeroSalesBothWarnInScoresPreviewAndFrozenSnapshot |
| Structured ended-season fields | RecommendationIntegrationTest#endedSnapshotSeasonHasStructuredFlagsAndPreservesFrozenEvidence; inherited by RecommendationMySqlIntegrationTest |
| Unsupported config version | PushDayIntegrationTest#unsupportedStoredConfigVersionReturnsCodedError |
| Unsupported snapshot version | PushDayIntegrationTest#corruptSnapshotVersionReturnsCodedErrorWithoutChangingStoredJson |

### Every skipped feature group and its covering run

The package-only run omitted the MySQL and multi-JVM enable flags. Its skips are not passes. All entries below execute successfully in the full opt-in run; marked rows also execute in the 72-case MySQL group. The five JPA skips of the full run are a different group and pass in the supplementary run.

| Class | Gate | Feature SKIP entries | Full opt-in PASS invocations | Also MySQL group |
|---|---|---:|---:|---|
| MiningProgressMigrationTest | MySQL | 1 | 1 | no |
| WeightV2MigrationTest | MySQL | 1 | 1 | no |
| MiningSchedulerMultiJvmMySqlTest | multi-JVM | 12 | 12 | no |
| ProductPersistenceMySqlIntegrationTest | MySQL | 18 | 18 | no |
| RecommendationMigrationTest | MySQL | 2 | 2 | yes |
| RecommendationHardeningMySqlIntegrationTest | MySQL | 13 | 15 | no |
| MiningMultiJvmMySqlTest | multi-JVM | 5 | 6 | no |
| VersionedUtilityPersistenceMySqlTest | MySQL | 3 | 3 | no |
| CategoryPersistenceMySqlIntegrationTest | MySQL | 14 | 14 | no |
| MiningMySqlIntegrationTest | MySQL | 30 | 30 | no |
| RecommendationExpiryMySqlIntegrationTest | MySQL | 5 | 5 | no |
| MiningSchedulerUpgradeCompatibilityMySqlTest | multi-JVM | 5 | 5 | no |
| MiningScheduleReservationMySqlTest | MySQL | 1 | 1 | no |
| MiningMigrationMySqlTest | MySQL | 5 | 5 | yes |
| RetailCatalogUtilityMySqlIntegrationTest | MySQL | 8 | 8 | yes |
| MiningScheduledActivationE2EMySqlTest | MySQL | 3 | 3 | no |
| RecommendationMySqlIntegrationTest | MySQL | 17 | 17 | no |
| MiningScheduledDatabaseOutageMySqlTest | MySQL | 1 | 1 | no |
| MiningDispatchLeaseMySqlTest | MySQL | 1 | 1 | no |

Totals: **123 MySQL + 22 multi-JVM = 145 skipped entries**, covered by **148 PASS invocations** in full opt-in. The difference is parameter expansion: `RecommendationHardeningMySqlIntegrationTest` has two skipped parameterized methods that each run twice (13→15); `MiningMultiJvmMySqlTest` has one that runs twice (5→6). No skipped method remains uncovered. `feature-skip-coverage.json` (external evidence).

The owner authorized the exact local fix commit after this review. No push is authorized. The completed implementation/test evidence above remains the verification baseline for this unchanged source.

### Local commit delivery
Coverage review completed: all required cases covered and all 145 feature skips reconciled to passing full opt-in invocations. This record accompanies the local fix commit `fix(f02,f03,f06): clear batch push day, combine warnings and expose season/version errors`. Git log/status after the commit are the authoritative delivery evidence. No push.

## Evidence kept outside the repository

Location: **outputs/evidence/2026-11-16/ in the project folder (not in Git)**. These files retain their original bytes, including the original execution provenance. All eight copies were compared against the original commit and verified by SHA-256 before removing the repository copies. The historical counts, baseline comparison, test-method mapping, per-class skip coverage and NOT RUN items remain above.

| File | SHA-256 |
|---|---|
| `full-optin-counts.json` | `d81879e849fdf156ebb0db8a35b852eb3bcbcee921d93fcd678d7650ca5c0fea` |
| `feature-suites-counts.json` | `f17a39227a1e373b105f111995aec20c987fe82217e653ebfaacd692b835effb` |
| `feature-skip-coverage.json` | `e7f6bbe6a808e3f13922897802d1be0738a24e00e11edc3f7b1c2d64b2379a7d` |
| `mysql-group-counts.json` | `4ca1d19e0b7dd8b5299755907e68f956c825246d944fd5c222194df308fe6c86` |
| `FINAL_QA.json` | `7f13681b4203dff1ae90e77b6515cf7bae3156081897dd69e3d0f00e5ccc242d` |
| `development-attempts.json` | `6aaa0cd081738fbadd685e21c899d85174bc5577414ed8c6826f379e8692c960` |
| `jpa-status.json` | `be00a5e908a321cf04c83dbfa7da97d37077626dc0616665452f9b484e3794f5` |
| `baseline-comparison.json` | `b42e9486204b3523ebd797686dd36a0ab0742e564f0422dd6aa07a5e8b597d7a` |

Owner-approved local cleanup: only this HEAD commit is amended, with the same message. Source/tests/migrations are untouched. Earlier records permit metadata normalization under rule 8; their historical test conclusions are preserved. No new test run is needed because the src tree remains identical to the original fix commit. No push.

### Older documentation metadata cleanup
A separate docs-only commit normalizes historical machine paths and the local account name into placeholders. Rule 8 permits metadata normalization while preserving results, failures, limits and links. `<project-folder>`, `<worktree>`, `<evidence-dir>`, `<temporary-dir>`, `<reference-dir>`, `<user-home>`, `<java17-home>` and `<implementation-author>` describe environment roles rather than concrete machine locations or personal identity. Test results and review/approval decisions were not changed. Two documentation-audit helpers use equivalent escaped/constructed path signatures so their local-path detection still works without embedding literal workstation prefixes. Their syntax and detector equivalence were checked in memory; no files were generated. No src/test/migration changes or new Maven execution.

Cleanup verification: **1040 relative Markdown links, 0 broken**; **8/8 external SHA-256 copies match the original fix commit**. Original src tree fingerprint: `a20970025d3040b80daf602856a681b9d385c1fd`; unchanged after the amend and docs normalization. No test rerun. The exact requested path/username grep has zero matches under docs; any remaining non-doc matches are reported in the delivery response without edits. No push.

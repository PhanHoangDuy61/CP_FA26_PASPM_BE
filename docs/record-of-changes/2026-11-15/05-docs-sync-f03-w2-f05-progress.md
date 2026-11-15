# ROC-20261115-005 — Docs sync: feature docs, decisions and records for F03 Weight v2 and F05 progress

| Field | Value |
|---|---|
| Record ID | ROC-20261115-005 |
| Date / timezone | 2026-11-15 / Asia/Ho_Chi_Minh |
| Type | Documentation |
| Features | F02, F03, F05, F06, F07/F08 (status note), F01 (one session-impact line), F04 (dependency line) |
| Branch / base HEAD | `Khoi/feat/f05-progress-f03-weight-v2` @ `c8c837a` (5 commits ahead of `origin/dev` `8f94f94`) |
| Execution status | COMPLETED — documentation only; no code, test, migration, script or `openapi.json` change |
| Release status | UNRELEASED — not committed; one docs commit proposed |
| Daily summary | [README of the day](README.md) |

## 1. Request and scope

Bring every feature doc, the cross-cutting docs (RBAC, API, Frontend handoff, decisions, requirements, schema/ERD notes, indexes) and the record of changes in line with the branch. Code is the source of truth: behaviour statements cite `file:line` on `c8c837a` (code identical to `e38b55f`). Earlier rules are relabelled legacy/superseded with the date, not deleted. Drawings and Word reports are not edited.

## 2. Change details

### Added
- [Owner decision register 2026-11-15](../../decisions/OWNER_DECISIONS_F03_F05_2026-11-15.md) and eleven decision records [DEC-20261115-01…11](../../decisions/2026-11-15/) (P2–P6 listed as assumptions pending owner confirmation).
- [F03 Weight v2 requirements](../../requirements/F03_WEIGHT_V2_REQUIREMENTS.md) — derived from owner decisions 2026-11-15, pending formal approval.
- This record.

### Changed (summary — full table in §4)
- Feature READMEs F02, F03, F05, F06: "Current status" tables with code evidence, superseded markers, limitations / known gaps / NOT VERIFIED. F04: v(i) dependency. F01 session guide: one line. Features index: F07/F08 row.
- RBAC matrix: verification banner with controller/`SecurityConfig` lines; progress rows and Merchandiser POST in the main table; batch `pushDay`; counts 124 operations; legacy relabels.
- API docs: index statuses and counts (102 paths / 124 operations); legacy banners in F02, F05 (`WeightConfig`, `LambdaPolicy` schema sections), F06 (eligibility and expiry-batch interfaces); F07/F08 priority-coverage note.
- Frontend handoff: the 14 breaking changes with old → new shape, affected screen and code evidence; F05 progress polling (1–2 s, stop on terminal `status`, `attemptNo` may increase); Option B handoff and F02 handoff relabels; F07/F08 handoff note.
- Schema notes: V24/V25 evidence lines and the list of diagram pages to redraw; diagram index pointer.
- Records 002, 003, 004: dated addenda (commits, migrations, counted runs, invalid runs, NOT RUN, before-deploy checklist, report changes). Record 2026-11-11/01: four dead source links turned into plain paths with a dated note.

## 3. Files changed

| Area | Files |
|---|---|
| Feature docs | `features/README.md`, `features/F01/F01_SESSION_MANAGEMENT.md`, `features/F02/README.md`, `features/F03/README.md`, `features/F04/README.md`, `features/F05/README.md`, `features/F06/README.md`, `features/F06/F06_IMPLEMENTATION_SUMMARY.md` |
| API | `api/README.md`, `api/F01_F02_RBAC_MATRIX.md`, `api/F02_PRODUCT_INVENTORY_API.md`, `api/F05_MINING_API.md`, `api/F06_RECOMMENDATION_API.md`, `api/F07_F08_LAYOUT_CAMPAIGN_API.md` |
| Frontend handoff | `frontend_handoff/F03_W2_BREAKING_CHANGES.md`, `frontend_handoff/F02_HANDOFF.md`, `frontend_handoff/OPTION_B_BACKEND_API_HANDOFF.md`, `frontend_handoff/F07_F08_HANDOFF.md` |
| Decisions | `decisions/OWNER_DECISIONS_F03_F05_2026-11-15.md` (new), `decisions/2026-11-15/DEC-20261115-01…11-*.md` (11 new) |
| Requirements | `requirements/F03_WEIGHT_V2_REQUIREMENTS.md` (new), `requirements/README.md` |
| Schema / diagrams index | `architecture/F03_W2_SCHEMA_V24_V25.md`, `diagrams/README.md` |
| Records / indexes | `record-of-changes/2026-11-15/02-…`, `03-…`, `04-…` (addenda), `05-…` (new), `record-of-changes/2026-11-15/README.md`, `record-of-changes/README.md`, `record-of-changes/2026-11-11/01-f01-session-implementation.md` (links), `DOCUMENTATION_MAP.md`, `README.md` (docs portal) |

## 4. Statement changes (old → new, with code evidence)

Paths relative to `src/main/java/vn/edu/fpt/paspm/` unless shown fully.

| Document | Section | Old statement | New statement | Code evidence |
|---|---|---|---|---|
| features/F02/README.md | §1, §4 | `expiryPushDays` is the F03 threshold, owned here | Batch `pushDay` (≤ expiry, Store Admin) is used; `expiryPushDays` accepted, stored, ignored (P5) | `catalog/CatalogContracts.java:38`, `:57`, `:63`, `:69`; `catalog/inventory/InventoryBatchService.java:44`, `:52-53`, `:68-70` |
| features/F02/README.md | §4 PATCH | `remainingQuantity`, `status`; at least one field | adds `pushDay`, which counts toward the rule | `catalog/inventory/InventoryBatchService.java:52` |
| features/F03/README.md | Current status | (none; v1.2 formula in §1 before c8c837a) | W2 formula, FEFO, N = 30, v = 0, seasons, λ ownership, 410 routes, conversion, versioned readers — each with evidence | `utility/UtilityMath.java:31-39`; `utility/ExpiryRiskCalculator.java:41-84`; `utility/WeightV2Calculator.java:30`, `:39-43`; `utility/UtilityJsonVersions.java:29-31`; `utility/WeightV2Conversion.java:44-49` |
| features/F05/README.md | Current branch update | "No F03 Weight v2 behavior is implemented yet" | Status table: progress stages/DTO, `attemptNo` = `lease_fence`, Merchandiser submit, λ_max block, scheduler 503, W2 scheduled snapshots | `mining/MiningProgressController.java:22-29`; `mining/MiningContracts.java:25-30`; `mining/MiningDispatchLease.java:21`, `:41-43`; `mining/MiningService.java:52-53`, `:83-84`; `mining/MiningScheduleService.java:67` |
| features/F05/README.md | §1 | "The approved requirements give the System Admin three duties" (current) | Same text labelled historical wording before P1 | `security/SecurityConfig.java:94` |
| features/F06/README.md | §4 | Expiry push: `0 < daysRemaining ≤ Product.expiryPushDays` | Legacy; current: batch push day ≤ today | `utility/PushDayRule.java:23`; `recommendation/RecommendationExpiryService.java:43` |
| features/F06/README.md | Current status | — | `EligibilityV2`, W2 fingerprint, one-time `inventoryChanged`, ended-season warning, no re-run | `recommendation/ProductEligibilityService.java:28`, `:39`, `:56`; `recommendation/RecommendationService.java:80-81`; `recommendation/RecommendationRepository.java:42` |
| api/F07_F08_LAYOUT_CAMPAIGN_API.md | new section | `priorityCoverage: null` without explanation | Still null; "priority item = W > 1" not wired | `layout/LayoutCampaignRepository.java:344-345`; `layout/LayoutCampaignService.java:267` |
| api/F01_F02_RBAC_MATRIX.md | F05 rows | Progress routes only in a separate "uncommitted" section | Progress routes + Merchandiser POST in the main table | `mining/MiningProgressController.java:22-29`; `security/SecurityConfig.java:93-94` |
| api/F01_F02_RBAC_MATRIX.md | counts | 92 operations (12 F03, 24 F05) as current | 124 operations (F03 20, F05 26, …); older counts historical | `docs/api/openapi.json` (102 paths) |
| api/README.md | export counts | "98 paths and 116 operations" as current | 102 paths / 124 operations; 96/114 and 98/116 historical | `docs/api/openapi.json` |
| api/F05_MINING_API.md | `WeightConfig`, `LambdaPolicy` schemas | Presented as current snapshot shapes | Legacy (pre-V25); W2 uses `FrozenV2`, `LambdaV2` | `utility/UtilityJsonVersions.java:29-31`; `utility/UtilityVersionedContracts.java:27` |
| api/F06_RECOMMENDATION_API.md | `RecProductEligibility`, `RecExpiryBatch`, expiry-push rule | Current shapes/rule with `expiryPushDays` | Legacy; current shapes cited | `recommendation/RecommendationVersionedContracts.java:14`; `recommendation/RecommendationExpiryService.java:23` |
| frontend_handoff/OPTION_B_BACKEND_API_HANDOFF.md | §3 "Never call" | `POST /api/mining/requests` always 403 | Superseded: Merchandiser own-Store submission | `security/SecurityConfig.java:94`; `mining/MiningController.java:24` |
| frontend_handoff/F03_W2_BREAKING_CHANGES.md | Breaking table | Old/new without screen or evidence | Old → new, screen affected, evidence per row; progress polling section | per row in that file |
| frontend_handoff/F03_W2_BREAKING_CHANGES.md | Polling | — | Stop on terminal `status` only; `LEGACY` is not terminal | `src/main/resources/db/migration/V24__f05_mining_progress.sql:2`; `mining/MiningDispatchLease.java:21`, `:49` |

## 5. Findings — code looks wrong or unclear (not fixed)

| # | Finding | Evidence | Impact |
|---|---|---|---|
| F1 | An unknown or corrupt stored `scoringVersion` (or any unreadable stored JSON) raises `IllegalStateException`, which `ApiError` does not map → generic HTTP 500 | `utility/UtilitySettingsRepository.java:51`; `common/ApiError.java:17-47` | Clients see 500 instead of a clear 409/500 with a code; run the §6 query of record 004 before deploy |
| F2 | Batch PATCH cannot clear `pushDay` (`COALESCE(?, push_day)`) | `catalog/inventory/InventoryBatchRepository.java:78` | A wrong push day can only be moved, not removed |
| F3 | Season-name uniqueness is case-insensitive in the service but the DB unique constraint follows collation (case-sensitive on H2) | `utility/SeasonRepository.java:34-35`; `src/main/java/db/migration/V25__f03_weight_v2_expiry_season.java:36` | Concurrent creates differing only in case could pass on a case-sensitive collation |
| F4 | `mining_run.attempt_no` is exposed but never incremented | `mining/MiningRepository.java:46`; `src/main/resources/db/migration/V9__f05_mining_engine.sql:35` | Easy to confuse with progress `attemptNo` |
| F5 | When v = 0, status `SLOW_MOVING` hides `MISSING_PUSH_DAY` (the batch list is still returned) | `utility/ExpiryRiskCalculator.java:60`, `:84` | UI must read `batchesMissingPushDay`, not only the status |
| F6 | Ended-season warning is free text in `explanation`, not a structured field | `recommendation/RecommendationRepository.java:42` | Harder for the Frontend to badge |
| F7 | F06 eligibility runs one sales aggregate per product | `utility/UtilityProductRepository.java:34`; `recommendation/ProductEligibilityService.java:44` | Not measured for large lists |
| F8 | Old instances cannot read W2 Store configs (strict Jackson, legacy `WeightConfig`) — inferred, **NOT VERIFIED** | `utility/UtilityContracts.java:19`; `src/main/resources/application.yml:12-14` | Stop all old instances before V25 |
| F9 | Progress `percent` can decrease after a lease recovery | `mining/MiningProgressReader.java:22-38` | By design; documented for the Frontend |

## 6. Diagram and report changes still needed (not done)

- Diagrams: see [schema notes — pages to redraw](../../architecture/F03_W2_SCHEMA_V24_V25.md#diagram-pages-that-must-be-redrawn-not-edited-in-this-pass) (conceptual and logical ERD, F03 area page, R2-06.02/03/04 state machines, F03/F05 class pages, sequence R2-08.03 and the F03 snapshot sequence, use cases and screen flow).
- Reports (Word, not edited): SRS BR-05, §3.4, mining use cases, screen authorization table; Report 1 roles; Report 4 §4.1 (λ owner, seasons, 1.115 → 1.12). Details: [record 004 addendum](04-f03-weight-v2-expiry-season.md#report-changes-needed-word-reports-not-edited-here).

## 7. Verification (this docs pass; no Maven/test run)

| Check | Result |
|---|---|
| Relative links under `docs/` (all `*.md`, fenced code ignored) | **1,035 checked, 0 broken.** Fixed: 4 dead source links in `2026-11-11/01-f01-session-implementation.md` (classes not on current branches) → plain paths with a dated note; 1 forward link to this record (resolved by creating it) |
| Orphans | Every new file has ≥ 1 inbound link (register 18, requirements 6, each DEC ≥ 1, this record 2) |
| Scope | `git status` / `git diff --stat`: `docs/` only — 29 modified files + 14 new files; no `src/`, test, migration, script or `openapi.json` change |
| API docs vs `openapi.json` | 124/124 operations named in `docs/api/*.md`; extra in docs only: `GET /api/utility/snapshots` (listed as NOT IMPLEMENTED, kept) |
| Stale terms (`stock_priority`, `stockPriorityScore`, `gamma`, `enableStock`, `lambdaMin`, `expiryPushDays`, binary expiry, "System Admin only" submitter) | Every remaining hit outside `archive/` and `reference*/` is either labelled legacy/superseded/deprecated with the date, or in a dated historical report/plan (`reports/*2026-11-02*`, `testing/reports/RETAIL_CATALOG_UTILITY_VERIFICATION.md`, `superpowers/plans|specs/2026-10-21*`), or still true (`architecture/MIG_JPA_F02_01_IMPLEMENTATION_REPORT.md`: entity does not map the unused score columns). Not edited by rule: `requirements/F06_RECOMMENDATION_RULES_v1_1.md` (approved original; its `expiryPushDays` clause is marked superseded in the F06 README and the decision register) |

## 8. Proposed commit (not executed)

`docs: sync feature docs, decisions and record of changes for F03 Weight v2 and F05 progress`

## 2026-11-16 delivery addendum
The owner-approved 43-file documentation sync was committed locally as `369dba4` with message `docs: sync feature docs, decisions and record of changes for F03 Weight v2 and F05 progress`. The working tree was clean immediately afterward. No push or merge. Follow-up implementation is recorded in [record 006](../2026-11-16/06-f03-f05-followup-fixes.md); earlier evidence above remains historical.

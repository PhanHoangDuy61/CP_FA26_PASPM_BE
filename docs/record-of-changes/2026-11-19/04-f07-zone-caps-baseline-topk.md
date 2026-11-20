# ROC-20261119-004 — F07/F08 zone capacities, top-K baseline A and equal-size simulation

| Field | Value |
|---|---|
| Record ID | ROC-20261119-004 |
| Date / timezone | 2026-11-19 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Opus 5.5) |
| Type | Implementation |
| Features | F07 layout (capacities, baseline A, utility score), F08 simulation report |
| Branch / base HEAD | `Khoi/feat/f07-zone-caps-baseline-topk`, created from `origin/dev` `530651bad5b02d90bcb48ecffaf7fa5a0f6b0b7e`; `origin/dev` `222e54f` merged into the branch before the final verification |
| Execution status | COMPLETED for the scope below |
| Release status | PR_OPEN pending — branch pushed; the pull request is opened on GitHub (`gh` not available) |
| Daily summary | [2026-11-19](README.md) |

## 1. Request and scope

Owner decisions of 2026-11-19, recorded as [DEC-20261119-06](../../decisions/2026-11-19/DEC-20261119-06-layout-caps-topk-baseline-equal-size.md): zone capacities for layout B and baseline A, a top-K baseline (`F07_BASELINE_V2`), and an equal-size comparison in F08. Historical layouts, revisions and reports stay as stored. No migration.

## 2. Change details

### Added
- `application.yml` `paspm.layout.max-blocks-by-zone` / `max-products-by-zone`: BANNER 3/3, BUNDLE 4/16, CROSS_SELL 2/8, FBT 2/8, BASELINE 35/35, each overridable through `PASPM_LAYOUT_MAX_{BLOCKS,PRODUCTS}_<ZONE>`. Map keys are bracketed (`"[BANNER]"`) so Spring keeps their upper case.
- `LayoutRules.baselineK()`, `baselineSelection()`, `equalSizePrefix()`, `equalSizeAssumptions()`.
- `LayoutView.equalSizeBaselineScore`, `LayoutView.equalSizeProductCount` (layout B only; additive).
- Tests: `LayoutRulesTest` (zone boundaries, top-K, unconfigured BASELINE, prefix and N > |A|); four new `LayoutCampaignIntegrationTest` cases (policy caps, > 35 products, V1 exemption, equal-size metrics and V1 report read unchanged).
- [F08 layout-size sensitivity](../../testing/F08_LAYOUT_SIZE_SENSITIVITY_20261119.md) (Part 3 evidence for RQ3).

### Changed
- Campaign creation stores A as the top-K products (frequency order unchanged) with `baseline_params` `ruleVersion: F07_BASELINE_V2`, `k`, `candidateProducts`; without a BASELINE cap the V1 parameters and behaviour are kept.
- Layout validation applies the BASELINE cap only to V2 baselines (stored V1 A layouts keep their size and validity).
- Simulation `OFFLINE_REPLAY_V2`: A's metrics use the first N products of A in placement order (N = distinct products of B; all of A when N > |A|); assumptions add `comparisonMode`, `comparisonRule`, `comparedProductCountN`, `baselineProductCount`, `baselineProductsCompared`, `baselineK`, `nExceedsBaseline`.
- Stored JSON columns (`baseline_params`, report `assumptions`) are read through one helper that unwraps a JSON string literal (H2 returns one for text written with `setString`; MySQL returns the object, so MySQL behaviour is unchanged).
- `LayoutCampaignIntegrationTest.storeIsolationRolesAndReadOnlyBaselineAreEnforced` asserted the unconfigured (unbounded) policy; it now asserts the configured BASELINE caps.

### Documentation
[DEC-20261119-06](../../decisions/2026-11-19/DEC-20261119-06-layout-caps-topk-baseline-equal-size.md); [F07/F08 API](../../api/F07_F08_LAYOUT_CAMPAIGN_API.md) (2026-11-19 section); [openapi.json](../../api/openapi.json) (two nullable `LayoutView` properties from the runtime export); [F07/F08 handoff](../../frontend_handoff/F07_F08_HANDOFF.md); this day summary and the record index (records 002–004).

## 3. Files changed

| Path | Change |
|---|---|
| `src/main/resources/application.yml` | Modified: zone capacities |
| `src/main/java/vn/edu/fpt/paspm/layout/LayoutRules.java`, `LayoutCampaignService.java`, `LayoutContracts.java` | Modified |
| `src/test/java/vn/edu/fpt/paspm/layout/LayoutRulesTest.java` | Added |
| `src/test/java/vn/edu/fpt/paspm/layout/LayoutCampaignIntegrationTest.java` | Modified |
| `docs/…` | See §2 Documentation; `docs/testing/F08_LAYOUT_SIZE_SENSITIVITY_20261119.md` added |

## 4. API and compatibility impact

Additive only: `LayoutView.equalSizeBaselineScore` (number, nullable) and `LayoutView.equalSizeProductCount` (integer, nullable). `GET /api/layouts/policy` now returns the configured caps. New reports carry `algorithmVersion: OFFLINE_REPLAY_V2` and the new assumption keys; V1 reports are returned as stored. B placements above a cap are rejected with 400 (rule unchanged, caps now configured). Campaign creation succeeds for snapshots with more than 35 products. No route was added or removed.

## 5. Verification executed in this session

Environment: Java 17.0.21 (Temurin), SPMF 2.67a on Java 25, disposable `mysql:8.4` (8.4.13) container on loopback, H2 for H2 suites.

| Command / method | Result |
|---|---|
| `./mvnw -o -B test -Dtest=LayoutRulesTest,LayoutCampaignIntegrationTest` (TDD: compile-red first, then 3 integration + 1 unit failure fixed) | 18 tests, 0 failures, 0 errors, 0 skipped |
| `RecommendationIntegrationTest#openApiExposesActualRecommendationOperations` with `-Dpaspm.f06.export-dir=…` (runtime export) | PASS; only the two `LayoutView` properties were inserted into `openapi.json` |
| Full suite on the branch after merging `origin/dev` `222e54f`: `./mvnw -o -B clean verify -DargLine=-Djava.io.tmpdir=… -Dpaspm.test.mysql=true -Dpaspm.test.java17-backend=true -Dpaspm.test.multijvm=true -Dpaspm.test.java17=… -Dpaspm.test.spmf-jar=… -Dpaspm.test.spmf-java=… -Dpaspm.avatar.directory=… -Dlogging.level.root=ERROR` | **798 tests, 1 failure, 0 errors, 11 skipped**. The failure, `CatalogIntegrationTest.openApiListsF02Endpoints` ("Expected no value at `$.components.schemas.ProductRequest.properties.sku.pattern` but found `^SKU-[A-Z0-9-]{11}$`"), is **pre-existing**: the same test fails alone on a clean `origin/dev` `222e54f` (introduced by the F02 merge `d22f735`), and this branch does not touch F02. Skipped: opt-in classes `FoodMartScaleSyntheticE2EMySqlIntegrationTest` (3), `MiningSchedulerUpgradeCompatibilityMySqlTest` (1), `MiningSchedulerMultiJvmMySqlTest` (2), `PilotCloneApiIntegrationTest` (5) |
| Layout-size sensitivity on the demo seed (backend JAR of commit `24ab62d`, caps overridden to 20 / 35 / 50) | see the sensitivity document |
| GitHub CI | NOT RUN (runs when the PR is opened) |

## 6. Previous evidence referenced

[ROC-20261119-001](01-record-owner-decisions.md) (DEC-20261112-01 two-layout A/B); demo seed record ROC-20261117-014.

## 7. Limitations and next actions

- The runtime OpenAPI export also differs from the checked-in `openapi.json` in `ProductRequest`, `/api/transactions/imports/csv/async` and `/api/products/{id}/batches`. That drift is already on `dev` and is not changed here.
- With the owner caps, B holds at most 35 products (3 + 16 + 8 + 8) and K = 35, so N > |A| occurs only for smaller snapshots or overridden caps; it is covered by `LayoutRulesTest`.
- Sensitivity sizes 35 and 50 reached only N = 21 because the approved pool has 21 distinct products.
- `priorityCoverage` stays `null` (DEC-20261117-02, not part of this change).
- `CatalogIntegrationTest.openApiListsF02Endpoints` fails on `dev` itself since the F02 merge; it is in the CI `backend` group, so CI is red until F02 fixes it.

## 8. Delivery

Commits on `Khoi/feat/f07-zone-caps-baseline-topk`, pushed to `origin`; no merge into `dev` from this session.

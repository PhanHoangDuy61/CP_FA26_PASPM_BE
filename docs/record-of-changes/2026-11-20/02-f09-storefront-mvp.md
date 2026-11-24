# ROC-20261120-002 — F09 Storefront MVP

| Field | Value |
|---|---|
| Record ID | ROC-20261120-002 |
| Date / timezone | 2026-11-20 / Asia/Ho_Chi_Minh |
| Agent | Copilot SDK assistant |
| Type | Implementation / Database / Documentation |
| Features | F02, F06, F07/F08 integration; F09 |
| Branch / base HEAD | `Duy/Feature/F09_Storefront`, base `530651b` |
| Execution status | COMPLETED |
| Release status | UNRELEASED |
| Daily summary | [README](README.md) |

> **Superseded in part (2026-11-25).** Renumbered from `01-…` to `02-…` when merged with `dev` (number 01 was taken by the product-docs record). The merge ([ROC-20261125-002](../2026-11-25/02-f09-storefront-merge-into-dev.md), [DEC-20261125-01](../../decisions/2026-11-25/DEC-20261125-01-f09-public-store-follows-store-status.md)) renumbered V27 → V29 and removed `store_slug`, `storefront_enabled` and the visibility endpoint. The text below is kept as written on 2026-11-20.

## 1. Request and scope

Implement the backend F09 MVP requested in the task: public Store selection, Homepage, Product list/detail and eligible selected-layout recommendations; SYSTEM_ADMIN visibility toggle; no Related Products/F10 or `priorityCoverage`.

The checkout HEAD is `530651b`; `origin/dev` is `6d2a877`, 30 commits ahead. No branch switch, rebase or reset was performed. The requested `docs/features/F09/F09_STOREFRONT_PLAN_API_SCREENS.md` was absent from both this checkout and `origin/dev`. Existing V25 and V26 migrations were present; inspection of refs/worktrees found no V27+ reservation, so V27 was used. Existing migrations were not changed.

## 2. Change details

### Added

- Anonymous public APIs: `GET /api/storefront/stores`, `/api/storefront/{storeSlug}/home`, `/products`, `/products/{productId}`.
- `PUT /api/system/stores/{storeId}/storefront-visibility`, SYSTEM_ADMIN-only.
- Public F09 DTOs, slug generation, Storefront repository/service, H2 integration tests and Java Flyway V27.
- F09 API contract, synthetic JSON examples, feature index, frontend handoff/checklists, RBAC/OpenAPI updates, and this record.

### Changed

- F02 Product create/update/view accept optional `imageUrl` and `description`; F02 remains authenticated and continues returning its administrative fields.
- Visibility defaults false. Existing Store slugs are deterministically backfilled with a collision-resistant suffix.
- Homepage uses the ACTIVE campaign's selected READY layout; no-active-campaign behavior is `DEFAULT_CATALOG`, not baseline A. Recommendation blocks are rechecked and filtered.
- Availability is summarized as `AVAILABLE|UNAVAILABLE`, using a bulk Store-scoped query and Store-local date.

### Security

Public responses use dedicated DTOs and exclude Store IDs, cost, stock quantity, batch/expiry details, F06 evidence/review metadata and F07 utility. Unknown and disabled Store slugs return the same 404. Existing authenticated `/api/storefront/active-layout` remains non-public.

### Database

V27 adds nullable unique `store.store_slug`, `store.storefront_enabled NOT NULL DEFAULT FALSE`, and nullable Product `image_url`/`description`. Store slugs are backfilled; V1–V26 are unchanged. Existing Stores remain private.

### Documentation

F02 contract/handoff, F09 API/OpenAPI/RBAC, examples, frontend checklists, feature indexes and root documentation overview were updated. The missing draft F09 plan was not reconstructed or represented as a source.

## 3. Files changed

| Path | Change | Reason |
|---|---|---|
| `src/main/java/db/migration/V27__f09_storefront.java` | Added | Store/Product schema and safe slug backfill |
| `src/main/java/vn/edu/fpt/paspm/storefront/` | Added | Slug, public contracts, repository, service, controller |
| `src/main/java/vn/edu/fpt/paspm/catalog/CatalogContracts.java` | Modified | F02 Product display fields |
| `src/main/java/vn/edu/fpt/paspm/catalog/SystemStoreController.java` | Modified | SYSTEM_ADMIN visibility API |
| `src/main/java/vn/edu/fpt/paspm/catalog/product/` | Modified | Persist and validate display metadata |
| `src/main/java/vn/edu/fpt/paspm/layout/LayoutRules.java` | Modified | Fail closed if active public layout caps are unconfigured |
| `src/main/java/vn/edu/fpt/paspm/security/SecurityConfig.java` | Modified | Permit only the intended anonymous F09 GET routes |
| `src/test/java/vn/edu/fpt/paspm/storefront/StorefrontIntegrationTest.java` | Added | F09 public API, privacy, eligibility, bulk availability, RBAC/OpenAPI checks |
| `src/test/java/vn/edu/fpt/paspm/catalog/CatalogIntegrationTest.java` | Modified | F02 metadata request/view and URL validation coverage |
| `src/test/java/vn/edu/fpt/paspm/catalog/product/persistence/ProductPersistenceIntegrationTest.java` | Modified | Persistence assertions |
| `src/test/java/vn/edu/fpt/paspm/deployment/MigrationVersioningTest.java` | Modified | V27 version and V26 upgrade coverage |
| `.github/scripts/test-groups.py` | Modified | Include F09 integration test in backend test grouping |
| `docs/api/F09_STOREFRONT_API.md`, `docs/features/F09/README.md` | Added | F09 contract and feature entry |
| `docs/frontend_handoff/F09_HANDOFF.md`, `docs/frontend_handoff/F09_examples/*.json` | Added | UI checklist and public payload examples |
| `docs/api/openapi.json` | Modified | Springdoc runtime export |
| `docs/api/README.md`, `docs/api/API_CHANGELOG.md`, `docs/api/F01_F02_RBAC_MATRIX.md` | Modified | F09 navigation, routes, roles and release history |
| `docs/api/F02_PRODUCT_INVENTORY_API.md`, `docs/features/F02/README.md`, `docs/frontend_handoff/F02_HANDOFF.md` | Modified | F02 metadata contract and public/admin DTO separation |
| `docs/frontend_handoff/README.md`, `docs/features/README.md`, `docs/README.md` | Modified | Feature indexes and current API/schema summary |
| `docs/record-of-changes/README.md`, `docs/record-of-changes/2026-11-20/` | Modified/Added | Session audit trail |

## 4. API and compatibility impact

Public routes are anonymous; list/home/products are scoped through enabled `storeSlug`. Product detail is scoped by Store and ACTIVE status. The admin visibility endpoint changes no campaign state. `GET /api/storefront/active-layout` keeps its existing auth behavior.

F02 Product API adds optional absolute HTTP(S) `imageUrl` (max 2,048 characters) and `description` (max 2,000). Existing Store records remain private after migration. F09 does not return cost, exact stock, batch/expiry data, recommendation ids/evidence/review history, utility, or `priorityCoverage`.

F09 uses the separately named `StorefrontProductPage` schema; the existing F02 `ProductPage` pagination contract is preserved.

When no campaign is ACTIVE, return `fallbackType=DEFAULT_CATALOG`; do not call it baseline A. An active layout requires configured recommendation-zone block/product caps; otherwise homepage responds 503. Related Products is deferred.

## 5. Verification executed in this session

| Command / method | Environment | Actual result | Evidence |
|---|---|---|---|
| `.\mvnw.cmd '-Dtest=StorefrontIntegrationTest,MigrationVersioningTest,CatalogIntegrationTest,ProductPersistenceIntegrationTest,LayoutCampaignIntegrationTest,RecommendationIntegrationTest#openApiExposesActualRecommendationOperations' '-Dpaspm.f06.export-dir=docs/api' test` | Local Java 17, H2 | PASS, 44/44; F09 6, F02 Catalog 7 + Product persistence 17, migration 4, F07/F08 9, F06 OpenAPI 1 | Surefire summary |
| `.\mvnw.cmd '-Dtest=StorefrontIntegrationTest' test` after expanding active-layout fixtures to all four recommendation types | Local Java 17, H2 | PASS, 6/6. An intermediate fixture attempt failed the F06 `CROSS_SELL` anchor constraint; the fixture was corrected and rerun successfully. This rerun overlaps the six F09 tests in the 44/44 suite and is not added to that count. | Surefire summary |
| Full `RecommendationIntegrationTest` + `LayoutCampaignIntegrationTest` attempt | Local Java 17, H2 | Layout 9/9 PASS; Recommendation 16/17 failed because mining returned `503 SPMF_JAR_NOT_FOUND`; isolated OpenAPI method later PASS 1/1 | Console output; see limitation |
| Runtime/static OpenAPI parsing and semantic comparison with base | PowerShell `ConvertFrom-Json` + Node JSON comparison | PASS, 105 paths / 126 operations; five F09 paths present; 20 additive nodes, zero removed/changed nodes; F02 `ProductPage` unchanged | `docs/api/openapi.json`; `StorefrontIntegrationTest` |
| Parse synthetic F09 JSON examples | PowerShell `ConvertFrom-Json` | PASS, 8 files | `docs/frontend_handoff/F09_examples/` |
| `git diff --check` | Local worktree | PASS | Console output |
| MySQL fresh/upgrade migration | NOT RUN | No MySQL migration execution in this session | — |

## 6. Previous evidence referenced

The task's context recorded the F07/F08 contract and F06/F02 source/tests already present in the checkout. Those prior test results are not counted as current-session verification. The supplied F09 plan was unavailable as noted above.

## 7. Limitations and next actions

- F08 zone capacity values remain owner-TBD. Active public Homepage fails closed with 503 until all recommendation-zone block/product caps are configured.
- The F06 full integration test needs a configured SPMF JAR; the local attempt did not pass. The isolated F06 OpenAPI test is used to export/verify docs without the mining runtime.
- MySQL migration, production migration, full Maven suite, hosted CI and browser/UI verification were not performed.
- `priorityCoverage` remains a separate F07/F08 follow-up. Related Products and F10 are deferred.

## 8. Delivery

No commit, push, merge or deployment was performed. All work remains local and unpublished.

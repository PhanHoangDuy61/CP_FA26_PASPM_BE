# ROC-20261125-005 — F09 Storefront MVP merged with dev

| Field | Value |
|---|---|
| Record ID | ROC-20261125-005 |
| Date / timezone | 2026-11-25 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Opus 5.5), requested by Nguyễn Minh Khôi |
| Type | Implementation / Database / Documentation |
| Features | F09 (with F01 Store management, F02 product, F07/F08 layouts) |
| Branch / base HEAD | `Khoi/merge/f09-storefront-be`, created from `origin/Duy/Feature/F09_Storefront` @ `0107582` (Duy Phan, 2026-11-21) and merged with `origin/dev` @ `82b0ced` |
| Execution status | COMPLETED |
| Release status | PR_OPEN pending (no PR opened by the agent) |
| Daily summary | [README](README.md) |

## 1. Request and scope

Bring the unmerged F09 Storefront branch into `dev` without undoing anything already on `dev` (Store management V27, F02 V28: `effectiveStatus`, `daysToExpiry`, warnings, product soft delete, server-generated SKU). Owner rules for the merge: renumber the F09 migration to the next free version, drop the branch's own `store_slug` and use `store.slug`, exclude soft-deleted and INACTIVE products and INACTIVE Stores. Owner answer during the merge: a Store is public exactly when ACTIVE, no separate visibility toggle ([DEC-20261125-04](../../decisions/2026-11-25/DEC-20261125-04-f09-public-store-follows-store-status.md)).

## 2. Change details

### Added
- Duy's F09 public API (`storefront/*`): `GET /api/storefront/stores`, `/{storeSlug}/home`, `/{storeSlug}/products`, `/{storeSlug}/products/{productId}` and `StorefrontIntegrationTest` (now in the CI `backend` group).
- F02 `ProductRequest` / `ProductView`: optional `imageUrl` (absolute HTTP(S)) and `description`, re-applied on top of `dev`'s server-generated SKU code.
- `LayoutRules.requireConfiguredPublicCapacities()` (503 when a zone cap is missing).
- [DEC-20261125-04](../../decisions/2026-11-25/DEC-20261125-04-f09-public-store-follows-store-status.md).

### Changed
- Public Store lookup: `store.slug` and `store.status='ACTIVE'` instead of `store_slug` / `storefront_enabled`.
- Every public product read adds `deleted_at IS NULL`; `availability` and placements report soft-deleted products as not ACTIVE.
- Availability counts a batch whose expiry date is today (`expiry_date >= today`, DEC-20261124-01; the branch used `>`).

### Removed (from the branch, never on dev)
- `V27__f09_storefront.java`, `StoreSlug`, `store.store_slug`, `store.storefront_enabled`, `PUT /api/system/stores/{storeId}/storefront-visibility`, `VisibilityRequest`/`VisibilityView`, the branch's edits to `SystemStoreController` (deleted on `dev`) and `store_visibility_not_found_error.json`.

### Database
- New `V29__f09_product_display_metadata.sql`: `product.image_url VARCHAR(2048) NULL`, `product.description TEXT NULL`. V1–V28 untouched.

### Documentation
- F09 API, handoff, examples, feature README; F02 API/handoff/README; RBAC matrix; API README, changelog and OpenAPI (111 paths / 138 operations); `docs/README.md`, feature index; product docs (SRS, SDD, BRD, GLOSSARY); Duy's 2026-11-20 record renumbered to 002.

## 3. Merge conflicts and resolution

| File | Conflict | Resolution |
|---|---|---|
| `catalog/CatalogContracts.java` | Branch added `imageUrl`/`description` on the old `ProductRequest` (with client `sku`) | Took `dev` (no `sku`, `confirmSimilarName`) and re-added the two fields |
| `catalog/product/ProductRepository.java`, `ProductService.java`, `persistence/ProductEntity.java`, `persistence/ProductMetadataPersistence.java` | Branch threaded image/description through the old SKU-taking signatures | Took `dev` (generated SKU, soft delete, similar-name check) and re-added the image/description overloads and `validateImageUrl` |
| `catalog/SystemStoreController.java` | Modified on branch, deleted on `dev` | Kept the deletion; the visibility endpoint is dropped (DEC-20261125-04) |
| `layout/LayoutRules.java`, `security/SecurityConfig.java` | Both sides changed nearby lines | Took `dev`, re-added the capacity guard and the anonymous F09 GET matchers |
| `CatalogIntegrationTest.java` | Old SKU-based body | Took `dev`, re-added the imageUrl/description assertions and the `javascript:` URL rejection |
| `docs/api/openapi.json` | Branch export on an old base | Took `dev`, inserted only the F09 paths/schemas and the two Product properties from the runtime export |
| `docs/README.md`, `docs/api/API_CHANGELOG.md`, `docs/api/F02_PRODUCT_INVENTORY_API.md`, `docs/features/F02/README.md`, `docs/frontend_handoff/F02_HANDOFF.md`, `docs/record-of-changes/README.md` | Both sides edited | Took `dev`, then re-added the F09 facts |
| `docs/record-of-changes/2026-11-20/README.md` (add/add) and `01-f09-storefront-mvp.md` | Number 01 taken by `dev` | Took `dev`'s README, renamed the branch record to `02-…` and listed it as record 002 |
| `V27__f09_storefront.java` (no textual conflict) | Duplicate Flyway version 27 | Removed; replaced by V29 |

## 4. API and compatibility impact

- Additive for staff clients: 4 anonymous GETs, 9 public schemas, 2 optional Product fields. `PUT /api/products/{id}` is a full replacement, so a client that omits `imageUrl`/`description` clears them (FE `dev` does not send them yet).
- No change to existing routes, RBAC or Store isolation. The INACTIVE-Store guard is unchanged for authenticated users; anonymous storefront reads hide INACTIVE Stores with 404.
- `ProductCard.availability` is `AVAILABLE | OUT_OF_STOCK` (PO review 2026-11-25, FR-09.3, DEC-20261125-04 addendum); the original branch's `UNAVAILABLE` is not exposed. Breaking only against the unmerged branch.

## 5. Verification executed in this session

| Command / method | Environment | Actual result | Evidence |
|---|---|---|---|
| `sh mvnw -B test -Dtest=StorefrontIntegrationTest,MigrationVersioningTest,CatalogIntegrationTest` | macOS arm64, Temurin 17.0.21.1, H2 | PASS — 24 tests, 0 failures, 0 errors, 0 skipped | local console |
| `sh mvnw -B clean verify -Dtest="$(python3 .github/scripts/test-groups.py backend)"` | same, H2 | PASS — 449 tests, 0 failures, 0 errors, 0 skipped, 66 classes, BUILD SUCCESS | local console |
| Fresh Flyway migrate (Flyway 11.7.2, `classpath:db/migration` incl. Java migrations) | disposable `mysql:8.4` container (8.4.13), empty schema | PASS — 29 migrations applied, now at V29; `product` has `image_url`, `description`; `store` has no `store_slug`/`storefront_enabled`; 0 failed history rows | local console |
| Runtime OpenAPI export (`-Dpaspm.test.export-openapi=true` on `TransactionExcelImportIntegrationTest`) | same | PASS — 1 test; diff inserted only | `docs/api/openapi.json` |
| PO review — `OUT_OF_STOCK`: `sh mvnw -B test -Dtest=StorefrontIntegrationTest` before the code change | Temurin 17.0.21.1, H2 | FAIL as expected — 7 tests, 2 failures (`UNAVAILABLE` returned) | local console |
| Same, after the change | same | PASS — 7 tests, 0 failures | local console |
| PO review — `sh mvnw -B clean verify -Dtest="$(python3 .github/scripts/test-groups.py backend)"` | same | PASS — 449 tests, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS | local console |
| `mysql` CI group, full `integration` suite | — | NOT RUN (by request) | — |

`sh mvnw` was used because `mvnw` is mode 100644 in git; the file mode was not changed.

## 6. Previous evidence referenced

The 2026-11-20 branch record ([ROC-20261120-002](../2026-11-20/02-f09-storefront-mvp.md)) reported H2 targeted tests on base `530651b`; that evidence predates the merge and is not counted here.

## 7. Limitations and next actions

- FE: no storefront screen calls these routes yet (FE storefront is mock). FE F02 edit form should send `imageUrl`/`description` back on PUT.
- `mysql` group and the integration job: NOT RUN locally; the integration job also depends on `PASPM_CI_SPMF_ARTIFACT_URL` (PR #29).
- ERD: add `product.image_url`, `product.description` (V29) to the next ERD update.

## 8. Delivery

Committed and pushed as branch `Khoi/merge/f09-storefront-be`; PR not opened by the agent (draft body prepared outside the repository). No merge, no deploy. The original branch `Duy/Feature/F09_Storefront` was not modified.

# ROC-20261125-005 — F09 Storefront MVP merged with dev

| Field | Value |
|---|---|
| Record ID | ROC-20261125-005 |
| Date / timezone | 2026-11-25 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Opus 5.5), requested by Nguyễn Minh Khôi |
| Type | Implementation / Database / Documentation |
| Features | F09 (with F01 Store management, F02 product, F07/F08 layouts) |
| Branch / base HEAD | `Khoi/merge/f09-storefront-be`, created from `origin/Duy/Feature/F09_Storefront` @ `0107582` (Duy Phan, 2026-11-21) merged with `origin/dev` @ `82b0ced`, then again with `origin/dev` @ `516183d` (which already contained the old F09 merge `68d6f0b`) |
| Execution status | COMPLETED |
| Release status | PR_OPEN pending (no PR opened by the agent) |
| Daily summary | [README](README.md) |

## 1. Request and scope

Bring the unmerged F09 Storefront branch into `dev` without undoing anything already on `dev` (Store management V27, F02 V28: `effectiveStatus`, `daysToExpiry`, warnings, product soft delete, server-generated SKU). Owner rules for the merge: renumber the F09 migration to the next free version, drop the branch's own `store_slug` and use `store.slug`, exclude soft-deleted and INACTIVE products and INACTIVE Stores. Owner answer during the merge: a Store is public exactly when ACTIVE, no separate visibility toggle ([DEC-20261125-04](../../decisions/2026-11-25/DEC-20261125-04-f09-public-store-follows-store-status.md)).

### 1a. Second merge: `dev` already had an old F09 merge

After the first merge, `origin/dev` moved to `516183d` and turned out to contain an **older F09 merge**: commit `68d6f0b` "Merge branch 'Duy/Feature/F09_Storefront' into dev", author Duy Phan, 2026-11-21 13:50 +07:00, logged by `7d5cfce` ([ROC-20261121](../2026-11-21/README.md)) and pushed to `dev` together with `7df3c34` "F09" (2026-11-25 09:05) **without a pull request**. GitHub associates `68d6f0b` with no merged PR (only open PR #29 contains it). It brought `V29__f09_storefront.java` (`store.store_slug`, `store.storefront_enabled`, `product.image_url`, `product.description`), `StoreSlug`, the visibility endpoint and `availability` `UNAVAILABLE`.

PO decision (addendum "Migrations" of [DEC-20261125-04](../../decisions/2026-11-25/DEC-20261125-04-f09-public-store-follows-store-status.md)): keep `dev`'s V29 exactly, remove this branch's `V29__f09_product_display_metadata.sql`, keep the runtime rules of this branch, and add **V30** to drop `store.store_slug` / `store.storefront_enabled`. V30 was free on every `origin/*` branch (checked 2026-11-25); V31 is reserved for F10.

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

### Removed
- From the branch: `V27__f09_storefront.java`, the branch's edits to `SystemStoreController` (deleted on `dev`), `store_visibility_not_found_error.json`, and (second merge) `V29__f09_product_display_metadata.sql`.
- From `dev` (old F09 merge): `PUT /api/system/stores/{storeId}/storefront-visibility`, `VisibilityRequest`/`VisibilityView`, use of `store.store_slug` / `store.storefront_enabled`, `availability` `UNAVAILABLE`.
- Kept from `dev` on purpose: `vn.edu.fpt.paspm.storefront.StoreSlug`, only because the frozen V29 calls it (javadoc says so; algorithm unchanged).

### Database
- `V29__f09_storefront.java` from `dev`: unchanged (byte-identical to `origin/dev`), so its Flyway history row stays valid on databases that already applied it.
- New `V30__f09_drop_old_storefront_columns.sql`: `DROP INDEX uq_store_store_slug ON store`, then drop `store.store_slug` and `store.storefront_enabled`. `product.image_url`/`description` (from V29) are kept. V1–V29 untouched.

### Documentation
- F09 API, handoff, examples, feature README; F02 API/handoff/README; RBAC matrix; API README, changelog and OpenAPI (112 paths / 139 operations); DEC-20261125-04 addendum "Migrations"; SDD migration table, SRS; `paspm-backend-workflow` next-migration note; `docs/README.md`, feature index; product docs (SRS, SDD, BRD, GLOSSARY); Duy's 2026-11-20 record renumbered to 002.

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
| Second merge, `V29__f09_storefront.java` (modify/delete) and `V29__f09_product_display_metadata.sql` | Two V29s | Kept `dev`'s V29 unchanged, deleted ours, added V30 |
| Second merge, `CatalogContracts`, `ProductRepository`, `ProductService`, `ProductMetadataPersistence` | Same image/description feature on both sides | Took this branch's version (same behaviour, record component order kept) |
| Second merge, `SecurityConfig.java` | Same F09 matchers, different order | Took this branch |
| Second merge, `LayoutRules.java` | `dev` F07 methods next to the F09 capacity guard | Kept both |
| Second merge, `StoreAdministrationController.java` (auto-merged) | `dev`'s visibility endpoint came back | Removed again; the file equals this branch's version |
| Second merge, `StoreSlug.java` | Deleted here, used by `dev`'s V29 | Restored from `dev` with a javadoc only |
| Second merge, `StorefrontService.java` (auto-merged) | `dev`'s F07 per-placement homepage rendering (`5436bf7`) | Kept `dev`'s F07 logic; F09 docs updated (`type` is the zone name) |
| Second merge, `test-groups.py` | Both added classes | Kept `LayoutCampaignIntegrationTest` and `StorefrontIntegrationTest` |
| Second merge, `openapi.json` | Old F09 export on `dev` | Took `dev`'s file and applied only the F09 runtime differences |
| Second merge, docs and record indexes | Both sides edited | Kept both; F09 facts restated for V29 + V30 |

## 4. API and compatibility impact

- Against current `dev`: **breaking** for the System Admin visibility route (removed), for public Store visibility (follows `store.status`), `storeSlug` (= `store.slug`) and `availability` (`OUT_OF_STOCK`). No FE screen uses these yet.
- Against the `dev` before the old merge: additive for staff clients: 4 anonymous GETs, 9 public schemas, 2 optional Product fields. `PUT /api/products/{id}` is a full replacement, so a client that omits `imageUrl`/`description` clears them (FE `dev` does not send them yet).
- No change to existing routes, RBAC or Store isolation. The INACTIVE-Store guard is unchanged for authenticated users; anonymous storefront reads hide INACTIVE Stores with 404.
- `ProductCard.availability` is `AVAILABLE | OUT_OF_STOCK` (PO review 2026-11-25, FR-09.3, DEC-20261125-04 addendum); the original branch's `UNAVAILABLE` is not exposed. Breaking against current `dev`.

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
| **Second merge (dev `516183d`):** `sh mvnw -B clean test -Dtest=MigrationVersioningTest,StorefrontIntegrationTest,LayoutCampaignIntegrationTest` | Temurin 17.0.21, H2 | PASS — 28 tests (4 + 7 + 17), 0 failures, 0 errors, 0 skipped | local console |
| Second merge: `sh mvnw -B clean verify -Dtest="$(python3 .github/scripts/test-groups.py backend)"` | same | PASS — 466 tests, 0 failures, 0 errors, 0 skipped, 67 classes, BUILD SUCCESS | local console |
| Second merge: Flyway **fresh** V1→V30 with the branch's compiled classes (Flyway API, `classpath:db/migration`) | disposable `mysql:8.4` (8.4.13), empty schema | PASS — 30 applied; `store` has `slug` only (`uk_store_slug`), no `store_slug`/`storefront_enabled`/`uq_store_store_slug`; `product.image_url varchar(2048)`, `description text`; validate OK | local console |
| Second merge: Flyway **upgrade** — `origin/dev` `516183d` compiled classes to V28, insert 2 Stores + 2 products, `origin/dev` V28→V29 (backfills `store_slug`), insert 2 more Stores + products with `image_url`/`description`, set `storefront_enabled`; then branch classes V29→V30 | same container, second schema | PASS — 1 migration applied (V30); V29 history row (checksum null, Java) still valid; 4 Stores and 4 products unchanged (slug, status, image_url, description); old columns and index gone; column set identical to the fresh schema (487 columns) | local console |
| Second merge: runtime OpenAPI export | Temurin 17.0.21, H2 | PASS — 112 paths / 139 operations; only F09 differences applied to `dev`'s file | `docs/api/openapi.json` |
| `mysql` CI group, full `integration` suite | — | NOT RUN (by request) | — |

`sh mvnw` was used because `mvnw` is mode 100644 in git; the file mode was not changed.

## 6. Previous evidence referenced

The 2026-11-20 branch record ([ROC-20261120-002](../2026-11-20/02-f09-storefront-mvp.md)) reported H2 targeted tests on base `530651b`; that evidence predates the merge and is not counted here.

## 7. Limitations and next actions

- FE: no storefront screen calls these routes yet (FE storefront is mock). FE F02 edit form should send `imageUrl`/`description` back on PUT.
- `mysql` group and the integration job: NOT RUN locally; the integration job also depends on `PASPM_CI_SPMF_ARTIFACT_URL` (PR #29).
- ERD: add `product.image_url`, `product.description` (V29) to the next ERD update; `store` has no F09 columns after V30.
- Process: the old F09 merge reached `dev` without a PR, against [DEC-20261119-01](../../decisions/2026-11-19/DEC-20261119-01-dev-changes-only-through-pr.md). Teammates who ran it have V29 applied; V30 cleans up on their next start.
- Pre-existing OpenAPI drift on `dev` (F07 placement schemas, `servers`, CSV async `Idempotency-Key`) was not changed.

## 8. Delivery

Committed and pushed as branch `Khoi/merge/f09-storefront-be`; PR not opened by the agent (draft body prepared outside the repository). No merge, no deploy. The original branch `Duy/Feature/F09_Storefront` was not modified.

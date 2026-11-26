# ROC-20261127-001 — F09 public Store icon and banner URLs

| Field | Value |
|---|---|
| Date / timezone | 2026-11-27 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Sonnet 5.5), requested by Nguyễn Minh Khôi |
| Type | Backend implementation (additive API change) |
| Features | F09 storefront (also the F10 cart view, which reuses the public Store object); F01 Store images (shared URL builder) |
| Branch / base | `Khoi/fix/f09-store-images` / `origin/dev` @ `a390400` |
| Decision | [DEC-20261123-01](../../decisions/2026-11-23/DEC-20261123-01-store-management.md) (Store icon and banner) with F09; no new DEC, the PO decision already exists. The gap was reported by the frontend |
| Release | UNRELEASED |
| Daily index | [2026-11-27](README.md) |

## Why

The public storefront Store object returned only `storeSlug` and `name`, so the frontend could not show the Store icon and banner that F01 already stores and serves on the public image route.

## What changed

- **Fields added:** `iconUrl` and `bannerUrl` (both nullable strings) on the public Store object. It appears as an item of `GET /api/storefront/stores`, as `store` in `GET /api/storefront/{storeSlug}/home`, and as `store` in the F10 cart view (the same `PublicStore` record), so all three show the same four fields.
- **Example:** `{"storeSlug":"green-market","name":"Green Market","iconUrl":"/api/public/stores/7/images/3f6c0d0e-5a2b-4c1e-9d55-0a6b8f2d9c11.png","bannerUrl":null}`.
- **One URL formula:** new `catalog/store/StoreImageUrls.publicUrl(storeId, key)`; `StoreService.imageUrl` now delegates to it, so the staff Store views and the storefront cannot drift apart. The storefront `StoreRow` carries `icon_key` and `banner_key`; `StoreRow.toPublic()` and `toSummary()` build the two DTOs.
- **Unchanged:** who can see the storefront (ACTIVE Stores only; an INACTIVE Store is not listed, its home is 404 and its image URLs are 404); no route, request, status code or existing field; **no migration** (the columns exist since V27).
- **Information note:** the Store id still is not a field, but it is visible inside the image URLs. That is how the existing public image route and the staff Store API already work, and the PO decision says to reuse that route.

## Files

| Path | Change |
|---|---|
| `src/main/java/vn/edu/fpt/paspm/catalog/store/StoreImageUrls.java` | Added: the shared public URL builder |
| `catalog/store/StoreService.java` | `imageUrl` delegates to it |
| `storefront/StorefrontContracts.java` | `PublicStore` and `StoreSummary` gain `iconUrl`, `bannerUrl` (nullable schema) |
| `storefront/StorefrontRepository.java` | `StoreRow` gains the two keys; the two SELECTs read `icon_key`, `banner_key`; `toPublic`, `toSummary` |
| `storefront/StorefrontService.java`, `storefront/cart/CartService.java` | Use `toPublic()` / `toSummary()` |
| `src/test/.../StorefrontIntegrationTest.java` | Shape now 4 fields with JSON null; new test for list, home, independent images, INACTIVE; OpenAPI schema assertion |
| `src/test/.../StoreImageHttpTest.java` | New end-to-end test: upload through the staff API, storefront shows the same URLs, anonymous fetch serves the bytes, replace and remove, deactivate |
| `docs/api/openapi.json` | 16 inserted lines in two schemas (equals the runtime export; 118 paths / 147 operations) |
| `docs/api/F09_STOREFRONT_API.md`, `F10_BUNDLE_CART_API.md`, `API_CHANGELOG.md` | "Store branding" section, cart examples, changelog entry |
| `docs/frontend_handoff/F09_HANDOFF.md`, `F10_HANDOFF.md`, `F09_examples/*` | Types, a checklist, example JSON |
| `docs/features/F09/README.md`, `docs/product/SRS.md` (FR-09.1) | The stated fact |
| `docs/testing/blackbox/*` | TC-F09-001, -002, -008 reworded; TC-F09-014 added (249 cases; no ID changed); regenerated with `outputs/blackbox-tools` |

## Verification

Temurin Java 17.0.21 with `JAVA_HOME` pinned, Maven Wrapper (`sh ./mvnw`), disposable H2.

- RED: `./mvnw -B -q test -Dtest='StorefrontIntegrationTest,StoreImageHttpTest'` ran 18 tests: 4 failed for the intended reasons (store had 2 fields, OpenAPI lacked the properties, URLs were empty and the path was missing), 14 passed.
- GREEN: the same command plus the cart tests ran 31 tests (storefront 8, store images 10, cart 13), 0 failures, errors or skips.
- OpenAPI: the runtime export differs from the committed file only by those four properties; the committed file equals the export semantically.
- CI backend group: `./mvnw -B clean verify -Dtest="$(python3 .github/scripts/test-groups.py backend)"` ran **496 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS. No new test class was added, so `.github/scripts/test-groups.py` is unchanged.
- NOT RUN locally: the MySQL group and the integration job (they need a disposable MySQL 8.4 and SPMF on Java 25); the pull request's CI runs them.
- The black-box cases are designs and were not executed.

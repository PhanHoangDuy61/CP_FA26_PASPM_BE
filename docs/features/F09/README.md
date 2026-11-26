# F09 — Storefront MVP

Backend scope: public Store selection, Homepage, Product list/detail, and the selected F08 layout and recommendation projection. Status: four public GET operations merged into `dev` through PR #33 (`0ef5a55`). Original implementation: Duy Phan, branch `Duy/Feature/F09_Storefront` (2026-11-21).

The stacked F10 Part 2 branch adds an opaque `bundleKey` to public BUNDLE blocks on the active selected Layout B. See the [F10 contract](../../api/F10_BUNDLE_CART_API.md).

- API contract: [F09 Storefront API](../../api/F09_STOREFRONT_API.md)
- Frontend handoff and screen checklist: [F09 handoff](../../frontend_handoff/F09_HANDOFF.md)
- OpenAPI: [runtime export](../../api/openapi.json)
- Decisions: [DEC-20261117-03](../../decisions/2026-11-17/DEC-20261117-03-f09-f10-proceed-with-mvp-plans.md) (proceed with MVP plans), [DEC-20261123-01](../../decisions/2026-11-23/DEC-20261123-01-store-management.md) (Store slug = F09 `storeSlug`), [DEC-20261124-01](../../decisions/2026-11-24/DEC-20261124-01-batch-expiry-boundary-effective-status.md) (expiry boundary), [DEC-20261124-03](../../decisions/2026-11-24/DEC-20261124-03-product-soft-delete.md) (soft delete), [DEC-20261125-04](../../decisions/2026-11-25/DEC-20261125-04-f09-public-store-follows-store-status.md) (public = Store ACTIVE)
- Change records: [F09 Storefront MVP (branch)](../../record-of-changes/2026-11-20/02-f09-storefront-mvp.md), [merge into dev](../../record-of-changes/2026-11-25/05-f09-storefront-merge-into-dev.md)

## Rules

| Rule | Where |
|---|---|
| A Store is public only while `store.status = ACTIVE`; `storeSlug` is `store.slug` | `storefront/StorefrontRepository.java` (`publicStores`, `publicStore`) |
| The public Store carries nullable `iconUrl` and `bannerUrl` (2026-11-27), built by the shared `catalog/store/StoreImageUrls` from `store.icon_key` / `banner_key`; no migration | `StorefrontRepository.StoreRow.toPublic/toSummary`, `StorefrontContracts.PublicStore/StoreSummary`; tests `StorefrontIntegrationTest`, `StoreImageHttpTest` |
| Only ACTIVE, non-deleted Products (`deleted_at IS NULL`) are listed, shown or AVAILABLE | `storefront/StorefrontRepository.java` (`products`, `activeProduct`, `activeProductsByCategory`, `availability`, `placements`) |
| A batch is sellable on its expiry date, expired only the day after | `StorefrontRepository.availability` (`expiry_date >= today`) |
| Homepage placements are checked one by one (F07 product-level Layout B, DEC-20261125-01): the product must be public and sellable; a placement from a recommendation also needs that recommendation APPROVED, from the active snapshot, still containing the product, and stock at or above its policy minimum | `storefront/StorefrontService.java` (`validPlacement`) |
| Product display metadata: `product.image_url`, `product.description` | Migration `V29__f09_storefront.java` (old F09 merge on `dev`, kept unchanged) |
| No F09 Store columns: `store.store_slug` and `store.storefront_enabled` (added by V29) are dropped | Migration `V30__f09_drop_old_storefront_columns.sql` (DEC-20261125-04, addendum "Migrations") |

The draft plan lives outside the repository (`outputs/plans/F09_STOREFRONT_PLAN_API_SCREENS.md`, DEC-20261117-03). Related Products, F10 cart and checkout, image upload, discounts and priority coverage are deferred.

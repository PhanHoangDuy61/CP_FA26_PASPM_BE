# F09 — Storefront MVP

Backend scope: public Store selection, Homepage, Product list/detail, and the selected F08 layout and recommendation projection. Status: merged into `dev` through `Khoi/merge/f09-storefront-be` (PR pending). Original implementation: Duy Phan, branch `Duy/Feature/F09_Storefront` (2026-11-21).

- API contract: [F09 Storefront API](../../api/F09_STOREFRONT_API.md)
- Frontend handoff and screen checklist: [F09 handoff](../../frontend_handoff/F09_HANDOFF.md)
- OpenAPI: [runtime export](../../api/openapi.json)
- Decisions: [DEC-20261117-03](../../decisions/2026-11-17/DEC-20261117-03-f09-f10-proceed-with-mvp-plans.md) (proceed with MVP plans), [DEC-20261123-01](../../decisions/2026-11-23/DEC-20261123-01-store-management.md) (Store slug = F09 `storeSlug`), [DEC-20261124-01](../../decisions/2026-11-24/DEC-20261124-01-batch-expiry-boundary-effective-status.md) (expiry boundary), [DEC-20261124-03](../../decisions/2026-11-24/DEC-20261124-03-product-soft-delete.md) (soft delete), [DEC-20261125-04](../../decisions/2026-11-25/DEC-20261125-04-f09-public-store-follows-store-status.md) (public = Store ACTIVE)
- Change records: [F09 Storefront MVP (branch)](../../record-of-changes/2026-11-20/02-f09-storefront-mvp.md), [merge into dev](../../record-of-changes/2026-11-25/05-f09-storefront-merge-into-dev.md)

## Rules

| Rule | Where |
|---|---|
| A Store is public only while `store.status = ACTIVE`; `storeSlug` is `store.slug` | `storefront/StorefrontRepository.java` (`publicStores`, `publicStore`) |
| Only ACTIVE, non-deleted Products (`deleted_at IS NULL`) are listed, shown or AVAILABLE | `storefront/StorefrontRepository.java` (`products`, `activeProduct`, `activeProductsByCategory`, `availability`, `placements`) |
| A batch is sellable on its expiry date, expired only the day after | `StorefrontRepository.availability` (`expiry_date >= today`) |
| Recommendation blocks: APPROVED, same snapshot, unchanged product set, every component sellable | `storefront/StorefrontService.java` (`validBlock`) |
| Product display metadata: `product.image_url`, `product.description` | Migration `V29__f09_product_display_metadata.sql` |

The draft plan lives outside the repository (`outputs/plans/F09_STOREFRONT_PLAN_API_SCREENS.md`, DEC-20261117-03). Related Products, F10 cart and checkout, image upload, discounts and priority coverage are deferred.

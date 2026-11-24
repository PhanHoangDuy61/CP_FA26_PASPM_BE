# DEC-20261125-01 — F09: a Store is public exactly when it is ACTIVE

| Field | Value |
|---|---|
| Date | 2026-11-25 (Asia/Ho_Chi_Minh) |
| Source | Owner answers (Nguyễn Minh Khôi, Team Leader / acting Product Owner) during the merge of `Duy/Feature/F09_Storefront` into `dev` and the PO review of the merge branch, 2026-11-25 |
| Status | OWNER_DECISION, implemented on branch `Khoi/merge/f09-storefront-be`; PR to `dev` pending |
| Feature | F09 Storefront (with F01 Store management) |
| Related | DEC-20261117-03, DEC-20261123-01, DEC-20261124-03, SRS FR-09.1, BR-25, BR-30 |

## Context
The F09 branch (2026-11-21, base `530651b`) added its own public Store identity before Store management existed on `dev`:
- `store.store_slug` (unique) and `store.storefront_enabled` (default FALSE) in a migration numbered V27;
- `PUT /api/system/stores/{storeId}/storefront-visibility` (SYSTEM_ADMIN) in `SystemStoreController`.

Since then `dev` gained Store management (DEC-20261123-01, V27): `store.slug` is unique, set only by the System Admin and already defined as the F09 `storeSlug`; `store.status` is ACTIVE/INACTIVE; `SystemStoreController` was replaced. Keeping both would give a Store two slugs and two "visible" switches. No DEC or SRS line defined a separate storefront toggle.

## Decision
1. A Store is public in F09 **exactly when `store.status = ACTIVE`**. There is no separate storefront visibility flag or endpoint.
2. The public `storeSlug` is `store.slug`. F09 adds no slug column.
3. Unknown and INACTIVE Stores both return `404 Storefront not found`.
4. The F09 migration is renumbered to **V29** and only adds `product.image_url` and `product.description` (FR-09.4).
5. F09 (and F10 when built) exclude INACTIVE and soft-deleted products (`deleted_at IS NOT NULL`, DEC-20261124-03) from every public read, and follow the DEC-20261124-01 expiry boundary.

## Consequences
- Deactivating a Store hides its storefront immediately; reactivating shows it again. Neither touches campaigns.
- A newly created ACTIVE Store is public at once, with the fallback `DEFAULT_CATALOG` homepage until it has an ACTIVE campaign.
- Frontend: no visibility screen. Store status and slug are managed in the existing Store management screens.
- If the owner later wants a Store to stay ACTIVE for staff but hidden from shoppers, that needs a new DEC and a new migration (V30+).

## Addendum — FR-09.3 availability value (PO review, 2026-11-25)

The SRS is the standard ("docs làm chuẩn"): FR-09.3 says an out-of-stock product shows `OUT_OF_STOCK`. The original F09 branch returned `UNAVAILABLE`. Decision: the public `ProductCard.availability` is **`AVAILABLE | OUT_OF_STOCK`**; `UNAVAILABLE` is not used. Since INACTIVE and soft-deleted products are never returned publicly, every non-AVAILABLE product in a public response is one without sellable stock. Changed in code (`StorefrontContracts.Availability`), tests, OpenAPI and the F09 API/handoff on branch `Khoi/merge/f09-storefront-be`.

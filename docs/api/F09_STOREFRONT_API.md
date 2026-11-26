# F09 — Public Storefront API

F09 exposes a read-only public catalogue and selected F08 homepage. It does not publish or activate campaigns. Public responses use storefront-specific DTOs and never include F02 cost/stock details, F06 evidence/review history, F07 utility, or batch data.

## Authorization

| Method and path | Access | Behavior |
|---|---|---|
| `GET /api/storefront/stores` | Public, no bearer required | Lists only ACTIVE Stores; each has `storeSlug` (= the Store's `slug`), `name`, and the nullable `iconUrl` and `bannerUrl` (see "Store branding"). There is no `storeId` field. |
| `GET /api/storefront/{storeSlug}/home` | Public, no bearer required | Returns the currently ACTIVE campaign's selected READY layout, or `DEFAULT_CATALOG` only when there is no ACTIVE campaign. |
| `GET /api/storefront/{storeSlug}/products` | Public, no bearer required | Pages ACTIVE, non-deleted Products from the selected Store. |
| `GET /api/storefront/{storeSlug}/products/{productId}` | Public, no bearer required | Reads ACTIVE, non-deleted Product detail from the selected Store. |

Unknown and INACTIVE Stores both return `404 {"error":"Storefront not found"}`. Product ids are Store-scoped; INACTIVE, soft-deleted (DEC-20261124-03) or foreign Products return `404 {"error":"Product not found"}`.

## Store slug and visibility

The public `storeSlug` is the Store's existing `slug` from F01 Store management (V27, DEC-20261123-01): unique (`uk_store_slug`), set and changed only by the System Admin through the Store API. F09 adds no slug column of its own.

A Store is public exactly when `store.status = ACTIVE` (DEC-20261125-04). There is no separate storefront visibility flag or endpoint: deactivating a Store hides its storefront immediately, reactivating it shows it again. Neither action publishes, activates or ends a campaign.

> **Merge note (2026-11-25).** An older copy of the F09 branch (`Duy/Feature/F09_Storefront`) was merged into `dev` directly at `68d6f0b` (2026-11-21). It added `V29__f09_storefront.java` (`store.store_slug`, `store.storefront_enabled`, `product.image_url`, `product.description`) and `PUT /api/system/stores/{storeId}/storefront-visibility`. V29 is kept unchanged because it may already be applied. **V30** `f09_drop_old_storefront_columns` drops `store.store_slug` and `store.storefront_enabled`; the visibility endpoint is removed. Public lookup uses `store.slug` and `store.status` (DEC-20261125-04, addendum "Migrations").

## Store branding (2026-11-27)

Every public Store object has two nullable image URLs: `GET /api/storefront/stores` items, `store` in `GET /api/storefront/{storeSlug}/home`, and `store` in the F10 cart view (the same fields everywhere). They follow [DEC-20261123-01](../decisions/2026-11-23/DEC-20261123-01-store-management.md).

```json
{ "storeSlug": "green-market", "name": "Green Market",
  "iconUrl": "/api/public/stores/7/images/3f6c0d0e-5a2b-4c1e-9d55-0a6b8f2d9c11.png",
  "bannerUrl": null }
```

- `iconUrl` and `bannerUrl` are the public image routes of F01 (`GET /api/public/stores/{storeId}/images/{fileName}`, anonymous, PNG or JPEG), built by the same code as the staff Store views, so both APIs advertise identical URLs. They are relative paths: resolve them against the API origin.
- `null` means the Store has no such image; the client shows a placeholder. Each image is independent.
- Replacing an image gives a new UUID file name, so the URL changes and the old one answers 404; removing it makes the field `null`.
- Visibility is unchanged: only ACTIVE Stores are listed, and deactivating a Store also makes its image URLs answer 404. The Store id is not a field of the DTO; it appears only inside these URLs, as in the staff Store API.
- Additive change: no request, route, status code or existing field changed (OpenAPI stays at 118 paths / 147 operations; only the `PublicStore` and `StoreSummary` schemas gained the two properties).

## Public Product fields

`ProductCard` is separate from F02 `ProductView`:

```text
productId, name, description, imageUrl, unit, sellingPrice,
category { categoryId, name } | null,
availability: AVAILABLE | OUT_OF_STOCK
```

`imageUrl` is an optional absolute HTTP(S) URL; upload is not implemented. `description` is optional (maximum 2,000 characters). Availability is a summary only (FR-09.3): `AVAILABLE` when the product has sellable stock, otherwise `OUT_OF_STOCK`; no quantity is returned. It uses Store-local today (`paspm.store-time-zone`, default `Asia/Ho_Chi_Minh`) and counts ACTIVE batches with positive remaining quantity whose expiry date is today or later: a batch is still sellable on its expiry date and expired only the day after (DEC-20261124-01). Soft-deleted Products are never AVAILABLE. Non-expiring Products may use batches without an expiry; when `requiresExpiry=true`, a missing-expiry batch contributes no sellable quantity.

Product list query parameters follow F02 naming: `search` (name/description), `categoryId`, `page` (default 0), `pageSize` (default 20, maximum 100), `sortBy=name|sellingPrice` (default `name`), and `sortDirection=asc|desc` (default `asc`). Only ACTIVE, non-deleted Products are returned (soft-deleted Products are excluded before pagination). The response is `StorefrontProductPage {items,page,pageSize,totalElements}`, a distinct schema from the authenticated F02 `ProductPage`.

## Homepage projection

`GET /home` returns `{store,campaignName,fallbackType,zones,catalog}`.

- If an ACTIVE campaign exists, only `selected_layout_id` is read. The selected layout must be READY. Layout contents are emitted in stored zone/block/position order, one `HomePlacement` per zone and block. Since F07 product-level Layout B (DEC-20261125-01, `dev` `5436bf7`), `HomePlacement.type` is the zone name (`BUNDLE`, `CROSS_SELL`, `FBT` or `BANNER`); `BASELINE` is no longer emitted.
- On the F10 Part 2 branch, every `BUNDLE` block on the selected READY Layout B has an optional-in-schema opaque `HomePlacement.bundleKey`. The block remains visible even when all components are out of stock. Its public Product cards include ACTIVE, non-deleted components and their current availability. If all components are hidden, `products` is empty. Other placements omit `bundleKey`. The key is valid only for its Store, campaign, layout revision and block. Pass it to the [F10 add-bundle API](F10_BUNDLE_CART_API.md). Reload the homepage on `BUNDLE_UNAVAILABLE`.
- In other zones and on Layout A, each placed product is checked on its own. A block is omitted only when none of its products pass. A product passes when it is ACTIVE, not soft-deleted, has no missing required-expiry metadata and has at least 1 sellable unit. A product placed from a recommendation also needs that recommendation APPROVED, from the selected snapshot, still containing the product, and stock at or above its captured F06 `minimumSellableQuantity`. The response does not expose recommendation ids, policy/evidence, review status/history, or utility.
- When there is no ACTIVE campaign, the response has `fallbackType=DEFAULT_CATALOG`, no zones, and ACTIVE, non-deleted Products grouped by category; within each category products sort by name and then `productId`. This is not baseline A.
- A DRAFT, PUBLISHED-but-not-active, or ENDED campaign is never projected.
- `priorityCoverage` is not part of F09; its F07/F08 definition/use remains a separate follow-up.

F06 permits BANNER recommendations to contain a singleton or larger product pattern; the current contract does not define a one-banner-per-homepage rule. F09 does not invent one. F08 layout zone capacities are configured (DEC-20261119-06). As a defence, to prevent an unbounded active layout from being rendered publicly, `/home` returns `503 {"error":"Storefront layout capacities are not configured"}` until positive block and product caps are configured for every recommendation zone. This F09 guard does not change F07/F08 authorization or stored-layout behavior.

## Example response shapes

See [`../frontend_handoff/F09_examples/`](../frontend_handoff/F09_examples/) for synthetic Store list, active/fallback homepage, product list/detail, bundle and error examples. Related Products is deferred. The separate [F10 cart API](F10_BUNDLE_CART_API.md) handles cart writes. Checkout, payment, orders, discounts, image upload and priority-coverage calculation are outside F09.

## Verification scope

`StorefrontIntegrationTest` (registered in the CI `backend` group) covers anonymous access, INACTIVE Store hiding and reactivation, soft-deleted Product hiding (list, detail, homepage, availability), Store-isolated Product reads, fallback behavior, selected active layout/recommendation filtering, the expiry-date boundary, public DTO privacy, one bulk availability query, and runtime OpenAPI paths (the removed visibility route must be absent). `MigrationVersioningTest` checks V27/V28/V29 ordering and an H2 V28→V29 upgrade. A fresh MySQL 8.4 migrate V1→V29 was run for the merge; see the record of changes.

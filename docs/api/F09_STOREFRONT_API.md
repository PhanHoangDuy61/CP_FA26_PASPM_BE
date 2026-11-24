# F09 — Public Storefront API

F09 exposes a read-only public catalogue and selected F08 homepage. It does not publish or activate campaigns. Public responses use storefront-specific DTOs and never include F02 cost/stock details, F06 evidence/review history, F07 utility, or batch data.

## Authorization

| Method and path | Access | Behavior |
|---|---|---|
| `GET /api/storefront/stores` | Public, no bearer required | Lists only ACTIVE Stores; response has `storeSlug` (= the Store's `slug`) and `name`, never `storeId`. |
| `GET /api/storefront/{storeSlug}/home` | Public, no bearer required | Returns the currently ACTIVE campaign's selected READY layout, or `DEFAULT_CATALOG` only when there is no ACTIVE campaign. |
| `GET /api/storefront/{storeSlug}/products` | Public, no bearer required | Pages ACTIVE, non-deleted Products from the selected Store. |
| `GET /api/storefront/{storeSlug}/products/{productId}` | Public, no bearer required | Reads ACTIVE, non-deleted Product detail from the selected Store. |

Unknown and INACTIVE Stores both return `404 {"error":"Storefront not found"}`. Product ids are Store-scoped; INACTIVE, soft-deleted (DEC-20261124-03) or foreign Products return `404 {"error":"Product not found"}`.

## Store slug and visibility

The public `storeSlug` is the Store's existing `slug` from F01 Store management (V27, DEC-20261123-01): unique (`uk_store_slug`), set and changed only by the System Admin through the Store API. F09 adds no slug column of its own.

A Store is public exactly when `store.status = ACTIVE` (DEC-20261125-01). There is no separate storefront visibility flag or endpoint: deactivating a Store hides its storefront immediately, reactivating it shows it again. Neither action publishes, activates or ends a campaign.

> **Merge note (2026-11-25).** The original F09 branch (`Duy/Feature/F09_Storefront`, 2026-11-21) added `store.store_slug`, `store.storefront_enabled` and `PUT /api/system/stores/{storeId}/storefront-visibility` in a migration numbered V27. That number was already used by Store management on `dev`, so the merge renumbers the migration to **V29**, keeps only the product display columns, and drops the slug/visibility columns and the endpoint in favour of `store.slug` and `store.status`.

## Public Product fields

`ProductCard` is separate from F02 `ProductView`:

```text
productId, name, description, imageUrl, unit, sellingPrice,
category { categoryId, name } | null,
availability: AVAILABLE | UNAVAILABLE
```

`imageUrl` is an optional absolute HTTP(S) URL; upload is not implemented. `description` is optional (maximum 2,000 characters). Availability is a summary only; no quantity is returned. It uses Store-local today (`paspm.store-time-zone`, default `Asia/Ho_Chi_Minh`) and counts ACTIVE batches with positive remaining quantity whose expiry date is today or later: a batch is still sellable on its expiry date and expired only the day after (DEC-20261124-01). Soft-deleted Products are never AVAILABLE. Non-expiring Products may use batches without an expiry; when `requiresExpiry=true`, a missing-expiry batch contributes no sellable quantity.

Product list query parameters follow F02 naming: `search` (name/description), `categoryId`, `page` (default 0), `pageSize` (default 20, maximum 100), `sortBy=name|sellingPrice` (default `name`), and `sortDirection=asc|desc` (default `asc`). Only ACTIVE, non-deleted Products are returned (soft-deleted Products are excluded before pagination). The response is `StorefrontProductPage {items,page,pageSize,totalElements}`, a distinct schema from the authenticated F02 `ProductPage`.

## Homepage projection

`GET /home` returns `{store,campaignName,fallbackType,zones,catalog}`.

- If an ACTIVE campaign exists, only `selected_layout_id` is read. The selected layout must be READY. Layout contents are emitted in stored zone/block/position order. Baseline A placements use type `BASELINE`. Recommendation placements support the current F06 types `BUNDLE`, `CROSS_SELL`, `FBT`, and `BANNER`.
- A recommendation block is omitted as a whole unless its current status is APPROVED, it belongs to the selected snapshot, its stored product set still matches its placement, and every component is ACTIVE, not soft-deleted, and meets the recommendation's captured F06 `minimumSellableQuantity`. Required-expiry metadata is rechecked. The response does not expose recommendation ids, policy/evidence, review status/history, or utility.
- When there is no ACTIVE campaign, the response has `fallbackType=DEFAULT_CATALOG`, no zones, and ACTIVE, non-deleted Products grouped by category; within each category products sort by name and then `productId`. This is not baseline A.
- A DRAFT, PUBLISHED-but-not-active, or ENDED campaign is never projected.
- `priorityCoverage` is not part of F09; its F07/F08 definition/use remains a separate follow-up.

F06 permits BANNER recommendations to contain a singleton or larger product pattern; the current contract does not define a one-banner-per-homepage rule. F09 does not invent one. F08 layout zone capacities are configured (DEC-20261119-06). As a defence, to prevent an unbounded active layout from being rendered publicly, `/home` returns `503 {"error":"Storefront layout capacities are not configured"}` until positive block and product caps are configured for every recommendation zone. This F09 guard does not change F07/F08 authorization or stored-layout behavior.

## Example response shapes

See [`../frontend_handoff/F09_examples/`](../frontend_handoff/F09_examples/) for synthetic Store list, active/fallback homepage, product list/detail, bundle and error examples. Related Products is deferred. F10 cart, checkout, payment, orders, discounts, image upload and priority-coverage calculation are out of scope.

## Verification scope

`StorefrontIntegrationTest` (registered in the CI `backend` group) covers anonymous access, INACTIVE Store hiding and reactivation, soft-deleted Product hiding (list, detail, homepage, availability), Store-isolated Product reads, fallback behavior, selected active layout/recommendation filtering, the expiry-date boundary, public DTO privacy, one bulk availability query, and runtime OpenAPI paths (the removed visibility route must be absent). `MigrationVersioningTest` checks V27/V28/V29 ordering and an H2 V28→V29 upgrade. A fresh MySQL 8.4 migrate V1→V29 was run for the merge; see the record of changes.

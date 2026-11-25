# F09 Storefront MVP — Frontend Handoff

The public storefront is anonymous and read-only. Staff routes remain protected. Do not reuse F02 `ProductView`, F07 `LayoutView`, or F06 `RecView` as public models.

## Base URL and public access

- `GET /api/storefront/stores` returns ACTIVE Stores only, as `{storeSlug,name}`. `storeSlug` is the Store's `slug` from Store management (DEC-20261123-01). There is no public Store id.
- Public paths require no bearer token: `GET /api/storefront/{storeSlug}/home`, `/products`, and `/products/{productId}`.
- A Store is public exactly when it is ACTIVE (DEC-20261125-04). There is **no** storefront visibility endpoint: the System Admin deactivates/reactivates the Store and edits its slug through the existing Store management screens (`/api/system/stores`).
- Unknown and INACTIVE Store slugs intentionally share a 404 response so the API does not reveal the existence of inactive Stores.

## Screen checklist

### Store selection

- [ ] Load ACTIVE Stores and navigate by `storeSlug`.
- [ ] Show an empty state when no Store is ACTIVE.
- [ ] Do not depend on Store ids or expose internal Store ids in links.

### Homepage

- [ ] Render zones and placements in response order.
- [ ] Render the selected active layout only; DRAFT, PUBLISHED-but-not-active and ENDED campaigns are not public.
- [ ] When `fallbackType=DEFAULT_CATALOG`, render category groups and their name-then-productId sorted products.
- [ ] Render `BUNDLE`, `CROSS_SELL`, `FBT` and `BANNER` placement types. Only `fallbackType` identifies the catalog fallback.
- [ ] Treat omitted products or blocks as no longer eligible; do not infer why from private F06 evidence.
- [ ] If an active layout returns `503` because capacities are unconfigured, show a generic unavailable state and retry later. Do not show the internal configuration error to shoppers.

### Product list and Product Detail

- [ ] Use `search`, `categoryId`, `page`, `pageSize`, `sortBy=name|sellingPrice`, and `sortDirection=asc|desc`.
- [ ] Format displayed VND as whole đồng using HALF_UP rounding.
- [ ] Render only `AVAILABLE` / `OUT_OF_STOCK` (show "Hết hàng" for `OUT_OF_STOCK`); never estimate or display stock quantities.
- [ ] Render `imageUrl` only as a URL; upload is not supported.
- [ ] Use `productId` for detail navigation. Cross-Store, INACTIVE and soft-deleted Products return 404.
- [ ] Do not show SKU, cost, batch/expiry data, F03 scores, utility, or review information.

### Bundles

- [ ] Treat each placement (one zone + block) as a grouped list of public Products; `type` is the zone name.
- [ ] On the F10 Part 2 branch, a `BUNDLE` placement with `bundleKey` supports the whole-bundle action. Its cards include visible products with `OUT_OF_STOCK` status. The block stays visible and may have an empty products list when every component is hidden.
- [ ] Treat the key as opaque and use the latest F09 home result. Use the [F10 handoff](F10_HANDOFF.md) for authentication, request and error handling.

### Store visibility (no separate control)

- [ ] Do not build a storefront visibility toggle. Public visibility follows Store status; the existing Store management screen already deactivates/reactivates a Store and edits its slug.
- [ ] If a slug changes, old links return 404; reload the Store list.

> **Breaking vs current `dev` (old F09 merge `68d6f0b`, 2026-11-21):** `PUT /api/system/stores/{storeId}/storefront-visibility`, its `{"storefrontEnabled":…}` body and the `store_visibility_not_found_error.json` example are removed; V30 drops `store.store_slug` and `store.storefront_enabled`. Example slugs no longer carry a hash suffix because they are ordinary Store slugs. `availability` value `UNAVAILABLE` is renamed to `OUT_OF_STOCK` (FR-09.3, PO review 2026-11-25).

## Public models

```ts
export type Availability = 'AVAILABLE' | 'OUT_OF_STOCK';
export interface PublicStore { storeSlug: string; name: string }
export interface ProductCard {
  productId: number; name: string; description: string | null; imageUrl: string | null;
  unit: string; sellingPrice: number;
  category: { categoryId: number; name: string } | null;
  availability: Availability;
}
export interface StorefrontProductPage {
  items: ProductCard[]; page: number; pageSize: number; totalElements: number;
}
export interface HomePlacement {
  type: 'BUNDLE' | 'CROSS_SELL' | 'FBT' | 'BANNER'; // the zone name
  blockNo: number; startPosition: number; products: ProductCard[];
  bundleKey?: string;
}
export interface HomeZone { name: string; placements: HomePlacement[] }
```

Examples: [`F09_examples/`](F09_examples/). Related Products is deferred. F07/F08 `priorityCoverage` remains a separate follow-up and is not included in F09 responses.

# F09 Storefront MVP — Frontend Handoff

The public storefront is anonymous and read-only. Staff routes remain protected. Do not reuse F02 `ProductView`, F07 `LayoutView`, or F06 `RecView` as public models.

## Base URL and public access

- `GET /api/storefront/stores` returns enabled Stores only, as `{storeSlug,name}`. There is no public Store id.
- Public paths require no bearer token: `GET /api/storefront/{storeSlug}/home`, `/products`, and `/products/{productId}`.
- `PUT /api/system/stores/{storeId}/storefront-visibility` is SYSTEM_ADMIN-only. It toggles storefront visibility and has no campaign side effect.
- Unknown and disabled Store slugs intentionally share a 404 response so the API does not reveal the existence of private Stores.

## Screen checklist

### Store selection

- [ ] Load enabled Stores and navigate by `storeSlug`.
- [ ] Show an empty state when no Store has enabled public visibility.
- [ ] Do not depend on Store ids or expose internal Store ids in links.

### Homepage

- [ ] Render zones and placements in response order.
- [ ] Render the selected active layout only; DRAFT, PUBLISHED-but-not-active and ENDED campaigns are not public.
- [ ] Render baseline A as `BASELINE`; do not label it as fallback.
- [ ] When `fallbackType=DEFAULT_CATALOG`, render category groups and their name-then-productId sorted products.
- [ ] Render `BUNDLE`, `CROSS_SELL`, `FBT`, `BANNER` and `BASELINE` placement types.
- [ ] Treat omitted recommendation blocks as no longer eligible; do not infer why from private F06 evidence.
- [ ] If an active layout returns `503` because capacities are unconfigured, show a generic unavailable state and retry later. Do not show the internal configuration error to shoppers.

### Product list and Product Detail

- [ ] Use `search`, `categoryId`, `page`, `pageSize`, `sortBy=name|sellingPrice`, and `sortDirection=asc|desc`.
- [ ] Render only `AVAILABLE` / `UNAVAILABLE`; never estimate or display stock quantities.
- [ ] Render `imageUrl` only as a URL; upload is not supported.
- [ ] Use `productId` for detail navigation. Cross-Store and inactive Products return 404.
- [ ] Do not show SKU, cost, batch/expiry data, F03 scores, utility, or review information.

### Bundles

- [ ] Treat each `BUNDLE`/`FBT` recommendation placement as a grouped list of public Products.
- [ ] The backend hides the entire block if its approval/current eligibility or any component fails recheck.
- [ ] Cart, quantity selection, reservations, discounts, checkout, payment and order creation are F10/deferred.

### SYSTEM_ADMIN visibility management

- [ ] Provide a visibility toggle using `PUT /api/system/stores/{storeId}/storefront-visibility` and body `{"storefrontEnabled":true|false}`.
- [ ] Hide this control for every role except SYSTEM_ADMIN.
- [ ] Explain that visibility is not campaign publish/activation.
- [ ] Confirm the returned stable `storeSlug`; never treat visibility enablement as campaign activation.

## Public models

```ts
export type Availability = 'AVAILABLE' | 'UNAVAILABLE';
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
  type: 'BASELINE' | 'BUNDLE' | 'CROSS_SELL' | 'FBT' | 'BANNER';
  blockNo: number; startPosition: number; products: ProductCard[];
}
export interface HomeZone { name: string; placements: HomePlacement[] }
```

Examples: [`F09_examples/`](F09_examples/). Related Products is deferred. F07/F08 `priorityCoverage` remains a separate follow-up and is not included in F09 responses.

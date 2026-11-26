# F10 — Bundle Cart frontend handoff

**Part 2: IMPLEMENTED on `Khoi/feat/f10-bundle-add`, stacked on Part 1.** Integrate both ordinary cart and whole-bundle actions against a Backend running this branch. See the [F10 API](../api/F10_BUNDLE_CART_API.md). Use the [F09 API](../api/F09_STOREFRONT_API.md) and [F09 handoff](F09_HANDOFF.md) on dev (PR #33). The public Store is ACTIVE and addressed by `store.slug`. Product availability is `AVAILABLE | OUT_OF_STOCK`. The optional F10 bundle key is available on F09 home for eligible BUNDLE blocks. Public browsing can precede login. When a guest tries to add or view the cart, send them to F01 login and return to the same Store. Fetch server state before asking them to submit again.

## TypeScript types

```ts
// Prefer the shared lossless JSON adapter for IDs/versions outside the safe integer range.
type Int64 = number;
type CartLineState = 'AVAILABLE' | 'OUT_OF_STOCK' | 'NO_LONGER_AVAILABLE';
interface CartLine {
  itemId: Int64;
  productId: number;
  name: string | null;
  imageUrl: string | null;
  unit: string | null;
  quantity: number;
  unitPrice: number | null;
  lineTotal: number | null;
  state: CartLineState;
  addedFromBundle?: boolean;
}
interface CartView {
  cartId: Int64 | null;
  store: { storeSlug: string; name: string };
  version: Int64;
  currency: 'VND';
  updatedAt: string | null;
  items: CartLine[];
  subtotal: number | null;
}
type AddItemRequest = { productId: number; quantity: number };
type PatchItemRequest = { quantity: number };
type AddBundleRequest = { bundleKey: string };
type BundleComponentResult = { productId: number; requested: number; added: number };
type BundleAddResult = { cart: CartView; components: BundleComponentResult[] };
type BundleAddRejectedError = {
  error: string;
  code: 'BUNDLE_ADD_REJECTED';
  components: BundleComponentResult[];
};
type CartError = BundleAddRejectedError | {
  error: string;
  code?: 'CART_VERSION_CONFLICT' | 'BUNDLE_UNAVAILABLE'
    | 'INSUFFICIENT_SELLABLE_STOCK' | 'CART_QUANTITY_LIMIT';
};
```

## Part 1 flow

Load the cart with GET, then use POST `/items`, PATCH or DELETE `/items/{itemId}`, or DELETE on the cart root. All successful responses return `CartView` directly. Send the quoted latest cart version with `If-Match` on every mutation, including `"0"` for a virtual cart. Disable the action while pending. If the version is stale, GET the cart before retrying. Quantity edits and removal are available per line. If a single-item add or increase would exceed 99 or sellable stock, the server rejects the entire mutation with `CART_QUANTITY_LIMIT` or `INSUFFICIENT_SELLABLE_STOCK`. Keep the unchanged cart and let the shopper enter a smaller quantity. Format current price and totals as whole đồng using HALF_UP rounding, show “Chưa có giá” for null and `0 ₫` for zero.

## Whole-bundle flow

1. Read `bundleKey` from a F09 BUNDLE block. Show the action for a key on the active Layout B, irrespective of provenance. Disable it while the request is pending. POST to `/bundles` under the cart URL. Send only `{bundleKey}` and the latest quoted cart version in `If-Match`. Each click requests one bundle. Use the line quantity controls for later changes. Reuse the project auth/session client.
2. On 200, replace the cart and update the badge. Use `components` and match `productId` to the F09 block names. A row with `added = 0` is skipped. For example, “Đã thêm 1 sản phẩm. Không thêm được: Sữa yến mạch.” Where `0 < added < requested`, show the actual and requested quantities. Fall back to a count if names are unavailable. When every component is added in full, show the ordinary success state.
3. Display ordinary Product lines. Optional `addedFromBundle` is a label. All lines retain independent edit and remove actions. Read server line totals and subtotal. Use VND whole đồng for display, such as `64.000 ₫`. A null total displays “Chưa có giá”; numeric 0 displays `0 ₫`.
4. On 401 use the F01 session/login flow. On 403 show access denied. On 404 reload the Store or show “Không tìm thấy”, without exposing other Stores. On `CART_VERSION_CONFLICT` or an uncertain transport result, GET the cart before any user-confirmed retry. On `BUNDLE_UNAVAILABLE`, reload F09 home and clear any stale key. On `BUNDLE_ADD_REJECTED`, keep the cart and show that no component was added, using the zero-added rows in the response. On validation errors show the field or quantity limit. For 503 show unavailable with a manual retry.
5. Never drive the cart from local fixture inventory outside demo mode. Switch cart scope with Store slug and session; cancel or ignore old in-flight responses on navigation. Use a separate cart loading state and handle version 0 for a virtual cart.

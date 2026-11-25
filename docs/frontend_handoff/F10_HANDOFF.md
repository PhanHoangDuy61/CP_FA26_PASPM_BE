# F10 — Bundle Cart frontend handoff

**Status: PLANNED.** Use fixtures during FE development until the [F10 API](../api/F10_BUNDLE_CART_API.md) is available. Use the [F09 API](../api/F09_STOREFRONT_API.md) and [F09 handoff](F09_HANDOFF.md) on dev (PR #33). The public Store is ACTIVE and addressed by `store.slug`. Product availability is `AVAILABLE | OUT_OF_STOCK`. The F10 bundle key is a planned extension to F09 home. Public browsing can precede login. When a guest tries to add or view the cart, send them to F01 login and return to the same Store. Fetch server state before asking them to submit again.

## Draft TypeScript types

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

## Flow

1. Read `bundleKey` from a F09 BUNDLE block. Show the action for a key on the active Layout B, irrespective of provenance. Disable it while the request is pending. Send only `{bundleKey}` and the latest quoted cart version in `If-Match`. Each click requests one bundle. Use the line quantity controls for later changes. Reuse the project auth/session client.
2. On 200, replace the cart and update the badge. Use `components` and match `productId` to the F09 block names. A row with `added = 0` is skipped. For example, “Đã thêm 1 sản phẩm. Không thêm được: Sữa yến mạch.” Where `0 < added < requested`, show the actual and requested quantities. Fall back to a count if names are unavailable. When every component is added in full, show the ordinary success state.
3. Display ordinary Product lines. Optional `addedFromBundle` is a label. All lines retain independent edit and remove actions. Read server line totals and subtotal. Use VND whole đồng for display, such as `64.000 ₫`. A null total displays “Chưa có giá”; numeric 0 displays `0 ₫`.
4. On 401 use the F01 session/login flow. On 403 show access denied. On 404 reload the Store or show “Không tìm thấy”, without exposing other Stores. On `CART_VERSION_CONFLICT` or an uncertain transport result, GET the cart before any user-confirmed retry. On `BUNDLE_UNAVAILABLE`, reload F09 home and clear any stale key. On `BUNDLE_ADD_REJECTED`, keep the cart and show that no component was added, using the zero-added rows in the response. On validation errors show the field or quantity limit. For 503 show unavailable with a manual retry.
5. Never drive the cart from local fixture inventory outside demo mode. Switch cart scope with Store slug and session; cancel or ignore old in-flight responses on navigation. Use a separate cart loading state and handle version 0 for a virtual cart.

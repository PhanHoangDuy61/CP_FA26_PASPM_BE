# F10 — Bundle Cart API

**Status: PLANNED.** Illustrative request and response JSON. Decision: [DEC-20261125-06](../decisions/2026-11-25/DEC-20261125-06-f10-f11-mvp-contract.md). The F09 baseline is branch `origin/Khoi/merge/f09-storefront-be`. F10 targets V31 after F09 V30.

## Access and Store isolation

The prefix is `/api/storefront/{storeSlug}/cart`. All routes require Bearer JWT role `END_CUSTOMER`; shoppers browse F09 anonymously and sign in before accessing a cart. END_CUSTOMER has no Store claim. Resolve the slug through F09's ACTIVE Store lookup and scope all cart lookups by resolved Store and authenticated user. Treat unknown or INACTIVE Store, foreign Product or bundle key, and line IDs outside the caller's cart as 404. Scope Product and campaign/layout lookups to the same Store. The caller supplies neither owner nor Store ID in JSON.

## Endpoints

| Method / suffix | Request | Success |
|---|---|---|
| GET cart root | No body or version header | 200 `CartView`. A never-created cart is virtual with `cartId:null`, `version:0`, `items:[]`, `subtotal:0`, `updatedAt:null`. |
| POST `/items` | `{"productId":501,"quantity":2}` | 200 `CartView`. Adds to the existing line for the Product or creates it. |
| POST `/bundles` | `{"bundleKey":"opaque-f09-value"}` | 200 `BundleAddResult` when one or more components are added. |
| PATCH `/items/{itemId}` | `{"quantity":3}` | 200 `CartView`. Sets the quantity of that line. |
| DELETE `/items/{itemId}` | No body | 200 `CartView`. Removes exactly that line. |
| DELETE cart root | No body | 200 empty `CartView`. Clears the current Store cart and retains its identity if persisted. |

Strict JSON rejects unknown fields. Individual writes use positive integer quantities, with a total limit of 99 per Product in the cart. Quantity zero uses DELETE. The server reads current price and checks sellable stock on adds and quantity increases. Removal and reduction remain available for stale lines. Money persists at scale 2 and FE displays VND as whole đồng. Subtotal and line totals are computed server-side, with no discount.

## Versioning

Send a strong, quoted cart version on **every POST/PATCH/DELETE**, for example `If-Match: "4"`. Use `"0"` for the virtual cart. Missing or malformed header (including wildcard and weak tags) returns 400. A stale valid version returns 409 `CART_VERSION_CONFLICT`. The server checks version within the write transaction; one successful cart change advances it once. Serialize first creation using the unique customer/Store key. Clients use the latest response or GET after a conflict or uncertain response. There is no `Idempotency-Key` header or automatic replay. A cart no-op keeps its version.

## Bundle resolution and events

- The planned additive F09 `HomePlacement.bundleKey` is an opaque string on every `BUNDLE` block in the selected Layout B of an ACTIVE campaign, and absent or null on other projections. The key identifies Store, campaign, layout revision and block. The server revalidates all of them and reads the authoritative component IDs. Clients cannot send Product overrides or infer keys. A stale same-Store key needs a fresh F09 home response.
- A block may be added regardless of whether it matches a recommendation. F09 keeps the block visible when a component is out of stock and continues to suppress the private metadata of hidden Products. Classify its origin using the entire block before stock filtering. Exactly one complete matching BUNDLE recommendation from the same Store and snapshot that is APPROVED and readyForConsumption yields `MINED`. Zero or multiple matches yield `MANUAL`. A source ID attached to a placement is insufficient evidence by itself.
- Skip out-of-stock components; report their IDs in `skippedProductIds`. Persist changes to the remaining ordinary lines and the single `BUNDLE_ADDED` event in the same transaction. The event stores the origin, Store, campaign, layout/revision, block, UTC time and the **sum of actual Product units added**. Partial success counts as one action. Transaction failure rolls back lines and event together.
- When an authenticated, scoped, current block yields no eligible additions, commit a `BUNDLE_ADD_REJECTED` event with origin and zero units without changing the cart/version, then return 409. Invalid requests, failed authorization, stale cart versions and foreign resources produce no business event. For a stale block key return `BUNDLE_UNAVAILABLE`; it is not an evaluation of current block stock. The F11 metric excludes every rejection.
- Each successful cart mutation that changes persisted state records exactly one event. Other proposed event types are `ITEM_ADDED`, `ITEM_QUANTITY_CHANGED`, `ITEM_REMOVED`, `CART_CLEARED`. Event data is append-only. A no-op creates no event. Separate rejected requests can create separate rejection records.

**Draft quantity details for PO review:** the default action requests one unit per component. Optional `bundleQuantity` beyond one is proposed in the plan, not required for the MVP FE. For a component with positive but insufficient stock for the requested increment, or where the increment would exceed 99, the proposed rule skips that entire component and reports its ID. The public contract should finalize these details before clients rely on them.

## Response examples

Illustrative partial success. Product 501 had one unit in the cart and the block added another. Product 502 was skipped.

```json
{
  "cart": {
    "cartId": 31,
    "store": { "storeSlug": "demo-store", "name": "Demo Store" },
    "version": 5,
    "currency": "VND",
    "updatedAt": "2026-11-25T09:30:00Z",
    "items": [
      {
        "itemId": 71, "productId": 501, "name": "Oat milk", "imageUrl": null,
        "unit": "carton", "quantity": 2, "unitPrice": 32000.00,
        "lineTotal": 64000.00, "state": "AVAILABLE", "addedFromBundle": true
      }
    ],
    "subtotal": 64000.00
  },
  "skippedProductIds": [502]
}
```

`CartView` is the same object under `cart` for all other endpoints. IDs and version are signed int64 except Product IDs (int32). A line's state is `AVAILABLE`, `OUT_OF_STOCK` or `NO_LONGER_AVAILABLE`. A previously added hidden Product keeps its editable/removable line identity, with public metadata and money fields null when unavailable. Subtotal is null if any line cannot be priced. No internal inventory quantities, cost, batch data or recommendation evidence is exposed.

```json
{"error":"No bundle component can be added","code":"BUNDLE_ADD_REJECTED","skippedProductIds":[501,502]}
```

| HTTP | Code / behavior |
|---|---|
| 400 | Invalid field, quantity or version header. |
| 401 / 403 | Missing/invalid authentication / authenticated role cannot use F10. |
| 404 | Absent, hidden or foreign resource; invalid public bundle key. |
| 409 | `CART_VERSION_CONFLICT`, `BUNDLE_UNAVAILABLE`, `BUNDLE_ADD_REJECTED`, `INSUFFICIENT_SELLABLE_STOCK` or `CART_QUANTITY_LIMIT` as applicable. |
| 503 | Required layout capacity configuration is unavailable for bundle resolution. |

Related: [F10 handoff](../frontend_handoff/F10_HANDOFF.md), [F11 Dashboard API](F11_DASHBOARD_API.md).

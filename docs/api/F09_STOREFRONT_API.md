# F09 — Public Storefront API

F09 exposes a read-only public catalogue and selected F08 homepage. It does not publish or activate campaigns. Public responses use storefront-specific DTOs and never include F02 cost/stock details, F06 evidence/review history, F07 utility, or batch data.

## Authorization

| Method and path | Access | Behavior |
|---|---|---|
| `GET /api/storefront/stores` | Public, no bearer required | Lists only Stores with `storefront_enabled=true`; response has `storeSlug` and `name`, never `storeId`. |
| `PUT /api/system/stores/{storeId}/storefront-visibility` | `SYSTEM_ADMIN` | Sets only the Store visibility flag. It does not publish/activate/end a campaign. |
| `GET /api/storefront/{storeSlug}/home` | Public, no bearer required | Returns the currently ACTIVE campaign's selected READY layout, or `DEFAULT_CATALOG` only when there is no ACTIVE campaign. |
| `GET /api/storefront/{storeSlug}/products` | Public, no bearer required | Pages ACTIVE Products from the selected Store. |
| `GET /api/storefront/{storeSlug}/products/{productId}` | Public, no bearer required | Reads ACTIVE Product detail from the selected Store. |

Unknown and visibility-disabled Stores both return `404 {"error":"Storefront not found"}`. Product ids are Store-scoped; inactive or foreign Products return `404 {"error":"Product not found"}`. The visibility endpoint returns `404 {"error":"Store not found"}` for an unknown internal Store; unauthenticated and wrong-role requests are rejected by Spring Security (`401` / `403`).

## Store slug and visibility

V27 backfills each existing Store with a stable human-readable slug plus a deterministic SHA-256-derived suffix, so same-name Stores receive distinct slugs without embedding their internal numeric ids. Existing Stores remain private (`storefront_enabled=false`). Slugs have a unique index. Stores created outside this application must be assigned a slug before enabling public visibility; the SYSTEM_ADMIN visibility operation assigns one if absent.

## Public Product fields

`ProductCard` is separate from F02 `ProductView`:

```text
productId, name, description, imageUrl, unit, sellingPrice,
category { categoryId, name } | null,
availability: AVAILABLE | UNAVAILABLE
```

`imageUrl` is an optional absolute HTTP(S) URL; upload is not implemented. `description` is optional (maximum 2,000 characters). Availability is a summary only; no quantity is returned. It uses Store-local today (`paspm.store-time-zone`, default `Asia/Ho_Chi_Minh`) and counts ACTIVE batches with positive remaining quantity whose expiry is strictly after today. Non-expiring Products may use batches without an expiry; when `requiresExpiry=true`, a missing-expiry batch contributes no sellable quantity.

Product list query parameters follow F02 naming: `search` (name/description), `categoryId`, `page` (default 0), `pageSize` (default 20, maximum 100), `sortBy=name|sellingPrice` (default `name`), and `sortDirection=asc|desc` (default `asc`). Only ACTIVE Products are returned. The response is `StorefrontProductPage {items,page,pageSize,totalElements}`, a distinct schema from the authenticated F02 `ProductPage`.

## Homepage projection

`GET /home` returns `{store,campaignName,fallbackType,zones,catalog}`.

- If an ACTIVE campaign exists, only `selected_layout_id` is read. The selected layout must be READY. Layout contents are emitted in stored zone/block/position order. Baseline A placements use type `BASELINE`. Recommendation placements support the current F06 types `BUNDLE`, `CROSS_SELL`, `FBT`, and `BANNER`.
- A recommendation block is omitted as a whole unless its current status is APPROVED, it belongs to the selected snapshot, its stored product set still matches its placement, and every component is ACTIVE and meets the recommendation's captured F06 `minimumSellableQuantity`. Required-expiry metadata is rechecked. The response does not expose recommendation ids, policy/evidence, review status/history, or utility.
- When there is no ACTIVE campaign, the response has `fallbackType=DEFAULT_CATALOG`, no zones, and ACTIVE Products grouped by category; within each category products sort by name and then `productId`. This is not baseline A.
- A DRAFT, PUBLISHED-but-not-active, or ENDED campaign is never projected.
- `priorityCoverage` is not part of F09; its F07/F08 definition/use remains a separate follow-up.

F06 permits BANNER recommendations to contain a singleton or larger product pattern; the current contract does not define a one-banner-per-homepage rule. F09 does not invent one. F08 layout zone capacity settings are currently owner-TBD. To prevent an unbounded active layout from being rendered publicly, `/home` returns `503 {"error":"Storefront layout capacities are not configured"}` until positive block and product caps are configured for every recommendation zone. This F09 guard does not change F07/F08 authorization or stored-layout behavior.

## Example response shapes

See [`../frontend_handoff/F09_examples/`](../frontend_handoff/F09_examples/) for synthetic Store list, active/fallback homepage, product list/detail, bundle and error examples. Related Products is deferred. F10 cart, checkout, payment, orders, discounts, image upload and priority-coverage calculation are out of scope.

## Verification scope

`StorefrontIntegrationTest` covers anonymous access, Store visibility and role checks, missing/disabled Store handling, Store-isolated Product reads, fallback behavior, selected active layout/recommendation filtering, sellability, public DTO privacy, one bulk availability query, and runtime OpenAPI paths. Flyway V27 is covered by fresh-context migration and V26-to-V27 H2 upgrade tests. MySQL migration execution remains NOT RUN locally.

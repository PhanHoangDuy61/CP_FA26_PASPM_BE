# ROC-20261126-004 — F09 public Storefront (V29 + V30) in the diagrams

| Field | Value |
|---|---|
| Date / timezone | 2026-11-26 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Sonnet 5.5), requested by Nguyễn Minh Khôi (unattended overnight task) |
| Type | Documentation (diagrams) |
| Branch / base | `Khoi/docs/diagrams-update-20261126` / `origin/dev` @ `b94584b` |
| Rules drawn | [DEC-20261125-04](../../decisions/2026-11-25/DEC-20261125-04-f09-public-store-follows-store-status.md); [F09 API](../../api/F09_STOREFRONT_API.md); V29 and V30 |
| Release | UNRELEASED |
| Daily index | [2026-11-26](README.md) |

## Scope and changes

- **AD08 (activity):** the homepage is opened by the Store slug (public, no login, ACTIVE Stores only); the service returns the ACTIVE campaign layout, or `DEFAULT_CATALOG` when none; the product step shows the product card with image, description and availability.
- **R2-08.08 (sequence):** `openHomepage(storeSlug)` and `getHomepage(storeSlug)`; the product reads return `ProductCard` (messages 8 and 14).
- **R2-07.08 (class):** `StorefrontScreen.openHomepage(storeSlug : String)`, `StorefrontService.getHomepage(storeSlug : String)`; `ProductView` is replaced by `ProductCard` (productId, name, description, imageUrl, unit, sellingPrice, availability `AVAILABLE | OUT_OF_STOCK`), box widened. `ProductRepository.find` returns `ProductCard`.
- **FR / Use Case:** FR09.02 "Display product details and availability"; UC26 "…(public, by Store slug)"; UC27 "View Product Details (Related Products deferred)".
- **ERD** (renamed `PASPM_ERD_V1_V28.drawio` → `PASPM_ERD_V1_V30.drawio`): `product.image_url` and `product.description` (`[V29]`) on the Logical ERD and the Catalog & Inventory page, each with a note: the public key is `store.slug` (V27); the old F09 merge added `store.store_slug` and `store.storefront_enabled` in V29 and V30 dropped them, so they are not drawn; a Store is public exactly when ACTIVE. The area frame of the Logical ERD was enlarged for the notes; the V28 season note on Utility & Snapshot was shortened to fit above the next table. Titles say V1–V30.
- R2-06.07 Store State and AD09: the INACTIVE notes now say that the public Storefront returns 404 (as the public images do).
- No visibility endpoint and no `storefront_enabled` appears anywhere (there is none on `dev`).
- All other pages are byte-identical to the previous commit.

## Not done

- Related Products and the cart stay as drawn (F10 is planned; the planned marking is done in the F10/F11 record of this day).
- The Mermaid mirrors `mermaid/state-*.mmd`, `logical-erd.mmd` and `architecture.mmd` were not regenerated (no renderer was run); they still describe V1–V19.

## Verification

| Check | Result |
|---|---|
| `validate_drawio.py` on the pack and the ERD | PASS |
| Pages not listed above | byte-identical |
| Overlap check of the new ERD notes against the other shapes of their pages | no overlap (the notes were moved once after the first export showed the V29 note on the batch table) |
| PNG export, looked at: AD08, R2-08.08, R2-07.08, AD09, ERD Catalog & Inventory | `outputs/diagrams-overnight/png/E-f09/` |
| Maven | NOT RUN |

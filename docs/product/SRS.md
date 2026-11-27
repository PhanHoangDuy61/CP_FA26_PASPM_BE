# PASPM — Software Requirements Specification (agent edition)

| Field | Value |
|---|---|
| Project | PASPM (FA26SE048) |
| Version / date | 0.4 — API inventory synchronized 2026-11-25 |
| Baseline | Backend `dev` @ `cded4ab`: Flyway V1–V30, OpenAPI 112 paths / 139 operations |
| Audited against code | `530651bad5b02d90bcb48ecffaf7fa5a0f6b0b7e`, see (audit report kept outside the repo, 2026-11-19) |
| Synced with | `dev` @ `636952b` (2026-11-20): F02 update `d22f735`; PR #20 (decisions); PR #21 (F04 Excel); PR #22 (zone caps, top-K baseline, equal-size simulation, DEC-20261119-06); PR #23 (SPMF whole-đồng fallback, DEC-20261119-05); PR #25 (agent kit). Checked for these changes only, not a full re-audit |
| Based on | Report 3 (SRS) plus newer owner decisions and current code |
| Companion files | [BRD_VISION.md](BRD_VISION.md) · [ARCHITECTURE_SDD.md](ARCHITECTURE_SDD.md) · [GLOSSARY.md](GLOSSARY.md) |

**Status labels**

| Label | Meaning |
|---|---|
| **DONE** | Merged on `dev` |
| **PARTIAL** | Merged, with a documented gap |
| **PLANNED** | Specified or planned, no code |
| **REMOVED** | Superseded by a decision |
| **TBD** | Not decided. Ask the owner |

**Authoritative detail** lives in the backend repo:
- `docs/api/*.md` and `openapi.json`;
- `docs/features/F0x/`;
- `docs/frontend_handoff/`;
- `docs/decisions/`.

This file is the map. When it disagrees with those files, they win. Fix this file.

---

## 1. Actors and roles

| Actor | Store-bound | Summary |
|---|---|---|
| System Administrator (`SYSTEM_ADMIN`) | No | Accounts and roles, λmax policy, mining configuration (minUtil), mining for a selected Store, schedules, Store management (create, edit including slug, deactivate/reactivate, images; no delete) |
| Store Admin (`STORE_ADMIN`) | Yes | Catalogue, prices and costs, batches and push days, imports, activate / end / rollback campaigns, own Store profile (address, phone, icon, banner) |
| Merchandiser (`MERCHANDISER`) | Yes | λ / α / β / N, seasons, snapshots, mining for its own Store, recommendations, layouts, campaigns, simulation, view own Store |
| End Customer (`END_CUSTOMER`) | No | Self-registers. F10 cart on the Part 2 branch; anonymous F09 browsing |
| System Scheduler (`SYSTEM_SCHEDULER`) | — | Internal principal that creates scheduled snapshots and requests. It cannot log in |

## 2. Use cases (Report 3 numbering kept)

UC03 and UC22 were removed earlier, and their numbers are not reused.

| UC | Name | Actor(s) | Feature | Status | Change vs Report 3 |
|---|---|---|---|---|---|
| 01 | Manage User Accounts | SA | F01 | DONE | — |
| 02 | Manage Roles & Permissions | SA | F01 | DONE | — |
| 04 | Set Mining Threshold | SA | F05 | DONE | **Algorithm selection removed.** EFIM is fixed. minUtil is a percentage or an absolute value. Sending `algorithm` gives 400 |
| 05 | Submit Async Mining | SA (selected Store), **Merchandiser (own Store)** | F05 | DONE | EFIM-only. Merchandiser body is `{requestKey, snapshotId}` |
| 06 | Schedule Mining / Occurrence History | SA | F05 | PARTIAL | Enable returns **503** until sign-off. `compareAlgorithms` removed |
| 07 | View Mining Request / Run Status | SA, Merch, Store Admin | F05 | DONE | **Progress API** added (stages, %, elapsed) |
| 08 | View HUI-Miner / EFIM Benchmark | SA | F05 | **REMOVED** (2026-11-16) | Per-run runtime and memory remain in `RunView` |
| 09 | Configure Utility Formula & Priority | Merch | F03 | DONE | Now λ ∈ [0, λmax], α + β = 1, N. **Seasons CRUD plus product assignment.** Product scores and the expiry window were removed |
| 10 | Generate Bundle / Cross-sell / FBT / Banner | Merch | F06 | DONE | — |
| 11 | Review / Approve / Reject | Merch | F06 | DONE | Warns about `seasonEnded` and `inventoryChanged` |
| 12 | Arrange Layout | Merch | F07 | DONE (API) | B is edited **per placed product** (`PUT /placements` sends product, zone, block, position and nullable source recommendation). F06 evidence and Layout A remain immutable |
| 13 | Create A/B Layout Variants | Merch | F07/F08 | DONE (API) | A is generated (frequency baseline, read-only). B is built at campaign creation |
| 14 | Prepare / Publish Campaign | Merch | F08 | DONE (API) | Also covers *withdraw* |
| 15 | Run A/B Simulation | Merch | F08 | DONE (API) | **Offline replay** of transactions dated after the snapshot. Synthetic traffic is not modelled |
| 16 | Compare Simulated Results | Merch | F08 | PARTIAL | Metrics are hitRate, bundleConversion, capturedUtility and priorityCoverage (PR #32, `516183d`). CTR and estimated revenue from Report 3 are not implemented |
| 17 | Manage Products | Store Admin | F02 | DONE | Legacy score fields removed. SKU format and similar-name confirmation (2026-11-19) |
| 18 | Manage Categories | Store Admin | F02 | DONE | — |
| 19 | Manage Selling Prices / Costs | Store Admin | F02 | DONE | — |
| 20 | Manage Inventory | Store Admin | F02 | DONE | **Push day per batch** (set, PATCH, DELETE; `MISSING_PUSH_DAY` warning; no default). Expired only after the expiry date, `effectiveStatus` / `daysToExpiry` (2026-11-24). **Soft delete product** (FR-02.5). Batch code generated by the backend; expiry date editable by PATCH and clearable by DELETE (2026-11-19) |
| 21 | Import Transaction Files | Store Admin | F04 | DONE | Sync and **async** CSV routes; Excel (`POST /api/transactions/imports/excel`, synchronous) merged 2026-11-19 (PR #21) |
| 23 | Activate Approved Campaign | Store Admin | F08 | DONE (API) | Also **end** and **rollback** |
| 24 | View Retail Performance Dashboard | Store Admin, Merch | F11 | Backend implemented on F11 branch | MVP scope decided 2026-11-19 (see FR-11.1) |
| 25 | View A/B Reports | Store Admin, Merch | F08/F11 | DONE (API read) | Reports are immutable |
| 26 | View Merchandising Homepage | End Customer / guest | F09 | DONE (API, MVP) | Anonymous, by `storeSlug` = `store.slug`; ACTIVE Stores only (DEC-20261125-04). Frontend still mock |
| 27 | View Product Details / Related | End Customer / guest | F09 | PARTIAL (API) | Product list and detail done; related products come after the MVP |
| 28 | View Recommended Bundles | End Customer / guest | F09 | DONE (API, MVP) | Bundles are homepage placements. On the F10 Part 2 branch, every BUNDLE block in the active selected Layout B supports the bundle action |
| 29 | Add Whole Bundle to Cart | End Customer | F10 | Implemented on Part 2 branch | Login required. Skips unavailable components. No reservation |
| 30 | Validate / Preprocess Orders | (included in UC21) | F04 | DONE | — |
| 31 | Create Frozen Utility Snapshot | Merch | F03 | DONE | Freezes W2 inputs, seasons and warnings |
| 32 | Set Global Utility Lambda Policy | SA | F03 | DONE | **λmax only** (range [0, λmax], default 0.5) |
| 33 | Select Store | SA | F05 | DONE | The picker lists ACTIVE Stores by default (DEC-20261123-01) |
| 34 | Manage Stores | SA | F01 | DONE (branch) | Create, edit (slug included), deactivate/reactivate, icon/banner; no delete (DEC-20261123-01) |
| 35 | Manage own Store profile | Store Admin; Merch (view) | F01 | DONE (branch) | Store Admin edits address, phone, icon, banner |

Missing UC numbers:
- **Manage Seasons** is covered inside UC09.
- **Clear push day** is covered inside UC20.
- **View expiry alerts** belongs to F06.

Whether any of these get separate UC numbers is **TBD**.

## 3. Functional requirements by feature

Each line gives the behaviour an agent must preserve. Exact request and response shapes are in `docs/api`.

### F01 — Identity & access (DONE)

- FR-01.1: Sign-in uses email and password, or a Google ID token whose email Google has verified.
- FR-01.2: Self-registration (End Customer) and invitation both use an email OTP. Registration returns 202 for a new email and 409 for an existing one.
- FR-01.3: Session tokens:
  - the access JWT is a Bearer token, 3600 s by default;
  - the refresh credential is held **only in an HttpOnly cookie**;
  - refresh needs the CSRF cookie plus the `X-CSRF-Token` header;
  - `logout` and `logout-all` revoke sessions;
  - a password reset increments `token_version`.
- FR-01.4: An account has exactly one role. Store Admin and Merchandiser have exactly one Store.
- FR-01.5: Profile with avatar. Account states are PENDING_VERIFICATION, ACTIVE and INACTIVE.
- FR-01.6 (DEC-20261123-01, V27): A Store has `name` (unique after trimming, ignoring case), `slug` (unique, the F09 `storeSlug`), `status` ACTIVE/INACTIVE, optional `address` and `phone`, `version`, `created_at`/`updated_at`, `deactivated_at`/`deactivated_by`, and `icon_key`/`banner_key`.
- FR-01.7: The System Admin creates Stores, edits every field (slug included) and deactivates or reactivates them. There is no delete. The Store detail shows the assigned-user count and a read-only list of the users.
- FR-01.8: A Store Admin views their own Store and edits address and phone. A Merchandiser views their own Store.
- FR-01.9: Deactivation.
  - One central guard rejects every request, login and refresh of a user of an INACTIVE Store with 403 `STORE_INACTIVE`.
  - A System Admin cannot create new work for it (manual mining, schedule create or enable) or assign users to it (409 `STORE_INACTIVE`).
  - The scheduler records its due fire times as SKIPPED (`STORE_INACTIVE`).
  - In-flight work finishes and history stays readable.
- FR-01.10: Store icon and banner.
  - PNG or JPEG only (magic bytes plus decoding); icon ≤ 1 MiB, banner ≤ 5 MiB, ≤ 4000×4000 px.
  - Stored under a UUID name on the shared media storage (`PASPM_MEDIA_ROOT`).
  - The public GET serves only the current images of ACTIVE Stores.

### F02 — Catalogue & inventory (DONE)

- FR-02.1: CRUD for categories and products, scoped to the Store. Categories are deactivated, not deleted. A product is deactivated (`PATCH …/status`) or **soft-deleted** (`DELETE /api/products/{id}`, FR-02.5). SKU is **generated by the backend** from the product ID as `SKU-%011d` (for example `SKU-00000000042`), read-only and never changed; clients must not send `sku` (400 unknown field) ([DEC-20261119-07](../decisions/2026-11-19/DEC-20261119-07-server-generated-product-sku.md)). Existing SKUs, including `FOODMART-xxxx` in databases seeded before 2026-11-20, are kept. A product name that equals, or is one character away from, another product's normalized name in the Store (names of 4+ characters) returns 409 `PRODUCT_NAME_CONFIRMATION_REQUIRED` until the client retries with `confirmSimilarName: true`. Product and batch IDs are generated by the database.
- FR-02.2: Inventory batches:
  - quantity, remaining quantity and expiry date, which is mandatory when the product requires one;
  - status ACTIVE / BLOCKED;
  - **push day ≤ expiry date**; a push day the user leaves empty stays empty (no default, no backfill), and the batch response warns with `MISSING_PUSH_DAY` when a product that requires expiry has none and the batch is not expired;
  - a batch is **expired only when today (`Asia/Ho_Chi_Minh`) is after its expiry date**, so it is still active on the expiry date. Expiry is computed on read: `effectiveStatus` is `ACTIVE | BLOCKED | EXPIRED` (EXPIRED wins over BLOCKED) and `daysToExpiry` is 0 on the expiry date. The stored status stays ACTIVE / BLOCKED;
  - `batchCode` is generated as `BATCH-YYYYMMDD-NNN` (Store-local receive date, per-product sequence); clients must not send it (400);
  - PATCH can set `expiryDate`; `DELETE /api/inventory-batches/{id}/expiry-date` clears it, rejected with 400 when the product requires expiry.
- FR-02.3: Stock is derived from sellable batches and is never stored on the product.
- FR-02.4: Product requests and responses contain no `expiryPushDays`, `stockPriorityScore` or `seasonalPriorityScore`. Sending any of them returns 400.
- FR-02.5: A Store Admin soft-deletes a product of their own Store with `DELETE /api/products/{id}` (204; another Store's or unknown product 404). It is refused with 409 `PRODUCT_HAS_SELLABLE_STOCK` while ACTIVE, non-expired batches hold units, or `PRODUCT_IN_LIVE_CAMPAIGN` while a PUBLISHED or ACTIVE campaign layout (A or B) places it. A deleted product keeps its row, SKU (never reused), batches and every historical reference, becomes INACTIVE and is excluded from catalogue queries before pagination. There is no restore. F04 still resolves its SKU and warns on those rows; seasons keep the assignment (labelled deleted) and reject new ones.

### F03 — Utility profiling, Weight v2 (DONE)

- FR-03.1: `W = 1 + λ(α·U_expiry + β·M)`, rounded half-up to 2 decimals. WeightedUtility = Base × W, at scale 6.
- FR-03.2: U_expiry uses FEFO over batch push days with sales rate v over the last N days. A missing push day only warns (`MISSING_PUSH_DAY`). v = 0 gives `SLOW_MOVING`.
- FR-03.3: Seasons:
  - CRUD with optimistic version;
  - assign and unassign products;
  - a season that has products assigned cannot be deleted (409);
  - M is the highest active weight, where "active" includes both the start and end dates.
- FR-03.4: Preview the matrix for a date range, then create an **immutable** snapshot that freezes every input, the seasons and the warnings.
- FR-03.5: If the stored λ is above λmax, `lambdaValid=false` and new snapshots return 409.
- FR-03.6: Frozen JSON is read by `scoringVersion`. Old versions remain readable. Unknown versions are rejected.

### F04 — Transaction pipeline (DONE for CSV)

- FR-04.1: CSV upload follows the approved template. Limits are 5 MiB and 10,000 non-blank rows (both configurable).
- FR-04.2: Async import:
  - returns 202 and a batch;
  - progress is polled (stage `RECEIVING → QUEUED → IMPORTING → terminal`; the status field uses `PROCESSING` instead of `IMPORTING`);
  - the request takes an `Idempotency-Key` header (1–128 URL-safe characters);
  - the background worker uses leases.
- FR-04.3: Rows are grouped by order. Each order is validated and committed atomically.
  - The same order with the same content is a duplicate.
  - The same order with different content is a conflict.
- FR-04.4: Only COMPLETED orders with unit price ≥ unit cost are F03-eligible. Imports never change inventory.
- FR-04.5: Excel import (XLS/XLSX, contract approved 2026-10-26) is merged into `dev` on 2026-11-19 (PR #21): `POST /api/transactions/imports/excel`, `STORE_ADMIN`, multipart `file`, synchronous `201 ImportSummary`, same order pipeline as CSV. There is no async Excel route.

### F05 — Mining (DONE; scheduler PARTIAL)

- FR-05.1: Global minUtil policy (percentage or absolute), **versioned**. The algorithm is fixed to EFIM.
- FR-05.2: Submission:
  - the System Admin submits for a selected Store and READY snapshot;
  - the Merchandiser submits for its own Store;
  - submissions are idempotent by `requestKey`.
- FR-05.3: Input bounds are checked before the request is queued:
  - ≤ 1,600 distinct products and ≤ 20,000 matrix rows;
  - EFIM int32 guard on transaction utility, product TWU and converted minUtil, each with its own 400 message;
  - since PR #23 (DEC-20261119-05) the input is built losslessly first and, **only** if a bound is exceeded, rebuilt with each item rounded to 1 đồng HALF_UP (`conversionVersion F03_WHOLE_UNIT_HALF_UP_INT32_DENSE_V1`, `conversionMode WHOLE_UNIT_HALF_UP`, `roundingUnit 1`, shown as additive view fields). If it still overflows, the request gets 400 naming `WHOLE_UNIT_HALF_UP`. Snapshots stay exact.
- FR-05.4: Worker and runtime:
  - the worker claims requests with a lease and fencing token;
  - SPMF runs in a Java 25 child process with 256 MiB heap and a 60 s timeout;
  - output is capped at 4 MiB with a 64 KiB log;
  - more than 2,000 patterns fails with `OUTPUT_LIMIT`.
- FR-05.5: Progress endpoint for the Merchandiser and Store Admin (own Store) and the System Admin (audited).
- FR-05.6: Schedules:
  - weekly or monthly, lookback 7–365 days;
  - occurrences record `SKIPPED`;
  - preparation gets at most 3 attempts (15 s, then 30 s apart), after which the occurrence is `FAILED` with `RETRY_EXHAUSTED`;
  - **enabling dispatch returns 503**.
- FR-05.7: Store users read patterns. The System Admin cannot read raw patterns.

### F06 — Recommendations (DONE)

- FR-06.1: Generate BUNDLE, CROSS_SELL, FBT and BANNER from one COMPLETED run of the same Store, under a versioned Store policy:
  - max bundle size 4;
  - FBT support count ≥ 2 and support ratio ≥ 0.05;
  - cross-sell confidence ≥ 0.50;
  - minimum sellable quantity 1.
- FR-06.2: Generation is idempotent:
  - candidates are reused by identity hash;
  - at most 10,000 candidates per generation, otherwise 409.
- FR-06.3: Review:
  - approve or reject once, with a reason and an expected version;
  - approval is blocked when current eligibility is not ELIGIBLE.
- FR-06.4: Live eligibility is rechecked on every read. `readyForConsumption = APPROVED ∧ ELIGIBLE`.
- FR-06.5: Expiry alerts are derived from batch push days. The `seasonEnded` / `endedSeasons` warning is raised. Nothing is re-run automatically.
- FR-06.6: Ranked list order:
  1. eligibility;
  2. utility descending;
  3. support;
  4. canonical key;
  5. type;
  6. anchor;
  7. id.

### F07 — Layout simulator (DONE API; PARTIAL)

- FR-07.1: Baseline A is deterministic (frequency order, ties broken by product id) and read-only. Since PR #22 (DEC-20261119-06) it holds only the top-K products, with K = the BASELINE product capacity (35), as `F07_BASELINE_V2`. `baseline_params` stores `k` and `candidateProducts`. Older `F07_BASELINE_V1` layouts, which hold every product, are unchanged and stay valid.
- FR-07.2: Layout B is built per product, with an optional source F06 recommendation. A sourced placement remains in the same Store/snapshot and must belong to an APPROVED source recommendation; eligibility is checked per placed product. A manual placement uses `sourceRecommendationId:null` and is eligible only if active, snapshot-present and sellable. A product appears at most once. Zone capacities come from configuration (`paspm.layout.*`). Configured in `application.yml` since PR #22, and overridable through `PASPM_LAYOUT_MAX_BLOCKS_<ZONE>` / `PASPM_LAYOUT_MAX_PRODUCTS_<ZONE>`: BANNER 3 blocks / 3 products, BUNDLE 4 / 16, CROSS_SELL 2 / 8, FBT 2 / 8, BASELINE 35 / 35.
- FR-07.3: Each edit is a new revision. A block that becomes invalid is shown with errors and blocks READY, publish and activate.
- FR-07.4: The utility score is the sum of snapshot weighted utility over distinct displayed products. Preview returns validation and score.
- FR-07.5: Priority coverage is products with W > 1 on the layout ÷ products on the layout. The layout view uses the full layout, and the simulation uses the actual products compared on each side.

### F08 — Campaign & simulated A/B (DONE API)

- FR-08.1: Lifecycle `DRAFT → PUBLISHED → ACTIVE → ENDED`, plus withdraw from PUBLISHED back to DRAFT.
  - Merchandiser: create, decide, publish, withdraw.
  - Store Admin: activate, end, rollback.
- FR-08.2: There is at most one ACTIVE campaign per Store. Activation rechecks validity and ends the previously active campaign.
- FR-08.3: Simulation. Since PR #22, `OFFLINE_REPLAY_V2` compares B with the first N products of A, with N = distinct products in B (all of A when N > |A|), and stores `comparisonMode`, N, |A| and K in `assumptions`. Old V1 reports are unchanged. F07 shows the difference against `LayoutView.equalSizeBaselineScore` (additive, next to `equalSizeProductCount`):
  - offline replay over eligible transactions after the snapshot range, excluding snapshot transactions;
  - stores the dataset hash, transaction ids, assumptions and layout revisions;
  - returns `simulated:true`.
- FR-08.4: Every operation is synchronous. No polling.

### F09 — Storefront (DONE API, MVP)

- FR-09.1: Public read API, separate from the staff API, keyed by `storeSlug` = `store.slug` (V27). No login needed. The public Store has `storeSlug`, `name` and the nullable `iconUrl` / `bannerUrl` (public image routes of FR-01.10; `null` without an image). A Store is public exactly when it is ACTIVE; there is no separate visibility switch (DEC-20261125-04). Unknown or INACTIVE Store → 404.
- FR-09.2: The homepage renders the zones of the **active** campaign layout in placement order. With no active campaign it falls back to the default layout. Draft and ended layouts are never shown.
- FR-09.3: Product detail and the bundle list. On the F10 Part 2 branch, each BUNDLE block of the active selected Layout B keeps its public projection even when components are out of stock. Hidden Product metadata stays private. An out-of-stock product shows `OUT_OF_STOCK`: the public `availability` is `AVAILABLE | OUT_OF_STOCK` (PO review 2026-11-25, DEC-20261125-04 addendum). Soft-deleted and INACTIVE products are never public (DEC-20261124-03); a batch is sellable on its expiry date (DEC-20261124-01).
- FR-09.4: No admin fields (cost, batches) in public DTOs. The product gets `image_url` and `description` columns in **V29** (V30 drops the Store columns V29 also added); F02 `ProductRequest`/`ProductView` accept and return them (`imageUrl` absolute HTTP(S) only).

### F10 — Bundle cart (implemented on `Khoi/feat/f10-bundle-add`, stacked on Part 1)

- FR-10.1: END_CUSTOMER login, one persistent cart per customer/Store, a 99-unit cap per Product, current price and stock checked on adds/increases. A single-item mutation exceeding 99 or available stock is rejected in full. All mutations require `If-Match`. Stale version returns `409 CART_VERSION_CONFLICT`.
- FR-10.2: Each BUNDLE block on the active selected Layout B supports a whole-bundle action by opaque `bundleKey`. One click requests one bundle. Add up to the requested quantity, sellable stock available for the increment and remaining capacity under 99 per Product. Report requested and added quantities for every component. If no component can be added, return 409 and record rejection.
- FR-10.3: Each line is independently editable and removable. No reservation, discount, checkout, order or payment.
- FR-10.4: Record one event per successful mutation that changes the cart. Record unsuccessful adds when a resolved bundle has no eligible component. Bundle events classify the full block as MINED or MANUAL and store actual units added.

### F11 — Dashboard (Backend implemented on `Khoi/feat/f11-dashboard-api`)

- FR-11.1: `/store/dashboard` is shared by `STORE_ADMIN` and `MERCHANDISER` in their own Store. Three independent panels show frozen snapshot utility, stored simulated F08 reports (including priorityCoverage), and successful bundle additions by origin plus total Product units added. Date range defaults to 30 Store-local days, maximum 90, with optional campaign filter. Benchmark charts are out of scope.

See [DEC-20261125-06](../decisions/2026-11-25/DEC-20261125-06-f10-f11-mvp-contract.md), [F10 API](../api/F10_BUNDLE_CART_API.md) and [F11 API](../api/F11_DASHBOARD_API.md).

## 4. Business rules

BR-01 to BR-13 keep Report 3 numbering. BR-14 onward are new proposals in this document; their numbering needs owner confirmation.

| ID | Rule | Status |
|---|---|---|
| BR-01 | Every store-bound record belongs to exactly one Store. Users see only their Store's data. Another Store's resource returns 404 | Valid |
| BR-02 | Exactly one role per account. Store Admin and Merchandiser belong to one Store; System Admin and End Customer to none | Valid |
| BR-03 | Stock is derived from sellable batches and is never stored on the product | Valid |
| BR-04 | Only COMPLETED transactions with unit price ≥ unit cost are used for utility | Valid |
| BR-05 | **`W = 1 + λ(α·U_expiry + β·M)`**, with α + β = 1, λ ∈ [0, λmax], W rounded to 2 decimals half-up | **Replaced** (was `λ[αS+βE+γM]`) |
| BR-06 | A utility snapshot is immutable once created | Valid |
| BR-07 | Only a READY snapshot of the Store can be mined. **Mining is EFIM-only** | **Changed** (was "both algorithms receive identical input") |
| BR-08 | Mining configuration is versioned. A request keeps its version | Valid |
| BR-09 | Candidates come only from COMPLETED runs of the same Store | Valid |
| BR-10 | A candidate is approved or rejected once, by a Merchandiser, with a reason | Valid |
| BR-11 | Approval does not publish, activate or reserve stock | Valid |
| BR-12 | A campaign changes the storefront only after Store Admin activation | Valid |
| BR-13 | A/B results are simulated and labelled as simulated | Valid |
| BR-14 | Push day ≤ expiry date. A batch without a push day only warns and is excluded from U_expiry. No backfill and no default (not `expiry − 7`). The F02 batch API warns `MISSING_PUSH_DAY` for expiry-tracked, non-expired batches whatever their stored status; F03 keeps its own narrower snapshot conditions | New (DEC 2026-11-15; DEC-20261124-02) |
| BR-15 | Season weight is in [0,1]. Active means the analysis date lies in [start, end] inclusive. Highest active weight wins. A season with assigned products cannot be deleted | New |
| BR-16 | The System Admin sets only λmax. The Merchandiser sets λ, α, β and N (1–365, default 30) | New |
| BR-17 | Frozen JSON is never rewritten. It is read by `scoringVersion`, and unknown versions or fields are rejected | New |
| BR-18 | Mining input: ≤ 1,600 products, ≤ 20,000 rows. Per-transaction utility, product TWU and converted minUtil each ≤ int32 max | New |
| BR-19 | Downstream features consume a recommendation only if `APPROVED ∧ readyForConsumption`, rechecked at the moment of use | New |
| BR-20 | Layout B placements are edited per product with optional F06 provenance. A product appears once. Removing or moving a placement does not alter F06 evidence or Layout A | Changed by DEC-20261125-01 |
| BR-21 | At most one ACTIVE campaign per Store | New |
| BR-22 | Layout utility score is a snapshot priority score, not revenue or profit | New |
| BR-23 | A priority product is one with W > 1 in the layout's snapshot | Merged in PR #32 (`516183d`) |
| BR-24 | Scheduled-mining enable returns 503 until release sign-off | New |
| BR-25 | (F09) Only the active campaign layout of an ACTIVE Store is public. Public DTOs never expose cost or batch data | Valid (DEC-20261125-04) |
| BR-26 | (F10) Bundle add merges currently eligible Product lines. Each line is independently editable and removable. No reservation or discount | DEC-20261125-06, implemented on Part 2 branch |
| BR-27 | A Store name is unique after trimming, ignoring case (service rule; no database UNIQUE yet) | New (DEC-20261123-01) |
| BR-28 | A Store slug is unique and only a System Admin sets or changes it | New (DEC-20261123-01) |
| BR-29 | A Store is never hard-deleted | New (DEC-20261123-01) |
| BR-30 | Users of an INACTIVE Store are rejected (403 `STORE_INACTIVE`). No new System Admin work or user assignment for it (409). In-flight work finishes and history stays readable | New (DEC-20261123-01) |
| BR-31 | Store images: PNG/JPEG by magic bytes, no SVG/WebP, icon ≤ 1 MiB, banner ≤ 5 MiB, ≤ 4000×4000 px, UUID file names, public only for ACTIVE Stores | New (DEC-20261123-01) |
| BR-32 | A batch is expired only when today (`Asia/Ho_Chi_Minh`) is after its expiry date; the expiry date itself is still active. Computed on read, never stored; EXPIRED wins over BLOCKED; `daysToExpiry` is 0 on the expiry date. F02 summary, F03 and F06 share the rule | New (DEC-20261124-01) |
| BR-33 | A product is soft-deleted, never hard-deleted. Refused (409) while it has sellable stock or sits in a PUBLISHED / ACTIVE campaign layout. Deleted products are hidden from catalogue queries before pagination; SKU never reused; no restore | New (DEC-20261124-03) |

## 5. Non-functional requirements

### 5.1 External interfaces

- **Web SPA:** React and TypeScript, desktop and mobile widths from 320 to 1440 px.
- **REST/JSON API:** Bearer JWT plus refresh cookie and CSRF. Errors are returned as `{"error":"…"}`. **147 operations / 118 paths** on the F11 branch (dev baseline: 139 operations / 112 paths). Swagger is at `/swagger`.
- **Identity and email:** Google Identity for sign-in, SMTP for OTP (Mailpit in development).
- **Mining runtime:** SPMF 2.67a on Java 25, pinned by SHA-256.
- **Database:** MySQL 8.4 with Flyway **V1–V30** (V27 Store management, V28 product soft delete, V29 F09 storefront from the old `dev` merge, V30 drops its unused Store columns; V31 on the stacked F10 branch). H2 is used for some automated tests.

### 5.2 Limits

All limits are configurable unless noted.

| Area | Limit |
|---|---|
| Import | 5 MiB, 10,000 non-blank rows per file |
| Mining input | 1,600 distinct products; 20,000 matrix rows; int32 guard |
| Mining run | 256 MiB heap, 60 s, 4 MiB output, 64 KiB log, 2,000 patterns |
| Recommendations | 10,000 candidates per generation or ranked scope |
| Paging | page ≥ 0, pageSize 1–100 (the user list names it `size`) |
| Store images | Icon 1 MiB, banner 5 MiB, at most 4000×4000 px, PNG or JPEG (DEC-20261123-01) |

No response-time, throughput, availability or MTTR targets have been defined (**TBD**). Report measurements only as measurements.

### 5.3 Reliability

- A mining request is stored before it runs. Lease and fencing let another instance take over without duplicating results.
- Import orders and review decisions are committed atomically.
- Snapshots carry source and matrix hashes. Simulation reports pin their dataset hash and layout revisions.
- Activation and rollback are serialised by locking the Store row.

### 5.4 Security

- RBAC on every operation: 403 for the wrong role, 401 when not authenticated, 404 for another Store's resource.
- Passwords are hashed with BCrypt (strength 12). OTP codes are stored as HMAC-SHA256 hashes. Login, OTP, refresh, registration and Google sign-in are rate-limited (in memory, per instance). Sign-in allows 10 attempts per e-mail and 30 per IP in 15 minutes; every attempt counts, successful ones too, and the next one returns 429 (`Too many attempts; try later`). A backend restart clears the counters.
- With `paspm.refresh-cookie-secure=true` (the default), cookies are `__Host-` prefixed, Secure and SameSite=Strict. With `false`, they are unprefixed, still SameSite=Strict, and accepted only on localhost. The Origin header is checked on login, Google sign-in, refresh, logout and logout-all.
- Jackson rejects unknown fields (`fail-on-unknown-properties: true`), so clients must not send extra fields.
- The only anonymous business route is `GET /api/public/stores/{storeId}/images/{fileName}`. It serves only the current images of ACTIVE Stores, with `nosniff` (DEC-20261123-01). Image files are stored by relative key, never as URL or BLOB.
- One central Store guard: users of an INACTIVE Store get 403 `STORE_INACTIVE` on every authenticated request and at login or refresh.
- Secrets live only in environment variables. The committed `application.yml` holds `${…}` placeholders with no secret defaults.

### 5.5 Usability

- Each role lands on its own workspace.
- Validation errors are reported per field or per row.
- Empty results are an explicit state, not an error.
- Long jobs (import, mining) show polled progress.

## 6. Common requirements

- Instants are stored in UTC (`connectionTimeZone=UTC`). Store-local dates use `Asia/Ho_Chi_Minh`.
- Money has 2 decimals. Utility has 6 decimals, half-up. W has 2 decimals, half-up.
- Updates to versioned objects send `expectedVersion`. A stale version returns 409.
- Records are deactivated rather than deleted. Imports, reviews, occurrences, layout revisions and campaign history are kept.
- Clients never send `storeId` for Store-scoped calls. The Store comes from the JWT.

## 7. Messages

MSG01–MSG18 from Report 3 remain valid, with these changes:

| Code | Change |
|---|---|
| MSG12 | Actual texts (400): `Snapshot exceeds 1600 distinct products`; `Snapshot matrix must contain 1..20000 rows`; `Snapshot matrix exceeds mining row limit` (this last one, from the submit path, does not name the number) |
| MSG15 | Unchanged. Scheduler enable still returns 503 (`Scheduled mining dispatch is not available`) |
| *new* | `Transaction utility exceeds SPMF int32 capacity`; `Product <id> TWU exceeds SPMF int32 capacity`; `Converted minUtil exceeds SPMF int32 capacity` (all 400). Since PR #23 they are raised only after the whole-đồng fallback, and the message names the mode |
| *new* | `UNSUPPORTED_SCORING_VERSION` (409, raised by the F03 and F06 versioned JSON readers; the body adds `"code"`) |
| *new* | `Store is inactive` (403, `code: STORE_INACTIVE`); `Store is inactive; reactivate it first` (409, `code: STORE_INACTIVE`); `Store name already exists`, `Store slug already exists`, `Store version conflict`, `Store is already ACTIVE/INACTIVE` (409); `Only PNG or JPEG images are accepted`, `Image content is invalid`, `Image dimensions must be at most 4000×4000 pixels` (400); `The icon must be at most 1 MiB` / `The banner must be at most 5 MiB` (413) |
| *new* | Unknown or removed field → 400 `Invalid request fields`. Removed routes → 404 / 405 |
| *new* | `PRODUCT_NAME_CONFIRMATION_REQUIRED` (409, F02): similar product name; retry with `confirmSimilarName: true` |

The new rows still need MSG numbers (**TBD**).

## 8. Screens

Changes against Report 3 §3.1.2:

- **Remove:**
  - S06 Benchmark;
  - Expiry Window;
  - Product Scores (edit);
  - the algorithm selector in S02 Mining Setup.
- **Add or change:**
  - Seasons (list, edit, assign products);
  - Stores (System Admin): list with search and status filter, create, detail with assigned users (read-only), edit, deactivate/reactivate behind a confirm dialog, icon and banner upload with preview;
  - My Store (Store Admin edits address, phone, icon, banner; Merchandiser read-only);
  - push-day field and clear action on Inventory Batches;
  - λ / α / β / N on Utility Configuration;
  - SA λmax policy;
  - mining **progress** bar (stages plus elapsed time, polled);
  - async import progress;
  - Merchandiser "Run mining" on its own snapshot.
- **F07/F08:** campaign list and detail, layout B editor with zone policy and preview, simulation report, decision, publish / withdraw, and activate / end / rollback for the Store Admin.
- **F09/F10 customer screens:**
  - Store chooser, Homepage, Product Detail and Bundles;
  - cart drawer or cart page;
  - Related Products after the MVP.
- Authorization follows the role lists in §1 and §3. Each feature's RBAC matrix in `docs/api` is authoritative.

## 9. Traceability

| Feature | Use cases | API document (backend repo) | Frontend handoff |
|---|---|---|---|
| F01 | 01, 02, 34, 35 | `F01_AUTH_USER_API.md`, `F01_STORE_MANAGEMENT_API.md`, `F01_F02_RBAC_MATRIX.md` | `F01_HANDOFF.md` (Store management section) |
| F02 | 17–20 | `F02_PRODUCT_INVENTORY_API.md` | `F02_HANDOFF.md` |
| F03 | 09, 31, 32 | `F03_UTILITY_API.md`, `requirements/F03_WEIGHT_V2_REQUIREMENTS.md` | `F03_HANDOFF.md`, `F03_W2_BREAKING_CHANGES.md` |
| F04 | 21, 30 | `F04_TRANSACTION_API.md` | `F04_HANDOFF.md` |
| F05 | 04–07, 33 | `F05_MINING_API.md` | `F05_HANDOFF.md` |
| F06 | 10, 11 | `F06_RECOMMENDATION_API.md` | `F06_HANDOFF.md` |
| F07/F08 | 12–16, 23, 25 | `F07_F08_LAYOUT_CAMPAIGN_API.md` | `F07_F08_HANDOFF.md` |
| F09 | 26–28 | `F09_STOREFRONT_API.md` | `F09_HANDOFF.md` |
| F10 | 29 | [Bundle Cart API (Part 2 branch)](../api/F10_BUNDLE_CART_API.md) | [F10 handoff](../frontend_handoff/F10_HANDOFF.md) |
| F11 | 24 | [Dashboard API (F11 branch)](../api/F11_DASHBOARD_API.md) | [F11 handoff](../frontend_handoff/F11_HANDOFF.md) |

## 10. Report 3 items superseded

UC08 is gone. UC04, UC09 and UC32 changed. Also superseded:
- BR-05 and BR-07;
- the 256-product limit;
- 89 operations;
- Flyway V1–V19;
- 31 tables;
- product entity fields;
- screens 10, 14, 18, 19 and 20;
- CTR and estimated revenue in UC16.

Update Report 3 using the template layout, changing content only.

## 11. Open items

1. Historical simulation reports retain their stored priority coverage fields.
2. N+1 queries in F06 `ProductEligibilityService` (3 queries per product: product, batches, units sold) have not been measured.
3. Frontend still calls removed APIs and fields (breaking changes #15–#18). This is still true on FE `dev` @ `86d677d` (for example `expiryPushDays`, `compareAlgorithms`, `HUI_MINER`, `/benchmark`). `origin/feature/fe-v26-integration` removes part of it and is not merged.
4. Demo minUtil: the demo policy is `PERCENTAGE 0.12` on the full range 2026-10-01..04 (`asOfDate` 2026-10-05). With the co-purchase baskets in the demo seed (`Transactions_F04_Demo_Copurchase.csv`, 635 orders; ROC-20261127-002) it gives 182 patterns (165 of size 1, 11 of size 2, 5 of size 3, 1 of size 4; EFIM 520 ms, `LOSSLESS`), and F06 generates 17 BUNDLE (up to 5 disjoint), 41 CROSS_SELL, 5 FBT and 182 BANNER candidates, all `ELIGIBLE` (measured 2026-11-27 on a fresh isolated stack). The result is stable from 0.14 to 0.08 (100 to 862 patterns; the multi-product patterns do not change); the single day 2026-10-02 at 0.12 fails with `OUTPUT_LIMIT`. Before the baskets (random orders only) the same policy gave 180 patterns, all of size 1, so F06 produced BANNER candidates only (measured 2026-11-19 and again 2026-11-27, DEC-20261119-05). The demo still has to set the policy: it is a System Admin setting that the seed does not apply.
5. ~~Demo seed broken by the 2026-11-19 F02 rules~~: fixed on branch `Khoi/fix/sku-foodmart-seed` (DEC-20261119-07, ROC-20261125-004): the seed creates products by name, writes `mapping.csv` and imports CSV copies with server SKUs. Was: demo seed is broken on `dev` @ `222e54f` by the 2026-11-19 F02 rules (SKU format, similar-name 409, `batchCode` no longer accepted); the FoodMart import files still use `FOODMART-xxxx` SKUs. Fix deferred by the owner.
6. ~~The F02 rules of 2026-11-19 have no DEC~~: recorded as [DEC-20261119-07](../decisions/2026-11-19/DEC-20261119-07-server-generated-product-sku.md) (server-generated SKU).
7. F09/F10 scope notes:
   - ~~currency~~: VND, whole đồng (decided 2026-11-19);
   - maximum quantity is 99 per Product per cart (DEC-20261125-06);
   - anonymous `BUNDLE_VIEWED` is outside the MVP;
   - the "daily deal" single-product block (Direction 2) is outside the MVP.
8. F10 component quantities and the single-bundle action, plus F11 scope and planned contract, are recorded in DEC-20261125-06.
9. Cleanup after DEC-20261123-01: drop the unused `store.mining_schedule` column; consider a database UNIQUE on the normalized Store name once the data is clean.

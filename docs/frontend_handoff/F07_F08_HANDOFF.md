# F07/F08 React / TypeScript integration handoff

This handoff is for frontend integration with the current backend contract; it does not implement the UI. See the [F07/F08 API contract](../api/F07_F08_LAYOUT_CAMPAIGN_API.md) for all operations, fields, metrics, and lifecycle rules. Authenticate through the existing F01 flow and send the bearer token. The backend derives Store scope from the token; never send a Store ID.

## Roles and workflow

| Capability | MERCHANDISER | STORE_ADMIN |
|---|---:|---:|
| Read layouts, policy, recommendations, campaigns, reports and active layout | Yes | Yes |
| Create campaign, edit B, mark B READY, simulate, record decision, publish, withdraw | Yes | No |
| Activate, end, rollback | No | Yes |

Suggested screens:

1. **Layout list/detail/preview.** `GET /api/layouts` supports optional `campaignId`, `page`, and `pageSize`. Read a layout or preview through `GET /api/layouts/{id}` or `/preview`. Show A as read-only. Label its score and B's score **Weighted utility (snapshot)**, not revenue/profit.
2. **Recommendation and product sources.** Call `GET /api/layouts/recommendations?snapshotId=…` for expandable approved F06 sources and `GET /api/layouts/products?snapshotId=…&q=…&page=…&pageSize=…` for searchable snapshot products. A sourced placement carries `sourceRecommendationId`; a manual placement uses `null`. One source recommendation may supply multiple independent placements, but a product may appear once in B.
3. **Edit B.** Send `{expectedVersion, placements:[{productId,zone,blockNo,position,sourceRecommendationId}]}` to `PUT /api/layouts/{id}/placements`. Replace local state with the returned layout/version. Re-fetch after a 409 and let the user resolve conflicts. `POST /api/layouts/{id}/ready` also requires the current layout version. Save visibly distinguishes F06 provenance from manual source and shows per-product validation errors.
4. **Campaign setup.** `POST /api/campaigns` with `{name,snapshotId,recommendationIds}` returns campaign, A ID and B ID. A ranks products by distinct transaction frequency in the snapshot (descending, then product ID ascending); B can be edited while DRAFT. Read campaign/detail and paginate with server `totalElements`.
5. **Optional simulation.** Make A and B READY, then POST `/api/campaigns/{id}/simulations` with current campaign/A/B versions and an inclusive date range whose start is strictly after the snapshot's `rangeEnd`. Overlapping or earlier ranges return `400`. Keep a synchronous loading indicator until the response. Render `simulated:true`, both layout versions, assumptions, dataset hash, range, sample sizes and nullable metric rates. Do not show a fabricated progress bar. Zero denominator or not-yet-defined priority coverage is `null` / N/A.
6. **Decision and publish.** A simulation is optional. Send `KEEP_A` or `CHOOSE_B` with expected campaign version; attach a report ID only if it matches the current layout versions. Publish with the updated version. Publish does not alter the storefront.
7. **Store Admin activation.** Activate a published campaign after rechecking current state. Only activation changes `GET /api/storefront/active-layout`. Ending returns the default layout. Rollback restores the prior active campaign from history or default if none exists.

## Response/state handling

- Layout and campaign edits use optimistic `expectedVersion`; on `409`, reload the resource instead of retrying the stale payload.
- Handle `401` through existing session logic; `403` as role denial; `404` as missing or outside the current Store; `400` as request validation; and `409` as stale version, invalid state, or live eligibility conflict.
- Withdraw only applies to PUBLISHED and returns the campaign to DRAFT while clearing its decision/selection. Editing creates a new revision; a report can be attached to a new decision only when it matches the current layout versions.
- Lifecycle statuses: `DRAFT -> PUBLISHED -> ACTIVE -> ENDED`; `PUBLISHED -> DRAFT` is the explicit withdrawal transition. Simulation/report reads are synchronous/persisted; no endpoint needs polling.
- Cancel stale GET requests on route/Store change. Clear Store-scoped caches when the authenticated identity changes.
- Keep int64 IDs/versions and decimals losslessly if the frontend's numeric adapter requires it. JSON IDs/versions are numbers, not quoted strings.

## Simulation interpretation

The simulator replays historical completed, F03-eligible transactions strictly after the selected snapshot's training date range and excludes training transaction IDs. It retains equal-content baskets with distinct transaction IDs. Hit rate, saved-layout `BUNDLE + blockNo` conversion, and captured utility use the formulas and sample sizes in the [API contract](../api/F07_F08_LAYOUT_CAMPAIGN_API.md). F06 provenance does not define a simulation block. Priority coverage is not calculated and is returned as null pending the F03 definition. This is not a real-user A/B test and does not establish causal impact or forecast revenue/profit.

## 2026-11-15 — F03 Weight v2 and priority coverage

`priorityCoverage` and `priorityCoverageDenominator` are still always `null`: the report row is inserted with `NULL,NULL` (`src/main/java/vn/edu/fpt/paspm/layout/LayoutCampaignRepository.java:344-345`) and the stored assumption reads "N/A until the F03 priority coverage formula is confirmed" (`layout/LayoutCampaignService.java:267`). The intended definition "priority item = W(i) > 1" (F03 Weight v2) is **not wired**; no F07/F08 code reads W. F07/F08 routes and bodies did not change on branch `Khoi/feat/f05-progress-f03-weight-v2`.

## 2026-11-19 — zone caps, top-K baseline, equal-size comparison (all additive)

- `GET /api/layouts/policy` now returns the configured caps (BANNER 3/3, BUNDLE 4/16, CROSS_SELL 2/8, FBT 2/8, BASELINE 35/35 blocks/products). Use them to limit the B editor; the server rejects over-cap placements with 400.
- Baseline A of a new campaign holds at most 35 products (top-K by frequency). Old campaigns keep their A.
- New `LayoutView` fields on **layout B**: `equalSizeBaselineScore` (A's score over its first N products, N = distinct products in B) and `equalSizeProductCount`. Show "difference vs A" as `B.weightedUtilityScore − B.equalSizeBaselineScore`. Both fields are `null` on layout A, whose `weightedUtilityScore` stays the full-A score.
- New reports have `algorithmVersion: OFFLINE_REPLAY_V2`; show `assumptions.comparedProductCountN` and `assumptions.baselineProductCount` ("B: N products vs A's top N of |A|"). For `OFFLINE_REPLAY_V1` reports, A was measured in full; label them accordingly.

See [F07/F08 API](../api/F07_F08_LAYOUT_CAMPAIGN_API.md) and [DEC-20261119-06](../decisions/2026-11-19/DEC-20261119-06-layout-caps-topk-baseline-equal-size.md).

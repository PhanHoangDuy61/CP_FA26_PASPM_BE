# F07 Layout Simulator and F08 Campaign API

Source-derived contract for the current working tree. This adds Store-scoped layout and campaign operations; it does not change F01–F06 contracts. Swagger is `/swagger`, the UI is `/swagger-ui/index.html`, and the runtime OpenAPI endpoint is `/v3/api-docs`.

## Authorization and common behavior

Every endpoint derives Store scope from the authenticated JWT; clients must not send a `storeId`. `MERCHANDISER` and `STORE_ADMIN` may read layouts, recommendations, campaigns, reports, policy, and the active-layout projection. A `MERCHANDISER` creates/edits layouts, creates campaigns, simulates, decides, publishes, and withdraws. A `STORE_ADMIN` activates, ends, and rolls back. Other roles are denied.

An own-Store resource that does not exist or belongs to another Store returns `404`. Role denial returns `403`; missing/invalid authentication returns `401`. Invalid input returns `400` with `{"error":"Invalid request fields"}`; stale versions, invalid transitions, or live eligibility conflicts return `409` with `{"error":"..."}`. Validation errors and constraint conflicts use the shared API error contract. No endpoint in this feature is asynchronous and no progress percentage or polling endpoint is needed.

Paged reads use `page` (default `0`, minimum `0`) and `pageSize` (default `20`, range `1..100`) and return `{items,page,pageSize,totalElements}`.

## Operations

| Method and path | Role | Response |
|---|---|---|
| `GET /api/layouts` (`campaignId` optional) | Both | Page of Store layouts |
| `GET /api/layouts/policy` | Both | Shared zones and configured capacities |
| `GET /api/layouts/recommendations?snapshotId=…` | Both | Page of currently consumable recommendations for the snapshot |
| `GET /api/layouts/{id}` | Both | Layout with placements, validation, and snapshot utility score |
| `GET /api/layouts/{id}/preview` | Both | Same current validation/score projection for preview |
| `PUT /api/layouts/{id}/placements` | Merchandiser | Updated Layout B |
| `POST /api/layouts/{id}/ready` | Merchandiser | Revalidated Layout B |
| `GET /api/campaigns` | Both | Page of campaign summaries |
| `POST /api/campaigns` | Merchandiser | `201`, new DRAFT campaign and A/B layout IDs |
| `GET /api/campaigns/{id}` | Both | Campaign detail and report IDs |
| `PUT /api/campaigns/{id}/decision` | Merchandiser | Updated campaign |
| `POST /api/campaigns/{id}/simulations` | Merchandiser | `201`, immutable simulated report |
| `GET /api/campaigns/{id}/reports` | Both | Page of immutable reports |
| `GET /api/campaign-reports/{reportId}` | Both | Immutable report |
| `POST /api/campaigns/{id}/publish` | Merchandiser | Published campaign; storefront is unchanged |
| `POST /api/campaigns/{id}/withdraw` | Merchandiser | Withdrawn campaign in DRAFT |
| `POST /api/campaigns/{id}/activate` | Store Admin | Active campaign |
| `POST /api/campaigns/{id}/end` | Store Admin | Ended campaign; active-layout API returns default |
| `POST /api/campaigns/{id}/rollback` | Store Admin | Restored active-layout projection |
| `GET /api/storefront/active-layout` | Both | Active selected layout or `defaultLayout:true` |

## Request examples

Create a campaign with an immutable baseline A and recommendations for editable B. An empty recommendation list is allowed at creation, but B must contain valid blocks before it can become READY or be selected.

```json
{
  "name": "Summer display comparison",
  "snapshotId": 18,
  "recommendationIds": [401, 405]
}
```

Replace all placements in B using whole recommendation blocks and the version returned by the last layout response:

```json
{
  "expectedVersion": 2,
  "recommendationIds": [401, 405]
}
```

Move B to READY after editing:

```json
{"expectedVersion": 3}
```

Run an optional, synchronous offline simulation. Date bounds are inclusive in the Store timezone; `rangeStart` must be strictly after the selected snapshot's `rangeEnd`, and all three versions must match the current campaign/layouts. A range that overlaps or precedes the training period is rejected with `400`.

```json
{
  "expectedCampaignVersion": 1,
  "expectedLayoutAVersion": 1,
  "expectedLayoutBVersion": 3,
  "rangeStart": "2026-01-01",
  "rangeEnd": "2026-01-31"
}
```

Record a decision; a report is optional. When supplied, it must belong to this campaign and match both current layout versions.

```json
{"expectedVersion": 1, "decision": "CHOOSE_B", "reportId": 72}
```

Publish, withdraw, activate, end, and rollback accept `{"expectedVersion": <current campaign version>}`. Re-read the campaign after each mutation and use the returned version for the next command.

## Layout rules and utility

- Baseline A is generated deterministically from the campaign snapshot by the number of distinct snapshot transactions containing each product, descending; ties are ordered by `product_id` ascending. A is read-only. This is the frequency baseline defined for the F08 comparison, not a weighted-utility ranking.
- B placements contain complete recommendation blocks. A recommendation must be in the same Store and snapshot, `reviewStatus=APPROVED`, and `readyForConsumption=true` at use time. F07 adds no extra `comparisonStatus` gate beyond the published F06 policy.
- A product may appear only once in a layout. A block that later loses eligibility remains visible and is marked invalid; preview exposes validation errors and READY/publish/activate are blocked until corrected. Eligibility is rechecked at these actions.
- Zone names map to recommendation types. Capacities are shared policy, exposed by `GET /api/layouts/policy`, and configured with `paspm.layout.max-blocks-by-zone` / `paspm.layout.max-products-by-zone`. An absent cap is unbounded; this implementation invents no default capacity.
- A layout utility score is the sum of `utility_matrix_item.weighted_utility` for distinct displayed products in the same snapshot. Itemset/recommendation utility is not added. The label is “Weighted utility (snapshot)”; it is not revenue or actual profit.
- A and B comparisons use the same snapshot and formula. The report pins the exact layout revisions used.

## Offline simulated metrics and report

The evaluation set contains completed, F03-eligible F04 transaction IDs in the requested date range, which must begin strictly after the snapshot's `rangeEnd`, and excludes every transaction ID present in `utility_matrix_item` for the campaign snapshot. Different transaction IDs with identical baskets remain eligible. The report stores the source namespace, date range, SHA-256 dataset hash, evaluation transaction IDs, assumptions, runner (`createdBy`), timestamp, simulation version, and exact A/B layout versions.

The response has `simulated:true`. It describes historical replay fit, not a real-user A/B test, causal lift, revenue forecast, or actual profit.

- **Hit rate:** transactions with at least one displayed product / evaluation transactions.
- **Bundle conversion:** transactions containing every product in at least one displayed BUNDLE block / evaluation transactions. FBT is not counted as BUNDLE.
- **Captured utility:** numerator is F03 `UtilityMath.base` for purchased item lines whose product is displayed; denominator is F03 base utility for all item lines in the evaluation set. Base utility is `(unit_price - unit_cost) * quantity`, rounded HALF_UP to six decimals.
- **Priority coverage:** `null` until the F03 formula is confirmed.
- A zero denominator yields `rate:null` (N/A). Empty evaluation sets are stored as valid reports with all denominator-based rates null.

Example response shape (IDs and values illustrative):

```json
{
  "reportId": 72,
  "campaignId": 31,
  "snapshotId": 18,
  "createdBy": 9,
  "rangeStart": "2026-01-01",
  "rangeEnd": "2026-01-31",
  "sourceNamespace": "F04_DATABASE_V1",
  "datasetHash": "<64 lowercase hex characters>",
  "evaluationCount": 120,
  "simulated": true,
  "algorithmVersion": "OFFLINE_REPLAY_V1",
  "assumptions": {},
  "layouts": [
    {
      "layoutId": 91,
      "layoutVersion": 1,
      "role": "A",
      "hitRate": {"numerator": 84, "denominator": 120, "rate": 0.7},
      "bundleConversion": {"numerator": 0, "denominator": 120, "rate": 0},
      "capturedUtility": {"numerator": 840, "denominator": 1200, "rate": 0.7},
      "priorityCoverage": null,
      "priorityCoverageDenominator": null
    }
  ],
  "createdAt": "2026-01-31T12:00:00Z"
}
```

`layouts` contains both A and B. `assumptions` in actual responses documents the train/test split, duplicate-cart handling, formulas, zero-denominator behavior, and simulated interpretation.

## Campaign lifecycle and storefront effect

```text
DRAFT -> PUBLISHED -> ACTIVE -> ENDED
  ^          |
  +----------+  withdraw
```

Only a DRAFT campaign can be edited, simulated, and decided. Simulation is optional for publish. Publish requires a decision and a currently valid selected READY layout, but does not change the storefront. Withdrawal returns a published campaign to DRAFT and clears the decision/selection; a new decision is required before republishing. Layout edits create a new revision and clear any prior decision, and a report can be attached to a new decision only while it matches the current layout versions. Activation rechecks validity and atomically ends the current active campaign, if any. Store-row locking serializes activate/rollback and enforces at most one active campaign per Store. End returns F09 to the default layout. Rollback restores the previously active campaign from history, or ends the current campaign and returns to default if no prior active campaign exists; an ENDED campaign is never silently exposed as active.

## Loading and polling

F07 preview, F08 simulation, and all lifecycle mutations are synchronous. Keep the corresponding action in a loading state until its HTTP response; success ends loading and renders that response, while an error ends loading and follows the shared `{"error":"..."}` contract. There is no polling interval, job ID, or percentage progress for these operations.

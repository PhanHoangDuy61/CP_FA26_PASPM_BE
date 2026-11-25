# F11 — Retail Performance Dashboard API

**Status: PLANNED.** Dashboard route `/store/dashboard`. See [DEC-20261125-06](../decisions/2026-11-25/DEC-20261125-06-f10-f11-mvp-contract.md). The three panels can load and fail independently.

## Authorization and scope

Bearers with `STORE_ADMIN` or `MERCHANDISER` use the Store resolved from JWT. No Store ID or slug parameter for dashboard APIs. A foreign or absent explicitly selected snapshot, campaign or report returns 404 after the role check. Guest/invalid token returns 401, unauthorized role returns 403, and an inactive staff Store follows F01's 403 `STORE_INACTIVE` guard. Invalid pagination, dates or IDs return 400. Errors use `{"error":"…","code":"…"}` where a machine code applies. An empty Store query returns 200 with empty items or zero counts.

## Snapshot utility

**Proposed** `GET /api/dashboard/snapshot-utilities?page=0&pageSize=20&snapshotId=18`. Optional positive `snapshotId`, page defaults to 0, page size 1–100. Returns READY same-Store snapshots ordered by createdAt and snapshotId descending. Read the stored `utility_snapshot.total_weighted_utility` with six-decimal precision. Utility is a score. The read model serves both roles without changing F03 access. Selecting an absent, foreign or unavailable snapshot returns 404.

```json
{
  "items": [
    {"snapshotId":18,"rangeStart":"2026-10-01","rangeEnd":"2026-10-04",
      "analysisDate":"2026-10-05","createdAt":"2026-11-25T05:00:00Z",
      "totalWeightedUtility":219.430000}
  ],
  "page":0,"pageSize":20,"totalElements":1
}
```

## Saved A/B simulations

Reuse **existing** `GET /api/campaigns`, `GET /api/campaigns/{id}/reports` and `GET /api/campaign-reports/{reportId}` with the [F07/F08 API](F07_F08_LAYOUT_CAMPAIGN_API.md). No F11 write or simulation operation. Read saved reports including evaluated revisions, evaluation window, evaluation count, hit rate, bundle conversion, captured utility and priority coverage. Report V4 uses the actual A prefix and B product counts. Show the `nExceedsBaseline` case. Historical values remain as stored.

Illustrative excerpt of a single report layout row (the actual report has both A and B):

```json
{
  "simulated": true, "algorithmVersion": "OFFLINE_REPLAY_V4", "evaluationCount": 12,
  "layouts": [
    {
      "layoutId":91,"layoutVersion":1,"role":"A",
      "hitRate":{"numerator":6,"denominator":12,"rate":0.5},
      "bundleConversion":{"numerator":0,"denominator":12,"rate":0},
      "capturedUtility":{"numerator":30,"denominator":120,"rate":0.25},
      "priorityCoverage":0,"priorityCoverageDenominator":4
    }
  ]
}
```

## Bundle additions

**Proposed** `GET /api/dashboard/bundle-additions?from=2026-11-24&to=2026-11-25&campaignId=42`.
`from` and `to` are supplied together as inclusive Store-local dates. Omit both for the last 30 days including today. The window may span at most 90 days counting both ends: exactly 90 days is accepted, 91 or more returns 400 ([DEC-20261126-02](../decisions/2026-11-26/DEC-20261126-02-vnd-display-cart-cap-dashboard-window-merch-screens.md)). `campaignId` is optional, must belong to the authenticated Store, and filters by the campaign captured on the event even if the campaign has since ended. The repository uses a UTC half-open boundary corresponding to the Store-local window and returns a row per day, filling missing days with zeros.

Main KPI counts successful `BUNDLE_ADDED` events by origin. Secondary KPI sums recorded **Product units actually added**. A partial success of A ×2 and B ×1 is one action and three units; rejected or skipped components contribute zero. No event rows, customer identities, or unrelated cart operations are returned. A valid empty window has both origin counts at zero. A panel whose backend is unavailable shows an unavailable state and does not claim zero activity.

```json
{
  "from":"2026-11-24","to":"2026-11-25","timeZone":"Asia/Ho_Chi_Minh","campaignId":42,
  "totalAddActions":2,"addActionsByOrigin":{"MINED":1,"MANUAL":1},"totalAddedProductUnits":5,
  "days":[
    {"date":"2026-11-24","addActionsByOrigin":{"MINED":0,"MANUAL":0},"addedProductUnits":0},
    {"date":"2026-11-25","addActionsByOrigin":{"MINED":1,"MANUAL":1},"addedProductUnits":5}
  ]
}
```

```json
{"error":"Campaign not found"}
```

All counts are nonnegative int64. Rates are numeric shares or null. A null rate displays unavailable, and numeric zero displays 0%. Dashboard events come from F10 mutations; the report remains offline simulation. No revenue, benchmark or real-user conversion metrics. See [F11 handoff](../frontend_handoff/F11_HANDOFF.md).

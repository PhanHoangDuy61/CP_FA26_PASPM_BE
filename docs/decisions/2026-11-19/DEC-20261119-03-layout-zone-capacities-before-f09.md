# DEC-20261119-03 — Configure layout zone capacities before F09

| Field | Value |
|---|---|
| Date | 2026-11-19 (Asia/Ho_Chi_Minh) |
| Source | Owner decision of 2026-11-19; recorded in [ROC-20261119-001](../../record-of-changes/2026-11-19/01-record-owner-decisions.md) |
| Status | OWNER_DECISION — values **TBD by the owner**; not configured |

## Decision
Layout zone capacities (`paspm.layout.max-blocks-by-zone` and `paspm.layout.max-products-by-zone`) must be configured before F09 Storefront is implemented, so the public homepage cannot render an unbounded zone. The values are to be set by the owner.

## Current code (`origin/dev` `530651b`)
- Zones are `BASELINE` plus one per recommendation type (`BUNDLE`, `CROSS_SELL`, `FBT`, `BANNER`): `src/main/java/vn/edu/fpt/paspm/layout/LayoutRules.java:14,20,29-34`.
- An absent cap is unbounded, reported as `UNCONFIGURED_LIMITS_ARE_UNBOUNDED`: `LayoutRules.java:36-38`; `docs/api/F07_F08_LAYOUT_CAMPAIGN_API.md:90`.
- `src/main/resources/application.yml` sets no `paspm.layout.*` key, so every zone is currently unbounded.

## Related
[DEC-20261117-03](../2026-11-17/DEC-20261117-03-f09-f10-proceed-with-mvp-plans.md) (F09/F10 MVP).

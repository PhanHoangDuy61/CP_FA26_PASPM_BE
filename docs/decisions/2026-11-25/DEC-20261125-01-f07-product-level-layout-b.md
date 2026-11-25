# DEC-20261125-01 — F07 edits individual Layout B products

| Field | Value |
|---|---|
| Date | 2026-11-25 (Asia/Ho_Chi_Minh) |
| Source | Owner-approved F07 change confirmed by the user; F08 bundle grouping confirmed on 2026-11-25 |
| Status | OWNER_DECISION — implementation is on `whisa/feat/f07-product-layout-editor`, not merged |
| Related | [F07/F08 API](../../api/F07_F08_LAYOUT_CAMPAIGN_API.md); [DEC-20261112-01](../2026-11-12/DEC-20261112-01-f07-f08-two-layout-ab-comparison.md); [DEC-20261119-06](../2026-11-19/DEC-20261119-06-layout-caps-topk-baseline-equal-size.md) |

## Decision

1. Supersede the whole-recommendation-block editing rule in [DEC-20261112-01](../2026-11-12/DEC-20261112-01-f07-f08-two-layout-ab-comparison.md) and SRS BR-20 for Layout B writes. A saved placement represents one product and carries its `zone`, `blockNo`, `position`, and nullable `sourceRecommendationId`. A recommendation may be referenced by multiple placements; a product may appear only once in a layout.
2. Keep F06 recommendations and evidence immutable. Removing, moving, or reordering a product changes only the Layout B placement set and its versioned revision. Layout A remains read-only. Campaign creation may still seed B from complete approved recommendations.
3. Recommendation products in the editor are initially offered only from same-Store, same-snapshot F06 recommendations that are `APPROVED` and `readyForConsumption`. A sourced placement must remain in the same Store and snapshot, reference an `APPROVED` recommendation that contains that product, and pass that product's current eligibility check. Do not use the recommendation's aggregate `readyForConsumption` result to invalidate every placement when another product in the source recommendation is ineligible; eligibility is evaluated for each placed product.
4. A manually added product must be present in the campaign snapshot, not deleted, currently `ACTIVE`, and have at least one unit of current sellable inventory. Source is persisted as `null`. Product utility is read from the frozen snapshot; no live utility is substituted.
5. The configured F07 policy remains authoritative for zones and capacities. A saved Layout B revision contains the exact product placements and their provenance. Store isolation, same campaign/snapshot, unique product, zone/position, and current product/source checks apply on save and READY.
6. F08 bundle conversion is evaluated from the exact saved B revision: a transaction converts when it contains every product in at least one `BUNDLE` zone block, grouped by `blockNo`. F06 recommendation provenance does not define the simulated bundle. `FBT` is excluded. New reports use `OFFLINE_REPLAY_V3`; old V1/V2 reports are immutable and continue to be read as stored.

## Compatibility and persistence

- `PUT /api/layouts/{id}/placements` replaces the breaking `recommendationIds` request body with per-product `placements` plus `expectedVersion`.
- `GET /api/layouts/products` is an additive paginated/searchable endpoint for snapshot products, utility, and current product eligibility.
- V23 already has a nullable `layout_placement.recommendation_id` plus block and position columns. No migration or historical row rewrite is required. Each save still appends a frozen layout revision.
- The change alters F08 bundle-conversion semantics, so it gets the new `OFFLINE_REPLAY_V3` version rather than reinterpreting historical V2 reports.

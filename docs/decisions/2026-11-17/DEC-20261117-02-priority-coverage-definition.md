# DEC-20261117-02 — `priorityCoverage`: share of the layout given to priority products

| Field | Value |
|---|---|
| Date | 2026-11-17 (Asia/Ho_Chi_Minh) |
| Source | Owner decision of 2026-11-17 in the working conversation; recorded in the repository on 2026-11-19 ([ROC-20261119-001](../../record-of-changes/2026-11-19/01-record-owner-decisions.md)) |
| Status | OWNER_DECISION — decided, **not implemented** |
| Supersedes | "Priority coverage: `null` until the F03 formula is confirmed" ([F07/F08 API](../../api/F07_F08_LAYOUT_CAMPAIGN_API.md)) as an open question; the API behaviour is unchanged until implemented |

## Decision
- A **priority product** is a product with **W > 1**, where W is read from the frozen F03 snapshot that the layout uses (`utility_matrix_item` of the campaign snapshot), never from live data.
- `priorityCoverage` = number of distinct priority products on the layout ÷ number of distinct products on the layout (share of display). `priorityCoverageDenominator` is the number of distinct products on the layout.

## Rationale
Retail sometimes has to push priority items individually (for example a "daily deal"), not only through bundles, combos or banners. Coverage measures how much of the display those items get.

## Current code (`origin/dev` `530651b`)
- The report row is still inserted with `NULL,NULL` for `priority_coverage, priority_coverage_denominator`: `src/main/java/vn/edu/fpt/paspm/layout/LayoutCampaignRepository.java:344-345`.
- The stored assumption still reads "N/A until the F03 priority coverage formula is confirmed": `src/main/java/vn/edu/fpt/paspm/layout/LayoutCampaignService.java:267`.
- The columns already exist (`comparison_report_layout`, V23); implementing the metric needs no migration.

## Open points
How historical reports (all `null`) are shown, and whether a zero-product layout gives `null` like the other rates, are left to the implementation PR.

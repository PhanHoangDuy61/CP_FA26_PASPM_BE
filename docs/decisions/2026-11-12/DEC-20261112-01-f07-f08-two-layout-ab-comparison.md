# DEC-20261112-01 — F07/F08 A/B compares two layouts; baseline A is view-only

| Field | Value |
|---|---|
| Date | 2026-11-12 (Asia/Ho_Chi_Minh) |
| Source | Owner decision of 2026-11-12 in the working conversation; recorded in the repository on 2026-11-19 ([ROC-20261119-001](../../record-of-changes/2026-11-19/01-record-owner-decisions.md)) |
| Status | OWNER_DECISION — implemented in the Backend API on `dev` (`530651b`); the score difference is a Frontend display task |
| Contract | [F07/F08 Layout and Campaign API](../../api/F07_F08_LAYOUT_CAMPAIGN_API.md) |

## Decision
- An A/B comparison compares exactly **2 layouts**: baseline A and proposed B of one campaign.
- F07 only opens layouts created by F08 (a campaign creates its A and B); there is no free-standing layout.
- Baseline layout A is **view-only**.
- F07 shows the **Utility Score difference** of the layout being edited versus layout A.

## Code evidence (`origin/dev` `530651b`)
- Two layouts per campaign: `src/main/resources/db/migration/V23__f07_f08_layout_campaign.sql:72` (`ck_layout_role CHECK(role IN ('A','B'))`) and `:61` (`uq_layout_campaign_role UNIQUE(store_id, campaign_id, role)`).
- Layouts are created only with their campaign: `src/main/java/vn/edu/fpt/paspm/layout/LayoutCampaignService.java:138-140` inserts A and B inside campaign creation. `LayoutCampaignController` has no layout-create route; the only layout writes are `PUT /api/layouts/{id}/placements` (`:63`) and `POST /api/layouts/{id}/ready` (`:71`).
- A is read-only: `LayoutCampaignService.java:642` answers 409 "Layout A is immutable and read-only"; the API contract says the same (`docs/api/F07_F08_LAYOUT_CAMPAIGN_API.md:87`).
- Utility Score: every `LayoutView` carries `weightedUtilityScore` (`src/main/java/vn/edu/fpt/paspm/layout/LayoutContracts.java:60`). A and B use the same snapshot and formula (`F07_F08_LAYOUT_CAMPAIGN_API.md:91-92`).

## Conformance
The first three points are enforced by the Backend. The difference "B − A" is **not** a Backend field: the F07 screen computes it from the two `weightedUtilityScore` values of the same campaign. No API change is needed for this decision.

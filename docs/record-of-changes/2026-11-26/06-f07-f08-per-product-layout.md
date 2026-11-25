# ROC-20261126-006 — F07/F08 per-product Layout B, baseline top-K and priority coverage in the diagrams

| Field | Value |
|---|---|
| Date / timezone | 2026-11-26 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Sonnet 5.5), requested by Nguyễn Minh Khôi (unattended overnight task) |
| Type | Documentation (diagrams) |
| Branch / base | `Khoi/docs/diagrams-update-20261126` / `origin/dev` @ `b94584b` |
| Rules drawn | [DEC-20261125-01](../../decisions/2026-11-25/DEC-20261125-01-f07-product-level-layout-b.md); SRS FR-07.1–FR-07.5, FR-08.3 (zone caps and baseline top-K of DEC-20261119-06, `priorityCoverage` of DEC-20261117-02) |
| Release | UNRELEASED |
| Daily index | [2026-11-26](README.md) |

## Scope and changes

- **AD06 (activity):** the baseline step says "baseline A (top-35, read-only)"; B is arranged one product at a time within the zone caps; the score step shows Utility, priority coverage (W > 1) and the equal-size baseline.
- **R2-08.06 (sequence):** `arrangeProducts` and `savePlacements` carry the version (optimistic locking); the `LayoutView` reply carries `priorityCoverage`.
- **R2-07.06 (class):** `LayoutEditorScreen.arrangeProducts` and `LayoutService.savePlacements` take `expectedVersion : long`; `LayoutView` gains `priorityCoverage`, `equalSizeBaselineScore` and `version`; `PlacementView` gains `zone`, `blockNo` and `sourceRecommendationId` (null = manual product). The generic parameter types are unchanged.
- **ERD Layout & Campaign:** three notes: per-product placements (`recommendation_id` NULL = manual, `block_no` groups the BUNDLE blocks for F08, `priorityCoverage` computed from the frozen snapshot and not stored); baseline A keeps the top-K products (K = 35, `F07_BASELINE_V2`, `baseline_params` has `k` and `candidateProducts`); new reports use `OFFLINE_REPLAY_V3` (bundle conversion from the saved BUNDLE blocks grouped by `block_no`; V1 and V2 reports stay as stored). The columns themselves were already correct in the V26 ERD.
- All other pages are byte-identical to the previous commit.

## Not done

- The sequence of the F08 simulation (R2-08.07, R2-07.07 and AD07) was not changed: it names a "compare" of two layouts, which is still right; the bundle-conversion rule (OFFLINE_REPLAY_V3) is drawn only as an ERD note.
- A new page for the product search (`GET /api/layouts/products`) was not added.
- Zone capacities are not drawn as numbers (they are configuration).

## Verification

| Check | Result |
|---|---|
| `validate_drawio.py` on the pack and the ERD | PASS |
| Pages not listed above | byte-identical |
| PNG export, looked at: AD06 (labels shortened after the first export showed overflow), R2-08.06, R2-07.06 | `outputs/diagrams-overnight/png/F-f07-f08/` |
| Maven | NOT RUN |

## Tooling note

Editing a class box must change the HTML once-unescaped value: the first run double-unescaped `List&lt;…&gt;` and the generic types disappeared from three class boxes. Found on the export, fixed before the commit, and checked by scanning the file for lost generics.

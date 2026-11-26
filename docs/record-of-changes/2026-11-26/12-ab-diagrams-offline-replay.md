# ROC-20261126-012 — AD07, R2-08.07 and R2-07.07 redrawn as the offline replay

| Field | Value |
|---|---|
| Date / timezone | 2026-11-26 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Sonnet 5.5), requested by Nguyễn Minh Khôi |
| Type | Documentation (diagrams) |
| Branch / base | `Khoi/docs/ab-diagrams-offline-replay` / `origin/dev` @ `fb48d55` |
| Decision | DEC-20261126-03 (branch `Khoi/docs/blackbox-test-cases`: CTR and estimated revenue are not part of PASPM) |
| Release | UNRELEASED |
| Daily index | [2026-11-26](README.md) |

## Why

The three A/B pages of `PASPM_Unified_UML2_Editable.drawio` still showed the original design: synthetic data, "Declare CTR/conversion assumptions", "Compare simulated utility, CTR, conversion and estimated revenue", and `MetricType {UTILITY, CTR, CONVERSION, ESTIMATED_REVENUE}`. The implemented F08 simulation is a synchronous offline replay (`OFFLINE_REPLAY_V4`) with four metrics. The SDD §9.1 rows for AD07 and R2-08.07 / R2-07.07 already said "Rewrite".

## Changes

| Page | Change |
|---|---|
| AD07 A/B Testing & Performance Evaluation | Merchandiser opens a DRAFT campaign with A and B READY and enters a range; the system checks DRAFT, versions, READY and that `rangeStart` is after the snapshot `rangeEnd`; replays eligible later transactions excluding the snapshot's own; compares B with A's first N products (hitRate, bundleConversion, capturedUtility, priorityCoverage); saves an immutable report with `simulated:true`. The decision step now says KEEP_A / CHOOSE_B with the report; "revise" means editing layouts in AD06. System-lane nodes widened to 340 and the lower part shifted down 40 |
| R2-07.07 Class (AD07) | `SimulationService` → `LayoutCampaignService.simulate`; `TransactionSource` → `OfflineLayoutSimulation.evaluate`; `ComparativeReportRepository` → `LayoutCampaignRepository`; `LayoutService` → `LayoutRules.equalSizePrefix`; `ComparisonReport` → `CampaignReportView` (`simulated`, `algorithmVersion`, `datasetHash`); `MetricComparison` → `ReportLayoutView`; `PreparedTransaction` → `ReplayMetrics`; `MetricType` → `RatioMetric`; `SimulationDecision` removed (the decision belongs to AD06); `LayoutRole {A, B}` added; `SimulationRequest` now has the three expected versions and the range |
| R2-08.07 Sequence (AD07) | Rebuilt with six participants (merchandiser, screen, `LayoutCampaignService`, `LayoutRules`, `OfflineLayoutSimulation`, `LayoutCampaignRepository`): validation, `alt` for 400/409, equal-size prefix, `priorityCoverage`, `evaluate`, `evaluationRows`, `createReport`, `insertReportLayout`, report returned. The three-way decision frames were removed (they belong to AD06) |

Removed wording: "Declare CTR/conversion assumptions", "Compare simulated utility, CTR, conversion and estimated revenue", `MetricType`, `ESTIMATED_REVENUE`. A search of the three pages for CTR, revenue, conversion (other than `bundleConversion`), Declare, MetricType and synthetic finds nothing.

`docs/product/ARCHITECTURE_SDD.md` §9.1: the AD07 row and the R2-08.07 / R2-07.07 row are marked done.

## Verification

| Check | Result |
|---|---|
| `validate_drawio.py` on the pack (42 pages) | PASS |
| Pages other than the three changed ones | byte-identical (checked page by page against `origin/dev`) |
| PNG export of the three pages, viewed | no overlaps; two label positions moved after the first export |
| Maven | NOT RUN (documentation only) |

## Open

- FR page label FR08.04 was changed on `Khoi/docs/blackbox-test-cases`, not here. Record 009 is on that branch.
- Not pushed; no PR opened.

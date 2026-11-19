# ROC-20261119 — Owner decisions, F04 Excel on dev, F05 whole-đồng input, F07/F08 zone caps, agent kit

2026-11-19 / Asia/Ho_Chi_Minh. Docs only; delivered through a pull request into `dev` ([DEC-20261119-01](../../decisions/2026-11-19/DEC-20261119-01-dev-changes-only-through-pr.md)).

## Record 001 — Owner decisions recorded; stale merge status corrected

[Record 001](01-record-owner-decisions.md) adds seven decision files: DEC-20261112-01 (F07/F08 two-layout A/B, A view-only), DEC-20261117-02 (`priorityCoverage` = priority products with W > 1 ÷ products on the layout; not implemented), DEC-20261117-03 (F09/F10 proceed with the MVP plans), and DEC-20261119-01…04 (`dev` only through PR with green CI; 36-page UML baseline; zone capacities before F09, values TBD; F04 Excel merge after rebase and green CI). It corrects the stale "local / unpublished" status lines of DEC-20261117-01 and records 013–015, which are merged on `dev` (`705e3d7`, `ac29811`, `530651b`). CI job names were kept because their branch-protection status could not be checked. No source, migration or OpenAPI change; Maven NOT RUN.

## Record 002 — F04 Excel import brought onto current `dev`

[Record 002](02-f04-excel-import-v26.md) brings the 2026-10-26 Excel import onto `dev` as branch `Khoi/feat/f04-excel-import-v26` (merged by PR #21, `222e54f`): the four original commits cherry-picked, an async-CSV adaptation, OpenAPI 101/122 with only the Excel path added, RBAC and changelog rows.

## Record 003 — F05 whole-đồng fallback for the SPMF input

[Record 003](03-f05-spmf-whole-dong-fallback.md) (merged into `dev` by PR #23, `69b179b`) implements DEC-20261119-05: the exact SPMF input first, whole-đồng HALF_UP only when an EFIM int32 bound overflows; additive `conversionMode`/`roundingUnit` on request and run views; F06 version dispatch. The realistic-utility fixture is now accepted; the demo seed stays lossless (0.12 % → 180 patterns).

## Record 004 — F07/F08 zone capacities, top-K baseline and equal-size simulation

[Record 004](04-f07-zone-caps-baseline-topk.md) (merged into `dev` by PR #22, `9b91742`) implements DEC-20261119-06: owner zone caps in `application.yml`, baseline A top-K (`F07_BASELINE_V2`, K = 35), `OFFLINE_REPLAY_V2` equal-size comparison, additive `LayoutView.equalSizeBaselineScore`/`equalSizeProductCount`, and the [layout-size sensitivity evidence](../../testing/F08_LAYOUT_SIZE_SENSITIVITY_20261119.md) (sizes 35 and 50 reach only N = 21: the approved pool has 21 distinct products).

## Record 005 — Agent kit and product documents installed

[Record 005](05-agent-kit-and-product-docs.md) adds the agent-facing product documents under [`docs/product/`](../../product/README.md), the root `CLAUDE.md` and `AGENTS.md`, and the skills `paspm-context` and `paspm-backend-workflow` under `.claude/skills/` and `.agents/skills/` (identical). Docs only; Maven NOT RUN.

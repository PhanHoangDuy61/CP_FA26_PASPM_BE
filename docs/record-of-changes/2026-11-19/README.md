# ROC-20261119 — Owner decisions recorded, F04 Excel on dev, agent kit

2026-11-19 / Asia/Ho_Chi_Minh. Docs only; delivered through a pull request into `dev` ([DEC-20261119-01](../../decisions/2026-11-19/DEC-20261119-01-dev-changes-only-through-pr.md)).

## Record 001 — Owner decisions recorded; stale merge status corrected

[Record 001](01-record-owner-decisions.md) adds seven decision files: DEC-20261112-01 (F07/F08 two-layout A/B, A view-only), DEC-20261117-02 (`priorityCoverage` = priority products with W > 1 ÷ products on the layout; not implemented), DEC-20261117-03 (F09/F10 proceed with the MVP plans), and DEC-20261119-01…04 (`dev` only through PR with green CI; 36-page UML baseline; zone capacities before F09, values TBD; F04 Excel merge after rebase and green CI). It corrects the stale "local / unpublished" status lines of DEC-20261117-01 and records 013–015, which are merged on `dev` (`705e3d7`, `ac29811`, `530651b`). CI job names were kept because their branch-protection status could not be checked. No source, migration or OpenAPI change; Maven NOT RUN.

## Record 002 — F04 Excel import brought onto current `dev`

[Record 002](02-f04-excel-import-v26.md) cherry-picks the four F04 Excel commits of 2026-10-26 onto `dev` `530651b` (branch `Khoi/feat/f04-excel-import-v26`), adapts the async CSV path to the shared file validator, and documents `POST /api/transactions/imports/excel` (OpenAPI 101 paths / 122 operations at that point). Merged into `dev` by PR #21 (`222e54f`).

## Record 005 — Agent kit and product documents installed

[Record 005](05-agent-kit-and-product-docs.md) adds the agent-facing product documents under [`docs/product/`](../../product/README.md), the root `CLAUDE.md` and `AGENTS.md`, and the skills `paspm-context` and `paspm-backend-workflow` under `.claude/skills/` and `.agents/skills/` (identical). Docs only; Maven NOT RUN. Records 003 and 004 belong to open pull requests.

# ROC-20261120 — Product documents v0.4; F09 Storefront MVP (branch)

2026-11-20 / Asia/Ho_Chi_Minh. Docs only; delivered through a pull request into `dev` ([DEC-20261119-01](../../decisions/2026-11-19/DEC-20261119-01-dev-changes-only-through-pr.md)).

## Record 001 — Product documents v0.4 and Codex sandbox note

[Record 001](01-product-docs-v0-4-sync.md) syncs [`docs/product/`](../../product/README.md) to v0.4 after PRs #22 and #23 (zone caps, top-K baseline, equal-size simulation, whole-đồng SPMF fallback marked DONE; multi-day demo minUtil and layout-size sensitivity added), updates the `paspm-context` skill in both skill folders, and adds a "Codex sandbox" section to `AGENTS.md`. Facts checked against `dev` `636952b`; Maven NOT RUN.

## Record 002 — F09 Storefront MVP (branch `Duy/Feature/F09_Storefront`)

[Record 002](02-f09-storefront-mvp.md) is Duy Phan's F09 Storefront MVP record, written on the feature branch as record 001 and renumbered when it was merged with `dev` on 2026-11-25. An older copy of this branch was merged into `dev` directly at `68d6f0b` on 2026-11-21 ([ROC-20261121](../2026-11-21/README.md)); its runtime was replaced and its `store_slug`/`storefront_enabled` columns dropped by V30 in [ROC-20261125-005](../2026-11-25/05-f09-storefront-merge-into-dev.md).

# ROC-20261120 — Product documents v0.4 and F09 Storefront

2026-11-20 / Asia/Ho_Chi_Minh. Docs only; delivered through a pull request into `dev` ([DEC-20261119-01](../../decisions/2026-11-19/DEC-20261119-01-dev-changes-only-through-pr.md)).

## Record 001 — Product documents v0.4 and Codex sandbox note

[Record 001](01-product-docs-v0-4-sync.md) syncs [`docs/product/`](../../product/README.md) to v0.4 after PRs #22 and #23 (zone caps, top-K baseline, equal-size simulation, whole-đồng SPMF fallback marked DONE; multi-day demo minUtil and layout-size sensitivity added), updates the `paspm-context` skill in both skill folders, and adds a "Codex sandbox" section to `AGENTS.md`. Facts checked against `dev` `636952b`; Maven NOT RUN.

## Record 002 — F09 Storefront MVP

[Record 002](01-f09-storefront-mvp.md) implemented F09 public Store selection, Homepage, Product list/detail and visibility control on `Duy/Feature/F09_Storefront`. The initial handoff was local and unpublished. Its subsequent integration into `dev` is recorded in [ROC-20261121](../2026-11-21/README.md).

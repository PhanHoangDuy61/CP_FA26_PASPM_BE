# ROC-20261125 — Source-of-truth rule

2026-11-25 / Asia/Ho_Chi_Minh. Delivered through a pull request into `dev` ([DEC-20261119-01](../../decisions/2026-11-19/DEC-20261119-01-dev-changes-only-through-pr.md)).

Records 001 (SPMF CI artifact, branch `Khoi/chore/spmf-ci-artifact`, PR #29) and 002 (F09 Storefront merged with dev, branch `Khoi/merge/f09-storefront-be`) of this day live on their own branches; this branch adds record 003.

## Record 003 — Source-of-truth rule: the latest PO decision wins

[Record 003](03-source-of-truth-rule.md) replaces the "docs làm chuẩn" precedence in `AGENTS.md`, `CLAUDE.md`, both `paspm-context` skill copies and `BRD_VISION.md` §9.3 with [DEC-20261125-02](../../decisions/2026-11-25/DEC-20261125-02-source-of-truth-latest-po-decision.md): docs record decisions as of their date, the latest PO decision wins, and code/docs differences are reported to the PO and then recorded as a DEC. Docs only; Maven NOT RUN.

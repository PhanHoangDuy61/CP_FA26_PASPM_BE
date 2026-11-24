# DEC-20261119-04 — Merge F04 Excel import after rebase onto `dev` and green CI

| Field | Value |
|---|---|
| Date | 2026-11-19 (Asia/Ho_Chi_Minh) |
| Source | Owner decision of 2026-11-19; recorded in [ROC-20261119-001](../../record-of-changes/2026-11-19/01-record-owner-decisions.md) |
| Status | OWNER_DECISION — in progress |

## Decision
The F04 Excel import (approved Excel contract of 2026-10-26, [F04 improvement report](../../features/F04/F04_IMPROVEMENT_REPORT.md)) is merged into `dev` after it is brought onto current `dev` and its pull request has green CI ([DEC-20261119-01](DEC-20261119-01-dev-changes-only-through-pr.md)).

## Current state
- The implementation is on `origin/Khoi/feat/f04-excel-import` (4 commits ahead of an older `dev`, last commit 2026-10-26); it is not in `dev` `530651b`.
- The old branch is kept unchanged; the rebased work goes to a new branch `Khoi/feat/f04-excel-import-v26`.
- The approved Excel contract is not changed by this decision.

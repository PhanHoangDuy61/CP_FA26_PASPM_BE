# DEC-20261119-02 — UML baseline is the 36-page pack

| Field | Value |
|---|---|
| Date | 2026-11-19 (Asia/Ho_Chi_Minh) |
| Source | Owner decision of 2026-11-19; recorded in [ROC-20261119-001](../../record-of-changes/2026-11-19/01-record-owner-decisions.md) |
| Status | OWNER_DECISION — not yet applied in the repository |

## Decision
- The UML baseline is the **36-page** `PASPM_Unified_27_Diagrams_UML2_Editable.drawio`: the 27 core diagrams, R2-05 Package Diagram and the 8 class diagrams R2-07.01–R2-07.08.
- It is committed to `docs/diagrams/` only after it has been updated to Flyway V26 and the decisions up to 2026-11-19 (EFIM-only mining, Weight v2, removed benchmark, F07/F08 as implemented).

## Current state (`origin/dev` `530651b`)
- `docs/diagrams/PASPM_Unified_27_Diagrams_UML2_Editable.drawio` has 27 pages (last changed 2026-11-02, `de3d143`); it has no R2-05 or R2-07.x page.
- The 36-page version exists only as an uncommitted working copy outside this repository's `dev` history.
- The ERD has separately been redrawn for V1–V26 (`PASPM_ERD_V1_V26.drawio`, outside the repository, pending review).

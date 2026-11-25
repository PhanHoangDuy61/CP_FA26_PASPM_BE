# DEC-20261126-01 — The 36-page UML pack and the ERD V1–V26 are the diagram baseline in the repository

| Field | Value |
|---|---|
| Date | 2026-11-26 (Asia/Ho_Chi_Minh) |
| Source | Product Owner instruction (Nguyễn Minh Khôi) of 2026-11-26: bring the UML and ERD diagrams in line with `dev`; applies [DEC-20261119-02](../2026-11-19/DEC-20261119-02-uml-baseline-36-page-pack.md) |
| Status | IMPLEMENTED on branch `Khoi/docs/diagrams-update-20261126`; in force when that branch is merged |
| Scope | `docs/diagrams/`: UML pack and ERD |

## Decision
1. `docs/diagrams/PASPM_Unified_UML2_Editable.drawio` holds the **36-page** pack (27 core diagrams, R2-05 Package Diagram, eight class diagrams R2-07.01–08), later extended by the Store management pages. On the PO's instruction of 2026-11-26 the file was renamed from `PASPM_Unified_27_Diagrams_UML2_Editable.drawio` to `PASPM_Unified_UML2_Editable.drawio`, because the old name no longer matched the page count; every link in the repository was updated.
2. The ERD baseline is `docs/diagrams/PASPM_ERD_V1_V26.drawio` when landed, and is renamed after each phase to its last Flyway version (`PASPM_ERD_V1_V27.drawio` after the Store phase, `PASPM_ERD_V1_V28.drawio` after F02, `PASPM_ERD_V1_V30.drawio` after F09, the name on the branch). It replaces `PASPM_ERD_V1_V19_clean.drawio`, which was never committed to this repository.
3. The pack is then brought to the state of `dev` one phase at a time (Store V27, F02 V28, F09 V29/V30, EFIM-only and Weight v2, F07/F08, F10/F11 and SPMF), as one commit per phase, with a record in `docs/record-of-changes/2026-11-26/`.
4. The source of truth for diagram content is the code on `dev` and the DEC files; the latest PO decision wins (DEC-20261125-02).

## Consequences
- The sentence "only as an uncommitted working copy" in DEC-20261119-02, in `ARCHITECTURE_SDD.md` §9 and in the BRD decision log is replaced by this DEC.
- The 2.8 MB review PDF and the 2026-11-04 review package of the old branch `Khoi/docs/diagrams-final-clean` are **not** landed.
- Mermaid files under `docs/diagrams/` (`mermaid/`, the top-level `*.mmd` and `rendered/`) are **ARCHIVED** mirrors (PO decision of 2026-11-26): outdated, not regenerated, not a source. The source of truth is the `.drawio` files and their exported images.

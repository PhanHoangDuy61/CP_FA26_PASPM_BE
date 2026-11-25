# ROC-20261126-001 — Diagram baseline: 36-page UML pack and ERD V1–V26 in the repository

| Field | Value |
|---|---|
| Date / timezone | 2026-11-26 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Sonnet 5.5), requested by Nguyễn Minh Khôi (unattended overnight task) |
| Type | Documentation (diagrams) |
| Branch / base | `Khoi/docs/diagrams-update-20261126` / `origin/dev` @ `b94584b` |
| Decision | [DEC-20261126-01](../../decisions/2026-11-26/DEC-20261126-01-uml-erd-baseline-in-repository.md), applies DEC-20261119-02 |
| Release | UNRELEASED (branch pushed only; no PR, no merge) |
| Daily index | [2026-11-26](README.md) |

## Scope and changes

- `docs/diagrams/PASPM_Unified_27_Diagrams_UML2_Editable.drawio` is replaced by the 36-page pack (byte-identical to the backup commit `a7dadf5` of the old branch, SHA-256 prefix `b7d23a48956c05f5`), then edited as described below. The eight numbered `.mmd` files and their SVG renders that the working copy had changed are taken from the same backup commit.
- `docs/diagrams/PASPM_ERD_V1_V26.drawio` (9 pages, SHA-256 prefix `3bd9749632792804`) is added.
- Not landed: the review PDF (2.8 MB) and the 2026-11-04 review package, the V14–V19 scheduler ERD file and the 27-page scheduler QA report (their content is covered by the V1–V26 ERD page "Mining & Scheduling" and by this pack), `PASPM_ERD_V1_V19.drawio` (superseded by V1–V26).
- Scheduler synchronisation of the old commit `f59f85a` (V18/V19 wording): the 36-page pack had part of it. Ported by cell: 3 cells that the 36-page pack had left unchanged, and 15 cells whose new text carries the gating or V18/V19 facts (GATED, SKIPPED, SYSTEM_SCHEDULER, D1/D9, "System Admin: no pattern reads") on the pages FR, Use Case, AD03, R2-03, R2-04, R2-06.03, R2-08.01, R2-08.03; 2 note cells were added. Not ported: 61 cells where the 36-page pack has newer wording of its own, and 18 explanatory note boxes (no free space on the page or no geometry to copy). The list is in `outputs/diagrams-overnight/phaseAB_port_sync_result.json`.
- `docs/diagrams/README.md`, `ARCHITECTURE_SDD.md` §9, the BRD decision log and DEC-20261119-02 no longer say the 36-page pack exists only as an uncommitted working copy.

## Verification

| Check | Result |
|---|---|
| `validate_drawio.py` on the pack (36 pages) and on the ERD (9 pages) | PASS (page and cell ids unique, edge ends and parents exist) |
| Pages not named above | byte-identical to the backup commit (checked by the editing tool) |
| PNG export of the changed pages, looked at | `outputs/diagrams-overnight/png/A-B/` (R2-04, AD03 checked; text fits, no overlap) |
| Maven | NOT RUN (documentation only) |

## Open items

- The pages still describe V1–V19, HUI-Miner/benchmark and F07–F11 as before; the following records of this day change them phase by phase.
- The Mermaid mirrors under `docs/diagrams/mermaid/` were not regenerated here.

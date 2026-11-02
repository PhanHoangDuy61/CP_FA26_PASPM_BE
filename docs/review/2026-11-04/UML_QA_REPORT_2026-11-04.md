# UML 2.0 pack — QA report for the 2026-11-04 review

Pack: [`PASPM_Unified_27_Diagrams_UML2_Editable.drawio`](../../diagrams/PASPM_Unified_27_Diagrams_UML2_Editable.drawio) (27 pages) and the separate [`PASPM_F05_Scheduler_ERD_V14_V19.drawio`](../../diagrams/PASPM_F05_Scheduler_ERD_V14_V19.drawio). Aligned with Backend branch `Khoi/feat/f05-scheduled-mining-option-b-completion` at **`c65daf2`** (tested source tree `501f980`; production code differs from the earlier synchronised head `89e6ee5` by one log statement only). PDF: [`PASPM_UML27_Review_Pack_2026-11-04.pdf`](PASPM_UML27_Review_Pack_2026-11-04.pdf) (29 pages: cover, 27 pages, F05 ERD).

## 1. What this round changed (on top of the 2026-11-02 sync)

The 2026-11-02 sync ([`UML27_F05_SCHEDULER_SYNC_QA_2026-11-02.md`](../../reports/UML27_F05_SCHEDULER_SYNC_QA_2026-11-02.md)) already drew Scheduled Mining on 11 pages. This round re-based that branch onto `c65daf2`, re-checked every page against the code, and changed **two more pages** plus one new file:

| Item | Change |
|---|---|
| R2-04 System Architecture | scheduler component now says it runs as the `SYSTEM_SCHEDULER` principal (D9); database node names V18 (`SKIPPED`) and V19 (principal) |
| R2-06.03 (panel R2-06.03b, occurrence states) | note states D8 (disable stops new fires, committed request finishes, unprepared occurrence → `FAILED/HTTP_409`), D9, D10 (GATED), V18 `failureMessage`; divider extended |
| **New file** `PASPM_F05_Scheduler_ERD_V14_V19.drawio` | editable ERD of the scheduler tables, V15/V16–V17/V18/V19 columns colour-coded, database-enforced vs code-enforced invariants, GATED/CI/Linux/sign-off status. **Not** a 28th page of the pack |
| Review PDF | cover (status legend, baseline, hashes, contents) + 27 cropped pages + ERD, bookmarked |

Pages changed for Scheduled Mining relative to `origin/dev` (11): FR, Context, Use Case, AD01, AD03, AD04, R2-03 Screen Flow, R2-04, R2-06.03, Sequence AD01, Sequence AD03. The other 16 pages are byte-identical to `origin/dev`.

## 2. Implemented, gated, planned — how the pack distinguishes them

| Class | Drawn as | Where |
|---|---|---|
| IMPLEMENTED | black, no tag | everything in F01–F06 and manual F05 mining |
| GATED · off by default | tag `[GATED · off by default]`, a separate scheduler lane/operand, HTTP 503 note | FR05.04, UC06, AD03 scheduler lane, Sequence AD01/AD03 scheduled operand, R2-04 scheduler node, R2-06.03b |
| PLANNED | grey text and `[PLANNED]` | F07–F11 (UC12–UC16, UC23–UC29, AD06–AD08, Sequences AD06–AD08) |

The cover page of the PDF repeats this legend and states **CI NOT RUN · native Linux NOT RUN · release sign-off PENDING**. Nothing on any page is labelled released or approved.

## 3. Mechanical checks (run on the final files)

| Check | Result |
|---|---|
| Well-formed XML (`xml.dom.minidom`, `ElementTree`) for both `.drawio` files | PASS |
| 27 pages; ids, names and order identical to `origin/dev` | PASS |
| Unique `mxCell` ids per page; no dangling `source`/`target`/`parent` | PASS (0 problems) |
| F02 and F03 Draw.io ERDs (and the second F02 copy) | unchanged — `git diff c65daf2 --stat -- '*.drawio'` lists only the pack and the new ERD |
| Status vocabularies vs database constraints (`CHECK … status IN`): import batch (4), utility snapshot (READY/FAILED), mining request (4) + comparison (5), mining run (5), recommendation review (3), staging row (4), occurrence (6) | PASS — the state-machine pages R2-06.01–R2-06.06 match the constraints |
| Roles in Use Case / AD01 / sequences vs code: `SYSTEM_ADMIN` mining routes (`@PreAuthorize` on `MiningSystemController`), F06 generate/review `MERCHANDISER`, policy `STORE_ADMIN` (`RecommendationController`), System Admin patterns always 403 (D1) | PASS (spot-checked against source) |
| Every page rendered with the Draw.io desktop engine (`draw.io -x`) to PDF, cropped, merged | PASS — 29 PDF pages, text layer present on the checked pages |
| Occurrence state machine vs `ck_occurrence_status` | PASS — PENDING, PROCESSING, DISPATCHED, COMPLETED, FAILED, SKIPPED |

## 4. Visual inspection

Rendered at readable scale and looked at: **AD03** (3 lanes, guards, finals), **Use Case** (UC06 association, UC33), **R2-04** (new node texts fit their boxes after widening), **R2-06.03** (both machines, extended note and divider), **Sequence AD03** (scheduled operand S1–S7), **F05 ERD** (tables, colours, labels), **PDF cover**. Pages FR, Context, AD01, AD04, R2-03 and Sequence AD01 were inspected in the 2026-11-02 round and are **unchanged since** (verified by comparing page XML with the inspected commit). Fixes made this round after looking: node texts widened/heightened on R2-04, note and divider on R2-06.03, ERD label overlaps (role/app_user rows shortened, run table moved right, truncated type texts shortened), cover box sizes and branch wording.

Known cosmetic items left as they are: crossing connector jumps on the Use Case page (pre-existing), the `created_by / updated_by` edge label on the ERD touches the neighbouring table border.

## 5. UML semantics (spot checks)

Activity: one initial node per flow, partitions, guarded decisions, flow finals; the scheduler lane merges into the existing worker flow. State machine: initial → PENDING / SKIPPED, every non-final state has an exit, finals reached from COMPLETED / FAILED / SKIPPED, self-transition for lease reclaim. Sequence: `alt`/`opt` operands with guards, synchronous solid and dashed return messages, notes for gate, retry and disable semantics. Use case: UC06 now associated with System Administrator; UC33 added; UC numbers not reused.

## 6. Not done / limits

- The files were rendered with the Draw.io desktop engine; they were **not** opened by hand in the diagrams.net web editor.
- No Frontend diagram work; no diagrams for F07–F11 beyond the existing PLANNED pages.
- F02/F03 ERDs and the Mermaid logical ERD (V1–V11) do not show V14–V19. The new F05 ERD covers the scheduler tables only; there is no whole-schema ERD for V1–V19.
- The earlier Mermaid pack (`docs/diagrams/*.mmd`, `rendered/*.svg`) remains marked stale in the diagrams README.
- Diagrams cite behaviour verified locally only: GitHub CI **NOT RUN**, native Linux **NOT RUN**, release sign-off **PENDING**.

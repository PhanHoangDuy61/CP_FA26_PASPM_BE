# ROC-20261126-008 — PO answers: pack renamed, Mermaid archived, FK edges, page-by-page review

| Field | Value |
|---|---|
| Date / timezone | 2026-11-26 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Sonnet 5.5), requested by Nguyễn Minh Khôi |
| Type | Documentation (diagrams) |
| Branch / base | `Khoi/docs/diagrams-update-20261126` / `origin/dev` @ `b94584b` |
| Decision | [DEC-20261126-01](../../decisions/2026-11-26/DEC-20261126-01-uml-erd-baseline-in-repository.md) (updated) |
| Release | UNRELEASED |
| Daily index | [2026-11-26](README.md) |

## PO answers applied

1. **Tombstone pages kept, no renumbering** (AD04, R2-08.04, R2-07.04): nothing to change.
2. **UML pack renamed** `PASPM_Unified_27_Diagrams_UML2_Editable.drawio` → `PASPM_Unified_UML2_Editable.drawio` (`git mv`, history kept). Every reference was updated (15 files: README, SDD, DOCUMENTATION_MAP, feature READMEs, reports, decisions, records, review-2). Link check over all 324 tracked Markdown files (1,647 relative links): 0 broken, also 0 before the rename.
3. **No more scheduler wording ported**: nothing done.
4. **`docs/diagrams/mermaid/` marked ARCHIVED**: a banner at the top of `docs/diagrams/README.md` ("source of truth = the `.drawio` files and their exported images"), an ARCHIVED heading for the Mermaid section, a new `mermaid/README.md`, and a changed consequence in DEC-20261126-01. Nothing regenerated.
5. **FK edges drawn**: `product.deleted_by` → `app_user` on the Logical ERD (routed along the left margin and through the free corridor at y = 1780 into the left side of `app_user`, label `deleted_by`) and on Catalog & Inventory (a new `app_user` stub table, as the other pages have). `store.deactivated_by` → `app_user` was already drawn by the Store script on the Logical ERD (`erd-logical-fk-100`) and on Identity & Access (`erd-identityaccess-fk-7`); no change.

## Page-by-page review

Every page that had not been looked at was exported and viewed: Context, AD01, AD03, R2-03, R2-05, R2-06.03, R2-06.07, R2-08.01, R2-07.01, R2-08.03, R2-07.03, the three tombstones, and the ERD pages Logical (map and the product area), Utility & Snapshot and Layout & Campaign. Problems found and fixed:

| Page | Problem | Fix |
|---|---|---|
| AD03 | Leftover multi-run wording: "run(s)", "All selected runs COMPLETED?", "[any failed]", "[all complete]", "COMPLETED sibling remains accessible"; the first node overflowed | Single EFIM run wording; node text shortened |
| R2-06.03 | "request and runs persisted" | "request and run persisted" |
| R2-08.01, R2-07.01 | `getHomepage(storeId)` after F09 | `getHomepage(storeSlug)` |
| R2-07.03 | `createAdministrative(..., compare : boolean)` still in `MiningService`; multiplicity "runs 1..2" | Parameter removed; "runs 1" |
| R2-05 | Dashboard package tab too narrow for the «planned» label | Tab widened |
| ERD Layout & Campaign | A line crossed the baseline note | Notes moved |

All other viewed pages (Context, AD01, R2-03, R2-06.07, R2-08.03, tombstones, ERD Utility & Snapshot, ERD Logical product area) needed no change.

## Before / after gallery

`outputs/diagrams-overnight/review.html` (images under `outputs/diagrams-overnight/review/<phase>/`): one section per phase (A+B, C, D, E, G, F, H, R), one row per changed page with the page name, a cell-level change summary (cells added / removed / changed and up to three label changes) and the BEFORE and AFTER images side by side. "Before" is the previous state of the same page; for A+B it is `origin/dev` (the 27-page pack) or, for pages that dev does not have, the 36-page backup. The gallery lives outside the repository.

## Verification

| Check | Result |
|---|---|
| `validate_drawio.py` on the pack (42 pages) and the ERD (9 pages) | PASS |
| Pages not changed by an item | byte-identical (checked by the editing tool) |
| Relative links in all tracked Markdown files | 0 broken |
| `git diff --check` | clean |
| Maven | NOT RUN |

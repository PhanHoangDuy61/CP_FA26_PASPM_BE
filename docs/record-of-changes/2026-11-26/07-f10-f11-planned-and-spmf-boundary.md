# ROC-20261126-007 — F10/F11 planned contracts and the SPMF process boundary in the diagrams

| Field | Value |
|---|---|
| Date / timezone | 2026-11-26 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Sonnet 5.5), requested by Nguyễn Minh Khôi (unattended overnight task) |
| Type | Documentation (diagrams) |
| Branch / base | `Khoi/docs/diagrams-update-20261126` / `origin/dev` @ `b94584b` |
| Rules drawn | [DEC-20261125-06](../../decisions/2026-11-25/DEC-20261125-06-f10-f11-mvp-contract.md) (F10/F11, planned); [DEC-20261125-03](../../decisions/2026-11-25/DEC-20261125-03-spmf-gpl-distribution-and-process-boundary.md) (SPMF only as a separate process) |
| Release | UNRELEASED |
| Daily index | [2026-11-26](README.md) |

## Scope and changes

**F10 / F11, marked planned (nothing is coded)**
- **FR:** FR10.01 and FR11.01–03 carry `[PLANNED]`; FR11 now names the three dashboard panels of the decision (frozen snapshot weighted utility, `BUNDLE_ADDED` actions split `MINED` / `MANUAL`, saved simulated A/B reports).
- **Use Case:** UC24 and UC29 say "(planned)". **AD08:** the whole-bundle steps say planned; the cart step says that eligible components become ordinary cart lines and that the cart carries an `If-Match` version.
- **R2-03 Screen Flow:** "Simulated checkout" becomes "Cart (planned; no checkout in the MVP)". **R2-04:** the SPA node says "storefront cart (planned)". **R2-05 Package:** the Cart and Performance Dashboard packages carry «planned».
- **R2-08.08 and R2-07.08:** `addWholeBundle(bundleKey)`, `addBundle(bundleKey, ifMatch)`; `CartService`, `CartView`, `CartItem`, `CartRepository` are marked «planned». The opaque `bundleKey` comes from the F09 home projection.
- Outside the MVP and therefore not drawn: guest carts, checkout, orders, payments, discounts, `BUNDLE_VIEWED`, daily deals and benchmark charts.

**SPMF boundary**
- **R2-04:** the process node reads "SPMF 2.67a (GPL v3): separate Java 25 child, EFIM only", with a note: `java -jar spmf.jar run EFIM`, files and arguments only, never imported or bundled, parent-death supervision on Linux, 256 MiB heap, 60 s and a 4 MiB output cap. The database node reads "Flyway V1–V30 (V31 reserved for F10)".
- **R2-05:** the SPMF element says GPL v3, separate Java 25 child process, files and arguments only.
- All other pages are byte-identical to the previous commit.

## Not done

- F10/F11 have no new sequence, class or activity pages (the decision defines contracts, not behaviour beyond the whole-bundle action).
- The Mermaid mirrors (`architecture.mmd`, activity mirrors) were not regenerated.

## Verification

| Check | Result |
|---|---|
| `validate_drawio.py` on the pack (42 pages) | PASS |
| Pages not listed above | byte-identical |
| Overlap check of the new SPMF note and the resized node on R2-04 | no overlap |
| Scan for lost generic types in class boxes | none lost |
| PNG export, looked at: R2-04, R2-07.08 (R2-05 and AD08 are label edits) | `outputs/diagrams-overnight/png/H-f10-f11-spmf/` |
| Maven | NOT RUN |

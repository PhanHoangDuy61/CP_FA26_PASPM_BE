# ROC-20261126-003 — F02 expiry, push day and product soft delete (V28) in the diagrams

| Field | Value |
|---|---|
| Date / timezone | 2026-11-26 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Sonnet 5.5), requested by Nguyễn Minh Khôi (unattended overnight task) |
| Type | Documentation (diagrams) |
| Branch / base | `Khoi/docs/diagrams-update-20261126` / `origin/dev` @ `b94584b` |
| Rules drawn | [DEC-20261124-01](../../decisions/2026-11-24/DEC-20261124-01-batch-expiry-boundary-effective-status.md), [-02](../../decisions/2026-11-24/DEC-20261124-02-push-day-missing-warning.md), [-03](../../decisions/2026-11-24/DEC-20261124-03-product-soft-delete.md); migration V28 |
| Release | UNRELEASED |
| Daily index | [2026-11-26](README.md) |

## Scope and changes

- **New UML page AD10 "Product Delete & Batch Expiry"** (two swimlanes, Store Admin and PASPM / Catalog Service), after AD09. Flow 1: delete a product (401/403, 404, 409 `PRODUCT_HAS_SELLABLE_STOCK`, 409 `PRODUCT_IN_LIVE_CAMPAIGN`, lock, set `deleted_at`, `deleted_by`, status INACTIVE, 204, hidden before pagination). Flow 2: read a batch (`effectiveStatus` EXPIRED only when today > expiry date, EXPIRED wins over BLOCKED, `daysToExpiry`, `MISSING_PUSH_DAY` warning when the product requires expiry, has no push day and the batch is not expired). Two UML notes carry the "no restore, SKU never reused, history stays" and "stored status stays ACTIVE | BLOCKED" rules.
- **Existing UML pages:** FR (FR02.01 "…; soft delete", FR02.05 "Manage inventory batches and expiry status"), Use Case (UC17 now includes soft delete, UC20 names the effective expiry status and the push-day warning).
- **ERD** (renamed `PASPM_ERD_V1_V27.drawio` → `PASPM_ERD_V1_V28.drawio`): `product.deleted_at` and `product.deleted_by` (`[V28]`) on the Logical ERD and the Catalog & Inventory page, with a V28 note (batch status and warning are computed on read, no column). The Utility & Snapshot page gets a note on seasons: a deleted product keeps its assignments, a new assignment is 409 `PRODUCT_DELETED`. Titles say V1–V28.
- All other pages are byte-identical to the previous commit.

## Not done

- No new sequence or class page for F02: the baseline pack has no F02 sequence or class diagram to extend, so only the activity page, the use cases and the ERD were drawn.
- The FK from `product.deleted_by` to `app_user` is written in the row text, not drawn as a line (`app_user` is far away on the Logical ERD).
- The Screen Flow page has no delete screen; the UI is not specified by the decisions.

## Verification

| Check | Result |
|---|---|
| `validate_drawio.py` on the pack (41 pages) and the ERD (9 pages) | PASS |
| Pages not listed above | byte-identical (checked by the editing tool) |
| PNG export, looked at: AD10, Use Case, ERD Catalog & Inventory | `outputs/diagrams-overnight/png/D-f02/`; text fits and no shape overlaps after one fix round (decision shapes enlarged, note moved, elbow edge moved off the node border) |
| Maven | NOT RUN |

## Open items

- Mermaid mirrors were not updated (none exists for these pages).
- Tooling note: a draw.io cell id equal to a JavaScript array method (for example `push`) makes the CLI export fail; the page generator now refuses such ids.

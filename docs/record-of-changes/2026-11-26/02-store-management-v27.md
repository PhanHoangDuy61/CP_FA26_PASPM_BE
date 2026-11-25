# ROC-20261126-002 — Store management (V27) in the UML pack and the ERD

| Field | Value |
|---|---|
| Date / timezone | 2026-11-26 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Sonnet 5.5), requested by Nguyễn Minh Khôi (unattended overnight task) |
| Type | Documentation (diagrams) |
| Branch / base | `Khoi/docs/diagrams-update-20261126` / `origin/dev` @ `b94584b` |
| Rules drawn | [DEC-20261123-01](../../decisions/2026-11-23/DEC-20261123-01-store-management.md); V27 on `dev` |
| Release | UNRELEASED |
| Daily index | [2026-11-26](README.md) |

## Scope and changes

The edits of the Store management draft (`store-mgmt/diagrams/apply_store_changes.py`, 2026-11-23) were applied to the landed files. The script ran unchanged on the landed pack (its SHA no longer matched `b7d23a48956c05f5` because record 001 had ported the scheduler wording, but the pages it edits are found by id and the run completed).

- UML pack 36 → 40 pages. Changed existing pages: FR (FR01.04–06), Use Case (UC34 Manage Stores / Deactivate & Reactivate, UC35 View / Manage Own Store Profile & Images), R2-03 Screen Flow (Stores, Store Detail, My Store), R2-05 Package Diagram. New pages: AD09 Store Management, R2-06.07 Store State, R2-08.09 Sequence AD09, R2-07.09 Class Diagram Sequence AD09.
- ERD (renamed `PASPM_ERD_V1_V26.drawio` → `PASPM_ERD_V1_V27.drawio`): the `store` table gets the V27 columns `slug`, `status`, `address`, `phone`, `icon_key`, `banner_key`, `version`, `deactivated_at`, `deactivated_by` (and the FK to `app_user`) on the Logical ERD and Identity & Access pages. The titles say V1–V27 and no longer say "planned".
- All other pages are byte-identical to the previous commit (checked page by page).

## Verification

| Check | Result |
|---|---|
| `validate_drawio.py` on the pack (40 pages) and the ERD (9 pages) | PASS |
| Pages not listed above | byte-identical |
| PNG export of FR, Use Case, Screen Flow, AD09, Store State, both new Sequence/Class pages, Logical ERD and Identity & Access | `outputs/diagrams-overnight/png/C-store/`; FR looked at; the layout of the other pages was reviewed in the Store checkpoint of 2026-11-23 and not changed since |
| Maven | NOT RUN |

## Open items

- `docs/diagrams/mermaid/` has no Store page; not added.

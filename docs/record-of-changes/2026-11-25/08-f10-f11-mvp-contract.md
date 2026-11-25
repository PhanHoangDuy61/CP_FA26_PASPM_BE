# ROC-20261125-008 — F10/F11 planned MVP contracts

| Field | Value |
|---|---|
| Date | 2026-11-25 / Asia/Ho_Chi_Minh |
| Agent | Codex |
| Type | Documentation |
| Branch / base | `Khoi/docs/f10-f11-contract` / `origin/dev` at `2d1280b` |
| Execution status | COMPLETED (planned contract documentation) |
| Release status | UNRELEASED |

[DEC-20261125-06](../../decisions/2026-11-25/DEC-20261125-06-f10-f11-mvp-contract.md) records the owner decisions. The [F10 API](../../api/F10_BUNDLE_CART_API.md), [F11 API](../../api/F11_DASHBOARD_API.md), [F10 handoff](../../frontend_handoff/F10_HANDOFF.md) and [F11 handoff](../../frontend_handoff/F11_HANDOFF.md) describe PLANNED behavior. Product docs and indexes reflect the updated decisions. The workspace plan outside Git was updated to v2.

[F09](../../api/F09_STOREFRONT_API.md) merged through PR #33 (`0ef5a55`). Its V29 remains unchanged and V30 drops the legacy storefront columns. F10 is planned for V31. Subsequent migration numbering starts at V32. Priority coverage is on dev as of PR #32. No migration, source, test, or runtime OpenAPI change belongs to this documentation branch. Existing API shapes remain in effect until implementation.

Local checks cover the documentation diff, new links and JSON examples. Backend/FE tests, MySQL and hosted CI are NOT RUN for this docs-only change.

## Product Owner follow-up

The owner approved a single bundle per click with a key-only request. The cart merges up to the minimum of requested quantity, sellable stock available for the increment and remaining capacity under the per-Product cap. The response reports `{productId, requested, added}` for every component. An all-zero outcome returns 409 and records `BUNDLE_ADD_REJECTED`. Customers edit quantities after the add. Updated DEC-20261125-06, the planned F10 contract and handoff, the corresponding SRS references and the workspace plan v2.

# ROC-20261125-001 — F07 product-level Layout B editor

| Field | Value |
|---|---|
| Record ID | ROC-20261125-001 |
| Date / timezone | 2026-11-25 / Asia/Ho_Chi_Minh |
| Agent | Copilot SDK in VS Code |
| Type | Implementation / Documentation |
| Features | F07 Layout, F08 Simulation, F09 public placement projection, frontend campaign/editor/simulation |
| Branch / base HEAD | `whisa/feat/f07-product-layout-editor` / local feature branch |
| Execution status | VERIFIED |
| Release status | UNRELEASED |
| Daily summary | [README for 2026-11-25](README.md) |

## 1. Request and scope

Owner requested end-to-end F07 per-product Layout B editing across backend and frontend. F06 evidence and Layout A remain immutable, each placement keeps optional F06 provenance or manual provenance, saved revisions pin F08 inputs, and bundle conversion is grouped by saved Layout B `BUNDLE + blockNo`.

## 2. Change details

### Backend

- `PUT /api/layouts/{id}/placements` now accepts individual placements and `expectedVersion`.
- Added snapshot-product search with utility and live per-product eligibility.
- Split validation and revisions now preserve provenance and leave F06 evidence untouched.
- F08 reports use the exact saved B revision and `OFFLINE_REPLAY_V3` bundle grouping.
- F09 active-layout projection now validates split/manual placements block-by-block and does not require all products from a source recommendation to remain in one whole block.

### Frontend

- Added a real Layout Editor screen with recommendation expansion, snapshot search, drag/drop and manual add, movement/reorder/removal, undo, save, READY, unsaved state, capacity warnings and product details.
- Replaced fake campaign and simulation demo actions with API-backed campaign review, saved layout revisions, synchronous simulation and report result views.
- Added layout and simulation query navigation with campaign/layout/report IDs.

### Documentation

- Added owner DEC-20261125-01 and updated F07/F08 API, handoff, SRS, glossary, architecture and API changelog.

## 3. Files changed

Backend: layout contracts/controller/repository/service/rules, product eligibility, storefront projection, integration tests and test registration.

Frontend: App navigation, campaign/review, Layout Editor, simulation setup/results, API adapter, RBAC route metadata and navigation types.

## 4. Verification

- Frontend `npm run lint`: PASS.
- Frontend `npm run build`: PASS (Vite emitted non-failing warnings for `__dirname` and a chunk over 500 kB).
- Frontend `npm test`: PASS, 568 tests, 0 failures.
- Backend focused integration checks: PASS — `LayoutCampaignIntegrationTest`, `SystemStoreListHttpTest`, and `StorefrontIntegrationTest`; 34 tests, 0 failures or errors.
- `git diff --check`: PASS in both backend and frontend repositories.
- `docs/api/openapi.json`: valid JSON; 140 operations present.
- Backend full test suite and browser-based manual review: NOT RUN.

## 5. Limitations and next actions

- Run the full backend CI test group before merge.
- Runtime OpenAPI export and browser-based review were not run.
- Feature remains unreleased on the feature branch; no push was performed.

# ROC-20261126-013 — F11 dashboard Backend

| Field | Value |
|---|---|
| Date / timezone | 2026-11-26 / Asia/Ho_Chi_Minh |
| Type | Backend implementation |
| Branch / base | `Khoi/feat/f11-dashboard-api` / `e4bce3b` (F10 Part 2) |
| Release | UNRELEASED |
| Daily index | [2026-11-26](README.md) |

## Scope

- [F11 dashboard API](../../api/F11_DASHBOARD_API.md) implements the MVP from [DEC-20261125-06](../../decisions/2026-11-25/DEC-20261125-06-f10-f11-mvp-contract.md) and the inclusive 90-day boundary from DEC-20261126-02 Q-14. Both routes are read-only and use the authenticated Store for STORE_ADMIN and MERCHANDISER. The existing F01 guard handles inactive Stores.
- `GET /api/dashboard/snapshot-utilities` pages READY headers newest first by creation time and snapshot ID, with optional same-Store snapshot selection. The six-decimal total comes from the persisted header without reading the configuration JSON or matrix.
- `GET /api/dashboard/bundle-additions` aggregates successful F10 `BUNDLE_ADDED` events by Store-local day and MINED/MANUAL origin, with actual units added and zero-filled days. The database groups records in bounded, half-open UTC ranges derived from local midnights. The optional campaign filter also works for ended same-Store campaigns. Foreign and unknown IDs return 404.
- F11 uses the existing F08 campaign/report read endpoints. Integration assertions exercise both staff roles, foreign report and campaign IDs, and the Store Admin denial on the simulation write. The F08 API and its permissions stay as implemented.
- The runtime [OpenAPI](../../api/openapi.json) adds two operations with explicit IDs and five schemas (116 paths / 145 operations to 118 paths / 147 operations). The checked-in file matches the runtime export and all 274 internal references resolve.
- No migrations. F11 uses the existing snapshot, campaign/report and V31 event tables. Merge order is F10 Part 1 → F10 Part 2 → F11. Checked 63 local remote refs and local branches for numbering. Record 012 is taken on a separate local diagram branch.

## Verification

Temurin Java 17.0.21.1 with pinned `JAVA_HOME`, offline Maven Wrapper and disposable H2.

- RED `./mvnw -o -B -Dtest=DashboardIntegrationTest test` ran seven cases. All failed because the Dashboard routes or OpenAPI entries were absent. Zero test errors or skips.
- `./mvnw -o -B '-Dtest=DashboardIntegrationTest,LayoutCampaignIntegrationTest,TransactionExcelImportIntegrationTest#openApiDocumentsBothMultipartImportRoutes' -Dpaspm.test.export-openapi=true test` ran 25 tests. All passed, zero errors or skips. This covers the inclusive 90/91 boundary, local midnight with microsecond precision, zero days, origin counts and actual units, cross-Store and unknown IDs, inactive Stores, default dates, paging, and both F08 read roles.
- Final `./mvnw -o -B clean verify -Dtest="$(python3 .github/scripts/test-groups.py backend)"` passed **494 tests**, with exit 0 and zero failures, errors or skips. The H2 fixtures applied Flyway V1–V31.
- Checked 185 relative links with zero broken links, parsed seven fenced JSON examples and verified `git diff --check`.
- Dedicated MySQL and hosted CI were NOT RUN. Frontend integration is outside this checkpoint.

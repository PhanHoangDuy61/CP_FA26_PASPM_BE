# F03 Dynamic Utility Profiling — Implementation Plan

## Goal

Implement F03 backend APIs and immutable Utility Matrix snapshots using the F03 v4 ERD, F01 StoreScope, and F02 Product/Inventory Batch data, without implementing Frontend, F04 import, or F05 mining.

## Verified project context

- Latest ERD: `docs/ERD_Diagram/PASPM_Complete_ERD_F03_Three_Weights_v4.drawio` (Conceptual ERD, Logical ERD, and Utility Profiling zoom).
- Existing migrations: V1 F01 shared Store, V2 F01 user change, V3 F02 catalog and inventory. The current backend has no `SALES_TRANSACTION`, `TRANSACTION_ITEM`, or `MINING_REQUEST` tables or F04 transaction implementation.
- Existing persistence style uses Spring JDBC repositories, service/controller packages, `StoreScope` via `CatalogAccess`, Spring Security roles, Flyway, Springdoc, and H2-backed integration tests.
- F03 requirements and GitHub Issue documents are present under `docs/PASPM_F03_ERD_and_Tasks_v4/`; the issue archive is already extracted. The backlog copy in the issue folder matches the top-level backlog.
- CodeGraph CLI 0.9.9 and the workspace index are available. Architecture exploration traced F01/F02 security and repository patterns; it found no F04 transaction or F05 mining request implementation.

## Planned work

1. **Schema and data contracts:** Add the next Flyway migration for the two nullable Product scores, score constraints, Store utility configuration storage, and F03 snapshot/matrix tables based on the logical ERD. Reuse Store, Product, and Inventory Batch. Do not create F04 transaction tables or F05 mining tables. Resolve how ERD foreign keys to those absent tables are delivered before claiming full schema completion.
2. **Weight configuration and score APIs:** Add validated Store-scoped configuration GET/PUT and Merchandiser-only score reads/updates. Keep stock and seasonal scores separate from inventory and preserve unspecified scores on partial updates.
3. **Calculation services:** Implement deterministic expiry scoring from eligible batches, historical base utility, configured weighted utility, transaction totals, and a stable matrix ordering/hash. Use `BigDecimal`; reject invalid, missing, or negative inputs instead of silently altering business values.
4. **Transaction boundary:** Define an F04-facing validated-transaction provider. Use clearly labeled dev/test fixtures while F04 is absent; do not add user upload or mining behavior.
5. **Preview and snapshots:** Add non-persisting preview plus atomic snapshot creation and Store-scoped snapshot/matrix reads. Freeze all scores, config, analysis date, base/weighted values, and hashes in each snapshot.
6. **Verification and handoff:** Add focused unit/integration tests, run Maven/Flyway/OpenAPI checks, exercise available APIs through Swagger UI, and document actual results and any F04/F05 integration blockers in this F03 directory.

## Confirmed decisions — 2026-10-17

- Store Admin alone sets integer `expiryWindowDays > 0` in `STORE.utility_config`; initial default 30. Calculation services receive T from config.
- System Admin alone sets persisted, versioned system lambda bounds. Initial range [0, 0.5]; minimum >= 0; maximum >= minimum. Merchandiser selects Store lambda within current bounds. Bounds changes never clamp Store settings.
- Approved linear quantity-weighted expiry score from v4. Analysis date is fixed. Non-expiring Product: NOT_APPLICABLE/E=0. No eligible batch: NO_SELLABLE_BATCH/E=0. Missing required expiry: preview warning; expiry-enabled snapshot rejected.
- Snapshot captures T, policy version and bounds, selected lambda, effective coefficients, scores, and analysis date. Historical reads never consult current policy/config/inventory.
- Precision: inputs allow six fractional digits; decimal calculations use DECIMAL128; item utilities round once to six digits HALF_UP; totals sum rounded items. Reject values outside DECIMAL(30,6). Product IDs are positive, stable mining item IDs; F05 must encode decimals by the documented scale without loss or reject overflow.

## Remaining dependencies

- F04 transaction and F05 mining tables are absent. F03 must not create duplicate feature-owned tables. The snapshot-to-transaction and mining-request foreign keys need a migration owner/sequence that preserves the ERD.

## Verification

- Run focused service and H2/Flyway integration tests after each implementation slice.
- Run `mvn -q verify` after the complete change.
- Verify every implemented route and authorization rule in generated OpenAPI; execute available endpoint cases in Swagger UI and record real statuses.
- Refresh/check CodeGraph after changes and confirm it discovers the F03 module and dependencies.

## Scope controls

- No Frontend changes, CSV import, mining algorithms, minUtil, `PRODUCT_LOT`, ingredient inventory, or stock movement tables.
- Preserve the existing F01/F02 and uncommitted workspace changes. Do not stage local credentials or unrelated documentation changes.
- Mark F03 PARTIAL/BLOCKED if an approved business rule or required F04/F05 schema remains unavailable.

## Ordered implementation tasks

| Task | Implementation and checks |
|---|---|
| F03-01 | V4 score constraints, one generic system configuration store (none currently exists), snapshot and matrix tables. Defer transaction/mining-request FKs to owning feature migrations; record PARTIAL schema status. |
| F03-02 | Lambda policy GET/PUT (System Admin), Store config GET/PUT (Merchandiser), expiry-window PUT (Store Admin). Verify role separation, preservation, versions and stale lambda rejection. |
| F03-03/F03-10 | Canonical shared GET/PATCH `/api/utility/products/{productId}/scores` from backlog v4. Reject invalid/empty/null edits; preserve omitted score. |
| F03-04 | Unit tests before expiry calculator; verify weighted multi-batch scores, expiry boundary, missing metadata and fixed date. |
| F03-05/F03-06 | TransactionSource port, explicit isolated fixture adapter; reject negative/invalid/duplicate source items; deterministic matrix, hashes and historical base utility. |
| F03-07 | Preview with validated date range and bounded pagination; no persistence. |
| F03-08 | Atomic snapshot persistence, immutable paged reads and frozen policy/config/item values. Test rollback and policy/config/batch changes. |
| F03-09 | Maven/Flyway/OpenAPI, interactive Swagger where available, F05 handoff and honest dependency status. |

## Execution status — 2026-10-17

Implementation and verification executed:44 automated tests PASS,120 functional Swagger UI requests,36 test groups verified. Overall PARTIAL pending F04 production source/FK and F05 FK/integration; see F03_IMPLEMENTATION_SUMMARY.md and F03_SWAGGER_TEST_REPORT.md. No Frontend/F05 engine implementation.

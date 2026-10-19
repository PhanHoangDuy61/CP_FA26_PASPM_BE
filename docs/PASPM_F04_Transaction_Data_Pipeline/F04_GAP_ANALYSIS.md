# F04 Repository Gap Analysis

## Verified current state

- Backend uses Java 17, Spring Boot 3.5.7, JDBC, Flyway, Spring Security and springdoc-openapi. It does not use Spring Data JPA.
- Flyway migrations currently stop at V6. F01 Store/User and F02 Product tables exist; F04 transaction/import tables do not.
- The latest full editable ERD is `docs/ERD_Diagram/PASPM_Complete_ERD_F03_Three_Weights_v4.drawio`. The repository path is `ERD_Diagram`, not the lowercase path in the task text.
- The ERD includes `API_CONNECTION`, `IMPORT_BATCH`, `SALES_TRANSACTION` and `TRANSACTION_ITEM` in Conceptual and Logical views. It has no `IMPORT_STAGING_ROW`. Its current zoom page is an F03 transaction/utility view.
- `API_CONNECTION` is modeled in the approved ERD but is absent from all current migrations. P0 will add only the ERD metadata table so `IMPORT_BATCH.connection_id` can keep its approved nullable FK; external API configuration and ingestion endpoints remain P1.
- V5 creates `utility_matrix_item.transaction_id` without a foreign key and explicitly delegates adding that FK to F04 after `SALES_TRANSACTION` is created.
- There is no importer, parser, transaction repository, query API, import API or background worker.
- F03 exposes `TransactionSource.PreparedTransaction` and computes Base Utility through `UtilityMath`; a production transaction provider is currently unavailable. F03 requires Store/date filtering and rejects negative Base Utility values.
- Store scoping is available via `CatalogAccess.currentStoreId(jwt)` and F01 `StoreScope`. Existing API errors use `ApiException` / `ApiError`; the global security and OpenAPI configuration already handles common errors and bearer auth.
- Existing user changes are present on `dev`, including F03 documentation/tests and the local `src/main/resources/application.yml`. F04 work must preserve them; the local configuration is not an F04 edit target.

## Required changes

1. Add a Flyway migration for the ERD metadata `API_CONNECTION` table, import batches, raw staging rows, sales transactions and transaction items, with Store/Product foreign keys, row/order status and counters, unique order idempotency key, transaction content hash, audit indexes and the deferred V5 transaction foreign key.
2. Add `IMPORT_STAGING_ROW` to Conceptual and Logical ERD and align all F04 fields/relationships to the migration.
3. Add a streaming RFC 4180 CSV parser, UTF-8/BOM support, header/file/row bounds and clear parse errors.
4. Add validation and preprocessing that rejects whole orders, keeps independent valid orders, resolves Store-owned SKUs, aggregates consistent duplicate lines, preserves historical prices/costs and marks cancelled/refunded/partial-refund or below-cost orders ineligible for F03.
5. Add synchronous upload and Store-scoped status/error/transaction query APIs with role checks and pagination.
6. Add database-backed `TransactionSource` integration without changing F03 utility calculations or inventory.
7. Add focused unit/integration tests, export generated OpenAPI, and deliver the F04 API contract, Swagger report, sample CSV and frontend handoff.

## Decisions approved by user

- Use the previously proposed transaction statuses, eligibility behavior, below-cost handling and duplicate-SKU aggregation policy.
- Run the MVP synchronously because there is no worker infrastructure.
- Keep P1 Excel and external API work deferred.
- Use `Asia/Ho_Chi_Minh` for mapping stored UTC timestamps to F03 `LocalDate` boundaries.

## Implementation risks and controls

- **Concurrent duplicate orders:** enforce idempotency with a database unique key and canonical content hash; resolve duplicate-key races as idempotent duplicate or content conflict.
- **Partial order persistence:** validate the full order before writes and use one database transaction for its header and all items.
- **Unbounded uploads:** stream records and enforce configurable file-byte and row-count limits.
- **Audit loss:** persist parsed raw rows before business validation; never delete rejected rows during processing.
- **Cross-Store access:** obtain Store from authenticated context and scope every query by Store ID; do not accept a client Store ID.
- **Historical stock mutation:** no F04 repository writes to `inventory_batch`.
- **User-local config:** do not edit `src/main/resources/application.yml`; bind F04 technical limits with defaults overridable through environment/system properties.
- **ERD drift:** validate Draw.io XML and compare the final schema fields/constraints with the Flyway migration before completion.

## Not claimed

No F04 migration, implementation, Swagger request, OpenAPI export or ERD edit has been executed yet. Existing baseline Maven verification passed before F04 changes; it is not evidence of F04 behavior.

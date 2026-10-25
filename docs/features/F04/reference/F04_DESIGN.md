# F04 Transaction Data Pipeline — Design

## Scope and repository findings

F04 P0 adds a CSV-to-database pipeline that stores source rows for audit, validates complete orders, persists clean transactions and exposes them through an internal contract to F03. It does not calculate utility or mutate inventory. Excel ingestion and external API ingestion remain P1.

The backend is a Java 17 / Spring Boot 3.5.7 modular monolith using JDBC (`JdbcTemplate`), Flyway and Spring Security. There is no existing import implementation, transaction table migration or background-job infrastructure. F03 currently reads `TransactionSource.PreparedTransaction`, whose item fields are `productId`, `quantity`, `unitPrice`, `unitCost` and `sourceUtility`; the F03 matrix builder filters by Store/date and accepts only eligible transactions. The latest editable ERD is `docs/ERD_Diagram/PASPM_Complete_ERD_F03_Three_Weights_v4.drawio` (the repository uses uppercase `ERD_Diagram`). It already models `IMPORT_BATCH`, `SALES_TRANSACTION` and `TRANSACTION_ITEM`, but has no staging entity. Existing Flyway migrations are V1–V6. V5 notes that the F04 migration must add a foreign key for `utility_matrix_item.transaction_id`.

## Selected approach

Use synchronous, bounded, streaming CSV processing because the repository has no job worker or queue. Parse RFC 4180 records (including UTF-8 BOM, quoted commas and escaped quotes). Persist the import batch and parsed raw staging records before business validation. Process each order independently in a database transaction: an invalid order is rejected as a whole, while other orders in the same file may succeed. The upload response returns the completed batch summary; clients can use the status and detail APIs for later inspection.

Add the next Flyway migration to create the ERD's missing `API_CONNECTION`, `IMPORT_BATCH`, `IMPORT_STAGING_ROW`, `SALES_TRANSACTION` and `TRANSACTION_ITEM` tables, constraints and indexes. `API_CONNECTION` is metadata-only in P0, with fields exactly as shown in the approved ERD; no external API endpoint, sync worker or secret storage is added. Reconcile the final columns against the current F03 ERD and existing F01/F02/F03 keys. Add Store-scoped APIs following existing authentication and `CatalogAccess` / `StoreScope` patterns. F03 will consume a database-backed `TransactionSource` adapter in-process; it will not call F04 over HTTP or parse CSV. Historical imports never decrement `INVENTORY_BATCH`.

## CSV and transaction policy

The CSV header is `source_order_id,occurred_at,sku,quantity,unit_price,unit_cost,status`. Each record represents one product line. Timestamps must be ISO-8601 values with an explicit UTC offset and are stored as UTC instants. File parsing supports UTF-8 and UTF-8 BOM, standard quoted values, escaped quotes, embedded commas and blank lines; unsupported or malformed files receive a clear client error.

Accepted normalized statuses are `COMPLETED`, `CANCELLED`, `REFUNDED` and `PARTIAL_REFUND`. The first is mining-eligible only when every line is compatible with F03's nonnegative-utility contract. The other three are retained as valid historical transactions but are ineligible for F03. An order with a sale price below cost remains in history and is marked ineligible instead of passing a negative utility input to F03. This does not make the CSV structurally invalid. F04 will not calculate utility: its adapter passes `sourceUtility = null` with historical unit price and cost, so the existing F03 `UtilityMath` derives base utility.

Lines for the same SKU in one order are combined by summing quantity only when unit price, unit cost and normalized status agree. If those values differ, reject the whole order and retain row-level errors. The staging table remains the source-row audit trail. Unknown or cross-Store SKUs reject the entire order; products are never auto-created.

The idempotency key is `(store_id, source_system, source_order_id)`, with CSV uploads using a stable `CSV_UPLOAD` source system. Persist a canonical content hash for each clean order. A retry with the same key and hash is an idempotent duplicate; a retry with the same key but different content is a conflict and never overwrites existing history. A database unique constraint is the final concurrency guard.

Batch row counters (`total_count`, `valid_count`, `rejected_count`) count CSV data rows only. API responses also report distinct order counts in separately named fields; row and order units are never combined. Row statuses are `PENDING`, `VALID`, `REJECTED`, `PROCESSED`; batch statuses are `PROCESSING`, `COMPLETED`, `COMPLETED_WITH_ERRORS`, `FAILED`; transaction statuses preserve the four normalized source statuses. An order is atomic across `SALES_TRANSACTION` and all its `TRANSACTION_ITEM` records.

## API and authorization design

The proposed upload route is `POST /api/transactions/imports/csv`, restricted to authenticated `STORE_ADMIN`; Store identity comes from the authenticated F01 context, never request data. Import batch list/detail/error reads and transaction list/detail reads are Store-scoped and available to `STORE_ADMIN` and `MERCHANDISER`, subject to existing role conventions. Record lookups outside the caller's Store return 404. List queries are always constrained by authorized Store and use bounded pagination. Upload validation covers extension/media type, configured size and row limits, header shape and parser errors. Limits must be configuration-backed and documented; existing multipart configuration will be inspected without changing user-local `application.yml`.

The exact final endpoint shapes, DTOs and statuses will be derived from the current controller/error conventions and verified OpenAPI, then documented in the API contract and Frontend handoff. No duplicate API_CONNECTION endpoint or P1 ingestion route is introduced.

## F03 integration

Implement a database-backed `TransactionSource` that selects only completed, mining-eligible transactions for the authorized Store and requested analysis date range, returning historical quantity, unit price and unit cost with `sourceUtility = null`. Keep the F03 interface stable. The adapter maps persisted UTC timestamps to the date semantics expected by the existing F03 contract. F03 remains responsible for Base Utility calculation through its existing `UtilityMath`; F04 only supplies validated sales facts and the eligibility decision.

## ERD and migration changes

Update both Conceptual and Logical pages of the latest F03 Draw.io ERD with `IMPORT_STAGING_ROW` and its one-to-many relation from `IMPORT_BATCH`. Update the Logical page with any F04 persistence fields needed for row/order counters, idempotency hashes and explicit statuses. Keep the existing entities, inventory model and layouts, and add the staging entity to the F03 logical zoom page only if that page represents the complete import-to-transaction path. Validate XML, unique cell IDs, relationships and entity/relationship counts after editing. The Flyway migration must match the resulting ERD, preserve the immutable F03 snapshot behavior and add the deferred transaction foreign key from V5. The ERD's existing `API_CONNECTION` entity will be migrated as metadata-only so `IMPORT_BATCH.connection_id` can retain its approved FK; P1 API ingestion remains deferred.

## Verification plan

Use focused tests for parsing, validation, Store isolation, order-level atomicity, idempotency, persistence, retry behavior, counter units and the F03 adapter. Include an integration test against the project's configured test database for constraints and Flyway. Verify that importing history leaves inventory quantities unchanged. Run the full Maven verification suite. Start the backend only with isolated/test configuration and verify the generated OpenAPI and Swagger routes; interactive Swagger results will be reported only if actually executed. Refresh CodeGraph and verify the new catalog/transaction symbols and F03 dependency after implementation.

## Deferred items and known limits

F04-10 Excel and F04-11 external API ingestion are P1 and deferred. No background job infrastructure exists, so the MVP upload is synchronous. Partial refunds are preserved as ineligible historical data; this release does not model refund line items or prorate reversals. Existing F03 date range semantics use `LocalDate`; the adapter must use the agreed project timezone (`Asia/Ho_Chi_Minh`) when deriving dates from stored UTC timestamps unless the current F03 implementation establishes a more specific rule. File and row limits will use safe configured technical bounds and be reflected in docs and OpenAPI.

## Approaches considered

1. **Synchronous bounded streaming (selected):** fits the current monolith and allows immediate per-order processing and results, with no new operational subsystem.
2. **Background worker / HTTP 202:** supports larger uploads but requires a queue, job lifecycle and worker recovery infrastructure that does not exist in this repository.
3. **One transaction per whole file:** simpler but causes valid orders to fail with one bad order and conflicts with the required partial-batch behavior.

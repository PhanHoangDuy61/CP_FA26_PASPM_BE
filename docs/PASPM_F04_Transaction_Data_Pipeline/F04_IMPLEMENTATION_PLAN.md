# F04 Transaction Data Pipeline Implementation Plan

> **For agentic workers:** Execute this plan inline in order. Keep each task independently testable and preserve all pre-existing worktree changes.

**Goal:** Implement the F04 P0 CSV-to-staging-to-clean-transaction pipeline and connect eligible stored transactions to F03.

**Architecture:** Add a JDBC-backed `transaction` feature module. A bounded streaming parser stores raw import rows, then validates and writes each complete order atomically; REST queries remain Store-scoped, and F03 consumes an in-process `TransactionSource` adapter.

**Tech Stack:** Java 17, Spring Boot 3.5.7, Spring JDBC, Flyway, Spring Security, springdoc-openapi, Apache Commons CSV 1.14.1, JUnit 5, Spring Boot Test, H2 test database.

## Global Constraints

- F04 does not calculate Base/Weighted Utility, S/E/M scores, minUtil or itemsets.
- F04 imports never mutate `inventory_batch`.
- Store IDs come from F01 authenticated context; every read/write is Store-scoped.
- One invalid order never creates a partial `SALES_TRANSACTION` or `TRANSACTION_ITEM` set.
- Row counters count data rows; order counters use separately named fields.
- Preserve the approved statuses and rules in `F04_DESIGN.md`.
- P1 Excel and external API ingestion are deferred.
- Do not change Frontend code, F01/F02/F03 behavior, existing migrations, or local `src/main/resources/application.yml`.
- Put F04-specific analysis, plan, API contract, test report and handoff in the approved F04/API documentation locations.

## Execution status — 2026-10-19

F04 P0 code, migration, F03 adapter, API contract, handoff, sample CSV and runtime OpenAPI export are implemented. Full Maven verification passed once before the last focused integration test was added; the final full `verify` is rerunning now. The focused atomic-order integration case passed. Interactive Swagger verified the route listing, multipart binary schema and unauthenticated `GET /api/transactions` 401. Authenticated upload through Swagger remains blocked because the Edge extension does not allow file URL access, and the isolated H2 runtime has no Store Admin/Product fixture; do not mark authenticated Swagger scenarios complete. See `F04_SWAGGER_TEST_REPORT.md`.

---

## Files and responsibilities

- `src/main/resources/db/migration/V7__f04_transaction_import_pipeline.sql` — ERD metadata `api_connection`, F04 tables, keys, constraints and indexes; add the deferred F03 transaction FK.
- `src/main/java/vn/edu/fpt/paspm/transaction/importing/TransactionImportController.java` — multipart upload and batch list/detail/error routes.
- `src/main/java/vn/edu/fpt/paspm/transaction/importing/TransactionImportService.java` — orchestration, per-order transactions, counters and retry results.
- `src/main/java/vn/edu/fpt/paspm/transaction/importing/TransactionCsvParser.java` — streaming CSV decoding, header validation and bounded row emission.
- `src/main/java/vn/edu/fpt/paspm/transaction/importing/TransactionOrderValidator.java` — typed field validation, status normalization, SKU resolution, grouping and duplicate-line policy.
- `src/main/java/vn/edu/fpt/paspm/transaction/importing/TransactionImportRepository.java` — JDBC batch/staging/order persistence and transactional state updates.
- `src/main/java/vn/edu/fpt/paspm/transaction/query/TransactionController.java` — Store-scoped transaction list/detail routes.
- `src/main/java/vn/edu/fpt/paspm/transaction/query/TransactionQueryRepository.java` — paginated Store-scoped transaction reads.
- `src/main/java/vn/edu/fpt/paspm/transaction/TransactionContracts.java` — request/response records and enums.
- `src/main/java/vn/edu/fpt/paspm/transaction/TransactionImportProperties.java` — default 5 MiB upload and 10,000 data-row limits, overridable with `paspm.transaction.import.max-file-bytes` and `paspm.transaction.import.max-rows`.
- `src/main/java/vn/edu/fpt/paspm/transaction/TransactionMultipartConfiguration.java` — multipart servlet cap, derived from the F04 byte limit plus bounded request overhead, without editing the local YAML file.
- `src/main/java/vn/edu/fpt/paspm/transaction/DatabaseTransactionSource.java` — F03 `TransactionSource` implementation; returns only eligible rows and passes `sourceUtility = null`.
- `src/test/java/vn/edu/fpt/paspm/transaction/importing/TransactionCsvParserTest.java` — CSV dialect/header/BOM/bounds tests.
- `src/test/java/vn/edu/fpt/paspm/transaction/importing/TransactionOrderValidatorTest.java` — row/order/status/aggregation validation tests.
- `src/test/java/vn/edu/fpt/paspm/transaction/TransactionImportIntegrationTest.java` — migrations, authorization, persistence, idempotency, isolation and no-stock-mutation tests.
- `src/test/java/vn/edu/fpt/paspm/transaction/DatabaseTransactionSourceTest.java` — F03 adapter Store/date/eligibility contract tests.
- `src/test/java/vn/edu/fpt/paspm/utility/UtilityIntegrationTest.java` — seed persisted transaction fixtures now required by the V5-deferred transaction FK.
- `docs/ERD_Diagram/PASPM_Complete_ERD_F03_Three_Weights_v4.drawio` — add staging row to Conceptual and Logical views and align F04 fields.
- `docs/PASPM_F04_Transaction_Data_Pipeline/F04_IMPLEMENTATION_PLAN.md` — this execution checklist.
- `docs/PASPM_F04_Transaction_Data_Pipeline/F04_GAP_ANALYSIS.md` — verified repo gaps and risks.
- `docs/api/F04_TRANSACTION_API.md`, `docs/api/openapi.json`, `docs/api/API_CHANGELOG.md`, `docs/frontend_handoff/F04_HANDOFF.md` — generated/verified integration contract and handoff.
- `docs/PASPM_F04_Transaction_Data_Pipeline/F04_SWAGGER_TEST_REPORT.md` and `examples/transactions.csv` — actual Swagger verification results and safe fixture.

## Task 1: Add migration and verify schema constraints

**Interfaces produced:** tables `api_connection`, `import_batch`, `import_staging_row`, `sales_transaction`, `transaction_item`; Store + source-system + source-order unique key; content hash; FK `utility_matrix_item.transaction_id → sales_transaction.transaction_id`.

- [x] Add migration test asserting `Flyway` creates the F04 tables and idempotency constraint.
- [x] Run the migration test before V7; it failed at `SELECT COUNT(*) FROM import_batch` because the table did not exist.
- [x] Create V7 with `api_connection` metadata fields exactly matching the approved ERD (`connection_id`, `store_id`, `name`, `base_url`, nullable `secret_reference`, `status`, nullable `last_synced_at`); add Store-scoped import metadata; row `source_order_id`, `row_number`, raw payload, status/errors and optional resolved Product/transaction/item references; transaction status, eligibility and canonical hash; transaction item historical quantity, unit price and unit cost.
- [x] Keep FKs restrictive for auditability; add unique `(import_batch_id,row_number)`, `(store_id,source_system,source_order_id)`, and indexes for `(store_id,started_at)`, `(import_batch_id,status)`, and `(store_id,occurred_at,transaction_id)`; add composite Store FKs for staging/item traceability.
- [x] Run `mvn -q -Dpaspm.avatar.directory=/private/tmp/paspm-f04-test-avatars -Dtest=TransactionImportSchemaTest#flywayCreatesStoreScopedImportAndTransactionTables test`; H2 MySQL mode migrated through V7 and assertions passed.

## Task 2: Implement and test the bounded RFC 4180 CSV parser

**Interface produced:** `TransactionCsvParser.parse(InputStream, Consumer<ParsedCsvRow>)` validates the exact approved header and emits one typed raw-record value at a time.

- [x] Add parser tests for UTF-8 BOM, quoted commas, escaped double quotes, embedded newline, blank lines, malformed quote, missing headers and row limit overflow.
- [x] Run `mvn -q -Dtest=TransactionCsvParserTest test` before implementation; it failed because `TransactionCsvParser` was absent.
- [x] Add Apache Commons CSV 1.14.1; strip UTF-8 BOM with a bounded `PushbackInputStream`, configure RFC4180, and map only the exact seven headers `source_order_id,occurred_at,sku,quantity,unit_price,unit_cost,status`.
- [x] Stop parsing once configured `max-rows` is exceeded; close parser/stream on success and failure; convert parser failures to existing `ApiException(400, ...)` without including raw row values in the message.
- [x] Run `mvn -q -Dtest=TransactionCsvParserTest test`; all five dialect and bounds tests passed after mapping parser `UncheckedIOException` to the API's 400 response.

## Task 3: Add status normalization, typed validation and order grouping

**Interface produced:** `TransactionOrderValidator.validateAndGroup(int storeId, List<ParsedCsvRow>)` returns order groups containing normalized status, UTC timestamp, resolved product IDs, consolidated lines, row references and explicit row/order errors.

- [x] Add validator tests for quantity, statuses, repeated SKU policy, below-cost eligibility and Store-owned SKU lookup; malformed time/negative money remain covered by validation code but are not separately exercised in current test suite.
- [x] Test repeated matching lines aggregation; differing prices, unknown and cross-Store SKU reject the order.
- [x] Run validator tests before implementation; missing validator initially failed.
- [x] Implement timestamp parsing using `OffsetDateTime`, case-insensitive approved statuses and Store-scoped SKU lookup.
- [x] Mark only `COMPLETED` orders with all `unit_price >= unit_cost` eligible; retain other approved statuses and below-cost orders as valid but ineligible. No utility is calculated in F04.
- [x] Run `TransactionOrderValidatorTest`; all five tests passed.

## Task 4: Persist batches and staging rows before business validation

**Interface produced:** repository methods create a Store-owned batch, insert staged rows in bounded chunks and update row/batch state with row and order counters in separate units.

- [x] Add integration coverage for staged rejected rows, unique row positions and separate row/order counters.
- [x] The initial controller request failed with 404 before the F04 API existed; implemented the import service and persistence path.
- [x] Add JDBC inserts, default 5 MiB and 10,000 data-row limits and multipart overrides without editing `application.yml`.
- [x] Persist parsed CSV fields as text and retain source row numbers; mark parse failures on their batch before returning the 400 error.
- [x] Run focused import integration and Flyway schema tests successfully.

## Task 5: Persist orders atomically and enforce idempotency

**Interface produced:** `TransactionImportService.importCsv(Jwt, MultipartFile)` writes each valid order in an isolated transaction and returns counts for rows plus distinct orders.

- [x] Integration coverage verifies multi-item persistence, all-or-nothing rejection of an order with a bad line, independent order acceptance, duplicate import, content conflict and unchanged inventory. Process restart replay is not separately tested.
- [x] A new import request returned 404 before the importer existed.
- [x] Persist canonical `content_sha256` and enforce `(store_id,source_system,source_order_id)` idempotency.
- [x] Same hash marks staging rows as duplicate/processed; changed content is recorded as conflict without overwriting history.
- [x] Insert each transaction and its consolidated items atomically per order; update staging outcome; source system is `CSV_UPLOAD`.
- [x] Store separate row and order counters and leave `inventory_batch` unchanged.
- [x] Focused integration tests passed for atomicity, duplicate/conflict behavior and stock preservation.

## Task 6: Add Store-scoped import APIs and role checks

**Interfaces produced:** `POST /api/transactions/imports/csv`, `GET /api/transactions/imports`, `GET /api/transactions/imports/{id}`, and `GET /api/transactions/imports/{id}/errors`.

- [x] MockMvc verifies no-token 401, Merchandiser upload 403, Store Admin upload, authorized reads, cross-Store detail 404 and ignored client-supplied `storeId`. Invalid file/size/content-type cases and cross-Store errors endpoint are not all separately covered by integration tests.
- [x] Confirmed the missing import route returned 404 before implementation; after implementation, ran all import integration tests.
- [x] Use `@PreAuthorize`, authenticated `Jwt`, `CatalogAccess.currentStoreId(jwt)`, `@RequestPart("file")` and no client-supplied Store ID.
- [x] Limit pagination to page >= 0 and pageSize 1–100; return row/order counters.
- [x] Focused role and Store isolation integration cases passed.

## Task 7: Add Store-scoped transaction query APIs

**Interfaces produced:** `GET /api/transactions` and `GET /api/transactions/{id}` with Store-scoped list/detail DTOs and bounded pagination/filtering.

- [x] MockMvc verifies Store Admin and Merchandiser reads, foreign Store detail 404, cross-Store list isolation, status filtering and pagination inputs. Date filtering and stable sort are implemented but not separately asserted in the current integration suite.
- [x] Use Store-constrained JDBC queries before optional filters; detail reads include Store ID.
- [x] Return source order ID/system, UTC `occurredAt`, status, F03 eligibility, amount and historical line prices/costs.
- [x] Run import/query integration tests successfully.

## Task 8: Connect F03 to persisted eligible transactions

**Interface produced:** `DatabaseTransactionSource implements TransactionSource`, using the current `load(int storeId, LocalDate rangeStart, LocalDate rangeEnd)` contract.

- [x] Adapter tests verify eligible Completed-only loading, Store/local-date boundaries and historical values with null `sourceUtility`.
- [x] Replace the normal-profile unavailable source; keep the fixture profile source.
- [x] Query Store/date-filtered persisted headers/items and map UTC timestamps to `Asia/Ho_Chi_Minh` dates.
- [x] Run database adapter and existing utility calculation/integration tests successfully.

## Task 9: Update and validate the editable ERD

- [x] Add `IMPORT_STAGING_ROW` to Conceptual and Logical pages and its `IMPORT_BATCH 1:N` relationship.
- [x] Add V7 counters, hashes, Store key, traceability fields and constraints to the Logical view; retain inventory and F03 utility design.
- [x] Keep the F03 zoom page focused on utility rather than duplicating the full transaction import schema.
- [x] Parse the Draw.io XML; all three diagrams have unique cell IDs and valid relationship endpoints (45, 45 and 9 relationships respectively). V7 columns and composite Store FKs were checked against the Logical page.

## Task 10: Verify OpenAPI, Swagger and frontend handoff

- [x] Start the backend on isolated H2 with throwaway test secrets; Flyway applied V1–V7.
- [x] Export runtime OpenAPI 3.1 from `/v3/api-docs`; assert six F04 endpoints and multipart binary file request schema.
- [ ] Swagger route listing, multipart form and unauthenticated list 401 were verified. Authenticated file upload is BLOCKED because Edge extension file URL access is disabled; no import request was sent. The isolated H2 runtime had no Store Admin/Product fixture.
- [x] Create the F04 API contract, React handoff, safe sample CSV and changelog entry from source DTOs and runtime OpenAPI.
- [x] Run Flyway/schema and ERD checks, Maven `verify`, OpenAPI JSON/endpoint assertions and `git diff --check`. Final full verify passed: 73 tests across 12 suites, 0 failures/errors/skips; 19 were F04.
- [x] CodeGraph's workspace index watcher reflected the importer, transaction query and F03 adapter; status and relationships are recorded in `F04_CODEGRAPH_REPORT.md`.

## Completion mapping

- F04-01: Tasks 1, 3, 8, 9.
- F04-02: Task 2 and sample CSV in Task 10.
- F04-03: Tasks 4, 5, 6.
- F04-04: Task 3 and atomic-order tests in Task 5.
- F04-05: Task 3 and Store security tests in Task 6.
- F04-06: Tasks 3 and 5.
- F04-07: Task 5.
- F04-08: Tasks 6 and 7.
- F04-09: Task 8 and Task 10 verification.
- F04-10 / F04-11: Deferred P1; explicitly marked deferred in final docs/report.

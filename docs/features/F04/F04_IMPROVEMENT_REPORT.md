# F04 File Import Improvement Report

Date: 2026-10-26 — Asia/Ho_Chi_Minh. Base: dev `c 06d 43e`. Status: CSV and Excel LOCAL_VERIFIED on their isolated stacked branches; human review and GitHub-hosted checks remain pending. Human review and GitHub-hosted verification remain separate.

## Approved scope

F04 is file-based CSV/Excel transaction import only. External API Connection and POS integration are not required and were not implemented. Webhooks, polling, realtime ingestion and scheduled external synchronization are excluded. Existing API_CONNECTION metadata remains unchanged.

## Root cause, reproduction and correction

The service stages parsed rows before `TransactionOrderValidator.validateAndGroup()`. V7 defines the searchable staging `source_order_id` as VARCHAR(150). Previously, `insertStagingRows()` bound the complete unvalidated ID, so a 151-unit ID triggered a data-integrity error before per-order validation. Independent valid orders were never processed, HTTP409 was returned and the batch became FAILED.

Actual RED execution on both H2 and disposable MySQL 8.4.11 returned 409 for the mixed valid/151-ID case, where the regression expected 201. The correction binds NULL to the nullable staging lookup projection for blank/unrepresentable normalized IDs and retains the entire original parsed row map in `raw_data`. The validator still receives original rows; grouping/idempotency never uses the nullable staging projection. No ID is truncated or converted into a different identity. Existing `trim()` and 150 UTF-16-unit validation remains unchanged.

A second actual MySQL RED test found that a 70,000-character ID still failed because raw_data TEXT could not hold the original JSON. The supported file limit is5 MiB, so changing only the searchable projection could not meet the raw-preservation requirement. Additive **V12__f04_preserve_large_raw_import_rows.sql** widens raw_data to LONGTEXT. LONGTEXT accounts for JSON escaping expansion; silently cutting raw evidence or accepting a fake shortened ID would violate the task. Existing V1–V11 are unchanged. No new table, key or transaction-history record is introduced.

The V11→V12 MySQL test retains original raw JSON exactly, checks previous Flyway checksum rows remain identical and verifies no transaction/inventory records are fabricated. H2 and MySQL tests now accept the independent valid order, reject the invalid one and preserve both source rows.

## Unchanged import semantics

- 201 with ImportSummary for a structurally valid parsed file; row/order errors are exposed through the existing errors endpoint.
- Atomicity remains per order. A business-invalid order is fully rejected while independent valid orders may succeed.
- Store+CSV_UPLOAD+normalized order ID remains the identity. Equal canonical content is duplicate; changed content conflicts without rewriting history.
- A real database persistence failure still yields a FAILED batch, with the affected transaction/items rolled back. Previously committed independent orders retain the existing behavior; no false successful summary is returned.
- Store is derived from F01; role checks and cross-Store 404 remain. No inventory changes, F03 score changes or fabricated history.

## Actual regression evidence

[REGRESSION_RESULTS.json](../../testing/evidence/f04_csv_correctness/REGRESSION_RESULTS.json): **39 PASS, 0 FAIL, 0 ERROR, 0 SKIP** on Java 17, H2 MySQL mode and newly created schemas in the dedicated disposable MySQL 8.4 instance.

| Suite | PASS | FAIL | ERROR | SKIP |
|---|---:|---:|---:|---:|
| TransactionImportCorrectnessIntegrationTest |16|0|0|0|
| TransactionImportCorrectnessMySqlIntegrationTest |16|0|0|0|
| TransactionOrderIdentifierTest |6|0|0|0|
| TransactionRawEvidenceMigrationMySqlTest |1|0|0|0|

Coverage includes ASCII150/151, padded IDs, BMP/supplementary Unicode with existing UTF-16 semantics, null/empty/blank values, repeated invalid-ID rows, multiple valid/invalid orders, mixed-line atomicity, duplicate/reimport/conflict, a deterministic two-thread identity race, injected failure after real item inserts,401/403/cross-Store 404/list isolation and unchanged inventory/history. The 70KB case proves the additional migration requirement and correction. CI selectors include the new correctness/unit/MySQL migration suites.

The initial full-run environment failure is retained in [INITIAL_FULL_ATTEMPT.json](../../testing/evidence/f04_csv_correctness/INITIAL_FULL_ATTEMPT.json):243 PASS, 0 FAIL, 123 ERROR, 5 SKIP. Cause: the disposable server's 60-connection limit was exhausted while two full suites retained Spring pools. Only test-instance capacity was increased, with test-only Hikari pool3/min-idle1 used on retest. This was not a production change or a business-test PASS.

[Full Maven verification](../../testing/evidence/f04_csv_correctness/FULL_VERIFICATION_RESULTS.json) completed successfully: **370 total = 365 PASS, 0 FAIL, 0 ERROR, 5 SKIP**. It covers F01–F06, Product/Category H2 and fresh-schema MySQL, fresh V1→V12, supported populated V5 B+C recovery, mixed JPA/JDBC rollback and the actual Java17 Backend→Java25 SPMF process tests. The separately gated five PilotClone API tests need a configured approved clone and must not be counted as executed; Product/Category new-schema integration and supported historical recovery are exercised independently.

## Performance observations

[Measured import fixtures](../../testing/evidence/f04_csv_correctness/F04_PERFORMANCE.json) and [EXPLAIN plans](../../testing/evidence/f04_csv_correctness/F04_QUERY_PLANS.json) are synthetic local measurements. The largest fixture contains 10,000 rows and5,158,959 bytes, close to the default5 MiB upload limit. It completed in6,524.475ms, with measured request-thread CPU1,572.856ms and sampled incremental heap 91,988,480bytes. These are one-client service measurements, not production throughput/RSS/SLA guarantees.

The run performed 10,000 per-order transactions and 10,004 SELECT executions:10,000 identity lookups plus one Product lookup, one summary read and two authorized-user reads. Existing unique indexes were selected with const access; no new index was justified. Row-state writes and per-order commits remain intentional correctness costs. No F04 performance optimization is claimed. The combined F04/F06 performance report is delivered on the independent F06 hardening branch.

The benchmark is an opt-in harness named `F04ImportPerformanceMySqlBenchmark`; default CI discovery does not add a skipped performance test. Run explicitly with `-Dtest=F04ImportPerformanceMySqlBenchmark -Dpaspm.test.performance=true` and the isolated MySQL test environment.

## Excel implementation and approved contract

The user approved the Excel format on 2026-10-26. `POST /api/transactions/imports/excel` is implemented for STORE_ADMIN multipart uploads and returns the existing 201 ImportSummary. XLS/XLSX normalize to the same ParsedCsvRow model and enter the existing validator, staging, idempotency and independent order transactions. The CSV endpoint and existing history/error reads are preserved.

Exactly one nonempty worksheet is accepted; extra blank sheets are allowed. Header row 1 uses the exact seven CSV columns. Order ID/SKU/occurred_at are text, leading zeros are retained and timestamps require an explicit offset. Both formats deliberately retain source_system=CSV_UPLOAD as the same compatibility namespace. CSV→Excel and Excel→CSV equal-content uploads are duplicates; changed content conflicts without relabeling or rewriting history.

[Approved Excel contract and actual XLS/XLSX templates](F04_EXCEL_FORMAT_CONTRACT.md) describe structural 400 versus per-order 201 errors, allowed cell types, limits and unsupported advanced workbook content. The actual templates are exercised through both real HTTP handlers in regression fixtures.

POI 6.0.0 uses bounded file-backed XSSF/SAX and HSSF event parsing with BIFF preflight; no full user-model workbook is constructed in production. Independent review found and corrected large numeric-exponent expansion, blank-row counting bypasses, non-cell formula carriers and malformed XML root/namespace handling. The parser now has 44 passing cases. Formulas/conditional formatting/data validation, external links, macros/embedded/encrypted content and unsupported parts are rejected. Limits reduce risk; in-process parsing is not a process sandbox or complete malicious-document CPU/heap guarantee.

The shared money validator also materialized a huge exponent before checking DECIMAL12,2. A 32 MiB child-JVM regression reproduced an OutOfMemoryError in the old code while the parent remained safe. Normalizing trailing zeros and checking scale/sign/integer precision before setScale now rejects hostile values without expansion. Eleven money tests preserve valid scientific/trailing-zero behavior and invalid-range errors; endpoint tests verify invalid money remains an order error while a valid peer succeeds.

[Excel regression evidence](../../testing/evidence/f04_excel_import/REGRESSION_RESULTS.json) records the initial 105 PASS focused execution (including one parser benchmark), subsequent safety RED/GREEN results and final full-suite verification. [Final full Maven results](../../testing/evidence/f04_excel_import/FULL_VERIFICATION_RESULTS.json): **477 total = 472 PASS, 0 FAIL, 0 ERROR, 5 SKIP**, including 107 Excel/API/money cases (44 parser, 52 API, 11 money). They cover Java 17, H2,dedicated MySQL 8.4, local SPMF, Product/Category and fresh/legacy migration scenarios. Five separately gated clone tests remain SKIP, not real-data acceptance. An intermediate MySQL connection-refused run is retained separately; it did not fall back to the original database.

[Excel parser measurements](../../testing/evidence/f04_excel_import/PARSER_PERFORMANCE.json) measure 100/1000/10000 rows in both formats, with fixture creation excluded and sampled heap/current-thread CPU. They exclude HTTP, business validation and database persistence and are not comparable to end-to-end import throughput. Production concurrency and p95/p99 remain NOT_MEASURED.

The actual runtime OpenAPI export has 60 paths/74 operations. Every previous 59 path contract and component schema remains semantically identical; only the Excel route and export server metadata are added/updated, with existing key ordering retained. Interactive browser Swagger verification is NOT_RUN; actual handlers/OpenAPI were executed via Spring Boot MockMvc.

The dependency assessment checked official POI Java 17/license/security guidance and actual dependency tree. POI 6.0.0 is pinned; Commons IO 2.22.0 is pinned to avoid Commons CSV's nearer 2.20.0 downgrading POI's declared IO dependency. Spring Boot continues to manage Log4j. Executed compatibility tests do not constitute a complete dependency vulnerability audit. Excel support does not require external API/POS integration, public SPMF hosting, inventory mutation or new mining logic.

## Verification commands and delivery

Focused reproducer/correctness commands use Maven Java 17 with explicit H2 or disposable MySQL settings, e.g.:

```sh
mvn --batch-mode --no-transfer-progress test -Dtest=TransactionImportCorrectnessIntegrationTest,TransactionImportCorrectnessMySqlIntegrationTest,TransactionOrderIdentifierTest,TransactionRawEvidenceMigrationMySqlTest -Dpaspm.test.mysql=true
mvn --batch-mode --no-transfer-progress test -Dtest=F04ImportPerformanceMySqlBenchmark -Dpaspm.test.performance=true
```

The full verification uses `mvn clean verify`, MySQL and Java 17 Backend→Java 25 local SPMF opt-ins and bounded test pools. The approved SPMF SHA-256 is `4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a`; actual banner 2.67a was checked. No public hosting is needed for these local executions. No source database was connected to, no artifact was published, and no merge/deployment is part of delivery.

API consumers should use [F04 contract](../../api/F04_TRANSACTION_API.md) and [handoff](../../frontend_handoff/F04_HANDOFF.md). Source fields/DTOs and the six routes are preserved. V12 requires a normal reviewed migration rollout; downgrade is not automatic because larger raw evidence might no longer fit TEXT. Do not alter an applied migration or silently narrow the column on rollback.

CodeGraph was queried before analysis and initialized/refreshed for this isolated implementation: 169 files, 3,923 nodes, 9,879 edges; `insertStagingRows` is discoverable at the updated source location. [Graph execution metadata](../../testing/evidence/f04_csv_correctness/CODEGRAPH.json) contains no index database or credentials.

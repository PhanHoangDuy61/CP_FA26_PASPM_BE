# F04 Excel Import Implementation Plan

> **For agentic workers:** Execute with Superpowers TDD and verification-before-completion; the user explicitly approved the Excel format contract on 2026-10-26 and authorized implementation.

**Goal:** Add XLSX/XLS file imports through the same F04 order validation, Store isolation and persistence pipeline as CSV.

**Architecture:** Keep the six CSV/history routes and CSV_UPLOAD identity. Add one multipart Store-Admin Excel route. Bounded XLSX SAX and XLS BIFF event readers normalize cells into the existing ParsedCsvRow model; shared validator/persistence retain atomicity, idempotency and history. Advanced active workbook content is rejected, never evaluated.

**Stack:** Java 17, Spring Boot 3.5.7, POI 6.0.0, existing JPA/JDBC/Flyway and shared import pipeline.

## Approved business contract

- Exactly one nonempty worksheet; any additional sheets must be empty.
- Row1 has the exact seven CSV headers in the approved order.
- source_order_id, sku and occurred_at are text cells; preserve zeros and text; occurred_at has an explicit ISO8601 UTC offset, not native dates/serial numbers.
- Shared normalized identity across CSV and Excel: Store + existing CSV_UPLOAD + normalized order ID. No relabel/rewrite of historical transactions.
- Same canonical content is duplicate; changed content is conflict. No inventory side effects.
- External API Connection, POS, webhooks and realtime ingestion are excluded.

## Tasks and evidence

- [x] Review current contracts, separate audit branch and official POI Java 17/license/security guidance. User approved previously missing policy before implementation.
- [x] Create the Excel branch from the tested CSV/V12 branch; PR2 base must be PR1 until PR1 merges.
- [x] Parser TDD: initial23RED then23GREEN; independent safety review adds exponent/formula/row-cap/XML/BIFF regressions with actualRED and41GREEN.
- [x] API TDD: valid XLSX upload expected201, actual405 before adding route. Add importExcel and a private common importFile in TransactionImportService; controllers retain StoreAdmin rules and201ImportSummary.
- [x] Add H2/MySQL API tests for XLS/XLSX, partial order acceptance, CSV→Excel and Excel→CSV idempotency/conflict, leading zeros, timestamps/type validation, worksheets, bounds,401/403/404, rollback and inventory preservation.
- [x] Reproduce shared-money exponent allocation in an isolated32MiB child JVM; guard DECIMAL12,2 range before scale expansion while retaining valid scientific/trailing-zero acceptance.
- [x] Re-run focused tests after safety fixes; include current templates and runtime OpenAPI export.
- [x] Run full Java 17/MySQL/SPMF regression, fresh/legacy migration tests and Product/Category checks; distinguish authorized-clone skips.
- [x] Measure Excel parser fixtures using actual XLS/XLSX, record local bounds and remaining containment risks.
- [x] Update API/handoff/changelog/current reports with actual route, limits, templates and results. Preserve historical artifacts and immutable V1–V11.
- [ ] Refresh CodeGraph, review diff, commit/push only after checks. Create stacked draft PR or provide manual URL on403. Never auto-merge/deploy.

## Technical limits and review requirements

Defaults: file5MiB, data10000rows, expanded32MiB total/8MiB per ZIPentry,128entries,32767UTF-16units per cell,100000shared strings,1024styles,100000scanned rows. Bound actual read bytes, not ZIPmetadata alone. Reject duplicate ZIPparts, unsafe compression, external XML/entities/relationships, macros/embedded/encrypted content, formulas/CF/DV, invalid roots/namespaces and unsupported advanced workbook parts. No per-request global POI limit mutation. In-process parsing retains residual hostile-document CPU/heap risk; limits are not a production sandbox guarantee.

# F04 CSV Correctness Implementation Plan

> **For agentic workers:** Execute this scoped plan task-by-task using Superpowers TDD and verification-before-completion. The user has authorized implementation and isolated regression verification.

**Goal:** A parsed invalid source order ID must not prevent unrelated valid orders from importing.

**Architecture:** Retain the existing parser → raw staging → validator → independent order transaction pipeline. The nullable staging `source_order_id` is a lookup projection; `raw_data` is the original row-value evidence. A value outside the existing normalized 150 UTF-16-unit validator limit is represented by NULL in the projection, never shortened or used as a different order identity.

**Tech stack:** Java 17, Spring Boot 3.5.7, existing Hybrid JPA/JDBC, Commons CSV, H2 and a dedicated MySQL 8.4 test instance.

## Global constraints

- Existing Flyway V1–V11 and historical source data remain immutable.
- Do not access the configured source database; tests use explicit H2 or newly created schemas on a disposable MySQL instance.
- Persist raw parsed field values exactly in JSON; normalization never rewrites raw evidence.
- Keep HTTP201 ImportSummary, per-order atomicity, Store authorization, CSV_UPLOAD identity and existing content hashes.
- A fatal database failure remains a failed batch; prior independent committed orders may survive under existing semantics.
- F04 scope is file-based CSV/Excel; external API/POS/webhook/polling is excluded, not a completeness requirement.
- Excel support is a separate stacked branch; Excel policy approval is recorded in the subsequent user instruction; implement it on the stacked Excel branch.

## Task 1 — Reproduce the defect

Files: new `src/test/java/vn/edu/fpt/paspm/transaction/TransactionImportCorrectnessIntegrationTest.java` and MySQL subclass.

- [x] Inspect parser/service/repository/validator and V7 schema.
- [x] Upload one valid order and a different151-unit order, assert HTTP201, one accepted and one rejected order, exact raw ID and no fabricated transaction. Execute on H2 and MySQL before the fix and retain actual failure evidence.

## Task 2 — Correct staging projection

File: `src/main/java/vn/edu/fpt/paspm/transaction/importing/TransactionImportRepository.java`.

- [x] Normalize a local projection using existing `trim()` semantics. Bind NULL for missing/empty/>150 values, otherwise the complete normalized ID. Continue serializing `row.values()` unchanged to raw_data.
- [x] Run the reproducer again on both databases; expect201 and correct counters/evidence. No migration is needed for the151-unit staging-column defect because the column is already nullable.

## Task 3 — Boundary, transaction and authorization regression

- [x] Test150/151, padded/BMP/supplementary Unicode, empty/null identifiers, mixed multiple orders, same-order invalid line, duplicate/conflicting/repeated uploads.
- [x] Test concurrent same-order imports with unique-key arbitration and injected item-write failure proving affected order rollback, failed batch and retained previous independent commits.
- [x] Verify401/403, cross-Store404/list isolation, forged Store parameters, immutable inventory and historical transaction facts.
- [x] Select new suites in existing CI test groups without weakening existing gates.

## Task 4 — Measurements and delivery

- [x] Measure bounded import fixtures, SQL statements, transaction behavior and representative SELECT plans with EXPLAIN ANALYZE on disposable MySQL. Record observed values only; add no index without evidence.
- [x] Run focused suites, Product/Category and workflow regression, fresh/legacy Flyway checks and full verification where available with local verified SPMF.
- [x] Update current F04 API/handoff/index scope and F04_IMPROVEMENT_REPORT; keep historical reports frozen.
- [x] Verify only scoped files changed, V1–V11 hashes unchanged and local stash/dev preserved. Commit/push only after relevant checks pass; attempt PR to dev without merging.

## Verified additional raw-capacity correction

A 70 KB identifier reproduced a second MySQL 409 because raw_data TEXT could not preserve a permitted raw row. Additive V12 widens raw_data to LONGTEXT; TransactionRawEvidenceMigrationMySqlTest targets V11 then 12 and verifies original bytes/checksum rows/no fabricated history. Count/version assertions for current-latest migration tests move to 12 without editing V1–V11. Benchmarks use the opt-in F04ImportPerformanceMySqlBenchmark class; default test discovery stays free of a new performance SKIP. Excel policy has now been explicitly approved; no Excel implementation step is claimed complete by this CSV plan.

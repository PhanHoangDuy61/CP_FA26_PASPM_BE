# Approved Product Metadata JPA Pilot Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development for the focused Product implementation and independent review; execute database safety and release verification in the coordinating session.

**Goal:** Implement approved MIG-JPA-CORE-00 + MIG-JPA-F02-01 while preserving all APIs and non-pilot JDBC semantics.

**Architecture:** Existing ProductRepository remains the JDBC query facade. Internal scoped JPA commands persist only F02 Product metadata using the same DataSource/transaction manager as JDBC. Flyway owns DDL; validate mappings and disable Open Session in View.

**Tech Stack:** Java17, Spring Boot3.5.7 BOM-managed JPA, existing MySQL/Flyway/Security.

## Global constraints

- Approval is granted by the task attachment dda164a7-faa6-46c5-b8ba-0a3a1b0e9142; no further approval for this scope.
- Preserve dirty application.yml and all unrelated untracked work; no secrets in commits/evidence.
- Dedicated branch: Khoi/Fear/jpa-product-metadata-pilot; do not merge/push as part of this task.
- No DDL/mutations/tests on source database. Source probe is SELECT-only, connection readOnly=true.
- Source V5, catalog empty, missing V8 expiry_push_days. User explicitly approved simulated food-store alternative after probe. Real existing-record checks NOT RUN; never relabel synthetic fixtures as real data.
- No V1–V10 edits, no @Version, no mapped S/M, no Inventory relationship/cascade or stock field, no other module migration.

## Task 1 — Baseline / source safety (coordinator)

- [x] Read audit, roadmap, matrix, baseline evidence and source/CodeGraph dependencies.
- [x] Record current commit/status/config+DDL hashes; create dedicated branch.
- [x] Run full original Java17/MySQL/SPMF baseline; preserve original test reports/log.
- [x] Capture baseline OpenAPI using isolated H2 runtime, no source startup.
- [x] Probe source read-only; report V5/empty data and ask for actual dataset path while continuing independent work.
- [x] Provision explicit disposable isolated copy with dedicated credential and safety marker; reject source target or absent mutation switch. If original data is empty, label copy EMPTY and real-record tests BLOCKED.

## Task 2 — Product implementation / TDD (implementer)

Files: pom.xml; safe persistence configuration/properties; catalog/product internal ProductEntity, scoped repository and command adapter; existing ProductRepository; focused integration tests.

- [x] Write focused integration test asserting shared JPA transaction manager/mapping; run RED before implementation.
- [x] Add BOM starter JPA without Boot upgrade; ddl-auto validate and OSIV false outside dirty private config.
- [x] Map Product metadata only with exact existing types/IDENTITY/scalar scope/category IDs.
- [x] Delegate only create/update/status to scoped JPA writes; flush before JDBC readback, retain read SQL, prevent stale metadata overwrite and score writes.
- [x] Run GREEN targeted H2 and real MySQL fixture tests for shared connection, commit/rollback, refresh and REQUIRES_NEW.

## Task 3 — Disposable copy / API verification (coordinator)

Files: src/test/java/vn/edu/fpt/paspm/persistence/ safety and dataset verification classes; docs/testing/JPA_PILOT_REAL_DATA_VERIFICATION.md.

- [x] Copy authorized non-PII catalog rows into isolated schema; preserve identifiers/quantities/score decimals and scope. Do not copy auth secrets/challenges/external identities.
- [x] Characterize source-schema mapping failure if V5, migrate isolated copy only to V10; verify no source changes.
- [x] Actual dataset gates: mapping, metadata projection parity, API writes/errors/RBAC, S/M preservation, inventory invariants, transaction semantics. Mark nonexistent preexisting-record cases BLOCKED.
- [x] Use synthetic fixtures for remaining API/race/negative checks on disposable target only; distinguish from original rows.

## Task 4 — Review / release evidence (coordinator + fresh reviewer)

- [x] Targeted tests → full Java17/MySQL/SPMF verify; compare original 232 test results and new cases.
- [x] Capture/compare runtime OpenAPI paths/components and authorized contract behavior.
- [x] Verify source checksum/counts, local configuration hash and all original migration hashes unchanged.
- [x] Independent spec/security/code-quality review; fix verified defects within scope and rerun relevant tests.
- [x] Write CORE-00, F02-01 implementation reports, real-data report with exact PASS/FAIL/BLOCKED/NOT RUN and logs.
- [x] Keep branch unmerged if real-data gate remains blocked; document code rollback with no DDL rollback.

## Execution outcome

2026-10-22: approved simulated-data scope completed,274tests PASS, original232 preserved, OpenAPI59paths and9Product contract cases equal. Source stillV5/read-only/unchanged. No merge/push/deployment; no other module migration. See3implementation/verificationreports for logs and actual scope limitations.

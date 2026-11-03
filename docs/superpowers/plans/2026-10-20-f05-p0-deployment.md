# F05 P0 Deployment Fix Implementation Plan

> For agentic workers: execute inline using Superpowers executing-plans, with focused tests before production changes and independent code review before completion.

**Goal:** Repair fresh MySQL migration and validate/provision the separate SPMF child runtime while preserving Java 17 backend compatibility.

**Architecture:** Quote the existing row_number identifier in historical V7 and every F04 JDBC statement. Historical V7 checksum reconciliation is an explicit operator procedure, never automatic application behavior. Keep the external ProcessBuilder runner and add bounded cached dependency preflight before admission and execution.

**Tech stack:** Java 17 target, Spring Boot 3.5.7, JDBC, current Flyway, MySQL 8.4, H2, external pinned SPMF 2.67a/Java 25.

## Constraints

- Preserve application.yml local changes, untracked user documents and existing snapshots/data.
- No repair on live/unknown databases; tests create dedicated paspm_p0_test_* schemas only.
- No algorithm, scaling, threshold, Product weight, F06, UI, or resource-limit changes.
- No download/install of dependencies without approval; separately verify Java 17 only if available/approved.

## Task 1: Reproduce and repair MySQL syntax

- [x] Add opt-in MySQL test database helper and real F04/F05 integration subclasses.
- [x] Run MySQL F04 startup before patch; capture failed V7 SQL/error/history.
- [x] Quote row_number in V7, JDBC statements and relevant SQL test queries without renaming the column.
- [x] Run fresh MySQL V1–V9 plus actual F04 and F05 integration suites.
- [x] Test existing-schema upgrade with data and genuine old-checksum validation/controlled repair on isolated H2; document MySQL limitations explicitly.

Files: V7__f04_transaction_import_pipeline.sql; TransactionImportRepository.java; TransactionImportIntegrationTest.java; deployment/MySqlTestDatabase.java; transaction/TransactionImportMySqlIntegrationTest.java; mining/MiningMySqlIntegrationTest.java; deployment/FlywayRecoveryTest.java.

## Task 2: SPMF preflight

- [x] Add red tests for diagnostic codes, wrong Java major, missing/changed JAR/executable, entrypoint failure and cleanup.
- [x] Require explicit independent Java path using existing Spring property/env mapping; retain JAR pin.
- [x] Add SpmfRuntime preflight: readable absolute JAR/hash, executable/version, bounded SPMF version entrypoint, usable temp directory. Cache successful expensive probes by content fingerprint and short lifetime; recompute file fingerprints before reuse.
- [x] Keep existing runner arguments/parser/numerical behavior, limits and failure persistence. Dependency errors return 503 using existing error envelope with sanitized code/message.
- [x] Verify both real algorithms and Java 17 backend + Java 25 child if runtime available.

Files: mining/SpmfRuntime.java; mining/SpmfMiningEngine.java; focused runtime tests and existing runner tests.

## Task 3: Verification, setup and review

- [x] Run Maven verify, real MySQL suites, runtime failures, OpenAPI and Store isolation checks.
- [x] Create docs/setup/FLYWAY_MYSQL_V7_RECOVERY.md and docs/setup/F05_SPMF_RUNTIME_SETUP.md with portable Windows/macOS/Linux configuration and operator preflight/rollback.
- [x] Create docs/audits/F05_P0_DEPLOYMENT_FIX_REPORT.md with actual evidence, checksum changes, blocked/not-run items and changed files.
- [x] Request an independent review using Superpowers requesting-code-review; resolve material findings and inspect final diff.
- [x] No live DB reconciliation, commit or push without a later request.

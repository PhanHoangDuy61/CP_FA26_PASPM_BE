# F05 Flyway Renumbering (V12–V16 → V13–V17) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox syntax.

**Goal:** Remove the Flyway version collision between `origin/dev` `V12__f04_preserve_large_raw_import_rows.sql` and this branch's F05 migrations by renumbering F05 to V13–V17 without changing SQL content.

**Architecture:** File renames only (descending order to avoid clobbering) plus test/doc reference updates. A DB-free JUnit test pins the version↔name mapping. No application code references migration numbers (verified by grep of `src/main/java`).

**Tech Stack:** Flyway, JUnit 5, MySQL 8.4 (opt-in tests), Maven.

**Spec:** user approval 2026-11-01 (conditions: dev has no V13–V17; F05 migrations never applied to shared DBs; V12 F04 → V13–V17 order safe).

## Global Constraints

- Never edit an applied Flyway migration; SQL bodies stay byte-identical.
- No `flyway repair`; no commit/push/merge without separate instruction.
- Frozen evidence JSON/reports are not rewritten; add a dated note instead.

## Review Focus

- Local dev DB that already applied old V12–V16 will fail validation after rename → must be recreated (disposable local data only).
- A tree that merges dev must have exactly one V12 (F04) and F05 at 13–17.
- Tests that count executed migrations or assert the latest version must use 17.

### Task 1: Pin the mapping (RED)
**Files:** Create `src/test/java/vn/edu/fpt/paspm/deployment/MigrationVersioningTest.java`
- [ ] Test `f05MiningMigrationsOccupyV13ToV17InOrder`, `noFlywayVersionIsUsedTwice`, `f04V12IsNotTakenByMining`.
- [ ] Run: expect FAIL (mining files are still V12–V16).

### Task 2: Rename (GREEN)
**Files:** `src/main/resources/db/migration/V16→V17, V15→V16, V14→V15, V13→V14, V12→V13` (same suffix names).
- [ ] Rename in descending order; verify SHA-256 of each body unchanged.

### Task 3: Update dependent tests
**Files:** `MiningV12MigrationMySqlTest` (→ `MiningMigrationMySqlTest`, targets +1), `JpaPilotMigrationMySqlTest`, `FlywayRecoveryTest`, `RecommendationMigrationTest` (values for the merged V1–V17 history).

### Task 4: Update current docs; add dated note to implementation report
**Files:** F05 README, F05_WORKER_DISPATCH, setup guides, root README mentions of V12–V16.

### Task 5: Verify
- [ ] Re-run Task 1 tests (GREEN); static ordering check on merged-tree simulation; MySQL fresh + upgrade runs where the environment allows; otherwise mark NOT RUN.

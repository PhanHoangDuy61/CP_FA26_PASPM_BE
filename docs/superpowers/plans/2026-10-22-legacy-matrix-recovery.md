# Historical Utility Matrix B+C Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development task by task; independent review and verification before completion.

**Goal:** Upgrade populated V5 safely without changing V1–V10 or inventing sales, preserve historical reads/mining and reject unverifiable F06 association evidence.
**Architecture:** Flyway-registered pre-V7 callback performs explicit opt-in verified archive transition with durable checkpoints. Additive V11 prepares common archive/registry schema on all installations. Store-scoped repository routing preserves public DTOs; historical matrices remain source-local evidence.
**Tech stack:** Java 17, Spring Boot 3.5.7, Flyway 11.7.2, MySQL 8.4, existing JdbcTemplate and H2 tests.

## Global constraints
- Keep original configured V5 database read-only; all writes in newly created isolated MySQL schemas.
- Do not modify already-applied V1–V10 scripts; no fake sales, historical renumbering, snapshot recalculation or silent loss.
- Branch from dev; no Product Pilot source copied, merge, deploy or Category migration.
- Preserve frozen source/matrix hashes, identity, fields, timestamps and Store isolation.
- Require verified target-bound backup/restore fingerprints before populated transition; reject partial V7 and inconsistent checkpoints.
- Current reads/writes remain supported. Legacy utility alone cannot authorize F06 support/confidence evidence.
- No secrets or raw backups in Git; full opt-in regressions and actual engines required; independent review/CI before merge.

## Task 1: Registered callback, immutable archive, preflight, additive schema
Files: new deployment/legacy recovery classes, db/migration/V11 Java or SQL migration, deployment integration/unit tests. Interfaces: archive `utility_matrix_item_legacy_v5`, registry `utility_snapshot_storage(snapshot_id,store_id,storage_kind,source_namespace)` with `LEGACY_V5_ARCHIVE` or `F04_LIVE`; journal stores original canonical fingerprints, manifest binding and phase.
- [x] Write failing test for actual registered Spring Flyway path upgrading V5 populated; missing opt-in/manifest refuses without DDL.
- [x] Implement canonical ordered V5 matrix/header fingerprints and integrity validation (all ownership/parents, totals, unique keys, positive IDs, hashes); backup manifest must bind MySQL server UUID/schema and exact dataset, backup file SHA, restored-copy equivalence and owner approval reference. Read manifest from private external path; fail closed on mismatch.
- [x] Implement registered `beforeEachMigrate` callback for exact V7. Validate clean version/checksums and absence F04 partial objects, then initialize durable journal, stage empty live table, restore original FKs, atomically rename, compare archive and complete registry/immutability. Resume known states, refuse unknown or mixed rows. Use MySQL DDL checkpoints, never transaction rollback as recovery.
- [x] Add V11 for empty archive/registry on fresh/modern installations with equivalent V5 fields, FKs and archive guards (MySQL triggers; H2 supported path), no old script edits. Strengthen modern Store integrity only where validated and portable; do not silently reinterpret existing unknown sources.
- [x] Test fresh, V5 empty/populated, multi-Store IDs, guard rejection/tampering/orphans/partial V7/checkpoints, DBA trigger permissions, applied V10 no-op. Run tests red then green and report output.
- [x] Commit scoped core + tests after test success. Do not stage unrelated work.

## Task 2: Historical reader compatibility and evidence gates
Files: UtilitySnapshotRepository, MiningService, FrozenEvidenceReader; focused utility/mining/recommendation tests.
- [x] Write failing tests demonstrating legacy pages/counts Store isolation and mining capture; F06 rejects unverified legacy membership.
- [x] Route by registry with fixed allowlisted table names, no UNION. Legacy projection uses NULL expiry_evidence, counts/page same order/totals as original. Verify route exclusivity/completeness and reject attempts to saveItems into archived snapshots.
- [x] Replace MiningService live-only ownership SQL with route-aware validation; require snapshot/Product Store integrity for legacy, actual sales ownership for modern. Preserve frozen mining input semantics and thresholds.
- [x] Gate F06 frozen evidence for legacy/unverified sources with 409 before support/confidence generation; no invented membership. Modern behavior unchanged.
- [x] Execute focused tests and commit.

## Task 3: Isolated acceptance, regression and Product compatibility
Files: new MySQL integration tests/evidence and test fixtures; source DB read-only evidence.
- [x] Initialize separate MySQL 8.4 server with private credential files and schema-restricted account. Source fingerprint before/after SELECT-only. No writes or startup against source.
- [x] Execute all eight requested scenario groups. Actual pre/post HUI-Miner + EFIM compare Dataset bytes, mapping, scale, thresholds and exact pattern maps, not counts alone.
- [x] Run Java17 Maven verify with MySQL/runtime opt-ins. Separately integrate patch on temporary Product Pilot checkout to run Product JPA compatibility tests without merging branches or copying Pilot source into this feature.
- [x] Review test failures systematically; rerun covering tests after each fix. Record exact totals and skips; do not count characterized failures as migration acceptance.
- [x] Stop isolated server/remove temporary plaintext credentials after verification.

## Task 4: Documentation, review and delivery
Files: approved recovery design/test plan, JPA verification/readiness, LEGACY_UTILITY_MATRIX_COMPATIBILITY.md, sanitized evidence.
- [x] Document actual final schema, opt-in/backup CLI/setup, migration order and crash/partial-DDL restore instructions, F06 limitations, actual results/remaining risks.
- [x] Independent security/correctness review of complete branch, fix important findings and rerun covering tests.
- [x] Verify old migration SHA, source immutability, diff/secrets; commit scoped deliverables; push feature branch and create PR into dev or provide manual compare link on 403. No merge.

## Execution record (2026-10-23)

Core0e4adbb +review fixes7bf89e1; reader7e8503d +empty-read correction2553e54; actual failed-V7 recovery acceptance9c19f2a. Final Maven verify:254 feature and299 detached Pilot compatibility PASS, zero failures/errors/skips. Source remains read-onlyV5/unchanged. Production deployment/real backfill/merge/Category are not authorized. Controlled crash checkpoints, not every OS-kill boundary, executed; operational limits remain documented. Code review approved for review; documentation committed, isolated server stopped, temporary credentials removed, and branch pushed. Actual GitHub PR creation returned 403; manual compare link and ready-to-use description provided. No merge or deployment.

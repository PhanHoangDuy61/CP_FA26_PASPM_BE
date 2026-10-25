# Hybrid Product integration and dependent Category JPA plan

> Execute inline with Superpowers executing-plans and independent technical review. User explicitly authorizes implementation now without waiting for external SPMF hosting/human review; PR merge remains prohibited.

**Goal:** Reuse the implemented Product metadata Pilot on current dev, verify its recovery compatibility, then migrate existing Category CRUD on a separate dependent branch.
**Architecture:** One Boot-managed JpaTransactionManager/shared datasource; JPA metadata CRUD with scoped queries/flush-refresh bridge; JDBC retains Product catalog/inventory, analytical and F03–F06 paths. Flyway exclusively owns schema; no V1–V11 changes or extra migration without demonstrated need.
**Stack:** Java17, SpringBoot3.5.7, MySQL8.4 disposable schemas, separate Java25/exact approved SPMF2.67a.

## Global constraints
- Original Product branch12d348e and its dirty user checkout remain untouched. Use existing isolated linked worktree.
- Current dev783725c; old Pilot is three commits ahead/twelve behind. All three commits concern Pilot implementation/reports; no unrelated feature commits identified.
- Recovery1 already merged; CI3 remains open/unmerged with SPMF CI and human review pending. Do not change PR3 or integrate its unmerged work merely to make CI green.
- Latest owner policy permits development/Product local verification/Category dependent development before merges. No merge, deploy, source database write/repair/reset or third-party binary publish.
- Synthetic fixtures only; REAL_DATA_BLOCKED stays unresolved. Commit only sanitized result counts/fingerprints, never raw reports/dumps/options/secrets.

## Task1 — Integrate and verify Product
Files: existing ProductEntity/ProductJpaRepository/ProductMetadataPersistence, ProductRepository, PersistenceConfiguration, persistence.properties, existing ProductPersistence* and JpaPilotMigrationMySqlTest; current readiness/recovery reports.
- [x] Verify dev baseline Catalog API tests under Java17; record exact result.
- [x] Create integration branch from dev, merge the three verified Pilot commits preserving recovery code. Resolve conflicting readiness/migration docs in favor of current recovery facts, then update current integration results.
- [x] Execute existing Product JPA/H2/MySQL transaction tests; adapt obsolete V10-only assertions to V11 without weakening constraints. Preserve unsupported-original-V7 negative characterization; supported registered callback positive recovery must also run.
- [x] Reuse/add missing tests for scoped writes, Store immutability, S/M and inventory integrity, JDBC/JPA shared commit/rollback and stale context behavior.
- [x] Run full Maven clean verify with real local SPMF, all required MySQL/Java17/Pilot opt-ins and zero unexpected skipped tests; review verified issues and fix narrowly.
- [x] Publish Product branch/PR package after documented local acceptance. Never merge.

## Task2 — Category implementation after Product local acceptance
Files: catalog/category/persistence/CategoryEntity.java, CategoryJpaRepository.java, CategoryPersistence.java; existing CategoryRepository; Category persistence/MySQL/API tests.
- [x] Inspect schema/contracts. Existing Category API has list/detail/create/update/status; no delete endpoint and no uniqueness constraint inferred from names.
- [x] Write meaningful failing tests for scoped CRUD/status, JDBC/JPA mixed rollback/staleness, referenced Product preservation/no cascade, duplicate/invalid behavior matching actual schema.
- [x] Implement package-local entity with immutable Store column and no cascade relationship; scoped Spring Data repository plus transaction/DTO bridge. Keep service/controller/DTO behavior and Product JDBC queries intact.
- [x] Run targeted MySQL tests, existing Product tests and full regression/Flyway/replay; fix actual regressions.
- [x] Create dependent Category branch from verified Product head, commit Category separately, push and prepare Category PR targeting Product branch until authorized retarget after merge.

## Task3 — Final review/delivery
- [x] Independent agent review transaction safety, ownership, cache staleness, schema/migration/replay, F04 and API invariants. Record agent findings separately from human GitHub approval.
- [x] Update Hybrid architecture decision summary, both PR bodies, actual counts/commands, branch/head/URLs and CI/reviewer blockers.
- [x] Verify branch ancestry, remote heads, clean scoped checkout and original checkout/config/migration preservation. Stop at review-ready branches; no PR merges.


Final local acceptance: Product302 PASS; dependent Category full330 PASS; zero failures/errors/skips. Product pushed202761a; PR App403/manual creation prepared. Category push/PR outcome recorded in HYBRID_JPA_DELIVERY_STATUS.md. Optional review suggestions are documented, not observed failures. External remote/human/real-data gates remain unmet and no merge is authorized.

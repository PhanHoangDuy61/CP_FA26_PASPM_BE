# Backend Retail Catalog and current F03 Product priorities Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Let Merchandisers read their Store Catalog and return current Backend-calculated E/W through existing Product score APIs.

**Architecture:** Method-level read permissions override StoreAdmin-only Catalog controller defaults. ProductScoreService composes the current scoped Product, saved configuration and shared ExpiryScoreCalculator/UtilityMath, while ProductScoreRepository keeps atomic partial writes. Existing GET/PATCH score routes gain additive read-only fields; no duplicate endpoint or migration.

**Tech Stack:** Java17, Spring Boot3.5.7, JdbcTemplate, Flyway V1–V10, Spring Security, JUnit5/MockMvc, H2 and opt-in isolated MySQL8.

## Global constraints

- Approved spec: ../specs/2026-10-21-retail-catalog-utility-design.md. Backend only; no Frontend changes.
- Preserve approved formula, missing-data/expiry rules, F03 snapshots, F04 historical values, F05 algorithms/runs/patterns and inventory quantities.
- Do not stage existing private application.yml or unrelated dirty/untracked documents. Do not edit old migrations, deploy, push or merge.
- Work in place on feature branch Khoi/fix/retail-catalog-utility-access from F06 HEAD68d2844. Existing baseline25 relevant Backend tests passed. No dependency install required.

## Task1: Catalog read RBAC

Files: ProductController.java and CategoryController.java under src/main/java/vn/edu/fpt/paspm/catalog; CatalogIntegrationTest.java; new utility/RetailCatalogUtilityIntegrationTest.java and opt-in RetailCatalogUtilityMySqlIntegrationTest.java under src/test/java/vn/edu/fpt/paspm.

Interface: GET /api/products and /api/products/{id}, GET /api/categories and /api/categories/{id}; existing ProductPage/ProductView/CategoryView responses unchanged.

- [x] Write new fixture using unique Store A/B, real active F01 tokens, category/Product and future active batch. Reproduce merchant reads using `mvc.perform(get("/api/products").header("Authorization","Bearer "+merchant)).andExpect(status().isOk())`; verify foreign404, filter/pagination and all mutation restrictions.
- [x] Run `mvn -o -Dtest=RetailCatalogUtilityIntegrationTest test` on Java17 and confirm expected403 failures before source edits.
- [x] Add `@PreAuthorize("hasAnyRole('STORE_ADMIN','MERCHANDISER')")` on only the four GET methods. Retain controller StoreAdmin defaults for mutations. Replace old CatalogIntegrationTest merchant403 expectation with SystemAdmin/customer403 and current Store fixture membership.
- [x] Rerun targeted tests; read access passes and forbidden writes/store leakage remain rejected.
- [x] Commit this tested slice using explicit file paths, after staged diff/secret/whitespace check.

## Task2: Current score read model

Files: utility/UtilityContracts.java, ProductScoreController.java, ProductScoreService.java, ProductScoreRepository.java; same new integration tests; recommendation/RecommendationIntegrationTest.java for API-driven history regression.

Consumes: UtilityProductRepository.product(store,id)/batches(store,id), UtilitySettingsRepository.config(store,false)/policy(false), UtilityTimeZone.today()/id(), ExpiryScoreCalculator.calculate(...), UtilityMath.weight(config,S,E,M).
Produces: Scores(productId,stockPriorityScore,seasonalPriorityScore,expiryScore,weightValue,expiryStatus,expiryEvidence,expiryPushDays,asOfDate,timeZone,configVersion,lambdaValid,ready,warnings).

- [x] Write failing current GET/PATCH assertions: `.andExpect(jsonPath("$.weightValue").value(1.435)).andExpect(jsonPath("$.expiryScore").value(1))`, then threshold3 returns1.335/E0, no transactions needed. Cover score boundaries/partial/null/unknown read-only fields, disabled/missing data, invalid lambda, role/store checks and unchanged inventory.
- [x] Run targeted tests; confirm computed fields absent/StoreAdmin GET403 before implementation.
- [x] Extend Scores with read-only schema metadata and nullable annotations. Reuse UtilityProductRepository.Product for manual score reads; remove the redundant score-only repository find method and preserve its SQL update. Build response in ProductScoreService using shared functions in repeatable-read GET/PATCH transactions, with explicit null W/readyfalse/warnings when lambda invalid or enabled inputs incomplete. Read-only GET permits StoreAdmin/Merchandiser; PATCH remains Merchandiser-only.
- [x] Re-run targeted + CatalogIntegrationTest/UtilityIntegrationTest/UtilityCalculationTest. Test0/1, all-disabled W1, expired/blocked/missing/inactive cases and immutable snapshot behavior.
- [x] Extend existing actual F03→SPMF→F06 regression to call S/M PATCH and config PUT; compare all frozen snapshot/mining tables and inventory before/after. No algorithm changes.
- [x] Commit tested score API slice explicitly.

## Task3: Full verification and contracts

Files: docs/api/F03_UTILITY_API.md, F02_PRODUCT_INVENTORY_API.md and F01_F02_RBAC_MATRIX.md, API_CHANGELOG.md, openapi.json; docs/frontend_handoff/F03_HANDOFF.md/F02_HANDOFF.md as applicable; new docs/requirements/RETAIL_CATALOG_UTILITY_VERIFICATION.md and evidence/retail-catalog-* only.

- [x] Run Java17 `mvn -o ... clean verify` with approved Java25/SPMF child path, opt-in MySQL and Java17 deployment verification. Expected no failure/error/skipped when all opt-ins supplied. Read actual report counts, not estimates.
- [x] Start an isolated local development runtime with private /tmp config, no existing application.yml edits; verify actual OpenAPI score schema/RBAC descriptions and exercise GET/PATCH/PUT flows including representative401/403/404/400 errors and GET persistence. Use Swagger UI if browser integration is available; clearly distinguish HTTP checks from interactive Swagger tests.
- [x] Export actual /v3/api-docs to shared openapi.json. Document exact added fields, example E1/W1.435 and E0/W1.335, role matrix, partial-write validation, current-preview warnings and immutable history. Remove stale F03 handoff statements claiming F05/F06 unimplemented by linking their existing contracts instead.
- [x] Refresh/check CodeGraph for changed score composition and Catalog route dependencies. Inspect final diff against spec, meaningful tests and public schema for nullability/read-only consistency.
- [x] Commit docs/evidence selectively. Verify Frontend Git status unchanged, feature commits complete, private config remains uncommitted. Final report in Vietnamese with actual tests/limits and branch status; no push/merge.

## Execution ledger

- User approved Backend-only design2026-10-21; feature branch created from68d2844.
- Baseline25 tests PASS. Catalog RED403 reproduced; targeted GREEN10. Score contract RED7/11 failures reproduced; targeted GREEN36.
- Source commits5516bc4 and281a264. Final Java17/MySQL clean verify232/232 PASS at13:32:02+07:00 (22 new +210 regression). Actual S/M/config API updates preserve all frozen F03/F05/history and inventory rows.
- New isolated runtime18087 migrated V1–V10 successfully;32 HTTP checks and11 interactive Swagger checks PASS. Actual OpenAPI exported59 paths/73 operations.
- Corrected sandbox localhost denial and history-test query scope before final passing run. No production migration/config/schema workaround.
- Source/self-review, JSON/link/secret/whitespace checks completed; CodeGraph current composition verified. Docs/evidence selectively committed; Frontend/private configuration excluded. No push or merge.

# F02 Backend Implementation Plan

## Goal
Implement F02-01 through F02-08 in the existing Spring Boot backend, preserve F01 authorization, verify build, migration, API behavior and OpenAPI, and report actual Swagger UI verification status.

## Repository findings
- The approved source directory contains the v3 backlog, README, all eight issue files, and the updated conceptual/logical Draw.io ERD. No ZIP archives are present.
- The logical ERD defines `CATEGORY`, `PRODUCT`, and `INVENTORY_BATCH`; `PRODUCT` has no stored stock field. `STORE` already exists in Flyway V1 and must not be recreated. F01 Google verification is in V2, so F02 must use V3.
- Existing backend packages are `auth`, `user`, `security`, `common`, and `mining`. The project uses Spring Boot 3.5.7, Java 17, Spring JDBC, and Flyway. It does not currently include Spring Data JPA; follow the established JDBC pattern.
- Existing security uses JWT, method security, `StoreScope`, and `@PreAuthorize`; API errors use `{ "error": "..." }` through `ApiError`.
- Existing CodeGraph was absent. CodeGraph CLI 0.9.9 initialized the workspace root, indexing Backend and Frontend code: 118 files, 1,987 nodes, 4,930 edges. MCP status confirms the index is ready.
- Preserve pre-existing local edits/deletions and untracked reference material. Do not change Frontend implementation or F01 files except the narrow StoreScope integration required to derive the authenticated Store for create operations.

## Requirements map
- F02-01: V3 Flyway migration for CATEGORY, PRODUCT, INVENTORY_BATCH, integrity constraints, indexes, and development seed data; never recreate STORE.
- F02-02: Store-scoped category list/detail/create/update/status APIs.
- F02-03: Store-scoped product list/detail, search, filters, page and sorting; derive response stock from batches.
- F02-04: Product create/update validation, Store-scoped SKU uniqueness, Category ownership, and no writable stock field.
- F02-05: Batch list/create/detail/patch, expiry and quantity rules, Store ownership, and safe concurrent updates.
- F02-06: Product activation/deactivation API.
- F02-08: Derived inventory summary with expired, blocked, sellable and total quantities; no stock/weight persistence.
- F02-07: OpenAPI contract checks and actual Swagger UI verification where the environment permits.

## Global constraints
- Backend only; do not modify FE, Figma, or unrelated modules.
- Do not implement STOCK_MOVEMENT, recipes/ingredients, Custom Weight, Expiry Weight, or Seasonality Weight.
- All F02 documentation stays in `docs/PASPM_F02_Inventory_Batch_Backlog_v3/`.
- Use DTOs and the existing JDBC, security, and exception conventions; never return persistence entities.
- Never trust a client-supplied Store ID; every read and write is scoped to the authenticated Store.
- Do not edit existing Flyway migrations.
- No commits or pushes as part of implementation; the checkout already contains unrelated local changes.

## Implementation phases
1. **Schema and persistence** — add V3 for the three F02 tables, same-Store composite constraints, unique keys, checks, query indexes, and development fixtures. Add catalog JDBC repositories and tests for constraints and derived quantity queries.
2. **Authorization foundation** — establish an authenticated Store Admin Store ID via the existing F01 security context/StoreScope; test unauthenticated, wrong-role, and cross-Store cases without weakening security.
3. **Category and product APIs** — implement request/response DTOs, validation, services, controllers, filtering/pagination/sorting, duplicate handling, and derived stock in product responses. Add controller/service integration tests.
4. **Inventory batches and summary** — implement create/list/detail/patch, optimistic concurrency or an equivalent guarded update, expiry validation, and a single-pass summary calculation. Add boundary, blocked, expired, and concurrent-update tests.
5. **OpenAPI and verification** — document all routes, DTOs, status codes, JWT security, and error format; run Maven tests/build and migration checks; start the actual application and exercise Swagger UI if available. Refresh CodeGraph and verify catalog symbols/edges.
6. **F02 reports** — write API contract, technical decisions, Swagger test report, implementation summary, and CodeGraph report in this directory only. Mark unexecuted Swagger checks NOT RUN/BLOCKED; never claim unexecuted checks passed.

## Verification plan
- Run focused tests first, then `mvn test` and `mvn verify` from `BE`.
- Validate V3 on the configured development database when credentials/service are available; independently test migration and persistence with the project test database.
- Fetch `/v3/api-docs` from the running backend and assert all required F02 paths and DTO schemas exist.
- Use Swagger UI for the F02-07 success/error matrix only when the actual running app, database, and authorized Store Admin credentials are available. Do not substitute Postman or frontend E2E.
- Refresh the workspace CodeGraph index after code changes, then query catalog symbols and StoreScope/AuthService relationships and record status.

## Decisions and dependency gates
- **Store timezone decision:** the ERD and current STORE schema have no timezone field. The user approved `Asia/Ho_Chi_Minh` as the MVP timezone; expiry summary behavior uses that default through `paspm.store-time-zone`, which can be overridden by deployment configuration.
- F02-08 issue documents `expiringWithinDays` and `expiringSoonQuantity`; retain that documented behavior unless a later approved source supersedes it.
- Seed fixtures must support two-Store isolation tests without recreating STORE. Ensure the chosen development seed mechanism does not silently insert demo data into production databases.

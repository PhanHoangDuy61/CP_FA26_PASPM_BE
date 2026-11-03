# F02 Implementation Summary

**Feature status: DONE for the defined backend scope.** All F02 endpoint groups, migration, validation, Store authorization, automated checks, OpenAPI coverage, Swagger UI verification, and CodeGraph refresh were completed. The approved ERD/backlog source files were left unchanged.

## Completed tasks

- **F02-01:** Added Flyway V3 for `CATEGORY`, `PRODUCT`, and `INVENTORY_BATCH`, including Store/product foreign keys, same-Store category constraint, Store-scoped SKU uniqueness, quantity/price/status checks, and query indexes. Added idempotent development-profile seed data for two Stores, Store Admin accounts, a Merchandiser, products, batches, and categories.
- **F02-02:** Implemented category list, detail, create, update, and status APIs.
- **F02-03:** Implemented product list/detail with name/SKU search, category/status filters, pagination, sorting, and Store scope.
- **F02-04:** Implemented product create/update validation and Store-scoped SKU uniqueness. Stock is not accepted in product requests; the response derives stock from inventory batches.
- **F02-05:** Implemented batch list/create/detail/patch, expiry validation, status and quantity updates, Store ownership, and locked transactional updates.
- **F02-06:** Implemented product activation and deactivation.
- **F02-07:** OpenAPI paths were checked by automated test and Swagger UI workflows were executed against the running backend. See the actual results in `F02_SWAGGER_TEST_REPORT.md`.
- **F02-08:** Implemented derived total, sellable, expired, blocked, and expiring-soon quantity summary with the approved inclusive date boundary and `Asia/Ho_Chi_Minh` timezone.

## Source and migration changes

- New catalog DTOs/access helper, category/product/inventory controllers, services, and JDBC repositories under `src/main/java/vn/edu/fpt/paspm/catalog/`.
- New `src/main/java/vn/edu/fpt/paspm/catalog/CatalogDevSeeder.java` (dev profile only).
- New `src/main/resources/db/migration/V3__f02_catalog_inventory_batch.sql`.
- New integration and development-seeder tests under `src/test/java/vn/edu/fpt/paspm/catalog/`.
- Narrow `SecurityConfig` changes give Swagger the backend-wide title and document Store-scoped `404` responses; authentication and authorization policy were not weakened.

## Implemented endpoint inventory

- Categories: `GET /api/categories`, `GET /api/categories/{id}`, `POST /api/categories`, `PUT /api/categories/{id}`, `PATCH /api/categories/{id}/status`.
- Products: `GET /api/products`, `GET /api/products/{id}`, `POST /api/products`, `PUT /api/products/{id}`, `PATCH /api/products/{id}/status`.
- Inventory: `GET /api/products/{id}/batches`, `POST /api/products/{id}/batches`, `GET /api/inventory-batches/{id}`, `PATCH /api/inventory-batches/{id}`, `GET /api/products/{id}/inventory-summary`.

## Verification results

- `mvn -q verify`: **PASS** (exit code 0), including Maven build/package and the complete automated test suite.
- Latest Surefire reports: 24 tests total across `AuthIntegrationTest` (14), `GoogleRegistrationTest` (3), `CatalogIntegrationTest` (6), and `CatalogDevSeederTest` (1); zero failures, errors, or skips.
- Automated H2/Flyway startup applied migrations through V3. Tests cover constraints, persistence, Store isolation, API validation, derived quantities, OpenAPI paths, and dev seed idempotence.
- Live Swagger UI checks: every F02 endpoint exercised successfully; unauthenticated `401`, insufficient-role `403`, and cross-Store product `404` observed. Automated-only invalid-input cases are identified in the test report.
- CodeGraph refreshed at the workspace root and verified to discover catalog symbols and F01 security references. Details: `F02_CODEGRAPH_REPORT.md`.

## Remaining issues and scope

- No known blocker within F02 backend scope.
- The real database service/credentials were not used for the migration run; V3 was applied and verified by the project's H2-backed Flyway tests and dev-profile runtime.
- Interactive Swagger UI does not individually repeat every invalid-input case; those are covered by automated backend integration tests.
- Frontend was indexed read-only and not modified. No F03 or stock-movement feature was added.

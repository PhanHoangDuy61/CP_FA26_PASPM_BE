# ROC-20261126-010 — F10 Part 1 ordinary cart

| Field | Value |
|---|---|
| Date / timezone | 2026-11-26 / Asia/Ho_Chi_Minh |
| Type | Backend implementation |
| Branch / base | `Khoi/feat/f10-cart-core` / `fb48d55` |
| Release | UNRELEASED |
| Daily index | [2026-11-26](README.md) |

## Scope and behavior

- Implemented the five ordinary cart operations in the [F10 contract](../../api/F10_BUNDLE_CART_API.md) under DEC-20261125-06. All require END_CUSTOMER. The Store comes from the public ACTIVE slug and the cart belongs to that Store and the authenticated user.
- V31 creates one persistent cart per customer and Store, ordinary Product lines with a database-enforced 1–99 quantity bound, and the append-only storefront event table. Existing migrations are unchanged. The bundle event columns are in place for the planned feature. Part 1 writes only `ITEM_ADDED`, `ITEM_QUANTITY_CHANGED`, `ITEM_REMOVED` and `CART_CLEARED`.
- Reads of a never-created cart return version 0. Mutations serialize on the existing customer row and check `If-Match` before writing. This covers simultaneous first writes without a preexisting cart row. The line change, version and single event commit together. A no-op or rejection writes no event.
- Adds and increases reuse the F09 sellable stock query and Store-local expiry date. The cart projects current prices, reserves no inventory, and allows quantity reductions and removal from otherwise stale lines.
- [OpenAPI](../../api/openapi.json) matches the captured runtime export and only adds Part 1 operations and their DTO schemas: 112 paths / 139 operations → 115 paths / 144 operations. Whole-bundle add, F09 bundle keys, bundle event emission and F11 remain planned.
- Checked 61 local origin refs: V31 is free. DEC 01 and ROC 001–009 are allocated for 2026-11-26. This record uses 010. No new business decision.

## Verification

Environment: Temurin 17.0.21.1 with pinned `JAVA_HOME`, Maven Wrapper with cached dependencies, H2 for HTTP acceptance and the backend group. Current-run evidence is under `outputs/evidence/f10-cart-core-checkpoint1/` outside Git.

- The original test-first run produced the expected 404 before the first cart route existed. That evidence is under `outputs/evidence/f10-cart-core/`. The unpublished F10 commit was rebased onto `fb48d55` after its documentation-only changes, with the tested source and V31 unchanged.
- `./mvnw -o -B '-Dtest=CartIntegrationTest,MigrationVersioningTest,TransactionExcelImportIntegrationTest#openApiDocumentsBothMultipartImportRoutes' -Dpaspm.test.export-openapi=true test` — exit 0. Cart 7 PASS, migration 4 PASS, export 1 PASS. Zero failures, errors or skips.
- MySQL 8.4.13 disposable container, real Flyway with Java 17 — fresh V1→V31 applied 31 migrations. The independent V30→V31 schema with Store and Product fixtures applied one migration and preserved those rows. Validation and table/FK checks passed in both schemas. The disposable container was removed.
- The latest PO instruction clarifies that the increment must fit sellable stock minus the current cart quantity. Part 1 already enforces the equivalent check on the resulting quantity. The API guide now states that rule explicitly.
- With pinned Java 17 and `MAVEN_ARGS=-o`, `./mvnw -B clean verify -Dtest="$(python3 .github/scripts/test-groups.py backend)"` finished with exit 0: **481 PASS, 0 failures, 0 errors and 0 skips**. The selector includes CartIntegrationTest.
- Verified that the export only adds five operations with explicit IDs and their four DTO schemas, with semantic equality to the runtime. Checked 261 internal OpenAPI references, 170 relative documentation links, three JSON examples and `git diff --check`.
- Cart HTTP and concurrent creation were verified on H2. MySQL covered Flyway and the schema checks. Hosted CI was **NOT RUN**.

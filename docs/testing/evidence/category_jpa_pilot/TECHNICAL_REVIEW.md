# Independent technical review — Category JPA CRUD pilot

Date: 2026-10-25 (Asia/Ho_Chi_Minh).
Reviewed worktree: `/private/tmp/paspm-legacy-matrix-recovery`.
Base: Product integration `202761a81abf8ce627f55a4dd2eaa8194070546b`.
Category changes were uncommitted when reviewed. Reviewer made no implementation, GitHub, or database changes.

## Verdict

No verified Critical/Required production defect identified in the inspected Category change. Suitable for continued local verification and technical review; this is **not human GitHub approval and not authorization to merge**. Full remote CI, human review and real-data verification remain separate gates.

## Reviewed implementation and invariants

- `CategoryEntity`: exact scalar mappings `category_id`, immutable SQL `store_id`, `name(150)`, `status(20)`. No associations, collection ownership, cascade, orphan removal or delete method. Existing V3 DDL remains authoritative.
- `CategoryJpaRepository`: all selects and DML require Store ID. DTO projections retain list order `(name, category_id)` and avoid returning stale managed entity state after JDBC edits. Repository deliberately exposes no unscoped save/find/delete methods.
- `CategoryPersistence`: translated repository boundary, shared existing transaction manager, transactional writes, explicit flush before JDBC consumers, refresh before replacing metadata. Status DML changes only status and flushes before clearing the shared persistence context.
- `CategoryRepository`: retains prior public application-facing signatures and delegates to the new bridge. Existing Category service/controller/request/response DTOs, StoreScope access and Product service contract unchanged.
- No name uniqueness added: V3 permits duplicate names. No hard delete introduced: existing status-only semantics preserved even while referenced by Product.
- Product-to-Category composite Store foreign key remains database-enforced. Store field is not SQL-updatable; direct dirty managed field test covers persistence behavior.
- Product catalog/inventory/analytical JDBC workloads and F03 scores are not newly mapped into Category.
- Product mapping test adaptation retains exact Product attributes/association checks and explicitly locates ProductEntity after CategoryEntity is added.
- No changes observed in Flyway migration files, F03/F04/F05/F06 business source, or persistence transaction-manager configuration.

## Verification inspected

Read the new 14 Category tests inherited by the MySQL subclass (28 executions), existing Product persistence tests, CatalogIntegrationTest successful own-Store Category PUT/PATCH coverage, ProductService category validation, V3 schema, PersistenceConfiguration and persistence.properties.

Observed actual parent-run `/private/tmp/paspm-hybrid-integration/category-after.log`: **63 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS** (28 Category + 35 Product tests, H2/MySQL). This reviewer inspected that log and source; did not rerun database suites concurrently with the primary agent. Full F01–F06/SPMF and migration verification must be reported using the primary agent's eventual full run, not inferred from these 63 cases.

`git diff --check` completed successfully. `git diff --name-only HEAD -- src/main/resources/db/migration` returned no files.

## Optional coverage improvements (not verified defects)

1. `CategoryPersistenceIntegrationTest`, around `productStatusContextClearDoesNotLoseCategoryEdits`: add the reverse mixed-context direction (pending Product metadata change then Category status DML). Implementation uses the same tested flush-before-clear pattern, so this is additional confidence rather than a demonstrated loss of data.
2. `CategoryPersistenceIntegrationTest`, scoped update/status cases: explicitly exercise repeated same-value metadata/status updates. Existing code returns success for a scoped entity found and JPQL matched-row count for status; no incorrect behavior observed.
3. `src/main/resources/persistence.properties:1` still says Hibernate validates only Product mapping. Consider updating the comment to mention Product and Category; runtime `validate` setting already applies to both.

## Remaining acceptance limitations

- Tests use synthetic data; they do not resolve REAL_DATA_BLOCKED.
- No claims about remote GitHub CI or human approval made by this review.
- No new schema migration required for the inspected scalar mapping.
- Merge remains disallowed until the owner's acceptance gates and explicit merge authorization are met.

# Category JPA CRUD pilot — 2026-10-25

Branch: `Khoi/feat/category-jpa-crud-pilot`. Dependency/base: verified Product integration `202761a81abf8ce627f55a4dd2eaa8194070546b` on `Khoi/feat/product-jpa-integration`, itself based on dev783725c. Product and Category remain separate changes; do not merge either automatically.

## Implementation and preserved contracts

`CategoryEntity` maps only categoryId, storeId, name and status. Store is not SQL-updatable. No entity relationship, cascade, orphan removal or delete operation is introduced. Existing composite Product/Category Store FK remains authoritative. Existing V3 allows duplicate category names; this implementation deliberately retains that behavior. No new migration is required and V1–V11 are unchanged.

`CategoryJpaRepository` exposes only Store-scoped entity lookup, scalar DTO projections, ordered list and status DML. `CategoryPersistence` is the translated transactional bridge; `CategoryRepository` keeps all prior application-facing signatures. Metadata writes flush for JDBC consumers and refresh a managed row before replacing metadata. Scalar projections read native/JDBC edits even with a stale managed entity. Status DML writes only status, flushes pending changes and clears the shared context. Product/Category share the existing single JpaTransactionManager/datasource with JDBC.

Controllers, services, DTOs, endpoint paths and role rules remain unchanged: Store Admin writes, Store Admin/Merchandiser reads, unauthorized Store record access404. Unknown `storeId` JSON fields are already rejected400, not accepted/ignored; ownership comes from authenticated Store scope. Existing API validation retains nonblank/max150 name and ACTIVE/INACTIVE status. No DELETE endpoint: status changes remain allowed while Products reference the category and preserve Product/inventory rows. Product catalog/inventory/analytics stay JDBC and F03 S/M fields remain unmapped. Hibernate validates Product/Category schema only; OSIV remains false.

## Executed tests

The first 28-case run found both intended missing-JPA failures and two mistaken new test assumptions (unknown JSON field accepted; uniform low-level exception class). Correct the test to the existing actual contract before implementation: unknown field400 followed by valid create201; persistence invalid status must raise a Spring DataAccessException, while direct null name retains prior trim rejection. HTTP invalid values remain400 through existing validation.

Corrected pre-implementation run:28 tests,4 failures,8 errors,0 skipped,16 PASS. Failures verify Category is not yet mapped/JPA-backed; errors are absent entity lookups. After implementation: **63 PASS,0 failures/errors/skips** =28 Category+35 Product on H2/MySQL. Counts overlap with the full suite and are not additive unique coverage.

Full clean verification result: **330 PASS,0 failures/errors/skips; Maven BUILD SUCCESS**, Java17 Backend, Java25 approved SPMF2.67a, disposable MySQL8.4.13. Completed2026-10-25 13:54:59 Asia/Ho_Chi_Minh. [All per-case results](../testing/evidence/category_jpa_pilot/FULL_RESULTS.json)..

Category coverage (14 cases each on H2 and MySQL8.4): exact mapping/no cascade; genuine Hibernate writes/DTO queries; Store-scoped ordered reads and missing writes; duplicate names; shared Category/Product/Inventory commit and rollback with independent readbacks; existing-row rollback preserves S/M and quantities; stale managed/native writes; status/name preservation; Product status context clear flushes pending Category edits; dirty Store field cannot persist; referenced Category preserves Product/inventory and foreign Store FK rejects assignment; invalid DB input; existing API RBAC/unknown field/validation/foreign Store/no-delete behavior. Existing CatalogIntegrationTest also executes successful own-Store PUT/PATCH.

Use the same isolated harness/options as [Product verification](PRODUCT_JPA_INTEGRATION_REPORT.md), with `mvn test -Dtest=CategoryPersistenceIntegrationTest,CategoryPersistenceMySqlIntegrationTest,ProductPersistenceIntegrationTest,ProductPersistenceMySqlIntegrationTest`, then `mvn clean verify` with every MySQL/Java17/Pilot/SPMF opt-in. All fixtures are synthetic, generated in a dedicated loopback MySQL container; the schema-only runtime account runs against a clone pre-migrated by its disposable DBA. No original source datasource fallback.

## Independent review and limitations

[Agent review](../testing/evidence/category_jpa_pilot/TECHNICAL_REVIEW.md) found no verified Critical/Required production defect. Optional confidence improvements remain: reverse mixed-context flush/clear direction and repeated same-value updates. Existing own-Store API success coverage was confirmed. A stale persistence comment was corrected. These are not observed failures. AI review does not replace human approval.

CodeGraph was synchronized using its installed `sync` command and discovers CategoryPersistence/new tests. Its index is ignored and not committed. No tooling installation or global rewiring.

Original user checkout branch/head/status, private application config and original migrations were compared with captured before hashes: unchanged. No source database connection or write occurred in this task; this is not genuine dataset acceptance. REAL_DATA_BLOCKED remains.

## Delivery gates

Status: LOCAL_VERIFIED / READY_FOR_REVIEW. READY_FOR_REVIEW is distinct from READY_FOR_MERGE. Remote Product PR-triggered workflow lookup at202761a returned no runs. Existing CI3 remains unmerged; approved external SPMF URL, accepted CI fixes and controlled Pilot clone provisioning/all test opt-ins remain needed. Human GitHub review is pending. SPMF binary remains local, hash-pinned and never published. No merge/deploy/repair was performed.

Product PR targets dev; Category PR targets Product integration while unmerged. Retarget Category to dev only after Product acceptance/merge and authorization. Category implementation/evidence commit4d5cf4283ec2821ad87fd289039f7dec0e1b0f6b was pushed. Actual stacked PR creation returned403 (Resource not accessible by integration), so no PR exists; owner manual creation is required and permission is not bypassed. See [delivery status](../archive/persistence/HYBRID_JPA_DELIVERY_STATUS.md).

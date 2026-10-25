# JPA Pilot — Fresh Code Review

Date: 2026-10-22, Asia/Ho_Chi_Minh. Branch `Khoi/Fear/jpa-product-metadata-pilot`, implementation commit `48bc0595777dcc1faaa83fb71aa3fd049bf7e872`, base `3d58a73543db3b5923289f901f9ffecba6228d23` (`dev` and freshly fetched `origin/dev` match at review time).

## Verdict

**Ready for human PR review, with REAL_DATA_BLOCKED explicitly disclosed.** No actionable production defect found in this review. No implementation rewrite or source fix was necessary. This is not an unqualified production deployment approval.

An independent fresh reviewer inspected the commit diff read-only across correctness, architecture, security, readability and performance. The reviewer did not run tests or access the DB; the coordinator separately executed fresh verification. CodeGraph traced ProductRepository → ProductMetadataPersistence → scoped ProductJpaRepository → internal ProductEntity. Graph's test-detection warning was not interpreted as absence of JUnit tests.

## Scope and checks

| Check | Observation | Result |
|---|---|---|
| Product-only JPA writes | Create/update/status delegate to metadata adapter; no Category/User/F03–F06 persistence migration | PASS |
| JDBC read compatibility | Original Product find/search/filter/sort/paging/stock SQL retained | PASS |
| Entity mapping | Existing V3/V8 column names/types; IDENTITY; BigDecimal money; scalar store/category IDs | PASS |
| Ownership separation | No S/M, stock quantity, associations/collections/cascade or invented @Version in ProductEntity | PASS |
| Store scope | Limited repository exposes scoped lookup and scoped status JPQL; no unscoped generic CRUD API | PASS |
| DTO/API | Services/controllers/contracts and existing 409 handler unchanged; entity not serialized as API response | PASS |
| Transactions | One Boot JpaTransactionManager, shared DataSource/connection, explicit flush/refresh, status column-only update | PASS, fresh H2/MySQL tests |
| Concurrency | Current tests cover native S/M vs metadata, stale entity vs native price/status, RC/RR and F04 REQUIRES_NEW | PASS |
| Schema ownership | Only validate + OSIV false; no added/edited Flyway SQL; SHA256 of V1–V10 unchanged | PASS |
| Test target guard | Explicit opt-in, local disposable schema marker, target≠source, actual identity and restricted grants | PASS |
| Secrets/local edits | Private application.yml remains preexisting dirty edit, hash unchanged; no credentials/dumps staged | PASS |
| Refactoring size | Production diff limited to pom, Product facade, three Product persistence classes and safe config/property file | ACCEPTABLE |

AuthIntegrationTest changes only its test schema setup to full Flyway; existing assertions remain. No production Auth behavior was changed. Diff includes tests/docs as expected, not frontend work.

## Fresh verification evidence

- Maven Java17 + MySQL opt-in + separate SPMF runtime + guarded simulated clone: **274 PASS, failures0/errors0/skipped0**, BUILD SUCCESS at **2026-10-22T05:03:18+07:00**. Existing232 preserved; focused H2=16, focused MySQL=17, clone API=5, safety=4.
- Actual Swagger UI: **18 PASS**, including Store A/B, Merch, no-token401, forbidden403, foreign404, invalid400, duplicate409, create201, read/update/status200 and persistence readbacks.
- Runtime OpenAPI:59paths, equal to original JDBC baseline except runtime server URL.
- Independent SQL confirms S0.8/M0.9, stock8, ownership/category and persisted name/price/status after UI metadata edits.
- See [fresh test evidence](../testing/evidence/jpa_pilot_review/fresh-test-results.json) and [Swagger report](../testing/JPA_PILOT_SWAGGER_VERIFICATION.md).

## Risks and limits — not hidden defects

1. **REAL_DATA_BLOCKED:** the configured source is V5/catalog0, no genuine populated source or approved dump located. Local QA/test datasets are synthetic and are not evidence of genuine retail-data compatibility.
2. **Source V5 is not deployment-ready.** It lacks later schema including Product expiry_push_days. Do not run the new app against it without separately approved backup/migration planning. No source migration/repair was performed.
3. Metadata updates use scoped lookup + refresh + UPDATE, adding two SELECTs compared with the original JDBC metadata command. This deliberate cache-consistency overhead was disclosed in the implementation report. No fresh claim that JPA is faster; no new load benchmark was performed in this review task.
4. There is no optimistic version column in Product; no @Version was added. Metadata conflict semantics remain those of the approved existing full-request update behavior. Batch concurrency stays JDBC.
5. clearAutomatically on scoped status update clears the persistence context; the pilot maps only Product. Reassess this boundary before introducing additional managed aggregates; no expansion approved now.
6. Two previous temporary QA configurations are no longer connectable (SQL state42000); version cannot be verified for those stale artifacts. They were not used for tests or classified as genuine sources.
7. Flyway11.7.2 logs its existing MySQL8.4 tested-support warning; actual fresh/upgrade test migrations pass. No dependency upgrade was included.

## Next decision

Approve technical code review separately from genuine-data/deployment acceptance. Product pilot meets the approved simulated-data technical checks and is ready to discuss in PR. Do not automatically begin MIG-JPA-F02-02 Category migration: first review/integrate this pilot and explicitly accept or resolve REAL_DATA_BLOCKED. No push, remote PR, merge or deployment authorized in this task.

# feat(catalog): migrate Category metadata CRUD to Store-scoped JPA

Stacked dependency: target `Khoi/feat/product-jpa-integration` at202761a, NOT dev while Product is unmerged. Category diff contains no Product reimplementation. After authorized Product merge, retarget/rebase separately.

Replace Category JDBC CRUD with a scalar entity, Store-scoped Spring Data queries and transactional DTO bridge; preserve existing routes/DTOs/RBAC, ordered reads, duplicate names and status-only behavior. No cascade/hard delete/schema migration. Product catalog/inventory and analytical workloads remain JDBC. Preserve shared transaction flush/refresh/clear semantics and immutable Store ownership.

Verification: targeted H2/MySQL63 PASS,0failures/errors/skips (28 Category+35 Product). Full Maven clean verify:330 PASS,0failures/errors/skips, including Flyway/recovery/F01–F06 and real HUI-Miner/EFIM.. Independent agent review found no verified required production defect; optional coverage notes are recorded. Synthetic fixtures only; REAL_DATA_BLOCKED remains.

LOCAL_VERIFIED / READY_FOR_REVIEW; NOT READY_FOR_MERGE. Remote CI/SPMF hosting/controlled clone setup and eligible independent human approval remain pending. No source V5 database access/write, applied migration edit, deployment, PR merge or third-party binary publication.

Evidence: docs/architecture/CATEGORY_JPA_IMPLEMENTATION_REPORT.md and docs/testing/evidence/category_jpa_pilot/.

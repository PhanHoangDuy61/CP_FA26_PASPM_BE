# feat(backend): integrate Product JPA metadata with V11 recovery

Integrate the existing Product Pilot12d348e onto dev783725c without rewriting its branch. Product metadata create/update/status uses the scoped JPA bridge; catalog/inventory and analytical workloads remain JDBC under one shared JpaTransactionManager. Recovery/V1–V11 bytes and API contracts remain unchanged.

Resolve two documentation conflicts by preserving current recovery facts. Update obsolete V10 test expectations and add direct managed Store-immutability plus registered populated-V5 recovery-to-Boot/JPA preservation coverage. A failed disposable clone was retained; a new clone was migrated by DBA before its schema-restricted runtime account executed API tests.

Local full clean verify:302 PASS,0failures/errors/skips (Java17 Backend, Java25 approved SPMF2.67a, synthetic MySQL8.4). Agent technical review found no unresolved high-priority production defect; it is not human approval. No original source connection/write, repair, deployment or binary publishing.

READY_FOR_REVIEW; NOT READY_FOR_MERGE. Remote gates remain BLOCKED_REMOTE_CI: CI3 unmerged, approved external SPMF URL and controlled Pilot clone provisioning/opt-ins required. Eligible independent human review is pending; REAL_DATA_BLOCKED persists. Dependent Category development is owner-authorized after this local acceptance but will use a separate stacked PR. Do not auto-merge.

Evidence: docs/architecture/PRODUCT_JPA_INTEGRATION_REPORT.md and docs/testing/evidence/product_jpa_integration/.

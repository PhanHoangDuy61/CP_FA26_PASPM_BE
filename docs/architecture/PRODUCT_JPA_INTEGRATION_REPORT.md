# Product JPA integration report — 2026-10-25

Status: **LOCAL_VERIFIED / READY_FOR_REVIEW**, **NOT READY_FOR_MERGE**. Branch `Khoi/feat/product-jpa-integration` starts from dev783725c and merges existing Pilot12d348e without rewriting its history. Original Pilot/user checkout is untouched. No Product/Recovery/CI PR is merged by this task.

## Integration and conflicts

The old Pilot has three scoped implementation/docs commits and is twelve commits behind dev. A normal feature-branch merge preserves both histories. Only two add/add documentation conflicts occurred: JPA_PILOT_PR_READINESS.md and JPA_V5_TO_V10_MIGRATION_VERIFICATION.md. Current recovery facts were retained and this report records the new integration. No production source conflicts occurred. Existing applied V1–V11 and all B+C recovery source remain unchanged.

Existing ProductEntity/ProductJpaRepository/ProductMetadataPersistence and PersistenceConfiguration are reused, not rewritten. Product metadata create/update/status uses JPA with Store scope; catalog/search/stock projections and Inventory stay JDBC. F03 scores are excluded from the entity. Store is non-updatable; there are no entity relationships/cascades. Explicit flush supports JDBC readback; refresh handles native updates and scoped status DML clears stale managed state. One Boot-managed JpaTransactionManager shares the JDBC connection; Hibernate validates only and OSIV is disabled.

## Verified issues and corrections

Initial focused execution:45 tests,2 failures,5 errors,0 skipped. Two assertions expected V10/five post-V5 migrations; update to V11/six migrations and accurate names/logs. The unsupported original V7 failure characterization remains negative, never called successful migration.

Five clone API errors were test-environment trigger-creation privilege failures with MySQL binary logging. Preserve the failed disposable clone; provision a new marked synthetic schema, migrate V1–V11 using its disposable DBA, then test with the unchanged schema-only application user. No global privilege increase, logging safety change, repair or production operation was used. This is environment provisioning, not a business-code fix.

Added meaningful coverage: accidental dirty managed Product Store assignment cannot persist (H2/MySQL flush-clear and independent committed readback), and actual dump/restricted restore/registered populated-V5 recovery toV11 followed by full Boot/JPA Product metadata/status changes preserves canonical frozen matrix/snapshot hashes, scores, inventory and zero fabricated sales rows. Agent review's two material coverage gaps are addressed; no high-priority production defect remains.

## Executed verification

- Dev Catalog API baseline:7 PASS.
- Focused Product retest:47 PASS,0failures/errors/skips.
- Final full Maven clean verify: **302 PASS,0failures/errors/skips**, Java17 Backend, separate Java25, exact approved SPMF2.67a, disposable MySQL8.4.13.
- Fresh V1–V11; empty V5; supported populated synthetic V5; existing V10; historical canonical/replay preservation; shared transactions/isolation/rollback/staleness; SKU/category constraints; RBAC/Store isolation; F03 S/M/inventory preservation; F04 REQUIRES_NEW/idempotency; F05 real HUI-Miner/EFIM; F06 unverified legacy-evidence refusal all execute through existing/new suites.
- Current API/controller/DTO contracts are unchanged. Existing API tests include MockMvc and actual fresh-schema Category/Product HTTP calls; no new interactive Swagger session is claimed.

Command (private config contains only a generated disposable schema account; no source fallback):

```sh
JAVA_HOME=<jdk17> PASPM_TEST_MYSQL_SERVER=jdbc:mysql://127.0.0.1:<dedicated-port>/ \
PASPM_TEST_MYSQL_USER=<disposable-dba> PASPM_JPA_PILOT_MUTATIONS_ALLOWED=true \
PASPM_TEST_RESTORE_PROVISIONER=<guarded-disposable-helper> \
PASPM_MINING_SPMF_JAR=<approved-local-jar> PASPM_MINING_JAVA_EXECUTABLE=<jdk25-java> \
mvn clean verify -DargLine=-Djava.io.tmpdir=<shared-private-test-dir> \
-Dpaspm.test.mysql=true -Dpaspm.test.java17-backend=true -Dpaspm.test.java17=<jdk17-java> \
-Dpaspm.test.jpa-clone=true -Dpaspm.test.jpa-clone-config=<private-generated-config> \
-Dpaspm.test.spmf-jar=<approved-local-jar> -Dpaspm.test.spmf-java=<jdk25-java>
```

Counts overlap between focused and full runs; do not add them as unique tests. Per-case evidence: [full results](../testing/evidence/product_jpa_integration/FULL_RESULTS.json), [failure/retest summary](../testing/evidence/product_jpa_integration/INTEGRATION_RESULTS.json), [technical review](../testing/evidence/product_jpa_integration/TECHNICAL_REVIEW.md).

## Remaining gates and dependent work

Latest owner instruction explicitly permits Product and dependent Category development while remote artifact/review gates remain pending. Category may now start from this locally verified Product branch, as a separate stacked change; Product need not be merged first. This supersedes earlier documents' development-entry gate, not their PR merge safeguards.

PR3 (CI fixes) is still open/unmerged. This Product branch deliberately starts from dev, not the unaccepted CI branch. Remote full CI depends on accepted CI infrastructure, approved SPMF provisioning and explicit disposable Pilot clone setup/all opt-ins. Human review remains pending. No GITHUB_VERIFIED claim is made for this Product integration. No original source-database connection/write, migration repair, deployment, binary publication or real-data verification occurred. REAL_DATA_BLOCKED remains. Product PR targets dev; never auto-merge.

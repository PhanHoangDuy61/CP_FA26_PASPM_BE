# Product JPA Pilot — MySQL Migration Verification

Date: 2026-10-22, Asia/Ho_Chi_Minh. **Overall migration acceptance: PARTIAL / BLOCKED.**

The original 274 tests and the final expanded suite both ran successfully with all opt-in checks enabled. A populated V5 historical Utility Matrix exposes an existing V7 migration failure. A passing characterization test records that failure; it does not make that upgrade scenario successful.

## Actual database scenarios

| Scenario | Actual result | Evidence |
|---|---|---|
| Fresh MySQL V1→V10 | PASS | All ten migrations, Flyway validate, Hibernate mapping validation, unique/FK/CHECK constraints and actual Category/Product HTTP roundtrips passed. |
| MySQL V5→V10, Catalog and snapshot header with no matrix rows | PASS | V1–V5 prepared separately; V6–V10 applied. Pre/post six-table SHA-256 fingerprints match. JPA writes/JDBC readbacks and mixed rollback passed. |
| MySQL V5→V10, populated historical matrix | **FAIL / merge blocker** | Valid V5 matrix row references prepared transaction ID 9101. V7 creates an empty sales table and fails adding `fk_utility_matrix_transaction`: SQLState `23000`, MySQL `1452`. Original V5 row fingerprints remain equal. |
| Existing quoted V7 `row_number`, V7→V10 | PASS | `FlywayRecoveryTest.existingMySqlV7UpgradesWithoutReplayingOrDeletingData` executed on MySQL. Three later migrations apply and the staging row remains. |
| Historical V7 checksum mismatch | PASS, H2 recovery test only | The historical checksum is rejected, followed by the existing explicitly controlled repair on an in-memory H2 test DB. No repair was executed on the source or MySQL test instances. |
| Original configured source | READ ONLY, unchanged | Source is MySQL 8.4.11, Flyway V5. Store/Category/Product/Batch/Snapshot/Matrix counts remain zero. Before/after columns, counts, history/checksums, private configuration hash and all migration file hashes match. |
| Authorized genuine retail copy | **REAL_DATA_BLOCKED** | All populated fixtures are simulated. No genuine-data claim is made. |

## Environment

Docker CLI/Compose exist, but the Docker daemon is unavailable. No `gh` CLI is installed. No Testcontainers dependency or Maven test profile is configured; this project uses opt-in system properties.

An installed MySQL 8.4.11 binary was used to initialize a **new dedicated instance**, with `--no-defaults`, a private temporary data directory, a separate private socket, and loopback port **13316**. It is independent of the configured source instance. The verification account has schema-prefix privileges only; the guarded API fixture has its own account restricted to exactly one disposable schema. Credentials are generated temporarily, stored privately, never printed, and excluded from Git. No tooling was installed. After verification the dedicated server was shut down through its own private socket and the generated plaintext credential/config files were removed. Private raw logs and the stopped test data directory remain outside Git.

`MySqlTestDatabase.create()` created unique `paspm_p0_test_<UUID>` schemas. No existing schema was reused, reset or deleted. Every write test used the dedicated instance. The source was accessed only by SELECT-only probes using read-only connections and rollback.

The initial sandboxed MySQL initialization crashed before creating the instance. Initialization succeeded with approved local execution outside that sandbox. This was an environment restriction, not an application migration failure.

## V5 preservation fixtures

Each V5 fixture contains two Stores, two Categories, two Products, two Inventory Batches, one Merchandiser and one immutable snapshot. The positive fixture has zero matrix rows; the additional failure fixture has one matrix row.

Representative keys: Store 1 → Category 101 → Product 201 (`V5-MILK`) → Batch 301; snapshot 401 and, in the populated-matrix case, matrix item 501. Product 201 keeps S=0.8, M=0.9; Batch 301 keeps initial=10 and remaining=7. Store 2 owns the other Category/Product and a blocked Batch (initial=8, remaining=5). JSON configurations, snapshot source/matrix hashes, dates and utility values are included in pre/post fingerprints.

The successful upgrade verifies Product IDs, SKUs, Category/Store ownership, S/M, Batch quantities and the immutable snapshot header. V8 initializes the new Product expiry threshold to zero and leaves old snapshot JSON/hashes unchanged. Existing JDBC reads see JPA updates; rollback uses the application's shared JdbcTemplate and transaction manager. Cross-store Category references, duplicate Store/SKU and invalid quantities are rejected.

Sales and mining tables do not exist at V5, so preservation of pre-existing rows in those tables is not asserted. Separate executed V7/V9/F04–F06 regression fixtures cover data in later schema versions.

## Newly identified V7 blocker and safe recovery boundary

V5 allows a Utility Matrix transaction ID without a sales-table foreign key. V7 subsequently creates `sales_transaction` and immediately adds that foreign key. For a nonempty V5 matrix, the new table has no matching historical transaction rows. MySQL rejects the constraint; DDL already executed by V7 remains in this disposable test schema and Flyway records failed V7.

The reproduction first failed as an acceptance test. It is now an explicit characterization test asserting the exact `1452`/constraint failure and preservation of the original records. **The business scenario remains FAIL.** No historical IDs, snapshots or matrix items were deleted or fabricated to make it pass. The migrations are unchanged.

Before supporting a populated V5 source, an approved recovery design is required:

1. Preflight must stop before migration if historical matrix references cannot be reconciled.
2. Obtain a verified transaction source/mapping and an authorized isolated backup copy, or approve a schema design that explicitly handles legacy snapshot references.
3. Review any pre-upgrade reconciliation and partial-DDL recovery procedure before running it. Do not invent source transactions or blindly repair schema history.
4. Re-run the populated V5 preservation scenario after that strategy is implemented.

The observed original source has no matrix rows, but that does not resolve the supported populated-V5 migration gap. Product merge and Category entry remain blocked under the requested gates.

## Fresh automated results

| Run | PASS | FAIL | ERROR | SKIPPED |
|---|---:|---:|---:|---:|
| Original suite, all MySQL/runtime switches enabled | 274 | 0 | 0 | 0 |
| Final suite with three new migration checks | 277 | 0 | 0 | 0 |

Final disjoint execution groups: **147 H2, 81 MySQL, 49 unit/runtime**. Feature/persistence views overlap: F01–F06 regressions, Product JPA/JDBC shared transactions, F04 REQUIRES_NEW, Store isolation, score/inventory preservation and real HUI-Miner/EFIM child processes all executed. Backend: Java 17.0.20.1; child SPMF runtime: Java 25.0.1.

Three new cases are in `JpaPilotMigrationMySqlTest`: fresh bootstrap/API smoke; V5 Catalog preservation/JPA-JDBC rollback; and explicit detection of the populated-V5 blocker. Initial new-test harness errors (CHECK exception translation and use of a separate JdbcTemplate instead of the application's shared bean) were corrected and rerun. They required no application-code change.

### Reconciliation of 274 versus 195/79

The preceding 195 PASS / 79 SKIPPED run omitted opt-in flags/configuration. It cannot be described as 274 executed passes. The older reported 274-pass run used those flags; the current fresh run independently re-established all 274 executed passes. The final total is 277 because this task added three migration checks.

| Previously skipped class/method | Count | Enable condition | Final status | Merge relevance |
|---|---:|---|---|---|
| ProductPersistenceMySqlIntegrationTest | 17 | `paspm.test.mysql=true` | PASS | Mandatory |
| MiningMySqlIntegrationTest | 16 | same | PASS | Mandatory regression |
| RecommendationMySqlIntegrationTest | 14 | same | PASS | Mandatory regression |
| RetailCatalogUtilityMySqlIntegrationTest | 11 | same | PASS | Mandatory regression |
| TransactionImportMySqlIntegrationTest | 6 | same | PASS | Mandatory REQUIRES_NEW/regression |
| RecommendationExpiryMySqlIntegrationTest | 5 | same | PASS | Mandatory regression |
| RecommendationMigrationTest | 2 | same | PASS | Mandatory migration |
| FlywayRecoveryTest.existingMySqlV7UpgradesWithoutReplayingOrDeletingData | 1 | same | PASS | Mandatory migration |
| PilotCloneApiIntegrationTest | 5 | `paspm.test.jpa-clone=true`, private marked target and mutation switch | PASS | Mandatory API/persistence |
| MiningHttpMySqlTest | 1 | `paspm.test.java17-backend=true`, Java 17 + SPMF Java 25 | PASS | Mandatory runtime regression |
| SpmfRuntimeTest.actualJava17IsRejectedBeforeMiningAdmission | 1 | `paspm.test.java17=<Java17 executable>` | PASS | Runtime guard |

All previous skips were intentional opt-in conditions, but were mandatory verification dependencies for this task. None remain skipped.

## Actual verification command (passwords omitted)

The private runner supplies `PASPM_TEST_MYSQL_SERVER`, `PASPM_TEST_MYSQL_USER`, `PASPM_TEST_MYSQL_PASSWORD`, the explicit disposable mutation switch and the two SPMF runtime environment variables. Secret values are not included here.

```sh
mvn -q verify \
  -Dpaspm.test.mysql=true \
  -Dpaspm.test.java17-backend=true \
  -Dpaspm.test.java17="$JDK17_HOME/bin/java" \
  -Dpaspm.test.jpa-clone=true \
  -Dpaspm.test.jpa-clone-config="$PRIVATE_TEST_DIR/clone.properties" \
  -Dpaspm.test.spmf-jar="$SPMF_JAR" \
  -Dpaspm.test.spmf-java="$JDK25_HOME/bin/java" \
  -Dpaspm.avatar.directory="$PRIVATE_TEST_DIR/avatars" \
  -Dlogging.level.root=ERROR
```

`JAVA_HOME` and PATH use Java 17. Targeted migration retests used the same environment with `mvn -q test -Dtest=JpaPilotMigrationMySqlTest` and the same flags. Raw logs remain in a private temporary directory; only sanitized summaries/fingerprints are committed.

## Evidence

- [Original 274-suite counts](evidence/jpa_phase2/BASELINE_274_RESULTS.json)
- [Final suite and every test case](evidence/jpa_phase2/SUITE_RESULTS.json)
- [Migration results and preservation fingerprints](evidence/jpa_phase2/MIGRATION_RESULTS.json)
- [Read-only source before/after proof](evidence/jpa_phase2/SOURCE_PRESERVATION.json)

No new interactive Swagger session was executed in this task. Existing Swagger evidence is historical; fresh actual HTTP Category/Product smoke tests and the backend API/OpenAPI regression tests executed here are identified separately.

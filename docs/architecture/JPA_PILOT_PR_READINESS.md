# JPA Pilot — Local Pull Request Readiness

Date: 2026-10-22, Asia/Ho_Chi_Minh. Implementation: `48bc059`; branch `Khoi/Fear/jpa-product-metadata-pilot`; proposed base `dev`. Fresh fetch confirms origin/dev and local dev both at `3d58a73543db3b5923289f901f9ffecba6228d23` at verification time.

## Status

**READY FOR HUMAN REVIEW WITH DISCLOSED REAL_DATA_BLOCKED.** No unresolved failing test or actionable code-review defect. Source V5 compatibility and genuine-data validation are not complete. This is not a release/deployment approval.

No branch push or remote PR created: the task explicitly requires separate authorization. No merge/deploy. The local working tree also contains unrelated preexisting private/untracked work; only pilot verification docs/evidence belong to this follow-up.

## Ready-to-use PR title

`feat(backend): add JPA foundation and Product metadata pilot`

## Ready-to-use PR description

```markdown
Product metadata writes now use Spring Data JPA while existing Product reads, stock projections, Inventory Batch operations and F03–F06 stay on JDBC. The internal Product mapping excludes F03 S/M and stock; API DTOs and Flyway V1–V10 are unchanged.

Boot3.5.7 manages the JPA dependency. One JpaTransactionManager coordinates JPA/JDBC through the same DataSource; mappings validate against the existing schema and Open Session in View is disabled. Writes explicitly flush for JDBC readbacks, refresh stale managed metadata, and scope status updates by Store.

Validation:
- Fresh Java17 full Maven verify:274 PASS,0failures/errors/skips, including original232 +42pilot checks.
- H2/MySQL tests verify shared physical connection, mixed commit/rollback, native refresh, RC/RR, F04 REQUIRES_NEW, constraints and concurrent score/price preservation.
-18actual Swagger UI cases PASS on an isolated SIMULATED MySQLV10 dataset.
-59OpenAPI paths unchanged except runtime server URL; Product create/read/update/status,400/401/403/404/409 and persistence readbacks verified.
- Source DB inspected READ ONLY: remainsV5 with empty catalog; metadata/counts/Flyway history and private config/migration hashes unchanged.

Limitations:
- REAL_DATA_BLOCKED: no genuine populated retail source/copy or approved dump is available. Simulated food-store tests are not genuine-data tests.
- Existing sourceV5 is not deployment-ready; it needs separately approved backup/migration planning before running this app.
- Metadata update adds scoped lookup/refresh queries; no performance superiority claim.
- Two stale temporary QA configs were unavailable and were not used.

Rollback: restore/revert the pilot code and JPA foundation together to the baseline JDBC artifact. No production schema rollback/backfill is required because the pilot introduces no DDL. Do not repair/reset the source DB or regenerate historical data.

Reports: docs/testing/JPA_PILOT_SWAGGER_VERIFICATION.md, docs/testing/JPA_PILOT_REAL_DATA_VERIFICATION.md, docs/architecture/JPA_PILOT_CODE_REVIEW.md.
```

## File and safety checklist

- Pilot production scope: `pom.xml`; ProductRepository facade; internal ProductEntity/ProductJpaRepository/ProductMetadataPersistence; PersistenceConfiguration; persistence.properties.
- Test scope: Product mapping/transactions/MySQL proofs, disposable clone safety/API tests; Auth test schema setup only.
- Follow-up scope: the four requested verification/review/readiness documents and sanitized evidence under `docs/testing/evidence/jpa_pilot_review/`.
- Exclude application.yml, .env, SQL dumps, private target/credential files, full raw test logs and unrelated audit/feature files from staging.
- Source code/migrations were not modified during this verification task. git diff --check passes.
- Genuine-data gate remains BLOCKED; source migration/deployment NOT RUN. Category migration NOT IMPLEMENTED.

## Commands and evidence

Fresh command: see the latest-run section in [database verification](../testing/JPA_PILOT_REAL_DATA_VERIFICATION.md). Raw log `/tmp/paspm-jpa-review/verify.log`; persistent per-suite results [fresh-test-results.json](../testing/evidence/jpa_pilot_review/fresh-test-results.json). Actual UI observations and response assertions [SWAGGER_RESULTS.json](../testing/evidence/jpa_pilot_review/SWAGGER_RESULTS.json).

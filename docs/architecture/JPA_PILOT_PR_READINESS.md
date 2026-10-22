# Product JPA Pilot — PR Readiness

Date: 2026-10-22, Asia/Ho_Chi_Minh. Branch: `Khoi/Fear/jpa-product-metadata-pilot`; base: `dev`.

**Status: PR_CREATION_BLOCKED; NOT READY TO MERGE.** Implementation remains limited to CORE-00 and Product metadata. Category migration has not started.

## GitHub status

- The Pilot branch exists on the configured public repository and was pushed successfully.
- GitHub comparison confirms the implementation branch descends from current `dev` (`3d58a73543db3b5923289f901f9ffecba6228d23`) without being behind it at this check.
- No matching PR exists. Retrying the GitHub app returns HTTP 403, `Resource not accessible by integration`.
- `gh` is not installed, so there are no existing CLI credentials to check or use. No CLI was installed and no alternate credential source was used.
- No reviewer has been assigned and no approval exists. No reviewer identity or repository approval policy is invented.
- No CI status was registered for the pushed implementation commit when checked; required PR checks/review threads/merge conflicts cannot be established without a PR. Repository protection policies have not been bypassed.

Manual creation: [compare Pilot against dev](https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/compare/dev...Khoi%2FFear%2Fjpa-product-metadata-pilot?expand=1). Select **base `dev`**, **compare `Khoi/Fear/jpa-product-metadata-pilot`**, use the title/body below, and create the PR for independent review. An eligible repository maintainer must assign reviewers according to the actual policy. Creating a PR does not authorize merging past the outstanding migration gate.

## Merge gates

| Gate | Status |
|---|---|
| Java 17 full verification, MySQL/SPMF enabled | PASS: 277 tests, zero failures/errors/skips |
| Fresh MySQL V1→V10 + Hibernate validation + Category/Product actual HTTP | PASS |
| V5 Catalog + snapshot header, no matrix rows → V10 | PASS, fingerprints and mixed JPA/JDBC rollback preserved |
| V5 populated historical matrix → V10 | **FAIL: V7 foreign key error 1452; approved reconciliation strategy required** |
| Historical quoted V7/checksum recovery tests | PASS; repair only inside existing in-memory H2 recovery test |
| Original V5 source preservation | PASS, SELECT-only before/after proof |
| Genuine retail data | REAL_DATA_BLOCKED; all populated fixtures are synthetic |
| Required CI/reviewer approval and no blocking threads | BLOCKED / unavailable |
| Product PR merged | NO |
| Category migration entry | NOT SATISFIED |

## Ready-to-use PR title

`feat(backend): introduce JPA foundation and Product metadata persistence`

## Ready-to-use PR description

```markdown
Product metadata create/update/status writes now use Spring Data JPA. Product catalog reads and batch-derived stock projections stay on JDBC, as do Category CRUD, Inventory and the existing F03–F06 persistence paths. API DTOs and Flyway schema ownership are preserved.

CORE-00 adds Boot-managed JPA with a shared datasource/JpaTransactionManager, Hibernate mapping validation and disabled Open Session in View. F02-01 maps only Product metadata: S/M, inventory quantities and unapproved version columns are excluded. Explicit flush supports JDBC readbacks, refresh handles stale managed metadata, and status writes are Store-scoped.

Verification:
- Fresh Java 17 full Maven verify: 277 PASS, 0 failures/errors/skips. The previous 274 tests all executed, plus three new migration checks. There are 147 H2, 81 MySQL and 49 unit/runtime cases.
- Product JPA/JDBC shared connection/commit/rollback, F04 REQUIRES_NEW, Store isolation, S/M and inventory preservation, F01–F06 regression, and real HUI-Miner/EFIM with a Java 25 child runtime executed.
- Dedicated MySQL 8.4.11 instance: fresh V1–V10, Hibernate validation, constraints and actual Category/Product HTTP roundtrips passed. V5 Catalog/snapshot-header upgrade with no matrix rows passed with matching fingerprints.
- IMPORTANT: an additional valid populated-V5 matrix fixture fails at V7 with fk_utility_matrix_transaction / error 1452. Its characterization test asserts the known failure and preservation of original rows; it does not mean this upgrade path passes. Do not merge until an approved reconciliation strategy resolves this gate.
- Earlier interactive Swagger evidence records 18 cases and 59 unchanged OpenAPI paths. No new interactive Swagger session was claimed in this follow-up; fresh HTTP/API/OpenAPI regression checks ran.
- Original configured source remains V5 with empty Catalog. SELECT-only before/after probes show identical counts, columns, Flyway history, configuration and migration hashes.

REAL_DATA_BLOCKED: no authorized genuine populated retail copy is available. All populated fixtures are simulated; production deployment remains unverified.

This pilot adds no schema migration. Existing V7 SQL was not changed or repaired on MySQL/source. Roll back the pilot/foundation together to restore prior JDBC Product writes against the same schema; never reset source history or regenerate immutable data as part of rollback.

Reports: docs/testing/JPA_V5_TO_V10_MIGRATION_VERIFICATION.md, docs/testing/JPA_PILOT_REAL_DATA_VERIFICATION.md, docs/testing/JPA_PILOT_SWAGGER_VERIFICATION.md and docs/testing/evidence/jpa_phase2/.
```

## Scope and safety

Only the new MySQL acceptance test and sanitized verification/readiness documents are added in this follow-up. Product/Category production implementation and migrations are unchanged. Unrelated local F03/F06/audit documents, private `application.yml`, `.env`, dumps and temporary instance files are excluded from staging. No merge or deployment is performed.

The dedicated test server is disposable and is stopped after verification. The source is not a test target. The populated-V5 failure and GitHub PR permission issue are independent blockers; fixing permissions alone does not satisfy the merge gate.

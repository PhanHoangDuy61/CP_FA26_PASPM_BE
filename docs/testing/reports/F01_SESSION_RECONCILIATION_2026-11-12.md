# F01 reconciliation with dev — 2026-11-12

Status: local engineering verification PASS; merged and pushed to dev; no production/source database changes. Timezone Asia/Ho_Chi_Minh. User approved retaining dev V20/AuthSessionService and integrating additive protections before branch push/merge.

## Source and migration

Base dev42b8a9c; local feature689526b. V20 SHA-256 unchanged: `534003c0baf641960aac34ff5d5ad03b35a468f46d283593bf742e692d7c0002`. V21 only adds nullable BINARY(32) csrf_hash. Existing tokens/metadata/hash bytes are preserved; NULL-bound old tokens require sign-in again rather than fabricated history. No parallel session controller/service remains. No F03–F06 business logic or original database changed.

## Tests actually executed

- Canonical-dev red: 11 session cases, 8 failures reproducing missing stored binding/Origin/family JWT checks and OpenAPI requirement override. Zero setup errors.
- Initial green: 28 Auth + 14 Session + 1 V21 H2 migration =43 PASS.
- Additional failing tests proved missing cookie clearing on refresh401; controller catches both unknown/missing-token and service401 cases and expires both cookies. No failure was relabelled PASS without rerun.
- Final `mvn clean verify` (Java17.0.21, separate Java25.0.1 pinned SPMF): **594 discovered / 399 PASS / 195 SKIPPED, zero failures/errors**, exit0. [Per-suite counts](F01_SESSION_RECONCILIATION_VERIFY_COUNTS.json). Fresh target eliminates the stale-report count issue from earlier variant.

Command (substitute your own runtime/JAR paths):

```sh
mvn clean verify -Dpaspm.test.spmf-jar="$SPMF_JAR" -Dpaspm.test.spmf-java="$SPMF_JAVA" -Dpaspm.mining.java-executable="$SPMF_JAVA"
```

## Independent MySQL and runtime

Actual disposable Docker MySQL **8.4.13**: **29 PASS / 0 SKIPPED**, zero errors/failures. Suites: SessionMySqlIntegrationTest15, SessionV21MigrationMySqlTest1, FlywayRecoveryTest2, JpaPilotMigrationMySqlTest4, MiningMigrationMySqlTest5, RecommendationMigrationTest2. [MySQL counts](F01_SESSION_RECONCILIATION_MYSQL_COUNTS.json).

The populated V20→V21 fixture verifies unchanged session fields, token hash bytes and V20 checksum, NULL binding retained, exactly one migration, and a rerun applying zero migrations. Fresh V1→V21 and existing historical Matrix/JPA recovery paths also passed in their named synthetic suites. No production/source schema was used.

Actual Java17 application on an independent MySQL runtime schema, port18092: **11 HTTP cases PASS** (unauthenticated me401; login200; me200; missing CSRF403; hostile Origin403; forged matching cookie/header403; rotation200; me200; logout without raw refresh cookie200; revoked me401; relogin200). [Redacted HTTP results](F01_SESSION_RECONCILIATION_HTTP.json). Swagger UI page returned200; actual runtime OpenAPI exported to docs/api/openapi.json, 92 operations, public login/google/refresh and Token.csrfToken verified. This was HTTP-client/schema verification, **not interactive Swagger or browser testing**. Temporary runtime and MySQL container were stopped.

Of 195 default-skipped tests, 28 were independently executed in this MySQL group (15 session +1 V21 migration +1 Flyway MySQL +4 JPA migration +5 mining migration +2 recommendation migration); the remaining167 were not executed by this task. The group's Flyway H2 case overlaps the default suite. Do not add counts as independent unique tests.

MySQL command uses -Dpaspm.test.mysql=true and the six named suites, PASPM_TEST_MYSQL_SERVER/USER/PASSWORD for the disposable server, plus the existing restricted restore provisioner with its disposable guard. Both MySQL selectors now include SessionMySqlIntegrationTest and SessionV21MigrationMySqlTest; Backend CI selectors include SessionHttpIntegrationTest/SessionOriginGuardTest/SessionV21MigrationTest. Selector scripts executed successfully; no hosted workflow was claimed.

## Engineering review

Independent read-only reviewer checked preservation, concurrency, CSRF/Origin and JWT family semantics. One cookie401 correctness issue was reported, observed RED, patched and included in the final verify. No remaining concrete blocker in final code review. This is engineering review, not fabricated human PR approval.

## Scope and limitations

Full verify includes F03/F04/F05/F06 regressions and security tests under their stated fixtures. Default opt-in skips are not current PASS; separate MySQL executions are counted separately with overlap called out. Real Google/production HTTPS ingress and hosted GitHub CI are NOT RUN in this reconciliation unless a later record explicitly supplies evidence. Historical real Customer browser evidence belongs to the earlier local variant, not this build; current HTTP checks are not browser/interactive Swagger testing.

Current source DB and running local old-variant Backend are preserved. A DB with the rejected local V20 schema must not use this build or automatic Flyway repair; a separate conversion procedure is required. Scheduler policies/gates remain unchanged. No deploy.

## Git status

Verified implementation merge: `c8b10ba`, parents `42b8a9c` and `689526b`. All local gates above pass; branch push and non-fast-forward dev integration are authorized by the user. Final remote refs/Git history determine publication; this evidence does not claim hosted CI or deployment. [Agent record](../../record-of-changes/2026-11-12/01-f01-dev-reconciliation.md).

## Final delivery — 2026-11-12

The feature branch was published at4e18157 and merged into dev with **7304e29**, parents42b8a9c and4e18157. `git ls-remote` confirmed remote dev at7304e292cf71ad59a7d7fa9860a722535be4c073. This is a real non-fast-forward merge; no source copy/force-push occurred.

After merge, reran Auth28 + Session15 + V21Migration1 + OriginGuard3 = **47 PASS /0 SKIPPED**, zero errors/failures. [Merged-state counts](F01_SESSION_RECONCILIATION_MERGED_COUNTS.json). Merged tree equals the published feature tree; source/CI selectors equal the fully verified c8b10ba implementation. No further production source change accompanies this closing documentation record.

Local BE workspace stayed on its original working branch with private config/diagram edits preserved. No source DB was touched, no deploy performed, and hosted CI remains NOT RUN. The separate delivery-docs branch records this already-confirmed code merge without adding new runtime behavior.

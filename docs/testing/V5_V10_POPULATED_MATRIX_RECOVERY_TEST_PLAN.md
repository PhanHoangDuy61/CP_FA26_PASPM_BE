# Populated V5 Matrix recovery — acceptance plan and actual results

Execution:2026-10-22–23, Asia/Ho_Chi_Minh. Final generated:2026-10-23T00:34:04.776583+07:00. Tested code:`9c19f2ab8e40d21a68378fde72bb2311ef349884`. Original V1–V10 plus additive V11.

**IMPLEMENTED / TESTED / VERIFIED; READY_FOR_REVIEW, not READY_FOR_MERGE.** All populated fixtures are SYNTHETIC. No deployment, source mutation, Product merge or Category migration.

## Executed suites

| Run | PASS | FAIL | ERROR | SKIPPED |
|---|---:|---:|---:|---:|
| Untouched dev baseline with all MySQL/runtime switches |232|0|0|0|
| Final independent recovery feature Maven verify |254|0|0|0|
| Detached Product Pilot plus recovery patch Maven verify |299|0|0|0|

The Product checkout retains Pilot baseline12d348e and applies the scoped recovery patch only for verification. Its old migration-version assertions are adapted to11; no production Pilot branch is rewritten/merged. The original negative no-callback V7 characterization still reproduces the unsupported migration failure; it is not counted as positive acceptance of populated upgrades. Positive acceptance uses the actual recovery callback.

## Mandatory scenarios and actual evidence

| Scenario | Actual execution/result |
|---|---|
|1 Fresh originalV1–V10 +V11|PASS: all migrate/validate; empty archive; modern Category/Product API and JPA/JDBC checks run in the detached compatibility suite.|
|2 V5 empty matrix with existing catalog/header|PASS: actual registered callback path without opt-in/manifest; Categories/Products/Batches/snapshots retained; six original-column SHA fingerprints match; live/archive/sales empty; F03 count/page/service returns empty matrix. Writes/F06 remain refused for unverified empty history.|
|3 Populated V5 primary acceptance|PASS: verifier executes exact backup restore, callback transitions before originalV7, chain reachesV11 and validates; four original rows survive with exact IDs/utility/metadata; zero sales created by migration.|
|4 Multi-Store same legacy transaction ID|PASS: StoreA andB share ID7; StoreA also has a multi-product transaction and ID8. Store pages/capture remain separate; cross-Store repository/service access is404.|
|5 Frozen replay|PASS: two Stores ×two real engines ×before/after =8 actual executions. Dataset records/text/hash/item mapping/divisor/scale, thresholds and exact pattern maps match.|
|6 Interruptions / partial MySQL DDL|PASS: controlled PREPARED/STAGED/AFTER_RENAME/COMPLETE callback interruptions resume through real Flyway without duplication. Actual unmodified V7 fails1452 on a disposable copy; failed copy is refused/preserved, and its verified clean backup copy upgrades through CURRENT B+C separately. No MySQL repair.|
|7 Invalid evidence/data|PASS: missing parents/Store, wrong Product Store, totals/duplicates, staged CHECK tamper/unknown stage, dump/restore/fingerprint tamper, unrelated/empty/cross-schema SQL artifacts, broad restore credential and existing matching copy all refuse. Archive/header/routing/Product ownership guards tested on MySQL/H2.|
|8 F03–F06 and Product compatibility|PASS: full suites include Weightv1.2, F04 idempotency/REQUIRES_NEW, F05 both real engines/runtime guards, F06 legacy/unverified409 and modern regressions, Product JPA/shared transactions/API/persistence.|

Fresh ordinary/upgradedV10 behavior is additive; no V7 replay or out-of-order injection. Unknown populated provenance stays blocked for semantic audit, not silently relabeled.

## Fixture and preservation

Populated reader fixture:2 Stores,2 Categories,3 Products,2 Inventory Batches,2 snapshots,4 historical matrix rows. All original V5 Store/Product/Category/Batch/header/matrix columns are hashed in deterministic primary-key order with length-prefixed canonical fields. Additive V8 fields are not confused with original values. Frozen source/matrix hashes and JSON are retained rather than recomputed.

StoreA original utility8: transaction7 has Product1 utility2 and Product3 utility4; transaction8 has Product1 utility2. StoreB transaction7/Product2 utility3. Dense item mapping2→Product3 verifies nontrivial identity conversion. StoreA exact normalized pattern map is `{1:2, 1,3:3, 3:2}`; StoreB is `{2:1}` for both engines. Divisors are2,000,000 and3,000,000; utilities are converted back with the stored divisor/scale. Pattern ordering is normalized by map identity, not compared by arbitrary output-line order; runtime/memory equality is not asserted.

The migration creates no sale parents. A deliberately simulated coincident sale and mixed live row are added only AFTER positive preservation/replay checks to test denial; they are not backfill or authentic retail evidence. Actual F03 service/MiningService capture and FrozenEvidenceReader execute, while auth/access and worker dispatch are mocked in the legacy-specific fixture. Separate existing HTTP/auth/runtime tests run in the full suites. Do not describe the legacy fixture as Swagger UI, full HTTP authorization or actual worker-completion coverage.

## Failures investigated and retested

- Initial missing-runtime baseline and concurrent-target compilation issues were runner/environment failures; isolated baseline with correct existing SPMF paths passed232.
- Controlled interruption exposed a journal INSERT rollback; durable explicit phase commits fixed it and checkpoint tests passed.
- Independent review reproduced ordinary archived Product Store mutation and unbound backup artifact acceptance. Product parent guard and verifier-controlled exact artifact restoration fixed both, including adversarial negative cases.
- V11 caused4 old expected-version assertions to fail; only counts were updated, preservation checks retained.
- Modern total mismatch initially changed400→409; original modern400 restored. New routing FK required cleanup ordering in isolated tests.
- Global test Flyway identity accidentally affected H2 during initial Product compatibility run; runner scope corrected,45 Product-specific checks then passed. Final full compatibility run is separate evidence.
- Whole-branch review found valid empty historical snapshots returning409;2 behavioral red cases were reproduced, then a narrow read-only empty route fixed them. Nonzero/mixed/unverified populated history still refuses.
- Some earlier focused runs intentionally omitted runtime opt-ins and had a skip; only final single-command verify totals above represent full acceptance. No wrong-directory build error is presented as behavioral red evidence.

## Reproduction requirements

Use a NEW isolated MySQL8.4 instance and UUID test schemas. Never point test env at the configured source. Tests use `PASPM_TEST_MYSQL_SERVER`, `PASPM_TEST_MYSQL_USER/PASSWORD` and explicit flags. MySQL binary logging requires a separate migration identity for trigger DDL. Runtime account is tested without SUPER; production must keep identities/grants separate.

The core tests require `PASPM_TEST_RESTORE_PROVISIONER`: a private test-DBA helper that provisions a NEW schema-only restore account, receiving generated secrets through stdin. The executed helper refuses schemas outside disposable prefixes and escapes grant underscores. This helper is testing infrastructure, not a production application dependency. Actual `mysql`/`mysqldump` binaries must be available. The production backup verifier itself enforces empty exact-schema restore grants.

```sh
# JAVA_HOME/PATH use Java17; credentials supplied privately, never as documented literals.
mvn -q verify -Dpaspm.test.mysql=true -Dpaspm.test.java17-backend=true \
  -Dpaspm.test.java17="$JDK17_HOME/bin/java" \
  -Dpaspm.test.spmf-jar="$APPROVED_SPMF_JAR" \
  -Dpaspm.test.spmf-java="$JDK25_HOME/bin/java" \
  -Dpaspm.test.runtime-credentials="$PRIVATE_RUNTIME_PROPERTIES" \
  -Dpaspm.test.legacy-reader-evidence="$PRIVATE_EVIDENCE_FILE"
# Detached Pilot also uses its explicit disposable clone/mutation opt-in and private config.
```

Final processes exited0; no installed tooling/dependencies were added. Source probes use SELECT-only/read-only connections and rollback; source remains MySQL8.4.11/FlywayV5 with empty catalog/matrix and identical history/schema/configuration/tenSQL hashes. No source or MySQL repair/clean/reset occurred. The existing controlled repair regression is H2 in-memory only.

## Evidence and limits

- [Every feature test and status](evidence/historical_matrix_recovery/implementation/TEST_RESULTS.json)
- [Every compatibility test and status](evidence/historical_matrix_recovery/implementation/PRODUCT_PILOT_COMPATIBILITY.json)
- [Six-table fingerprints and exact replay](evidence/historical_matrix_recovery/implementation/REPLAY_RESULTS.json)
- [Source before/after](evidence/historical_matrix_recovery/implementation/SOURCE_PRESERVATION.json)
- [Migration/source manifest and cleanup](evidence/historical_matrix_recovery/implementation/MIGRATION_MANIFEST.json)
- [Independent review history](evidence/historical_matrix_recovery/implementation/REVIEW_SUMMARY.md)

Earlier13 PoC observations remain in [POC_RESULTS.json](evidence/historical_matrix_recovery/POC_RESULTS.json); their original migration FAIL is historical evidence, not a fixed acceptance result. Current acceptance is the actual registered/callback-backed implementation and verified restore path above.

NOT_RUN: genuine retail mapping/backfill, production deployment, production-scale load/metadata-lock/disk/PITR exercise, every OS-kill/DDL boundary, legacy-specific Swagger UI/HTTP and real asynchronous worker orchestration. These limits do not authorize inventing missing association evidence. Human review/CI and separate release/merge approval remain required.

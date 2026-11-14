# ROC-20261115-001 — F07/F08 integration

Date: 2026-11-15, Asia/Ho_Chi_Minh.

## Scope and authorization
Owner authorized merging F07/F08 into dev and explicitly authorized pushing only that merge. F05/F03 implementation is not included. Source: d651230; dev base: 28a6b27. No deployment or source database access.

## Changed
Merge the existing F07/F08 implementation and additive V23. Existing migration files unchanged. Two regression tests had fixed migration counts dating from V21; replace those with pending-count assertions, validation and zero-pending checks. Retain checksum/data preservation checks; assert successful V21 explicitly.

## Verification actually executed
- Initial Java 17 Maven clean verify: 609 tests, 409 passed, 2 failed, 198 skipped. Failures: FlywayRecoveryTest expected 14 migrations, actual 16; SessionV21MigrationTest expected 1, actual 3.
- Targeted retest: 12 tests, 11 passed, zero failures/errors, one opt-in MySQL test skipped. F07/F08: 9/9 passed.
- Final Maven clean verify: see F07_F08_MERGE_TEST_COUNTS.json for exact totals and per-class skips. BUILD SUCCESS.
- Command: `mvn --batch-mode --no-transfer-progress clean verify -Dpaspm.test.spmf-java=<Java25>/bin/java -Dpaspm.test.spmf-jar=<approved-spmf.jar> -Dlogging.level.root=ERROR`, JAVA_HOME=Corretto 17.0.21.
- SPMF banner 2.67a and SHA-256 4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a verified.
- H2 disposable databases only; MySQL and other opt-in suites NOT RUN. No claim of complete MySQL/Linux/scheduler acceptance or hosted CI success.
- Initial/final logs retained locally in /private/tmp/paspm-f07-merge-baseline.log and /private/tmp/paspm-f07-merge-verify.log.

## Compatibility and preservation
No F03 formula, F05 submission RBAC, scheduler flag, frozen data, or Word report changed by this merge. Existing BE uncommitted documentation remains untouched.

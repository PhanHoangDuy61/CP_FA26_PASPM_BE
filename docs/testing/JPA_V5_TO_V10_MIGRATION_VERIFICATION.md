# Product Pilot / B+C migration verification update

Updated:2026-10-23T00:34:04.776583+07:00. This recovery feature is independent of Product Pilot source changes; branch from dev3d58a73. OriginalV1–V10 unchanged; additiveV11 follows them.

## Current result

- Recovery feature:`254` executed PASS,0FAIL/ERROR/SKIPPED; Maven verify exit0.
- Detached Product Pilot baseline12d348e +recovery patch:`299` executed PASS,0FAIL/ERROR/SKIPPED; Maven verify exit0. Original Pilot branch/head/configuration untouched.
- PopulatedV5 through the REGISTERED callback and verified artifact-restoration workflow reachesV11 without1452 and preserves original matrix/header/catalog fingerprints. Multi-Store local IDs remain distinct. EmptyV5 preserves rows and empty matrix reads.
- Actual failed originalV7 disposable copy is refused/preserved; separate restored clean target upgrades with CURRENT B+C. No source/MySQL repair/reset or fake sales backfill.
- Original configured database remains read-onlyV5/empty, with identical before/after schema/history/counts/configuration/tenSQL hashes.

The historical277-pass Pilot run and its V7 failure characterization are previous evidence at [12d348e](https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/tree/12d348e). A characterization PASS never fixed that failure. Current positive migration acceptance is described in the [recovery test report](V5_V10_POPULATED_MATRIX_RECOVERY_TEST_PLAN.md), distinct from the still-valid negative test of a plain Flyway builder lacking the required callback.

The detached compatibility harness changes only old version-count expectations toV11 in the Pilot-specific migration test. Product JPA implementation is not copied into this feature or merged. The extra45 Pilot tests include real Category/Product HTTP, schema/JPA mapping validation, shared JDBC/JPA connection/isolation, commit/rollback, native stale-state behavior, concurrency and opt-in disposable clone checks.

All populated data is simulated. Genuine retail-copy/backfill remains REAL_DATA_NOT_AVAILABLE. F06 legacy association generation remains deliberately blocked pending verified membership; it cannot infer support/confidence from utility. Production-scale operational rehearsal, human review/CI, release approval, Product merge and Category entry are separate gates.

Evidence: [feature tests](evidence/historical_matrix_recovery/implementation/TEST_RESULTS.json), [detached compatibility](evidence/historical_matrix_recovery/implementation/PRODUCT_PILOT_COMPATIBILITY.json), [source preservation](evidence/historical_matrix_recovery/implementation/SOURCE_PRESERVATION.json), [replay](evidence/historical_matrix_recovery/implementation/REPLAY_RESULTS.json).

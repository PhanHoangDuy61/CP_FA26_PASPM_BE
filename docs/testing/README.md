# Testing and Verification

## Evidence is scoped

- **Historical execution:** recorded test counts, dates, commit/schema/runtime and actual responses. Keep them intact; do not read old PASS as current dev acceptance.
- **Local verification:** code/test behavior under the named local fixtures and opt-ins. Synthetic data are not real retailer data.
- **Hosted CI:** an actual GitHub run for the relevant head and required jobs. Local results or parsed YAML are not hosted PASS.
- **Real-data acceptance:** needs an authorized genuine dataset/source clone and recorded preservation evidence. Existing REAL_DATA_BLOCKED labels are not resolved by this reorganization.

## Report navigation

| Area | Reports / evidence |
|---|---|
| F01 | [Original tests](../features/F01/reference/TEST_RESULTS.md), [Swagger checklist](../features/F01/reference/SWAGGER_CHECKLIST.md) |
| F02 | [Original Swagger report](../features/F02/reference_v3/F02_SWAGGER_TEST_REPORT.md), [cases](../features/F02/reference_v3/F02_SWAGGER_TEST_CASES.md) |
| F03 | [Original linear-era report](../features/F03/reference_v4/F03_SWAGGER_TEST_REPORT.md), [current Catalog/Utility verification](reports/RETAIL_CATALOG_UTILITY_VERIFICATION.md) |
| F04 | [CSV verification](../features/F04/reference/F04_SWAGGER_TEST_REPORT.md) |
| F05 | [Historical mining verification](../features/F05/reference/F05_VERIFICATION.md), [P0 report](../audits/F05_P0_DEPLOYMENT_FIX_REPORT.md) |
| F06 | [Verification](reports/F06_VERIFICATION.md), [captured evidence](evidence/feature_verification) |
| JPA | [Pilot real-data report](JPA_PILOT_REAL_DATA_VERIFICATION.md), [Swagger](JPA_PILOT_SWAGGER_VERIFICATION.md), [V5→V11 verification record](JPA_V5_TO_V10_MIGRATION_VERIFICATION.md) |
| Recovery | [Populated matrix plan](V5_V10_POPULATED_MATRIX_RECOVERY_TEST_PLAN.md), [evidence](evidence/historical_matrix_recovery) |
| CI | [Remote verification](BACKEND_CI_REMOTE_VERIFICATION.md), [SPMF readiness](SPMF_CI_READINESS.md), [setup](../setup/BACKEND_CI.md) |

File names V5_TO_V10/v4 are retained identifiers, not a promise that their contents omit later V11 changes. Read the report body and source authority.

## Paths and preservation

Existing testing evidence directories remain in place. Raw F06/Catalog verification evidence formerly under requirements moved together to `testing/evidence/feature_verification`; links were updated, bytes were not. Captured manifests can contain original paths; resolve them through [DOCUMENTATION_MAP](../DOCUMENTATION_MAP.md), not by rewriting frozen metadata.

The docs reorganization runs link/hash/structure validation only. No new Maven, Swagger Execute, database or UI workflow verification is claimed. [Reorganization report](../audits/DOCS_REORGANIZATION_REPORT.md).

## F04 / F06 improvement verification

[Combined performance review](F04_F06_PERFORMANCE_REPORT.md) and [F06 hardening report](../features/F06/F06_HARDENING_REPORT.md) distinguish measured local results, injected failures, the successful retest and the five populated-clone cases not run. [F06 current counts](evidence/f06_hardening/FULL_VERIFY_RETEST.json) do not certify GitHub-hosted CI or production acceptance.

# DEC-20261125-05 — CI skipped-test policy: explicit allowlist and a manual full workflow

| Field | Value |
|---|---|
| Date | 2026-11-25 (Asia/Ho_Chi_Minh) |
| Source | Product Owner instruction in chat on 2026-11-25, after the first full CI run (PR #29, 872 tests, 6 failures, 12 skipped) |
| Status | OWNER_DECISION, implemented on branch `Khoi/chore/spmf-ci-artifact`; PR to `dev` pending |
| Feature | CI (all features) |
| Related | DEC-20261125-03 (SPMF in CI), `docs/setup/BACKEND_CI.md`, ROC-20261125-007 section 10 |

## Context
- `.github/scripts/test-summary.py` failed the `integration` job on **any** skipped test. The full suite always has opt-in tests that stay skipped on a hosted runner, so the job could never be green.
- Measured on 2026-11-25 (Linux amd64, the `integration` job flags, 882 tests): exactly 12 skipped, all opt-in:

| Tests | Opt-in flag | Why it is not enabled in the PR job |
|---|---|---|
| `FoodMartScaleSyntheticE2EMySqlIntegrationTest` ×3 | `paspm.test.foodmart-e2e=true` | long synthetic lifecycle |
| `MiningSchedulerMultiJvmMySqlTest` ×2 (crash point 3, stale owner) | `paspm.test.natural-lease=true` | wait for the real 300 s lease expiry, about 14 minutes |
| `MiningSchedulerUpgradeCompatibilityMySqlTest.characterizeTheOldBackendRunningOnTheNewSchema` | `paspm.test.legacy-jar=<jar>` | needs a jar of the old backend |
| `MiningConversionStabilityTest` ×1 | `paspm.test.stability-matrix=<TSV>` | needs a frozen utility matrix (private data) |
| `PilotCloneApiIntegrationTest` ×5 | `paspm.test.jpa-clone=true` | needs an authorized disposable clone of a real database |

## Decision
1. **Strict by default.** `test-summary.py` still fails on any failure, any error, zero tests, and any skipped test.
2. **Explicit allowlist.** `.github/scripts/allowed-skips.txt` lists each allowed skipped test (`Class#method`) with its opt-in flag, profiles, runtime and reason. Only the `integration` job passes `--allowed-skips … --profile pr`; the `backend` and `mysql` jobs stay strict with no allowlist. A skip that is not listed, or that no testcase identifies, fails. The summary prints every allowed skip that occurred.
3. **No silent growth.** Adding a test to the allowlist is a reviewed change in a pull request with the reason in the file. A new opt-in test must be added there in the same PR.
4. **Manual full workflow.** `.github/workflows/backend-full-verification.yml` (`workflow_dispatch` only) runs the full suite with every opt-in that can run on disposable synthetic data (`natural-lease`, `foodmart-e2e`, the import benchmark, and `legacy-jar` when the input `legacy_ref` names an old commit). Run it before a review or a release. Its summary uses profile `full`, where only the tests that need private data stay allowed (`stability-matrix`, `jpa-clone`, and `legacy-jar` without `legacy_ref`). The regular PR job's test selection is unchanged.
5. The five `jpa-clone` tests and the `stability-matrix` test are run by a person against an authorized clone or a frozen matrix; they are never enabled in CI.

## Consequences
- The `integration` job can be green with the 12 documented skips and still fails on an undocumented one.
- "All opt-ins enabled" is limited by data CI does not have; the allowlist states which tests that is and why.
- Native Linux acceptance (`scripts/verify-native-linux.sh`) remains a separate, human-run step.

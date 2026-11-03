# Final CI delta review

Read-only source/config/document review in `/tmp/paspm-legacy-matrix-recovery`; no test, Docker, database, credential or GitHub approval actions. No application changes are present in the inspected working diff. Earlier CI review otherwise stands.

## Verified finding

### LOW — Summary unit tests append synthetic results to the real GitHub job summary

- File: `.github/scripts/tests/test_ci_summary.py:17`.
- Evidence: `run_summary` launches `test-summary.py` without an isolated environment, so subprocesses inherit the Actions-provided `GITHUB_STEP_SUMMARY`. The production summary script appends to that path at `.github/scripts/test-summary.py:17–19`. The integration workflow runs these tests at `.github/workflows/backend-verification.yml:148–149`. Consequently the summary of a successful integration job includes synthetic NOT_RUN, zero/skipped/failure/error counts and a one-test PASS from helper fixtures, alongside the later real Maven results. This does not bypass the aggregate gate, but it misrepresents fixture outputs as actual Backend verification in the review evidence.
- Fix: remove `GITHUB_STEP_SUMMARY` from each fixture subprocess environment, or direct it to a file beneath that fixture's temporary directory. Test stdout capture can remain as implemented.

## Other reviewed delta

- `provision-spmf.py:15–25` rejects query/fragment/embedded-credential URLs both initially and on redirects; the exact checksum and fail-closed provisioning remain unchanged.
- `test-summary.py:20–21` now rejects missing reports as well as zero cases, skipped cases, errors and failures. The four added summary tests exercise those branches and sanitized property handling; their environment isolation needs the fix above.
- The gate report/readiness/compatibility/CI setup/progression documents distinguish local verification from remote NOT_RUN, retain SPMF_INTEGRATION_CI_BLOCKED, REVIEWER_PENDING and REAL_DATA_BLOCKED, identify the recovery merge as external, and keep Product/Category progression blocked. Historical detached Product counts are not presented as a current integration. This reviewer did not independently recheck external GitHub facts or execute the reported local tests.

Assessment: one LOW reporting defect; no verified CRITICAL/HIGH/MEDIUM defect in this final delta. This is agent source review, not independent human GitHub approval, remote CI success, or progression authorization.

## Fix verification disposition

**LOW finding CLOSED by source inspection.** `.github/scripts/tests/test_ci_summary.py:19–21` now copies the parent environment, removes `GITHUB_STEP_SUMMARY`, and passes the isolated environment explicitly to every fixture subprocess. Synthetic summaries can no longer append to the actual Actions summary through that inherited variable. The added regression test (`:41–47`) supplies an existing job-summary file through the parent environment and verifies its contents remain unchanged after invoking the fixture. This tests the reported failure mechanism directly.

Parent reports the regression failed before the fix and all 13 helper tests passed afterward; I inspected the fix/test source without rerunning tests. No application/pom/test-suite source changes appeared in the inspected diff. No outstanding verified source findings remain from this review. Remote CI, SPMF artifact provisioning, independent human approval and progression gates remain separate and unchanged.

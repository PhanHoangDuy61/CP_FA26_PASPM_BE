# Recovery PR #1 — review and CI gate report

Execution date: 2026-10-23, Asia/Ho_Chi_Minh. Overall progression: **BLOCKED**. No merge or deployment was performed by this task.

## Actual GitHub state

[Recovery PR #1](https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/pull/1) was merged by `khoinm04` at 2026-10-23 02:10:21 +07, before this audit. Merge commit: `691371b9a283208f41269f0956b1386a045fe27a`; recovery head: `3a9c66632cd52f2a0a6720977aff10828d5176e6`. Its description was empty; the prepared description appeared in an author discussion comment. The audit found 39 changed files, zero submitted reviews, zero inline review threads, zero requested reviewers and no checks/statuses/Actions runs at the recovery head.

| Gate | Verified status |
|---|---|
| Recovery PR | MERGED externally; existing PR #1 reused for audit |
| Independent human reviewer | REVIEWER_PENDING |
| GitHub CI | Initial workflow validation FAIL; Backend jobs NOT_RUN; SPMF_INTEGRATION_CI_BLOCKED |
| Recovery merge by this task | NOT_PERFORMED; pre-existing merge was not reverted |
| Product integration onto current dev | BLOCKED_REVIEW_AND_CI |
| Product PR | NOT_CREATED; repository PR search found none |
| Product merge | NOT_PERFORMED |
| Category entry | NOT_STARTED; prerequisites unmet |

Candidate `PhanHoangDuy61` and author `khoinm04` are different account names. This does not establish different people. Candidate permission lookup returned 403; no review request or approval was fabricated. Public dev metadata reports protected=false, active rules=[], rulesets=[]. Authenticated policy/collaborator REST probes returned 401. No required-check configuration or equivalent independent-approval gate was verified. These facts do not waive Team Lead requirements.

## Executed local verification

Source: dev merge `691371b`, application source tree fingerprint `4d326c0b161ff4916e720a83be27fe0f5cd9d40c2ddb5c6afb61ab6d9d63d736`. Java 17 Backend; separate Java 25 child; disposable MySQL 8.4.11. Approved existing SPMF JAR verified as 2.67a and SHA-256 `4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a`.

| Execution | PASS | FAIL / ERROR / SKIPPED | Evidence |
|---|---:|---|---|
| Full Maven clean verify, all current opt-ins | 254 | 0 / 0 / 0 | [Per-case results](evidence/pr1_review_gates/RECOVERY_TEST_RESULTS.json) |
| Selected Backend foundation without SPMF | 135 | 0 / 0 / 0 | [Group results](evidence/pr1_review_gates/CI_LOCAL_GROUP_RESULTS.json) |
| Selected MySQL group without SPMF | 33 | 0 / 0 / 0 | Same group evidence |
| Python CI helper tests | 13 | 0 / 0 / 0 | [Helper results](evidence/pr1_review_gates/CI_HELPER_RESULTS.json) |

The 135 and 33 cases are repeated subsets of the 254, not additional unique Backend tests. Full-suite module counts: auth 25, catalog 8, deployment 18, mining 49, recommendation 63, transaction 25, utility 66. The older detached Product compatibility run of 299 cases is historical evidence, not a new integration or execution in this task. Product JPA classes are absent from this dev revision; current CI cannot claim to run Pilot-specific JPA/JDBC tests. Those must be enabled and verified when a separately approved Product integration is proposed.

## Recovery acceptance and negative tests

- Fresh V1–V11, empty V5, populated V5 with registered B+C callback, already V10 and duplicate Store-local transaction references passed their assertions.
- Snapshot/matrix/catalog fingerprints across six tables were preserved. The replay fixture has two Stores and four matrix rows; eight actual HUI-Miner/EFIM runs compared pre/post datasets and patterns exactly. See [replay evidence](evidence/pr1_review_gates/HISTORICAL_REPLAY.json).
- Backup verification, restore binding, archive protection, Store ownership, tamper and invalid checkpoint refusals executed. No sales transactions were fabricated to satisfy legacy references.
- The original unsupported V7 path still produces MySQL 1452 on a disposable copy. The supported path refuses that partially migrated copy and succeeds on a separately restored clean target. The failure copy was preserved. A passing negative characterization is not reported as successful unsupported migration.
- F06 refuses unverified historical membership with 409. Legacy service tests mock authentication/worker boundaries; they are not a complete historical HTTP-to-worker rehearsal. Existing modern HTTP tests are included in the full suite.
- F04 transaction/idempotency regressions passed. An H2-only checksum characterization repairs an in-memory test database; no MySQL repair was run.
- Recovery source review found no verified defects. Final CI review found one LOW issue: summary test fixtures inherited GITHUB_STEP_SUMMARY and could append synthetic counts to the real job summary. The fixture subprocess now removes that variable. A new regression test failed before the fix and passed afterward; all 13 helper tests passed. No unresolved finding remains in this scoped review. They are [agent reviews](evidence/pr1_review_gates/RECOVERY_SOURCE_REVIEW.md), not independent human GitHub approval.

## CI implementation and limitations

The separate branch `Khoi/chore/recovery-review-ci-gates` prepares three jobs (Backend foundation, MySQL/Flyway, full F01–F06/SPMF) and an aggregate technical gate. It uses read-only GitHub permissions, pinned official Actions, Java 17 plus separate Java 25, disposable loopback MySQL and schema-scoped restore credentials. Missing reports, zero cases, skipped cases, errors or failures cannot pass the test summary. Missing approved SPMF URL fails provisioning before integration tests; aggregate CI then fails.

Workflow YAML and embedded Bash syntax were checked. The restore helper was exercised against the disposable server and its grants verified. One initial grant-check harness assertion mishandled escaped MySQL output; rechecking with raw output passed without changing the provisioner. The Linux Docker CLI wrapper and GitHub-hosted jobs have **NOT_RUN**. Local tests are not remote CI evidence. See [workflow validation](evidence/pr1_review_gates/CI_VALIDATION.json), [runtime](evidence/pr1_review_gates/RUNTIME_VERIFICATION.json), [restore check](evidence/pr1_review_gates/CI_HELPER_MYSQL.json), and [missing URL refusal](evidence/pr1_review_gates/ARTIFACT_PREREQUISITE.json).

No owner-approved immutable artifact URL is available. No JAR was uploaded or committed. Safe publication and configuration steps are in [Backend CI](../setup/BACKEND_CI.md). Technical job success alone would still leave human review pending.

## Source preservation and remaining gates

The configured original MySQL remained V5, empty of catalog/matrix rows, accessed by SELECT/ROLLBACK on read-only connections. Before/after schema, history, counts, private configuration hash and original V1–V10 file hashes match. Original Product branch/head remains `12d348e`; private configuration and unrelated working changes are untouched. All populated data in this run is synthetic: **REAL_DATA_BLOCKED**. See [source preservation](evidence/pr1_review_gates/SOURCE_PRESERVATION.json).

Before further integration: verify an eligible independent human reviewer; complete genuine review; approve lawful artifact hosting and the exact immutable URL; execute all remote CI jobs against the latest proposed head; establish required review/check policy or an explicit Team Lead equivalent. The latest user instruction prohibits merge, deployment and source database changes even after these technical tasks. Product integration and Category require a subsequent authorized progression decision.

Delivery and test-instance cleanup are recorded in [delivery status](evidence/pr1_review_gates/DELIVERY.json) and [cleanup](evidence/pr1_review_gates/CLEANUP.json).

## Publication outcome

The dedicated CI branch was pushed successfully. Creating its draft PR returned **403 Resource not accessible by integration**; no CI PR was created. GitHub recorded a failed workflow validation run at the first published head before any jobs executed. Use the [manual comparison](https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/compare/dev...Khoi%2Fchore%2Frecovery-review-ci-gates?expand=1) with the [prepared description](../architecture/BACKEND_CI_PR_DESCRIPTION.md). Remote CI remains NOT_RUN; reviewer and artifact prerequisites remain pending. No retries through alternate identities, merge or deployment occurred.

### Workflow validation correction

[Run 39878437809](https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/actions/runs/39878437809) at `fde0322` ended FAILURE with zero jobs and zero check runs. Inspection found `runner.temp` in job-level env, where GitHub does not allow the runner context ([official context availability](https://docs.github.com/en/actions/reference/workflows-and-actions/contexts#context-availability)). The workflow now exports SPMF_JAR_PATH from a runtime shell step using RUNNER_TEMP and GITHUB_ENV. YAML/Bash checks pass after correction. This was a CI configuration defect; no Backend tests ran remotely. Full GitHub runtime verification remains required.

# Backend CI remote verification — investigation and execution

2026-10-23, Asia/Ho_Chi_Minh. Branch `Khoi/fix/backend-ci-remote-verification` from current dev `783725c7c8aac810f43fce4971a3b45ce6562a94`.

## Plan and boundaries

1. Inspect run 39879145935 and trace the actual restore command. Reproduce Ubuntu 24.04 / Java 17 / MySQL 8.4 Docker CLI behavior; collect safe diagnostics before changing behavior.
2. Add a regression that demonstrates the cause. Fix only workflow/test tooling or necessary diagnostics; preserve production recovery safeguards and V1–V11.
3. Re-run all six affected cases, the complete MySQL group, and applicable Backend regression. Separate local container evidence from GitHub-hosted results.
4. Reverify local SPMF binary/hash/banner; prepare publication procedure without uploading. Keep full integration unsuccessful without approved URL.
5. Inspect review/policy metadata; do not equate distinct GitHub account names with distinct humans or retroactively approve merged PRs.
6. Commit/push scoped changes and create a dev-targeted fix PR if permitted. Do not merge, deploy, integrate Product or start Category.

Original configured V5 database is read-only. Only disposable synthetic schemas may be written. No raw logs containing credentials/options/dumps are committed. Final actual results are recorded below after execution.

## Initial evidence

GitHub run 39879145935: foundation SUCCESS; MySQL group 33 tests, 0 failures, 6 errors, 0 skipped. All six errors originate in fixture creation at LegacyBackupVerifier.restoreExactArtifact. The group fails before positive recovery assertions execute. Full integration fails at missing SPMF artifact prerequisite; aggregate fails. Runner Ubuntu 24.04.5, MySQL 8.4.13 image digest `sha256:76f0358884cb92d9a086d24e5671c38048d3fef40f2588703d73eb55392d6b69`.

Raw MySQL CLI stderr was intentionally suppressed by the verifier. The existing log does not prove the underlying client exit reason; reproduction/diagnostics are required.

## Verified root cause and minimal correction

A local Ubuntu 24.04 amd64 container with Java 17 and the exact failed-run MySQL 8.4.13 image reproduced **33 tests, 6 errors, 0 failures, 0 skipped**. This is a Linux reproduction on Docker Desktop, not a GitHub-hosted run or an identical Azure runner image.

The added real-client regression produced `exit=1, hostFileReadable=true, withinSharedTemp=false, defaultsFileError=true`. The client failed to open the required defaults file before authentication or SQL restore. The file existed on the runner with owner-only permissions but was outside the directory mounted into the CLI container. Java's temporary-file path was initialized before Surefire applied the Maven user property. Passing `-Djava.io.tmpdir` as a Maven property changed the visible property too late for the cached temporary-directory path.

Both MySQL-enabled workflow jobs now pass the property via Surefire `-DargLine`, so the forked JVM receives it at startup. The same regression then returned `exit=0, hostFileReadable=true, withinSharedTemp=true, defaultsFileError=false`, and confirmed MySQL 8.4 client compatibility. This follows [Surefire's distinction between startup JVM options and later system properties](https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html#systemPropertyVariables). No application recovery code, CLI stderr suppression, schema privileges, empty-schema checks, artifact hashes, read-only mounts or V1–V11 changed.

Safe diagnostics contain only exit code and boolean file-access/category flags, never file contents, credentials, SQL, dumps, schema names or raw stderr. The regression intentionally uses an empty `[client]` option file and the client's version command. Existing real dump/restore tests remain the acceptance criteria; the probe alone does not prove restoration succeeds.

The workflow also triggers on pushes to this exact repair branch to allow genuine remote verification even when the GitHub App cannot create a PR. PR-to-dev and manual triggers remain. No privileged event or deployment step was added.

## Completed local MySQL acceptance

After correction, the full Ubuntu/MySQL group completed Maven clean verify with **34 PASS, 0 failures, 0 errors, 0 skipped**. LegacyRecoveryMySqlTest completed all 11 cases, including the original six failing cases and the new actual-client probe. The six-case fixture failure is resolved in this container reproduction. Positive migration, populated V5 recovery, archive integrity, partial-V7 refusal/clean-copy recovery, ownership/backup guards and F04 transaction tests all executed. The unsupported original V7 failure remains an expected negative assertion; it is not presented as a successful unsupported migration. Per-case evidence: [after MySQL results](evidence/backend_ci_remote/AFTER_MYSQL_RESULTS.json).

Agent review found no critical/important issue; a minor probe timeout cleanup was applied. Agent review is not human GitHub approval.

## Completed regression and remote outcomes

Full local Maven clean verify under Java17 with separate Java25/exact local SPMF and disposable MySQL8.4.13: **255 PASS, 0 failures, 0 errors, 0 skipped**. This run was on macOS with Docker CLI, distinct from the Ubuntu group reproduction and the GitHub-hosted run. All existing F01–F06 regressions were discovered; Product JPA suites are absent from this dev revision and cannot be claimed. [Per-case full results](evidence/backend_ci_remote/FULL_LOCAL_RESULTS.json).

| GitHub job | Before: run39879145935 | After: run39909707415, commit9f362db |
|---|---|---|
| Java17 foundation | SUCCESS | SUCCESS; 135 tests, zero failures/errors/skips |
| MySQL/Flyway/F04 | FAILURE; 33 tests, six errors | SUCCESS; 34 tests, zero failures/errors/skips |
| LegacyRecoveryMySqlTest | Ten cases, six errors | Eleven cases PASS, including all original six and added probe |
| Full integration | Failed missing artifact prerequisite | Still fails missing artifact prerequisite; Backend tests NOT_RUN |
| Required aggregate | FAILURE | FAILURE; intentionally requires full integration |

[Before run](https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/actions/runs/39879145935), [verified repaired run](https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/actions/runs/39909707415), [sanitized remote evidence](evidence/backend_ci_remote/REMOTE_RESULTS.json). Documentation-only delivery commits may follow; check the exact head of the latest run before reviewing a PR. Local repeated groups overlap the 255-case full suite and must not be added as unique cases.

## SPMF and independent-review readiness

The existing local artifact was rechecked: exact SHA256 `4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a`, real banner `-- SPMF version 2.67a --`, Java25 child. No binary was uploaded, published or committed. The team has not supplied an approved immutable HTTPS artifact URL/host: **SPMF_INTEGRATION_CI_BLOCKED** remains. Follow [safe publication procedure](../setup/BACKEND_CI.md#publish-the-approved-spmf-artifact-safely): record provenance/distribution authorization, approve the host and immutable object path, publish only after owner approval, independently verify the downloaded bytes/banner, then set PASPM_CI_SPMF_ARTIFACT_URL and run full CI. The local hash check is not hosting authorization.

Both PR#1 and PR#2 are already merged and have zero submitted reviews in the current API inspection. Public dev protected=false, rules/rulesets empty; authenticated protection lookup401, candidate permission lookup403. Candidate PhanHoangDuy61 and author khoinm04 are different accounts; distinct humans and eligible independent permissions remain unverified: **REVIEWER_PENDING**. No self approval, review request or retroactive approval of the previous merges was created. Agent source review does not satisfy this human gate.

## Delivery, preservation and remaining blockers

Scoped changes: workflow forked-JVM options and exact-branch push verification; one actual-client regression in LegacyRecoveryMySqlTest; CI setup/report and sanitized evidence. mysql-client.sh, provision-restore.py and LegacyBackupVerifier production content are unchanged. No existing migration V1–V11 or F03–F06 business logic changed.

The fix branch is `Khoi/fix/backend-ci-remote-verification`. Draft PR creation returned **403 Resource not accessible by integration**. No fix PR was created by this task. Use the [manual PR creation link](https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/compare/dev...Khoi%2Ffix%2Fbackend-ci-remote-verification?expand=1) and [prepared PR description](BACKEND_CI_REMOTE_FIX_PR_DESCRIPTION.md). No merge/deployment occurred.

The original configured MySQL source remains V5. SELECT/ROLLBACK read-only before/after snapshots of schema/history/counts match; original Product branch/head remains12d348e. Populated fixtures are synthetic and REAL_DATA_BLOCKED remains. [Source preservation](evidence/backend_ci_remote/SOURCE_PRESERVATION.json). Dedicated reproduction containers were stopped/removed after execution; no shared container/data was reset. Private diagnostic/source snapshots remain outside Git; no raw dumps, option files, credentials or raw JUnit properties were published.

**Product JPA integration must not resume yet.** The MySQL technical blocker is fixed and verified remotely, but approved SPMF provisioning, successful full hosted integration, eligible independent human review and required progression authorization remain outstanding. Category remains NOT_STARTED.

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

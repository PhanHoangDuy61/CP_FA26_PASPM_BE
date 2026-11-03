# Independent code review: Backend CI MySQL temporary-file repair

Reviewed 2026-10-23 against base `783725c` in `/tmp/paspm-legacy-matrix-recovery`, including the uncommitted workflow/test diff, script mode changes, and investigation document. No applicable AGENTS.md found in the checkout or its /tmp ancestry. Review was read-only on the repository; no DB tests were run by this reviewer.

## Assessment

No critical or important implementation findings. The scoped correction is suitable to proceed to remote verification. This is an agent code review, not human GitHub approval and not evidence of a successful hosted workflow or complete integration run.

## Strengths and evidence

- Both affected Maven invocations now set `java.io.tmpdir` on the forked test JVM through `argLine`. The current POM has no competing argLine configuration. The configured directory is created before execution and is within the directory mounted read-only into the MySQL CLI container.
- The added probe uses the same default temporary-file allocation and owner-only POSIX permissions as production `LegacyBackupVerifier.restoreExactArtifact`, then invokes the actual MySQL wrapper with an explicit defaults file. It checks client success and the expected 8.4 client family without touching the database.
- Inspected `options-before.log`: the regression fails with exit 1, hostFileReadable=true, withinSharedTemp=false, defaultsFileError=true. Inspected `options-after.log`: it passes with exit 0, withinSharedTemp=true, defaultsFileError=false. This is direct red/green evidence for the specific failure mechanism.
- Inspected `before-mysql-results.json`: 33 tests, 0 failures, 6 errors, 0 skipped. The complete after-run was still being written during review, so this report does not certify its final result.
- Probe contents contain no credentials; inherited MYSQL_PWD is removed; history is disabled; raw stdout/stderr are not included in assertion messages. File cleanup occurs in finally. Production recovery and migration code are unchanged.
- The push trigger is limited to the named repair branch. Existing PR target, minimal workflow permissions, checkout credential handling, and required technical aggregate remain unchanged.
- `git diff --check 783725c` passed.

## Minor / optional findings

1. **P3: Terminate the client on probe timeout.** `src/test/java/vn/edu/fpt/paspm/deployment/LegacyRecoveryMySqlTest.java:28-39`: if the 20-second wait expires, assertTrue throws and the finally block removes only the options file. The launched client/wrapper can remain alive while the suite continues. Keep the Process in scope for finally and destroy it if still alive. This is a failure-path cleanup improvement, not a blocker for the temporary-directory fix.
2. **Scope cleanup:** `.github/scripts/mysql-client.sh` and `.github/scripts/provision-restore.py` have executable-bit-only changes (100644 to 100755). The workflow already chmods them before invoking them. Revert these incidental local reproduction changes unless intentionally including executable permissions in the repair.

## Remaining validation

Finish the full MySQL group and applicable foundation suite, then obtain hosted workflow evidence. Full integration remains a separate gate if the approved SPMF artifact prerequisite is unavailable. Update the investigation document with actual completed results rather than treating the successful targeted probe as a complete workflow pass.

## Author disposition

The optional timeout cleanup is applied: a still-live client is destroyed in finally before deleting the temporary option file. The actual-client regression was rerun after this final edit: 1 PASS, 0 failures/errors/skips. Incidental executable-bit changes are excluded from the scoped commit and will be restored after the local full run finishes.

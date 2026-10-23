# fix(ci): make private restore option files visible to MySQL container

Six LegacyRecoveryMySqlTest cases fail because MySQL cannot open the verifier's private defaults file outside its read-only mount. Configure java.io.tmpdir at forked JVM startup through Surefire argLine in both MySQL-enabled jobs. Add a real-client regression with sanitized diagnostics and timeout/file cleanup. Preserve all production recovery safeguards, mount limits, privileges and migration bytes.

Validation: original 33-test/six-error failure reproduced with Ubuntu24.04 amd64, Java17 and exact MySQL8.4.13 image; regression failed before and passed after. Repaired hosted run39909707415 at9f362db: foundation135 PASS, MySQL34 PASS (LegacyRecovery11 PASS), zero test failures/errors/skips in those groups. Full local regression255 PASS with Java17 Backend/Java25 SPMF. Documentation-only delivery commits follow; inspect current-head checks.

Full hosted integration and aggregate remain unsuccessful because the approved immutable SPMF URL is missing. Local exact2.67a SHA/banner verified; no binary uploaded. REVIEWER_PENDING remains; previous merged PRs are not retroactively approved. Original V5 source unchanged/read-only; populated fixtures synthetic. No Product integration, Category implementation, merge or deployment.

The branch-specific push trigger enables actual hosted verification despite the GitHub App's PR creation403. Details/evidence: docs/testing/BACKEND_CI_REMOTE_VERIFICATION.md. Do not auto-merge.

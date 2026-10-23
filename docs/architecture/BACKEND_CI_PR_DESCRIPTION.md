# ci: add isolated backend verification and record recovery gates

Recovery PR #1 is already merged, but the audited recovery head has no submitted human reviews or CI results. Add separate Java 17 Backend, disposable MySQL/Flyway V1–V11, and full F01–F06/SPMF jobs with an aggregate technical gate. Backend stays on Java 17; SPMF uses Java 25 and the exact approved 2.67a SHA-256. Missing approved artifact provisioning fails the integration gate rather than silently skipping it.

The workflow uses read-only permissions and pinned Actions; test summaries reject missing/empty reports, skips and failures. Restore helper credentials are restricted to generated disposable schemas. No application source, applied migration, Product integration or Category implementation changes are included.

Validation: fresh local Maven clean verify 254 PASS; repeated Backend/MySQL subsets 135/33 PASS without SPMF; 13 Python helper tests PASS; workflow YAML/Bash syntax checked; restore grants exercised on disposable MySQL 8.4.11. Original V5 database/configuration and migration bytes remain unchanged. All populated fixtures are synthetic.

Remote jobs and Linux Docker wrapper execution are NOT_RUN. No approved artifact URL is available: SPMF_INTEGRATION_CI_BLOCKED. Candidate reviewer eligibility and distinct human identities remain unverified: REVIEWER_PENDING. The older detached 299-test Product run is historical; Pilot-specific JPA tests are absent on current dev. This PR must not be treated as approval to merge or deploy.

See docs/testing/PR1_REVIEW_GATE_REPORT.md and docs/setup/BACKEND_CI.md for evidence and safe artifact publication instructions. Do not commit or upload the JAR until the team approves its distribution and location.

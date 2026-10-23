# Recovery PR #1 review and CI gate plan

> Execute the authorized review/verification tasks inline with Superpowers verification and independent code review. Human GitHub approvals remain separate.

**Goal:** Audit the real PR state, rerun supported recovery verification, and prepare an isolated CI follow-up without advancing Product or Category prematurely.
**Architecture:** Keep recovery application code and V1–V10 unchanged unless a verified scoped defect exists. Use a separate dev-based tooling/docs branch. CI uses a disposable MySQL 8.4 service, Java 17 backend, separately provisioned Java 25 and the exact already-approved SPMF hash. Missing approved artifact configuration fails before tests; no substitute JAR or fake PASS.
**Authorization:** User explicitly requested a minimal reviewable workflow when CI is absent, full local reverification and strict sequential gates. This implements that concrete preparation; no production deployment, protection change, merge override or new business design.

## Constraints
- Original configured V5 database: SELECT/ROLLBACK only; preserve private configuration and all migration bytes.
- No Product integration or Category implementation until required real review/CI gates are satisfied, even if Recovery was externally merged already.
- No private artifacts, credentials, raw JUnit properties or backup dumps in Git/CI artifacts.
- No CI is reported PASS before an actual remote run at the latest head completes.

## Task 1 — Actual GitHub preflight and source review
- [x] Fetch PR #1, reviews, threads, exact head and actual dev merge commit.
- [x] Read public branch/rules/checks/workflows metadata; preserve HTTP status for inaccessible policy/permissions.
- [x] Review actual B+C implementation through CodeGraph plus fresh independent agent review. Do not self-approve on GitHub.
- [x] Identify actual review/CI gaps and request missing eligible reviewer/artifact information from the user without guessing.

## Task 2 — Fresh isolated verification
- [x] Capture configured source baseline read-only; initialize a new MySQL 8.4 instance at a separate loopback port.
- [x] Run `clean verify` with all existing MySQL/Java17/SPMF opt-ins and exact approved SPMF binary.
- [x] Collect exact per-case results and historical replay/fingerprint evidence.
- [x] Recheck source equality, stop dedicated instance and remove temporary credentials after verification/tooling checks.

## Task 3 — Reproducible CI proposal
Files: `.github/workflows/backend-verification.yml`, `.github/scripts/provision-spmf.py`, `.github/scripts/provision-restore.py`, `.github/scripts/mysql-client.sh`, `docs/setup/BACKEND_CI.md`.
- [x] Configure PR/workflow-dispatch runs with read-only token permissions, pinned official Actions, Java17+Java25 and loopback-only disposable MySQL8.4.
- [x] Require repository variable `PASPM_CI_SPMF_ARTIFACT_URL` pointing to an owner-approved HTTPS artifact; verify exact project SHA before execution. Do not download current unpinned upstream releases or redistribute the JAR.
- [x] Provide matching MySQL8.4 CLI via ephemeral image wrappers and schema-scoped restore accounts. Test rejection of malformed provisioning inputs and actual account grants on the isolated server.
- [x] Validate workflow syntax and negative missing/wrong artifact checks. Remote workflow execution remains NOT_RUN until GitHub actually runs it.
- [x] Published scoped CI/docs branch. PR creation returned 403; manual PR package provided. No merge.

## Task 4 — Gate report and progression
Files: update readiness/compatibility/JPA migration verification; include a gate evidence JSON and current migration-plan gate note.
- [x] Record actual merged PR #1 with merge SHA, zero submitted reviews/checks, inaccessible authenticated policy endpoints and pending retrospective review/CI.
- [x] Keep REAL_DATA_BLOCKED. Distinguish fresh 254 test results from earlier detached 299 compatibility run.
- [x] Do not start Product/Category unless independent approval, required policy and CI are actually verified. No approval by elapsed time or by already-merged status.

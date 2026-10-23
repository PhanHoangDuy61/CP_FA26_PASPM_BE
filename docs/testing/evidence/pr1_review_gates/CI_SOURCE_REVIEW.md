# Independent agent source review — proposed Backend CI

Scope: only the new `.github/` workflow/scripts and their selection of existing test classes, inspected read-only in `/tmp/paspm-legacy-matrix-recovery` on `Khoi/chore/recovery-review-ci-gates` while `.github/` was untracked. No code edits, Docker execution, test execution, database access, credential reads, GitHub approvals, merge, or deployment performed. Parent-reported local counts are not independent remote-CI evidence.

## Verified findings

No verified defects found.

| Severity | Findings |
|---|---:|
| CRITICAL | 0 |
| HIGH | 0 |
| MEDIUM | 0 |
| LOW | 0 |

## Checked behavior

- `.github/workflows/backend-verification.yml:3–9` uses `pull_request` against dev and manual dispatch with read-only contents permission. It does not use a privileged `pull_request_target` event. Checkout disables persisted credentials (`:21–23`, `:55–57`, `:105–107`). Both pinned action revisions resolve to real official Node20 actions: [checkout action metadata](https://raw.githubusercontent.com/actions/checkout/11d5960a326750d5838078e36cf38b85af677262/action.yml), [setup-java action metadata](https://raw.githubusercontent.com/actions/setup-java/cf277c60eb25467037889841efdb72551f06f6c3/action.yml).
- `.github/scripts/test-groups.py:5–15` names only existing suites. The backend group contains pure/unit/H2 suites that do not execute the external SPMF engine. The mysql group includes the migration/backup/recovery and F04/retail utility MySQL suites, enables their MySQL condition (`backend-verification.yml:74–77`), and excludes the legacy replay reader, mining, runtime, and recommendation integration suites that need the artifact. `LegacyRecoverySchemaTest` has a MySQL-conditional method and is correctly placed in the mysql group. `FlywayRecoveryTest` also has a MySQL-conditional method and is correctly placed there.
- Full integration does not narrow test selection and enables all current MySQL/Java17-backend/Java17-child-rejection conditions (`backend-verification.yml:134–147`). Java25 is provisioned first, its absolute executable is saved, and Java17 is then restored for Maven/backend execution (`:108–120`). The saved SPMF environment names match the existing application configuration, including the HTTP integration test's environment-based paths (`:132–145`). No nonexistent JPA test group or PASS claim was added.
- The MySQL service is per-job and published on runner loopback (`backend-verification.yml:38–53`, `:85–101`). The CLI wrapper uses host networking and bind-mounts runner temp at the same absolute path read-only (`.github/scripts/mysql-client.sh:8–13`). Maven's `java.io.tmpdir` is directed beneath that mount (`backend-verification.yml:76`, `:140`), allowing the containerized CLI to read the private verifier options and SQL files while dump output remains host-redirected. The official image has no USER override, so direct client entrypoints retain container-root access to owner-only host option files; this is not a UID mismatch. [Official MySQL Dockerfile](https://raw.githubusercontent.com/docker-library/mysql/master/8.4/Dockerfile.oracle).
- `provision-restore.py:10–26` refuses without the disposable opt-in, exact loopback server form, disposable schema prefix, restricted generated username, and constrained password. Its escaped underscore grant scope matches the production verifier's exact-schema grant check. Generated SQL travels through stdin and errors are sanitized (`:27–40`), while the production backup verifier uses an owner-only temporary client file. The wrapper forwards MYSQL_PWD as an environment value rather than exposing its value in command arguments (`mysql-client.sh:12`). No backup/raw Surefire/binary/credential upload step is present.
- `provision-spmf.py:11–49` uses the exact existing SPMF2.67a checksum, requires credential-free HTTPS URLs, rejects HTTPS downgrade redirects, bounds download size, hashes before publishing the new private destination, and removes failed temporary artifacts. The workflow does not commit or upload the artifact. Failure diagnostics do not print potentially signed URLs (`:52–59`).
- `test-summary.py:6–21` publishes aggregate counts only, labels missing reports NOT_RUN, rejects reported skips/failures/errors/zero tests, and explicitly records absent JPA suites as unavailable. A failed prerequisite or Maven command already leaves its job failed; the always-running summary does not clear that failure. The always-running aggregate requires every job result to be `success` (`backend-verification.yml:153–170`), including cancellation/skips/failure. It does not turn missing SPMF provisioning into a green full-verification result.

## Assessment and remaining gates

No source-level blocker established for preparing this CI change for review. Docker/remote workflow behavior has not been executed by this reviewer; successful source inspection is not GitHub CI PASS.

No approved artifact URL is available: **SPMF_INTEGRATION_CI_BLOCKED** remains. The workflow intentionally fails that prerequisite and therefore the aggregate gate until authorized provisioning is configured. Human eligibility/independence for candidate reviewer PhanHoangDuy61 is outside these files and remains **REVIEWER_PENDING** until independently verified. This agent review does not supply human GitHub approval or authorize Product/Category progression.

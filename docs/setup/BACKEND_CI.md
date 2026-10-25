> SPMF readiness update2026-10-25: see [current report](../testing/SPMF_CI_READINESS.md) and [owner hosting/approval package](SPMF_ARTIFACT_HOSTING.md). PR#3 is still open; no approved artifact URL/publication or independent review exists. Earlier counts below retain their original dates.

# PASPM Backend CI setup and remaining gates

Status update (2026-10-23, Asia/Ho_Chi_Minh): CI PR #2 was merged externally into dev783725c. Remote run39879145935 passed foundation but failed six MySQL restore fixtures; the repair is verified in hosted run39909707415 (foundation135 PASS, MySQL34 PASS; full integration still blocked). Details are in [remote CI report](../testing/BACKEND_CI_REMOTE_VERIFICATION.md). Earlier local counts below are historical baselines, not proof of the repaired remote head. **SPMF_INTEGRATION_CI_BLOCKED** and **REVIEWER_PENDING**. No merge, deployment or original-database change is authorized.

## Verification groups

| Job | Actual scope | Prerequisites / current limitation |
|---|---|---|
| SPMF downloader security |20 artifact-independent URL/redirect/checksum/error-isolation tests |Runs even when the full external artifact prerequisite is blocked; does not test real mining or replace full integration. |
| Java17 Backend foundation | 22 named existing suites: F01 auth, F02 catalog, F03/weight v1.2, F04 parser/validation/transactions, pure F05/F06 logic, H2 guards | No SPMF required. Locally executed 135 PASS, zero failures/errors/skips. This is not remote CI PASS. |
| MySQL8.4 / FlywayV1–V11 / F04 | Six named existing suites: fresh/upgrade migrations, populated archive/partialV7/restore guards, F04 REQUIRES_NEW/idempotency, retail utility | Disposable MySQL+matching CLI. Locally executed 33 PASS, zero failures/errors/skips with a deliberately absent SPMF path. |
| Full F01–F06 / SPMF | Unfiltered Maven clean verify; every existing MySQL/Java17 opt-in; HUI-Miner+EFIM, legacy replay, F06 refusal and HTTP mining | Approved external exact JAR required. Local full run 254 PASS. Remote integration remains BLOCKED without the artifact URL. |
| JPA/JDBC shared transactions | Full job will discover existing test suites when a separately accepted Product Pilot integration adds them | No Product JPA suites exist at current dev691371b. NOT_AVAILABLE_IN_THIS_REVISION, not PASS or silently implemented. Prior detached compatibility299 is historical local evidence only. |
| Required Backend verification | Requires downloader security plus every Backend/MySQL/full integration job to succeed; skips/cancellation/missing dependency fail it | Does not replace independent eligible human review or approval of progression. No overall readiness claim while reviewer/artifact gate is pending. |

Selectors are in `.github/scripts/test-groups.py`. Full integration has no `-Dtest` filter. `.github/scripts/test-summary.py` fails on missing reports, zero tests, any skipped/failed/error tests, and publishes only sanitized counts. Intentional reduced job scopes are documented above; they are not called the complete regression suite.

## Isolation and runtime

Workflow triggers on PRs into dev, manual dispatch, and pushes to the exact repair branch Khoi/fix/backend-ci-remote-verification so the fix can be verified remotely despite PR creation permission failures. It uses read-only contents permission, pinned official checkout/setup-java commits and disables persisted checkout credentials. No privileged pull_request_target event or deployment step exists.

The Backend compiles/tests under Java17; Java25 is separately provisioned for the exact SPMF child process. The full job checks SHA-256 then the real version entrypoint before tests. setup-java provisions Temurin from its supported publisher integration; backend/child Java versions are logged separately. [Official setup-java documentation](https://github.com/actions/setup-java).

Each MySQL job owns a disposable mysql:8.4 service. Port3306 is bound only to runner loopback. Its empty-password DBA is exclusively an ephemeral test-container identity, never an application/production identity. No existing datasource URL or production secrets are supplied. Tests create random `paspm_p0_test_` / `paspm_bc_test_` schemas. Restore helper validates that prefix and generated user/password formats and grants only the exact escaped verification schema, without SUPER or GRANT OPTION.

The Linux CLI wrapper uses the same MySQL image as the service and host networking, with runner temp mounted read-only at the identical absolute path. Java temp is passed at forked JVM startup through Surefire argLine, within that shared directory, so private verifier option files/dumps are readable. A Maven user property alone is insufficient because Java may cache its temporary directory before Surefire applies that property. A real MySQL client regression checks owner-only defaults-file readability without printing its contents. Credentials travel via environment/private option files/stdin, never command arguments. No JAR, raw Surefire reports (which can include datasource properties), credentials, backups or database files are uploaded. Container destruction at job end discards test data.

MySQL image8.4 and Java major17/25 follow the approved version family; they are not a promise of an exact patch image/vendor build forever. Log actual versions per run; a stricter approved digest/version pin can be introduced through review. The earlier local run used installed MySQL8.4 CLI and missed the container mount problem. The repair is reproduced with Ubuntu24.04 amd64 Java17 and the exact failed-run MySQL8.4.13 image. Consult the remote CI report for current local/container and GitHub-hosted results; YAML/Bash parsing is not runtime proof.

## Publish the approved SPMF artifact safely

No approved CI artifact URL exists yet. The workflow deliberately fails its artifact prerequisite instead of downloading an upstream latest release, substituting a JAR or skipping mining tests.

1. Team Lead/artifact owner selects the exact `spmf.jar` already used in PASPM tests, outside Git. Verify SHA-256 `4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a` and run Java25+ `java -Djava.awt.headless=true -jar /approved/path/spmf.jar version`; require `-- SPMF version 2.67a --`.
2. Record provenance and approve redistribution/access according to the existing [SPMF setup/license guidance](F05_SPMF_RUNTIME_SETUP.md). The local pin establishes reviewed binary identity; it is not an independently authenticated upstream signature.
3. Publish only after authorization to an owner-managed HTTPS artifact store at an immutable content/version path. Do not replace the object at that path. No binary publication has been performed by this task.
4. Download from that approved location independently and verify the same checksum and version. Preserve the owner approval reference. Do not upload tokens, connection details, reports, backups or datasets alongside it.
5. A repository administrator sets the Actions repository variable `PASPM_CI_SPMF_ARTIFACT_URL` to the approved credential-free HTTPS object URL. Do not store passwords/tokens in the URL, repository variable or documentation. Input query strings, fragments and userinfo are rejected. Fixed-tag GitHub Release sources may follow signed HTTPS redirects only to exact release-assets.githubusercontent.com; custom storage stays same-origin and query-free. Signed CDN URLs are never configuration or log output. If authenticated private storage is required, stop and obtain approval for a dedicated least-privilege retrieval design; the current workflow does not borrow developer credentials.
6. Open/review the separate CI PR and run all jobs. Verify the exact latest commit SHA, zero skipped tests and actual SPMF outputs. Until this occurs retain SPMF_INTEGRATION_CI_BLOCKED / NOT_RUN. Never loosen the pin to accept a newly downloaded binary.

## Review and progression policy

PR#1 is already merged as691371b and CI PR#2 as783725c before this repair. Neither merge is retroactively approved by this audit. Actual inspection found zero submitted reviews/checks/runs, public dev protected=false, no active rules/rulesets. Authenticated protection/collaborator probes were unavailable (401/403); no equivalent gate is verified. This observation neither approves that merge nor permits later merges.

Candidate PhanHoangDuy61 and author khoinm04 are distinct GitHub account names. Distinct human identities and eligible reviewer permissions are not verified: REVIEWER_PENDING. No fake/self approval or impossible new review-request mutation on the merged PR was sent. Team Lead must confirm a different eligible human reviewer and obtain an actual review decision on the exact recovery implementation/current follow-up. Configure review/required-check policy through an authorized owner review; this task does not change protection settings or apply admin overrides.

The newest instruction prohibits merges. Product integration/PR/merge and Category implementation remain blocked. After missing reviews/CI and policy gates are resolved, obtain authorization to continue the next phase. See [gate report](../testing/PR1_REVIEW_GATE_REPORT.md).

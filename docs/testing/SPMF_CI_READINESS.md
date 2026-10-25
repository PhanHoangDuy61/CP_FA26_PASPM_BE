# SPMF CI readiness — 2026-10-25

## Scope and plan

Continue open PR #3 on Khoi/fix/backend-ci-remote-verification (head5412178; dev783725c). No duplicate PR, merge, deployment, source-database write or binary publication. Original working checkout and private application configuration stay untouched.

1. Refresh PR/review/CI/release metadata and inspect actual downloader/runtime paths with CodeGraph.
2. Verify existing local artifact hash/banner and real HUI-Miner/EFIM under Java25, with Backend Java17. Reuse passing foundation/MySQL results; do not rerun unrelated local suites.
3. Reproduce signed GitHub Release redirect incompatibility with HEAD only. Add failing regression tests, then allow only HTTPS same-origin redirects plus the exact GitHub asset CDN for fixed-tag GitHub Release sources. Keep input query/fragment/userinfo rejection, checksum, size, private atomic staging and banner checks.
4. Run downloader/security tests independently of artifact provisioning in CI. Full integration remains unsuccessful when the owner-approved URL is missing.
5. Prepare a reviewable hosting/license/source package and exact owner UI instructions; do not upload the JAR or set an invented URL. Verify independent review eligibility or retain REVIEWER_PENDING.
6. Review, commit/push the scoped changes to existing PR #3; inspect actual latest-head jobs and document all remaining gates.

## Initial observations

PR #3 is OPEN, not merged, author khoinm04, base dev783725c, head5412178, zero requested reviewers/submitted reviews/inline threads. Candidate permission probe403; distinct human identities unverified. Public dev protected=false, active rules/rulesets empty. Release list empty. Latest observed run39981570789: foundation/MySQL SUCCESS; full integration fails at artifact provisioning and aggregate FAILURE. No retrospective approval of PRs #1/#2.

HEAD-only reference probe of a fixed GitHub CLI checksum asset returned302 to HTTPS release-assets.githubusercontent.com with query parameters. No body was downloaded, and no signed URL was retained. Current require_https rejected this actual redirect. This reference asset is not an approved SPMF source and is never used for provisioning PASPM.

## Implemented and locally executed

- Fixed signed GitHub Release redirects without accepting query parameters in the configured source URL. Only HTTPS443 GET/HEAD, original same-origin query-free redirects and exact GitHub asset CDN for fixed-tag Release sources are permitted. Final URL is revalidated. Reject userinfo (including empty userinfo), fragments, control characters, backslashes, HTTP downgrade, unapproved hosts/ports and spoofed CDN names. The approved SHA/64MiB/private-temp/atomic-final-rename checks are unchanged. Single-writer isolated CI destination remains the supported publication model.
- Added an independent **SPMF downloader security** job to run20 pure policy/negative tests without a JAR. Required aggregate includes this job and still requires the full integration job. Full integration continues actual checksum, Java25 banner and Backend regression verification after artifact provisioning. Nothing bypasses or skips the SPMF prerequisite to obtain PASS.
-34 local helper tests PASS, zero failures/errors/skips. This includes the20 pure cases, existing restore/summary safeguards, and successful provisioning of the exact already-verified local JAR bytes using mocked direct and signed-CDN responses. Mocked transport is not proof of owner-approved hosting.
- Actual HEAD-only reference probe before:302 signed CDN rejected; after:200 exact CDN accepted. No reference asset body or SPMF binary was downloaded; no signed URL stored. The reference GitHub CLI asset is not an approved PASPM artifact source.
- Maven **clean verify** of SpmfRuntimeTest and SpmfMiningEngineTest:13 PASS, zero failures/errors/skips, Backend Java17 and separate Java25. HUI-Miner/EFIM each produced eight expected patterns/utilities on the five-transaction utility96 fixture at minUtil30. Initial invocation omitted the Java17 rejection-test opt-in (one skipped); corrected final invocation enabled it and reran both suites. No skipped case is counted as PASS.
- Original signed-redirect regression run:19 tests with2 failures/2 errors before the fix. Empty-userinfo regression separately failed before correction. Final combined helper result34 PASS. Read-only agent security review found no blocking issue; it is not human GitHub approval.

Evidence: [artifact identity](evidence/spmf_ci_readiness/ARTIFACT.json), [HEAD before](evidence/spmf_ci_readiness/REDIRECT_PROBE.json), [HEAD after](evidence/spmf_ci_readiness/REDIRECT_AFTER.json), [helper results](evidence/spmf_ci_readiness/DOWNLOADER_RESULTS.json), [runtime per-case results](evidence/spmf_ci_readiness/MINING_RUNTIME_RESULTS.json), [agent review](evidence/spmf_ci_readiness/AGENT_REVIEW.md).

## Artifact and owner decision

**NEEDS_OWNER_APPROVAL**: no Release, upload, publish, binary commit, artifact-host creation or repository variable write occurred. Exact Corresponding Source/build evidence is still UNVERIFIED; possession of a GPL copy or a test-files ZIP is not sufficient evidence. GitHub Releases with release immutability are preferred only after owner approval and a complete license/source package. A fixed HTTPS store is the alternative. No synthetic approved URL was supplied. Follow [hosting approval package and exact UI instructions](../setup/SPMF_ARTIFACT_HOSTING.md).

The current integration cannot inspect the Actions variable directly (HTTP401). No value was logged. Latest actual prior run39981570789 failed its artifact prerequisite. No owner-approved URL was provided in this task. Do not infer that a private variable is absent solely from401; hosting/provisioning remains BLOCKED until an approved URL is confirmed and actual full CI succeeds.

## Existing regression and database evidence

Unchanged foundation/MySQL modules are not rerun locally. Latest observed existing hosted run39981570789 at5412178 has foundation135 PASS and MySQL34 PASS with zero failures/errors/skips. Prior full local255 PASS is evidence from2026-10-23, not a fresh full run in this task. New current-head hosted results will be recorded below after publication. No production Java, business DTO/API, V1–V11, Product/Category JPA or private application configuration changes are included. This task makes no connection to the original source database; source data is not migrated/repaired/reset. [Scope preservation](evidence/spmf_ci_readiness/SCOPE_PRESERVATION.json).

## Review gate and next step

PR#3 remains the existing dev-targeted PR; no duplicate is created. Public protection/rules report no required checks, but the explicit task review requirements remain. Candidate PhanHoangDuy61 and author khoinm04 have different account names; distinct humans and eligible permissions are unverified (permission API403). Zero reviews/threads at inspection: **REVIEWER_PENDING**. No review request/self approval/fake review or retrospective approval of PRs#1/#2 occurs.

READY_FOR_MERGE: **NO**. Full hosted integration, approved artifact hosting and eligible actual independent review must all complete. Product JPA integration remains blocked; Category remains not started. No merge or deployment is performed.

## Publication and PR metadata

Implementation was pushed to existing PR#3 branch as b0cf027; hosted jobs were triggered. GitHub App rejected title/body update with403 Resource not accessible by integration; the PR remains open and its metadata is not falsely reported as updated. A ready-to-paste [current PR title/body](BACKEND_CI_REMOTE_FIX_PR_DESCRIPTION.md) is maintained for the owner. No duplicate PR, merge or review mutation was created. The temporary branch-specific push trigger is removed in the final update because PR#3 synchronize events now provide actual latest-head verification without duplicate push/PR runs.

## Executed GitHub checks at b0cf027

[Run40167457094](https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/actions/runs/40167457094) completed:

| Job | Actual result |
|---|---|
| SPMF downloader security | SUCCESS;20 unit/security tests, zero failures/errors/skips; no external JAR required |
| Java17 foundation | SUCCESS;135 tests, zero failures/errors/skips |
| MySQL/FlywayV1–V11 | SUCCESS;34 tests, zero failures/errors/skips |
| Full F01–F06/SPMF | FAILURE at missing approved artifact prerequisite; Backend test step NOT_RUN |
| Required aggregate | FAILURE; no overall CI PASS |

[Sanitized hosted results](evidence/spmf_ci_readiness/GITHUB_RESULTS.json). Later commit removes only the temporary duplicate push trigger and updates documentation; its latest-head PR run is still required. Do not treat unit/security success as a real approved SPMF hosting/download verification. The upstream download page currently links moving spmf.zip/spmf.jar and lists2.67 release notes; it does not establish immutable exact2.67a source correspondence. No upstream binary/source ZIP was downloaded.

# ROC-20261116-007 — Evidence cleanup and F03/F05 merge planning

| Field | Value |
|---|---|
| Record ID | ROC-20261116-007 |
| Date / timezone | 2026-11-16 / Asia/Ho_Chi_Minh |
| Type / features | Documentation / F02, F03, F05, F06; F07/F08 dependency note |
| Branch / base HEAD | Khoi/feat/f05-progress-f03-weight-v2 / 8f9ec7d |
| Execution status | COMPLETED — this documentation pass; merge gates remain open |
| Release status | UNRELEASED — docs pass uncommitted; no publication/integration authorized |
| Daily summary | [README](README.md) |

## 1. Request and scope
Document the approved cleanup, verify origin/dev, prepare a merge plan and two external drafts. Only docs are edited; no src/tests/migrations/scripts/OpenAPI/Word/Draw.io changes, no new test run, push, PR, merge, rebase, pull, reset, amend or tag in this pass. Main BE checkout left untouched. [Merge plan](../../release/F03_F05_MERGE_PLAN.md).

## 2. Cleanup history and provenance

| Original | Current | Meaning / evidence |
|---|---|---|
| 94ebefb | 2e7508e | Owner-approved HEAD-only amend, same fix message. Generated evidence removed from final Git tree after verified external copy. Safe for unpublished history: branch was unpushed, so no published collaborator history required a force update. Source unchanged; record 006 cleanup section |
| 369dba4 and earlier | unchanged | No older commit rewritten; verified ordered Git log |
| 8f9ec7d | current inspected HEAD | New docs-only commit normalizes older local paths/account identifiers; record 006 older-metadata cleanup |

Any mention of 94ebefb here identifies the old-to-new mapping; current fix references use 2e7508e. These earlier cleanup operations are recorded history, not newly executed in this documentation pass. Rule 8 of the record index permits metadata normalization while preserving historical results and limitations.

## 3. External evidence

Location: **outputs/evidence/2026-11-16/ in the project folder (not in Git)**.

- full-optin-counts.json
- feature-suites-counts.json
- feature-skip-coverage.json
- mysql-group-counts.json
- FINAL_QA.json
- development-attempts.json
- jpa-status.json
- baseline-comparison.json

The current pass reread all eight files and compared SHA-256 to the manifest in [record 006 — evidence kept outside the repository](06-f03-f05-followup-fixes.md#evidence-kept-outside-the-repository): **8/8 MATCH**. The original cleanup copied and verified them before removing repository copies. Do not commit them again; retain the relative location/hash manifest and concise results. This is historical synthetic test evidence, not a new runtime test.

## 4. Origin and migration check actually executed

`git status` initially clean; `git log --oneline -9` showed eight local commits above 8f94f94. `git fetch origin` succeeded. Resolved origin/dev: `8f94f94698e973bda11c84536cdc4e3311e5568a`. `git log --oneline 8f94f94..origin/dev` returned no commits; `git rev-list --left-right --count origin/dev...HEAD` returned **0 / 8**.

**dev has not moved.** No incoming commits to list. Read-only Git-tree inspection of SQL and Java migrations found max version **23** on origin/dev, while the branch adds V24 SQL and V25 Java (paths in merge plan §1). **No V24/V25 clash in the fetched state.** Fetch/check again before later integration; future numbering collisions block merge. No pull/merge/rebase/reset performed.

## 5. Source and recorded verification

`git rev-parse HEAD:src` = **a20970025d3040b80daf602856a681b9d385c1fd**, unchanged from the final tested source and the cleanup (record 006). Therefore **no Maven, MySQL or SPMF test run in this docs pass**.

| Historical run | Total | PASS | FAIL / ERROR | SKIP | Source |
|---|---:|---:|---:|---:|---|
| Full opt-in | 651 | 646 | 0 / 0 | 5 | record 006 final verification |
| JPA supplementary | 5 | 5 | 0 / 0 | 0 | record 006 |
| MySQL group | 72 | 72 | 0 / 0 | 0 | record 006 |

The five JPA skips were executed separately; do not add overlapping run totals or describe the full run as zero-skipped. Record 006 preserves the 145 feature-skip → 148 passing parameterized-invocation reconciliation. These results predate this pass.

Additional read-only checks this pass: CodeGraph exploration of V25/conversion dependencies, direct migration/workflow inspection, JSON OpenAPI operation counting and external hashes. `python3 .github/scripts/test-groups.py backend` **exited 1**: removed WeightRulesV12IntegrationTest still listed; mysql selector exited 0 and printed its suite list. No test was executed by these selector checks. The workflow and summary code also show that SPMF provisioning and skip-free CI remain gates (merge plan §2). No scripts fixed.

## 6. Merge recommendation and blockers

Recommend **Squash and merge** after owner approval and all gates close. Older intermediate branch commits still contain paths/generated evidence, although the final tracked text is cleaned (`8f9ec7d`). Squashing excludes those intermediate commits from dev ancestry; it cannot purge existing dev history or commits already uploaded for a PR. Publishing this current branch exposes its intermediate history until/unless a separately approved clean-publication approach is chosen. No additional history rewrite authorized or performed.

See [merge plan blocker table](../../release/F03_F05_MERGE_PLAN.md#2-merge-blockers-and-checks): current origin and numbering checks DONE; FE acknowledgement/readiness OPEN; selector issue OPEN; CI provisioning/coverage OPEN or NOT VERIFIED; actual PR checks NOT VERIFIED; independent review OPEN; Linux acceptance and business confirmations OPEN. Not MERGE_READY.

## 7. Before deploy / Frontend notes

Carry forward [record 004 §6 and Before deploy](../2026-11-15/04-f03-weight-v2-expiry-season.md#6-pre-deploy-checks-not-run--no-real-database-was-used), plus record 006's follow-up checks:

- Read-only scoringVersion query on approved target databases before conversion; no real-data query run here.
- Verified pre-V25 backup and isolated restore; stop all old instances; rolling upgrade is NOT VERIFIED. Review conversion evidence after V25; no automatic backfill.
- Store Admins enter push days. FE adds **DELETE /api/inventory-batches/{id}/push-day**, returns BatchView with null pushDay; PATCH absent/null keeps the old date. Route/roles/tests: record 006.
- Joint FE/BE release for all 14 breaking changes; both warnings, seasonEnded/endedSeasons and coded unsupported-version 409; support old and new snapshots. Scheduler stays **503**.
- Word/diagram backlog and findings 3/4/7/8/9/P2–P6/priorityCoverage remain open; detailed sources in merge plan §6. No reports/diagrams modified.

## 8. Files, verification and delivery

Added this record and `docs/release/F03_F05_MERGE_PLAN.md`; updated the day README, record index, documentation map and docs portal. Two drafts are outside Git in `outputs/merge/` in the project folder, not sent and no PR created. No existing release-notes directory found in the inspected docs levels; the requested release directory is used.

Final checks below record this pass; they do not claim runtime verification. Proposed commit: `docs: add record 007 and F03/F05 merge plan`; NOT CREATED. No publication or integration. NOT RUN remains Linux SPMF orphan cleanup, GitHub CI, interactive Swagger UI, Windows and real-data conversion; no production readiness claim.

### Final checks in this documentation pass
- Relative Markdown file targets: **1,062 checked, 0 broken** (same check method as record 006; fenced examples excluded). Newly authored section-anchor links were also checked against target headings; none missing.
- Working-tree forbidden-path/identifier grep returned **0 matches**; no absolute workstation locations or local account names were introduced. The clean committed HEAD check remains the cleanup result; new untracked docs were checked explicitly too.
- OpenAPI remains byte-identical to HEAD: **103 paths / 125 operations**; src tree remains `a20970025d3040b80daf602856a681b9d385c1fd`. No source/test/migration/script edits, no new Maven run.
- `git diff --check` passed. Four tracked docs modified plus two new docs; the new release directory contains only the merge plan. Staged diff empty. Both external drafts verified outside Git; FE notice has exactly 14 old→new rows and PR draft has the requested footer.
- Full wildcard remote tracking was verified after fetch; no origin tracking ref for this feature branch exists. This supports the unpushed state in the inspected remote snapshot, not a guarantee about other copies/remotes.
- No commit created. Proposed message remains `docs: add record 007 and F03/F05 merge plan`. No push, PR, merge, rebase, pull, reset, amend, tag, deployment or message send.

### Owner-approved CI selector follow-up — 2026-11-16

The sole CI/script reference to the removed integration class was `.github/scripts/test-groups.py:19`. Only that backend entry is replaced with `PushDayIntegrationTest`, `WeightV2CalculationTest`, `VersionedUtilityJsonTest` and `VersionedUtilityPersistenceTest`: batch rules, W2 calculation and legacy/frozen JSON compatibility. Already-selected UtilityIntegrationTest and WeightRulesV12MigrationTest remain; all other entries/order and the MySQL group are unchanged (checked by parsed-list equality).

Both selector validations exited 0: **51 backend classes and 11 MySQL classes**, every selected class present on the inspected committed source tree. One Maven execution ran **only the four newly selected classes** on Java 17 / disposable H2 memory databases: **31 PASS, 0 FAIL, 0 ERROR, 0 SKIP; exit 0 / BUILD SUCCESS**. Breakdown: PushDayIntegrationTest 9, WeightV2CalculationTest 12, VersionedUtilityJsonTest 7, VersionedUtilityPersistenceTest 3. No MySQL or shared database used; no full-gate rerun claimed.

External run evidence: `outputs/evidence/2026-11-16/ci-selector/` in the project folder (not in Git). This resolves the earlier backend-selector blocker only. Integration still requires an approved PASPM_CI_SPMF_ARTIFACT_URL, exact hash/banner and Java25 child; repository variables/secrets, workflow triggers and integration settings were not changed. Skip-free CI coverage and actual hosted job results remain unverified. Source/tests/migrations unchanged.

The owner authorized a local commit `ci: update backend test selector after Weight v1.2 test removal` and normal push of this feature branch only. No PR creation or merge authorized; the external PR-body draft is updated after a verified push.

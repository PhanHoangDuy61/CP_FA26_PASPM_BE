# ROC-20261116 — W2 follow-up, F05 scale and documentation updates

2026-11-16 / Asia/Ho_Chi_Minh. UNRELEASED, COMPLETED within local follow-up scope.

- Documentation: approved docs sync committed locally at `369dba4`.
- Fixed and locally verified: clear batch push day, combined warnings, structured ended-season view, coded unsupported-version error.
- [Record 006](06-f03-f05-followup-fixes.md) continues the prior session series using the actual work date.
- No push, merge or deployment. Local fix commit authorized after coverage review; see record 006 and Git log. No push.

Verification: full opt-in **646 PASS / 0 FAIL / 5 SKIP** (651 discovered), JPA supplementary **5/5 PASS**; MySQL group **72/72**. No migration changes. Linux orphan cleanup / CI / Swagger UI / real-data conversion NOT RUN.

## Record 012 — F05 FoodMart-scale mining input

[Record 012](12-f05-foodmart-scale.md) records the local change from 256 to 1,600 distinct Products (20,000 matrix rows unchanged), bounded FoodMart SPMF/EFIM measurements and the synthetic 1,559-Product persisted F03 → F05 → F06 acceptance. Synthetic scale E2E and the corrected MySQL group passed; the `minUtil=1,000` attempt hit OUTPUT_LIMIT. Authentic FoodMart F03 acceptance remains NOT RUN because the source lacks canonical order grouping and approved inventory/season evidence. The owner approved merging this change with the dense-fixture, pattern-cap, output-overshoot, JPA-clone and authentic-data items recorded as known limitations, not blockers. Git integration remains pending.

Latest gate: default verify 654 discovered / 445 PASS / 0 FAIL / 0 ERROR / 209 SKIP; corrected MySQL 72/72; full opt-in 664 discovered / 637 PASS / 0 FAIL / 0 ERROR / 27 SKIP; Flyway fresh/restart/upgrade V1–V26 PASS. A MySQL retest initially failed seven backup-test prerequisites when the restricted restore helper was not supplied; the subsequent disposable-MySQL run supplied the existing repository helper and passed all 72 selected cases. All 22 owner-grouped multi-JVM cases passed: 12 scheduler, 5 upgrade compatibility, and 5 MiningMultiJvm methods (6 Maven invocations because one is repeated). The 5 JPA clone cases remain NOT RUN. The first full-runner attempt's Flyway FAIL statuses came from stale V1–V19 assertions; the verifier was corrected and Flyway rerun passed. See external evidence manifest at `outputs/evidence/2026-11-16/f05-foodmart-scalability/manifest.json` (outside Git). No deployment.

## Record 007 — cleanup and merge planning

[Record 007](07-cleanup-and-merge-plan.md) records the owner-approved HEAD amend and metadata cleanup, the fresh origin/dev check and [merge plan](../../release/F03_F05_MERGE_PLAN.md). This docs pass is UNRELEASED/uncommitted; external PR/FE drafts are unsent. Current source evidence remains record 006; no new runtime test.

## Record 008 — F05 EFIM-only mining

[Record 008](08-f05-efim-only-and-benchmark-removal.md) records the local F05 change to fix new mining to EFIM, remove benchmark operations, preserve historical reads, correct the OpenAPI schemas, and document verification limits. Source and documentation changes are UNRELEASED and uncommitted; no migration, push, merge, or deployment.

## Record 010 — legacy product fields and two F03 routes removed (P5/P6)

[Record 010](10-remove-legacy-product-fields-and-routes.md) records the owner decisions of 2026-11-16 and their implementation on branch `Khoi/feat/f03-remove-legacy-product-fields` from `origin/dev` `2a944c5`:
- [P2–P4 approved](../../decisions/2026-11-16/DEC-20261116-01-p2-p4-approved.md);
- [P5: product legacy fields removed, migration V26](../../decisions/2026-11-16/DEC-20261116-02-p5-remove-legacy-product-fields.md);
- [P6: two routes deleted](../../decisions/2026-11-16/DEC-20261116-03-p6-delete-obsolete-routes.md).

It also covers a separate test-only repair for the stale tests left by merge `2a944c5`. The work is committed locally only and is UNRELEASED.

Verification: Final gate: feature **478 total / 330 PASS / 148 SKIP**, MySQL **72/72**, full opt-in **662 total / 657 PASS / 5 SKIP**, supplementary JPA **5/5**; all exits 0, zero FAIL/ERROR. The Flyway-checksum docs branch renumbers its record 008 to 011; 009 is unused.

## Record 011 — known Flyway checksum mismatches

[Record 011](11-flyway-checksum-troubleshooting.md) (first committed as 008): verified the V20 (`89726244` → `1793889682`) and V7 (`-1762009830` → `-1490036030`) checksums with Flyway 11.7.2's own calculator on Git history, added [known checksum mismatches](../../setup/LOCAL_SETUP_TROUBLESHOOTING.md#known-flyway-checksum-mismatches) with a guarded local-only recreate, and originally updated the setup schema range to V1–V25. This merge extends the current setup range to V1–V26. Branch `Khoi/docs/flyway-checksum-troubleshooting` from `origin/dev` `3e438f8`; documentation only; no real database touched.

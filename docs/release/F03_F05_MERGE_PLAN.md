# F03/F05 merge plan — W2 and mining progress

Status: **PROPOSED / NOT MERGE_READY**, 2026-11-16, Asia/Ho_Chi_Minh. Branch `Khoi/feat/f05-progress-f03-weight-v2`; inspected HEAD `8f9ec7d`. This plan authorizes no publication, integration, database operation or deployment. Owners must close the gates below. [Record 007](../record-of-changes/2026-11-16/07-cleanup-and-merge-plan.md) records the executed checks; [record 006](../record-of-changes/2026-11-16/06-f03-f05-followup-fixes.md) holds the verified implementation evidence.

No existing release/merge-notes directory was found at documentation root or one level below; `docs/release/` is created for this plan. Existing feature contracts and dated records remain canonical for implementation details.

## 1. What the branch delivers

| Feature | Delivery | Evidence |
|---|---|---|
| F02 | Batch pushDay create/PATCH/read; additive Store-Admin DELETE clear action, foreign/unknown 404; deprecated product expiryPushDays ignored by scoring | commits e38b55f, 2e7508e; record 006 §2; `src/main/java/vn/edu/fpt/paspm/catalog/inventory/InventoryBatchController.java:63` |
| F03 | W = 1 + lambda(alpha U_expiry + beta M), half-up 2 decimals; FEFO batch push-day risk, sales window N, seasons and assignments, version-aware frozen readers, live-config conversion; coded unsupported-version 409 | e38b55f, 2e7508e; records 004/006; [W2 requirements](../requirements/F03_WEIGHT_V2_REQUIREMENTS.md) |
| F05 | Fenced scalar progress; Merchandiser own-Store submission using system mining configuration; new Merchandiser requests blocked if snapshot lambda exceeds current maximum; System Admin rights retained; scheduler enable stays 503 | 3bdfee3, bd60c75; record 002; record 006 final regression |
| F06 | Push-day consumers, current eligibility warnings, both risk warnings, structured seasonEnded/endedSeasons plus explanation; no automatic mining or generation | e38b55f, 2e7508e; [F06 API follow-up](../api/F06_RECOMMENDATION_API.md#2026-11-16--structured-ended-season-state-additive) |
| F07/F08 | Already present in the base merge; no feature-source change in this branch. A test fixture constructor was adapted to additive RecView fields. priorityCoverage remains null/N/A | base 8f94f94; record 006 §3; `src/main/java/vn/edu/fpt/paspm/layout/LayoutCampaignService.java:267` |

Migrations: V24 SQL adds progress stage/timing/current-run metadata without changing request status; V25 Java adds batch push day, season/assignment/frozen-season tables, matrix columns/checks and `utility_weight_v2_conversion`, and converts only live Store configs/global policy. Frozen snapshot/mining/recommendation JSON is not rewritten. Sources: `src/main/resources/db/migration/V24__f05_mining_progress.sql:2`, `src/main/java/db/migration/V25__f03_weight_v2_expiry_season.java:15`, `:20`, `:80`, `:105`; [schema notes](../architecture/F03_W2_SCHEMA_V24_V25.md).

OpenAPI: **124 → 125 operations** (102 → 103 paths) after the follow-up; the [handoff table](../frontend_handoff/F03_W2_BREAKING_CHANGES.md#breaking) lists **14 breaking changes**. One additional **non-breaking route**: `DELETE /api/inventory-batches/{id}/push-day`. This +1 is relative to the W2 export, not the total branch delta from origin/dev. Evidence: record 006 final QA; checked-in [OpenAPI](../api/openapi.json).

## 2. Merge blockers and checks

| Check | Status | Evidence / closure |
|---|---|---|
| Fetch and compare dev | DONE | `git fetch origin` succeeded this session; origin/dev = 8f94f94698e973bda11c84536cdc4e3311e5568a; `git log 8f94f94..origin/dev` empty; left/right count 0/8 |
| V24/V25 number clash | DONE for fetched state | origin/dev migration max V23; branch adds V24 SQL and V25 Java. Recheck immediately before integration; a future clash is OPEN and blocks merge |
| Latest local full gate | DONE, scoped evidence | record 006: 651 discovered, 646 PASS, zero FAIL/ERROR, 5 SKIP; supplementary synthetic JPA 5/5; MySQL group 72/72. src tree a20970025d3040b80daf602856a681b9d385c1fd unchanged |
| Re-run after bringing in new dev or any source/test change | DONE: not currently triggered | Base has not moved and source has not changed. Becomes OPEN if dev/source changes; owner-approved integration and fresh full gate required |
| FE notice and coordinated FE readiness | OPEN | External draft prepared, NOT SENT; require delivery/acknowledgement and FE acceptance of all 14 changes + clear action/badges/errors |
| PR CI result | NOT VERIFIED | No PR or GitHub-hosted CI executed for this branch; require actual passing checks on the proposed PR head |
| CI SPMF provisioning | NOT VERIFIED | Current repository variable/approved hosting not checked. Integration requires PASPM_CI_SPMF_ARTIFACT_URL, Java25 child, exact hash and 2.67a banner; workflow :112, :132–141; provision-spmf.py:12, :16–28 |
| CI backend selector | OPEN — verified static preparation failure | `.github/scripts/test-groups.py:19` names removed WeightRulesV12IntegrationTest. Executing `python3 .github/scripts/test-groups.py backend` exits 1 with missing-suite error. Fix selector under separate authorization before claiming CI readiness; no script edited here |
| CI skipped-test coverage/provisioning | OPEN | `.github/scripts/test-summary.py:20` rejects skips. Current workflow :154–165 supplies multi-JVM but not natural-lease, baseline-JAR or synthetic-JPA opt-ins. Local supplementary success does not turn PR skips into CI PASS; define and execute valid CI coverage without weakening safeguards |
| Independent reviewer approval | OPEN | No review eligibility or approval verified this session; require an eligible independent human's approval on exact PR head, no self/retroactive approval |
| Linux SPMF orphan-cleanup acceptance | OPEN | record 006: NOT RUN; macOS recovery evidence is not Linux parent-death acceptance. Require the existing Linux deployment acceptance before production-readiness claims |
| P2–P6 business confirmation | OPEN | [Decision register](../decisions/OWNER_DECISIONS_F03_F05_2026-11-15.md#assumptions-pending-owner-confirmation); do not relabel assumptions as approval |

Recommendation: **Squash and merge**, because the branch is unpushed and older intermediate commits contain machine paths/generated artifacts removed from the final tree (record 007). Squashing prevents those intermediate branch commits from entering dev ancestry. It does not purge pre-existing dev history or hide commits uploaded for PR review. Pushing the current branch would publish those intermediate commits; the owner must consider that exposure, or separately authorize a clean publication strategy. No further history rewrite is authorized by this plan.

## 3. Ordered owner-controlled steps

| Step | Responsible role | Action and closing evidence |
|---|---|---|
| 1 | Owner authorizes; Backend maintainer executes | Commit this docs pass only after approval. Evidence: exact commit hash, docs-only diff and clean status. Proposed message: `docs: add record 007 and F03/F05 merge plan` |
| 2 | Owner / FE lead | Send the prepared FE notice. Evidence: actual acknowledgement, screen checklist and agreed joint release window. Draft existence is not notification |
| 3 | Owner / Backend maintainer | Fetch again. If dev moved, first obtain owner approval for integration, check migration numbering, then integrate and rerun the full opt-in gate plus supplementary cases. Record source fingerprint/counts/exit codes. Do not modify applied migrations |
| 4 | Owner / Backend maintainer | Authorize publication method after the history-exposure consideration above; push the branch only after explicit approval. Evidence: remote branch SHA equals reviewed local head |
| 5 | Owner / Backend maintainer | Create the dev-targeted PR using `outputs/merge/PR_BODY_F03_F05.md` in the project folder. Evidence: actual PR URL and head/base SHAs. No PR created by this task |
| 6 | Backend/CI maintainer | Resolve selector/provisioning/coverage issues under approval, configure approved SPMF artifact, run actual PR CI. Evidence: job URLs, counts, no unexplained skips, Java17/Java25/hash/banner checks; rerun after fixes |
| 7 | Eligible independent reviewer / PO / FE lead | Review exact head, migrations, history strategy, business assumptions and compatibility. Evidence: actual review decisions and FE/PO sign-off; no fabricated eligibility |
| 8 | Owner / release coordinator | Explicitly authorize Squash and merge into dev after gates close, coordinated with FE release. Evidence: final checks, squash commit and scheduled joint deployment; no merge performed now |
| 9 | Owner / maintainer | After verified merge and explicit cleanup authorization: delete remote feature branch, remove local worktree only when clean and no work remains. Leave `outputs/evidence/2026-11-16/` in the project folder intact; arrange durable restricted storage before deleting temporary environments. No cleanup executed by this task |

## 4. Before deploy

All operational checks below are **NOT RUN** here. Source: [record 004 §6 and checklist](../record-of-changes/2026-11-15/04-f03-weight-v2-expiry-season.md#6-pre-deploy-checks-not-run--no-real-database-was-used), record 006 Before deploy, V25 source.

- Inventory schema/history and run the read-only scoringVersion check on every target before V25. This is not permission to access or change real databases. Supported labels are F03_LINEAR_V1, EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1 and W2_EXPIRY_SEASON_V1; missing historical labels may be legacy. A clean query is not full data-readiness proof.

```sql
SELECT snapshot_id, JSON_EXTRACT(config_snapshot,'$.config.scoringVersion') AS v
FROM utility_snapshot
WHERE JSON_EXTRACT(config_snapshot,'$.config.scoringVersion') IS NOT NULL
  AND JSON_UNQUOTE(JSON_EXTRACT(config_snapshot,'$.config.scoringVersion'))
      NOT IN ('F03_LINEAR_V1','EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1','W2_EXPIRY_SEASON_V1');

SELECT request_id, JSON_EXTRACT(utility_config_snapshot,'$.configuration.config.scoringVersion') AS v
FROM mining_request
WHERE JSON_EXTRACT(utility_config_snapshot,'$.configuration.config.scoringVersion') IS NOT NULL
  AND JSON_UNQUOTE(JSON_EXTRACT(utility_config_snapshot,'$.configuration.config.scoringVersion'))
      NOT IN ('F03_LINEAR_V1','EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1','W2_EXPIRY_SEASON_V1');

SELECT store_id, JSON_EXTRACT(utility_config,'$.scoringVersion') AS v
FROM store
WHERE JSON_EXTRACT(utility_config,'$.scoringVersion') IS NOT NULL
  AND JSON_UNQUOTE(JSON_EXTRACT(utility_config,'$.scoringVersion'))
      NOT IN ('F03_LINEAR_V1','EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1','W2_EXPIRY_SEASON_V1');
```

- Take a verified pre-V25 database backup, including schema, data and Flyway history, and validate restore on an isolated copy. Record backup identity and recovery approval; never put credentials or private dumps in Git.
- Stop **all old Backend instances before V25**. Old-config reader incompatibility is inferred from code; a safe rolling upgrade is NOT VERIFIED. Keep old instances stopped while migrations run.
- Apply unchanged V24/V25 in Flyway order; check history/checksums and `utility_weight_v2_conversion`. Record actual per-Store conversion, defaults and clamping; synthetic results are not real Store conversion evidence.
- Store Admins enter batch push days; no automatic backfill. FE must support create/PATCH and the new **DELETE clear action**, whose returned BatchView has null pushDay; PATCH null does not clear. Sources: record 006; [F02 handoff](../frontend_handoff/F02_HANDOFF.md#2026-11-16--optional-clear-push-day-action).
- Release FE and BE together; accept legacy + W2 snapshot readers, both warning codes, structured ended-season fields and the 409 unsupported-version error. Recheck role and Store isolation on the approved environment. Sources: [breaking changes](../frontend_handoff/F03_W2_BREAKING_CHANGES.md), record 006.
- Scheduler enable **stays HTTP 503**. Successful startup does not approve scheduled-mining activation or Linux orphan cleanup. Source: record 006 final regression.

## 5. Rollback and partial migration

| Situation | Safe interpretation / recovery plan | Evidence |
|---|---|---|
| Before any V25 change | Retain old application + matching FE and pre-upgrade backup. Database untouched rollback is possible only if schema/data truly remain at the old state; deployment procedure NOT VERIFIED | record 004 Before deploy |
| V25 started and failed | MySQL DDL may already persist; do not assume transaction rollback or retry safety. Stop writers, inspect exact schema/history on an isolated copy and use the verified pre-upgrade backup as the safe recovery path. Do not edit migrations, auto-repair or blindly rerun | V25:105–110 executes DDL before conversion; record 004 records MySQL CHECK failures; recovery procedure NOT VERIFIED |
| V25 completed | Application-only downgrade against converted live configs is not established as safe. Schema additions plus normalized coefficients, defaults, clamping and config-version changes require coordinated data/schema recovery; no tested down migration exists in this plan | V25:20–110, :134–184; WeightV2Conversion:37–53; record 004 |
| Frozen history | V25 does not rewrite historical snapshot/mining/recommendation payloads; preservation is not a complete rollback mechanism | V25:15–17; record 006 legacy regression |
| Conversion evidence | old_value/new_value support inspection, not an approved inverse migration. How to reconcile new post-upgrade writes with an old backup is NOT VERIFIED; restore risks losing such writes and requires owner-approved recovery | V25:80–101, :154–184 |

No database recovery, Flyway repair, migration reversal or rollback rehearsal executed here. A database backup/isolated restore is the safe rollback basis; exact production procedure remains NOT VERIFIED.

## 6. Known gaps carried forward

- Findings **3, 4, 7, 8, 9**: H2 season-name case sensitivity; unused mining_run.attempt_no; per-product F06 sales aggregate; old-instance incompatibility; progress can drop after recovery. Source: record 005 §5 and record 006 §7. They are not fixed by this plan.
- **P2–P6 remain pending owner confirmation**: highest active season, inclusive active dates, reached push day means risk, deprecated expiryPushDays accepted/ignored, same routes with new bodies. Source: decision register assumptions.
- F07/F08 **priorityCoverage remains null/N/A**; no implementation added. Source: LayoutCampaignService:267 and record 005.
- Word/report backlog: SRS BR-05, §3.4, mining use cases and screen authorization; Report 1 roles; Report 4 §4.1 (lambda owner, season definition, 1.115 → 1.12, mining actors). Source: record 005 §6 and record 004 report addendum.
- Diagram backlog: conceptual/logical ERDs, F03 area page, R2-06.02/03/04 states, F03/F05 classes, R2-08.03/F03 snapshot sequences, use cases and screen flow. Source: record 005 §6 and schema notes. No Word/Draw.io edited.
- Linux orphan cleanup, GitHub CI, interactive Swagger UI, Windows and real-data conversion remain NOT RUN (record 006). Neither plan nor drafts close these gates.

## 7. Drafts and evidence retention

External unsent drafts: `outputs/merge/PR_BODY_F03_F05.md` and `outputs/merge/FE_NOTICE_F03_F05.md` in the project folder (not in Git). External evidence: `outputs/evidence/2026-11-16/` in the project folder (not in Git); the eight-file hash manifest is in record 006. Keep originals outside Git, do not recommit raw evidence or machine paths. Drafts require owner review before sending or PR creation. Record 007 supplies this session's checks and limitations.

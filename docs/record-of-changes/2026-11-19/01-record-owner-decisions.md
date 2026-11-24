# ROC-20261119-001 — Owner decisions recorded; stale merge status corrected

| Field | Value |
|---|---|
| Record ID | ROC-20261119-001 |
| Date / timezone | 2026-11-19 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Opus 5.5) |
| Type | Documentation |
| Features | F04 (Excel merge plan), F07/F08 (A/B, priority coverage, zone capacities), F09/F10 (MVP plans), process (PR rule, UML baseline) |
| Branch / base HEAD | `Khoi/docs/decisions-20261119`, created from `origin/dev` `530651bad5b02d90bcb48ecffaf7fa5a0f6b0b7e` after `git fetch` |
| Execution status | COMPLETED for the scope below |
| Release status | PR_OPEN pending — branch pushed; the pull request into `dev` is opened on GitHub (`gh` was not available) |
| Daily summary | [2026-11-19](README.md) |

## 1. Request and scope

The owner asked to record decisions that so far existed only in working conversations and in the project-level agent docs (`outputs/agent-docs/`, outside this repository; their audit of 2026-11-19 listed them as missing from `docs/decisions/`), and to correct status lines that still said "local / unpublished" after the merges of 2026-11-17. Docs only: no source, migration, OpenAPI or configuration change.

## 2. Change details

### Added

Seven decision files, one decision per file, in the existing date-folder convention:

| ID | Title | Status |
|---|---|---|
| [DEC-20261112-01](../../decisions/2026-11-12/DEC-20261112-01-f07-f08-two-layout-ab-comparison.md) | F07/F08 A/B compares two layouts; baseline A is view-only | Implemented in the API; the B − A score difference is a Frontend display |
| [DEC-20261117-02](../../decisions/2026-11-17/DEC-20261117-02-priority-coverage-definition.md) | `priorityCoverage`: share of the layout given to priority products (W > 1) | Decided, not implemented (`LayoutCampaignRepository.java:344-345` still writes `NULL,NULL`) |
| [DEC-20261117-03](../../decisions/2026-11-17/DEC-20261117-03-f09-f10-proceed-with-mvp-plans.md) | F09 Storefront and F10 Bundle Cart proceed with the proposed MVP plans | Approved direction; plan drafts live outside the repository |
| [DEC-20261119-01](../../decisions/2026-11-19/DEC-20261119-01-dev-changes-only-through-pr.md) | `dev` changes only through a pull request with green CI | Process rule, effective 2026-11-19 |
| [DEC-20261119-02](../../decisions/2026-11-19/DEC-20261119-02-uml-baseline-36-page-pack.md) | UML baseline is the 36-page pack | Not yet applied; committed after its V26 update |
| [DEC-20261119-03](../../decisions/2026-11-19/DEC-20261119-03-layout-zone-capacities-before-f09.md) | Configure layout zone capacities before F09 | Values TBD by the owner |
| [DEC-20261119-04](../../decisions/2026-11-19/DEC-20261119-04-f04-excel-merge-after-rebase.md) | Merge F04 Excel import after rebase onto `dev` and green CI | In progress (`Khoi/feat/f04-excel-import-v26`) |

For DEC-20261112-01 the points were checked against [F07/F08 API](../../api/F07_F08_LAYOUT_CAMPAIGN_API.md) and the code: two layout roles per campaign (V23 `ck_layout_role`, `uq_layout_campaign_role`), layouts created only inside campaign creation (`LayoutCampaignService.java:138-140`, no layout-create route), A rejected on edit with 409 (`:642`), `weightedUtilityScore` on every layout of the same snapshot (`LayoutContracts.java:60`).

### Changed

Status lines only; content is unchanged and the old wording is quoted in each line:

| File | Line changed | New status |
|---|---|---|
| [DEC-20261117-01](../../decisions/2026-11-17/DEC-20261117-01-efim-aware-lossless-int32-guard.md) | **Status** ("committed locally, not published") | Merged into `dev` by `530651b` on 2026-11-17 |
| [ROC-20261117-014](../2026-11-17/14-demo-seed-weight-v2.md) | Release status; §8 Delivery | MERGED — `eaee68d`, `331fe91` via `ac29811` |
| [ROC-20261117-015](../2026-11-17/15-f05-efim-aware-int32-guard.md) | **Status**; final delivery paragraph | MERGED via `530651b` (with fixup `bb4262d`) |
| [2026-11-17 daily summary](../2026-11-17/README.md) | Dated status note under the header | 013 via `705e3d7`, 014 via `ac29811`, 015 via `530651b` |
| [Record index](../README.md) | 2026-11-17 status column | MERGED_TO_DEV, with the three merge commits |

### Not changed: CI job names

Two jobs in `.github/workflows/backend-verification.yml` still read "MySQL 8.4 / Flyway V1-V11 / F04 transactions" (`:45`) and "Full F01-F06 / Flyway / SPMF HUI-Miner-EFIM" (`:92`). They were **not renamed**: whether these names are required status checks in the `dev` branch protection could not be checked (`gh` is not installed; branch protection is not readable from the repository). Renaming a required check would block every future PR until the protection rule is updated. A repository admin can rename them together with the protection rule.

## 3. Files changed

| Path | Added / Modified | Content |
|---|---|---|
| `docs/decisions/2026-11-12/DEC-20261112-01-…md` | Added | Decision |
| `docs/decisions/2026-11-17/DEC-20261117-02-…md`, `DEC-20261117-03-…md` | Added | Decisions |
| `docs/decisions/2026-11-19/DEC-20261119-01…04-…md` | Added | Decisions |
| `docs/decisions/2026-11-17/DEC-20261117-01-…md` | Modified | Status line |
| `docs/record-of-changes/2026-11-17/14-…md`, `15-…md`, `README.md` | Modified | Status lines |
| `docs/record-of-changes/README.md` | Modified | 2026-11-17 status; 2026-11-19 row |
| `docs/record-of-changes/2026-11-19/README.md`, `01-record-owner-decisions.md` | Added | This record |

## 4. API and compatibility impact

None. No endpoint, DTO, validation, RBAC, migration, OpenAPI or configuration change. DEC-20261117-02 and DEC-20261119-03 describe future work; until it is implemented `priorityCoverage` stays `null` and zones stay unbounded.

## 5. Verification executed in this session

| Command / method | Environment | Actual result | Evidence |
|---|---|---|---|
| `git diff --name-only origin/dev...HEAD` | Worktree | Only `docs/` paths | This branch |
| Relative-link check over `docs/**/*.md` (Python, read-only) | Worktree | 0 broken links | — |
| `git grep` scan for private absolute paths (temporary and home directories) | Worktree | 0 hits | — |
| Maven tests | — | NOT RUN (docs-only change) | — |

## 6. Previous evidence referenced

Code lines cited in the DEC files were read at `origin/dev` `530651b`. Merge commits `705e3d7`, `ac29811`, `530651b` were read from `git log`.

## 7. Limitations and next actions

- The pull request must be opened on GitHub and merged after CI is green (DEC-20261119-01).
- CI cannot be fully green while the repository variable `PASPM_CI_SPMF_ARTIFACT_URL` is missing (recorded 2026-11-16); the admin must set it.
- Owner: set the zone capacity values (DEC-20261119-03); commit the 36-page UML pack after its V26 update (DEC-20261119-02).
- `priorityCoverage` implementation is a separate change.

## 8. Delivery

One commit on `Khoi/docs/decisions-20261119`, pushed to `origin`; no merge into `dev` from this session.

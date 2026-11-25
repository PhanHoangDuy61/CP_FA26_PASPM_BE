# ROC-20261120-001 — Product documents v0.4 and Codex sandbox note

| Field | Value |
|---|---|
| Record ID | ROC-20261120-001 |
| Date / timezone | 2026-11-20 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Opus 5.5) |
| Type | Documentation |
| Features | F05 (whole-đồng SPMF input), F07/F08 (zone caps, top-K baseline, equal-size simulation); agent instructions |
| Branch / base HEAD | `Khoi/docs/product-sync-20261120`, created from `origin/dev` `636952b` after `git fetch` |
| Execution status | COMPLETED for the scope below |
| Release status | PR_OPEN pending — branch pushed; the pull request into `dev` is opened on GitHub (`gh` not available) |
| Daily summary | [2026-11-20](README.md) |

## 1. Request and scope

Sync the agent-facing product documents to v0.4 after PRs #22 (zone caps, top-K baseline, equal-size simulation) and #23 (whole-đồng SPMF fallback), and add a "Codex sandbox" note to `AGENTS.md`. The files were prepared outside the repository and are copied byte for byte. No source, configuration or test change; `CLAUDE.md` and `docs/product/README.md` are unchanged.

## 2. Change details

### Changed
- `docs/product/BRD_VISION.md`, `SRS.md`, `ARCHITECTURE_SDD.md`, `GLOSSARY.md` → v0.4, synced with `dev` @ `636952b`: the items "planned" for PRs #22 and #23 are now DONE; the multi-day demo minUtil result (`PERCENTAGE 0.12` → 180 patterns on 2026-10-01..04, `LOSSLESS`) and the layout-size sensitivity evidence are added.
- `paspm-context` skill (both `.agents/skills/` and `.claude/skills/`, identical): adds the whole-đồng fallback and the zone-caps / equal-size facts.
- `AGENTS.md`: new "Codex sandbox" section (no network: hand push and PR steps back to the human; Maven offline with `-o`).

## 3. Files changed

| Path | Change |
|---|---|
| `docs/product/BRD_VISION.md`, `SRS.md`, `ARCHITECTURE_SDD.md`, `GLOSSARY.md` | Modified (v0.4) |
| `.agents/skills/paspm-context/SKILL.md`, `.claude/skills/paspm-context/SKILL.md` | Modified (identical) |
| `AGENTS.md` | Modified |
| `docs/record-of-changes/2026-11-20/README.md`, `01-product-docs-v0-4-sync.md`, `docs/record-of-changes/README.md` | Added / modified |

## 4. API and compatibility impact

None. Documentation only.

## 5. Verification executed in this session

| Command / method | Environment | Actual result |
|---|---|---|
| `cmp` of every installed file against the prepared v0.4 files | Worktree | identical |
| `diff -r .agents/skills .claude/skills` | Worktree | no output |
| Relative-link check of `docs/product/*.md`, `AGENTS.md` and the skill | Worktree | 22 links, 0 broken, 0 outside the repo |
| Facts stated by the docs and the skill against `origin/dev` `636952b`: `F03_WHOLE_UNIT_HALF_UP_INT32_DENSE_V1` (`MiningInputBuilder`), `OFFLINE_REPLAY_V2` and `F07_BASELINE_V2` (`LayoutCampaignService`), `LayoutView.equalSizeBaselineScore` / `equalSizeProductCount` in `docs/api/openapi.json`, zone caps in `application.yml` (BANNER 3/3, BUNDLE 4/16, CROSS_SELL 2/8, FBT 2/8, BASELINE 35/35), `docs/testing/F08_LAYOUT_SIZE_SENSITIVITY_20261119.md` (N = 21; hit rate A 0.059967 / B 0.055525; captured utility A 0.017131 / B 0.019189) | Worktree | all match |
| Maven, backend, database | — | NOT RUN (docs-only change) |
| GitHub CI | — | NOT RUN (runs when the PR is opened) |

## 6. Previous evidence referenced

ROC-20261119-003 (whole-đồng fallback and the minUtil sweep), ROC-20261119-004 and `docs/testing/F08_LAYOUT_SIZE_SENSITIVITY_20261119.md` (equal-size sensitivity).

## 7. Limitations and next actions

- `BRD_VISION.md` open question 8 still calls the 20 / 35 / 50 sensitivity runs "optional", while the RQ3 row of the same file states they were run. Reported, not reworded; fix in the next product-docs edit.
- The demo policy value `PERCENTAGE 0.12` is measured but not configured; setting it is an owner action.

## 8. Delivery

One commit on `Khoi/docs/product-sync-20261120`, pushed to `origin`; no merge into `dev` from this session.

# ROC-20261119-005 — Agent kit and product documents installed

| Field | Value |
|---|---|
| Record ID | ROC-20261119-005 |
| Date / timezone | 2026-11-19 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Opus 5.5) |
| Type | Documentation, Organization |
| Features | All (agent instructions and product summaries); no feature behaviour |
| Branch / base HEAD | `Khoi/docs/agent-kit`, created from `origin/dev` `222e54f` after `git fetch` |
| Execution status | COMPLETED for the scope below |
| Release status | PR_OPEN pending — branch pushed; the pull request into `dev` is opened on GitHub (`gh` not available) |
| Daily summary | [2026-11-19](README.md) |

Record numbers 003 and 004 of this day are used by open pull requests (`Khoi/feat/f05-spmf-whole-dong-fallback`, `Khoi/feat/f07-zone-caps-baseline-topk`); this record takes 005 to avoid a collision.

## 1. Request and scope

Install the PASPM agent kit prepared outside the repository so Claude Code and Codex agents read the same rules and product summaries. Copy the files byte for byte; the only content change is the link to the audit report, which is not installed. No source, migration, OpenAPI, CI or configuration change.

## 2. Change details

### Added
- `docs/product/GLOSSARY.md`, `BRD_VISION.md`, `SRS.md`, `ARCHITECTURE_SDD.md` (v0.3.1, synced with `dev` @ `222e54f`) and `docs/product/README.md` (read order; summaries, not sources of truth).
- Root `CLAUDE.md` (Claude Code) and `AGENTS.md` (Codex).
- Skills `paspm-context` and `paspm-backend-workflow`, once under `.claude/skills/` and once under `.agents/skills/` (identical copies).

### Changed
- `docs/README.md` and `docs/DOCUMENTATION_MAP.md`: one link line each to `docs/product/README.md`.
- In the four product documents, the Markdown link to `AUDIT_AGENT_DOCS_20261119.md` (a file not installed) became the plain text `(audit report kept outside the repo, 2026-11-19)`; nothing else changed.
- This day's README lists records 002 and 005; the index row for 2026-11-19 shows the merged PRs #20 and #21.

## 3. Files changed

| Path | Change |
|---|---|
| `CLAUDE.md`, `AGENTS.md` | Added |
| `.claude/skills/paspm-context/SKILL.md`, `.claude/skills/paspm-backend-workflow/SKILL.md` | Added |
| `.agents/skills/paspm-context/SKILL.md`, `.agents/skills/paspm-backend-workflow/SKILL.md` | Added |
| `docs/product/README.md`, `GLOSSARY.md`, `BRD_VISION.md`, `SRS.md`, `ARCHITECTURE_SDD.md` | Added |
| `docs/README.md`, `docs/DOCUMENTATION_MAP.md` | Modified (one line each) |
| `docs/record-of-changes/2026-11-19/README.md`, `05-agent-kit-and-product-docs.md`, `docs/record-of-changes/README.md` | Added / modified |

## 4. API and compatibility impact

None. Agents that read `CLAUDE.md`, `AGENTS.md` or the skills now get project rules automatically; people are unaffected.

## 5. Verification executed in this session

| Command / method | Environment | Actual result |
|---|---|---|
| `cmp` of every installed file against the kit (product docs before the link edit) | Worktree | identical |
| `diff -r .claude/skills .agents/skills` | Worktree | no output (identical) |
| Front matter of each `SKILL.md`: strict Python parser (top-level `key: value` lines only) and Ruby Psych `YAML.safe_load`; PyYAML is not installed | Worktree | 4/4 valid in both; `name` matches the folder, `description` present |
| Relative-link check of `docs/product/*.md`, `CLAUDE.md`, `AGENTS.md`, the skills, `docs/README.md`, `docs/DOCUMENTATION_MAP.md` and this day's records | Worktree | 0 broken, 0 outside the repo |
| Skill facts against `dev` `222e54f`: `python3 .github/scripts/test-groups.py backend` / `mysql`; highest Flyway version; `vn.edu.fpt.paspm.common.ApiException`; `-Dpaspm.test.export-openapi=true` hook; `.github/workflows/backend-verification.yml`; `docs/setup/BACKEND_CI.md`; `docs/frontend_handoff/F03_W2_BREAKING_CHANGES.md`; `docs/setup/LOCAL_SETUP_*.md`; `provision-restore.py` mode; `spring.jpa.hibernate.ddl-auto=validate` | Worktree | all match (54 / 12 classes, exit 0; V26; hook in `TransactionExcelImportIntegrationTest`; mode 100644) |
| Maven, backend, database | — | NOT RUN (docs-only change) |
| GitHub CI | — | NOT RUN (runs when the PR is opened) |

## 6. Previous evidence referenced

The product documents were audited against `530651b` and synced with `222e54f` outside the repository (audit report kept outside the repo, 2026-11-19).

## 7. Limitations and next actions

- The kit as prepared had no `.claude/skills/` folder; its README requires `.claude/skills/` and `.agents/skills/` to be identical, so `.claude/skills/` was installed as a byte-for-byte copy of the kit's `.agents/skills/`.
- When records 003/004 merge, their day-README and index entries may need a trivial merge with this record's lines.
- The same kit for the frontend repository is delivered separately (`ThaiTuKien1012/FE-FA26`).

## 8. Delivery

One commit on `Khoi/docs/agent-kit`, pushed to `origin`; no merge into `dev` from this session.

# ROC-20261125-003 — Source-of-truth rule: the latest PO decision wins

| Field | Value |
|---|---|
| Record ID | ROC-20261125-003 |
| Date / timezone | 2026-11-25 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Opus 5.5), requested by Nguyễn Minh Khôi |
| Type | Documentation / Organization |
| Features | None (process rule for contributors and agents) |
| Branch / base HEAD | `Khoi/docs/source-of-truth-rule`, from `origin/dev` @ `82b0ced` |
| Execution status | COMPLETED |
| Release status | PR_OPEN pending (no PR opened by the agent) |
| Daily summary | [README](README.md) |

## 1. Request and scope

PO instruction (2026-11-25): replace the "docs làm chuẩn" / docs-first precedence for every coding agent with the rule in [DEC-20261125-02](../../decisions/2026-11-25/DEC-20261125-02-source-of-truth-latest-po-decision.md), keep everything else in the instruction files unchanged, and align `docs/product/BRD_VISION.md` §9.3.

## 2. Change details

### Changed
- `AGENTS.md`, `CLAUDE.md`: the "Precedence: DEC files and merged code > docs/product > Reports 1–4" line is replaced by the three-point rule; the Reports 1–4 and TBD notes are kept.
- `.claude/skills/paspm-context/SKILL.md`, `.agents/skills/paspm-context/SKILL.md` (byte-identical): §2 "Which source wins" now states the rule, including "Do not fix either side on your own".
- `docs/product/BRD_VISION.md` §9.3: the "docs làm chuẩn" bullet now states the rule and links the DEC.

### Added
- [DEC-20261125-02](../../decisions/2026-11-25/DEC-20261125-02-source-of-truth-latest-po-decision.md) (numbered `-02`: `-01` is used on branch `Khoi/merge/f09-storefront-be`).

### Security, Database
N/A.

## 3. Files changed

| Path | Change | Reason |
|---|---|---|
| `AGENTS.md`, `CLAUDE.md` | Modified | New source-of-truth rule |
| `.claude/skills/paspm-context/SKILL.md`, `.agents/skills/paspm-context/SKILL.md` | Modified | Same |
| `docs/product/BRD_VISION.md` | Modified | §9.3 working rule |
| `docs/decisions/2026-11-25/DEC-20261125-02-source-of-truth-latest-po-decision.md` | Added | The decision |
| `docs/record-of-changes/2026-11-25/03-source-of-truth-rule.md`, `README.md`, `../README.md` | Added / Modified | This record |

## 4. API and compatibility impact

None. No code, schema, API or test change.

## 5. Verification executed in this session

| Command / method | Environment | Actual result | Evidence |
|---|---|---|---|
| `cmp` of the two `paspm-context` skill copies | local | identical | — |
| `git grep "làm chuẩn"` in the changed agent files | local | no match | — |
| Maven | — | NOT RUN (docs only) | — |

## 6. Previous evidence referenced

None.

## 7. Limitations and next actions

- Other docs may still describe the docs-first principle in passing (reports, historical records); historical records are not rewritten.
- The FE repository has no agent instruction files on `dev`; the `paspm-frontend-integration` plugin skill is outside both repositories.
- `docs/record-of-changes/2026-11-25/README.md` is also created on the SPMF (PR #29) and F09 merge branches; expect a small add/add conflict, resolved by listing records 001, 002 and 003 together.

## 8. Delivery

Committed and pushed as branch `Khoi/docs/source-of-truth-rule`; PR not opened. No merge, no deploy.

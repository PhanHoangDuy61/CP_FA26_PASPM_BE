# ROC-20261125-003 — Source-of-truth rule: the latest PO decision wins

| Field | Value |
|---|---|
| Record ID | ROC-20261125-003 |
| Date / timezone | 2026-11-25 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Opus 5.5), requested by Nguyễn Minh Khôi |
| Type | Documentation / Organization |
| Features | None (process rule for contributors and agents) |
| Branch / base HEAD | `Khoi/docs/source-of-truth-rule`, from `origin/dev` @ `82b0ced`; `origin/dev` merged again @ `b94584b` (PR #29) |
| Execution status | COMPLETED |
| Release status | PR_OPEN pending (no PR opened by the agent) |
| Daily summary | [README](README.md) |

## 1. Request and scope

PO instruction (2026-11-25): replace the "docs làm chuẩn" / docs-first precedence for every coding agent with the rule in [DEC-20261125-02](../../decisions/2026-11-25/DEC-20261125-02-source-of-truth-latest-po-decision.md), keep everything else in the instruction files unchanged, and align `docs/product/BRD_VISION.md` §9.3.

## 2. Change details

### Changed
- `AGENTS.md`, `CLAUDE.md`: the "Precedence: DEC files and merged code > docs/product > Reports 1–4" line is replaced by the three-point rule; the Reports 1–4 and TBD notes are kept.
- `.claude/skills/paspm-context/SKILL.md`, `.agents/skills/paspm-context/SKILL.md` (byte-identical): §2 "Which source wins" now states the rule, including "Do not fix either side on your own".
- `docs/product/BRD_VISION.md` §9.3: the "docs làm chuẩn" bullet now states the rule and links the DEC. The opening "Source precedence" block (which ranked owner decisions and merged code first, then the brief, then Reports 1–4) is rewritten to the same rule: the latest PO decision wins; merged code, the docs and the brief are evidence and none wins automatically; differences are reported to the PO and recorded as a DEC.

### Extra fixes after the merge of `origin/dev` @ `b94584b`
- Migration numbering: `CLAUDE.md`, `AGENTS.md` and the schema row of both `paspm-backend-workflow` skill copies no longer say "V27+". They now say: V30 is used by F09, V31 is reserved for F10, new migrations start at **V32**. `ARCHITECTURE_SDD.md` §5 uses the same wording ("New migrations start at **V32**"). The "As of 2026-11-19 that is V27" sentence was already replaced on `dev`.
- `docs/product/ARCHITECTURE_SDD.md` (REST controllers row): "123 operations at `222e54f`" became "139 operations / 112 paths, `docs/api/openapi.json` runtime export at `cded4ab`". Checked by counting `paths` and the HTTP operations in `docs/api/openapi.json` on this branch: 112 / 139.
- `DEC-20261125-04` (F09 addendum): "The SRS is the standard ("docs làm chuẩn")" is reworded to "The SRS requirement FR-09.3 (confirmed by the PO review of 2026-11-25) says". Wording only; the decision (`AVAILABLE | OUT_OF_STOCK`) is unchanged.
- Record 007 (SPMF) in the index is now `MERGED_TO_DEV` (PR #29, `b94584b`); the day README and the index list records 001–010 and keep one row for 2026-11-25.

### Added
- [DEC-20261125-02](../../decisions/2026-11-25/DEC-20261125-02-source-of-truth-latest-po-decision.md) (numbered `-02`: `-01` is used on branch `Khoi/merge/f09-storefront-be`).

### Security, Database
N/A.

## 3. Files changed

| Path | Change | Reason |
|---|---|---|
| `AGENTS.md`, `CLAUDE.md` | Modified | New source-of-truth rule |
| `.claude/skills/paspm-context/SKILL.md`, `.agents/skills/paspm-context/SKILL.md` | Modified | Same |
| `docs/product/BRD_VISION.md` | Modified | §9.3 working rule; opening "Source precedence" block aligned with DEC-20261125-02 |
| `docs/decisions/2026-11-25/DEC-20261125-02-source-of-truth-latest-po-decision.md` | Added | The decision |
| `docs/record-of-changes/2026-11-25/03-source-of-truth-rule.md`, `README.md`, `../README.md` | Added / Modified | This record |
| `CLAUDE.md`, `AGENTS.md`, `.claude/skills/paspm-backend-workflow/SKILL.md`, `.agents/skills/paspm-backend-workflow/SKILL.md` | Modified | Migration numbering: new migrations start at V32 |
| `docs/product/ARCHITECTURE_SDD.md` | Modified | V32 wording; API count 112 paths / 139 operations |
| `docs/decisions/2026-11-25/DEC-20261125-04-f09-public-store-follows-store-status.md` | Modified | Removed the "docs làm chuẩn" wording (decision unchanged) |

## 4. API and compatibility impact

None. No code, schema, API or test change.

## 5. Verification executed in this session

| Command / method | Environment | Actual result | Evidence |
|---|---|---|---|
| `cmp` of the two `paspm-context` skill copies | local | identical | — |
| `git grep "làm chuẩn"` in the changed agent files | local | no match | — |
| `git grep -nIiE 'docs làm chuẩn\|làm chuẩn\|docs-first\|docs are the standard'` on the whole branch | local | matches only in DEC-20261125-02, this record and the day README, which name the replaced rule | — |
| `git grep -nIE 'V27\+\|V27 or later\|add V27\|that is V27'` (excluding `src/test`) | local | matches only in this record (it quotes the old wording) and one historical sentence in `2026-11-20/02-f09-storefront-mvp.md` (not rewritten) | — |
| Count of `paths` and HTTP operations in `docs/api/openapi.json` | local | 112 paths / 139 operations | — |
| Relative links in the changed Markdown files | local | all targets exist | — |
| `git diff --check` | local | clean | — |
| `git grep -nIE 'Source precedence|higher item overrides'` | local | matches only in this record (it names the replaced block); none in `docs/product/BRD_VISION.md` | — |
| Maven | — | NOT RUN (docs only) | — |

## 6. Previous evidence referenced

None.

## 7. Limitations and next actions

- Other docs may still describe the docs-first principle in passing (reports, historical records); historical records are not rewritten.
- The FE repository has no agent instruction files on `dev`; the `paspm-frontend-integration` plugin skill is outside both repositories.
- The add/add conflict of `docs/record-of-changes/2026-11-25/README.md` with the other branches was resolved by listing records 001–010 in order.

## 8. Delivery

Committed and pushed as branch `Khoi/docs/source-of-truth-rule`; PR not opened. No merge, no deploy.

# DEC-20261125-02 — Source of truth: the latest PO decision wins

| Field | Value |
|---|---|
| Date | 2026-11-25 (Asia/Ho_Chi_Minh) |
| Source | Product Owner instruction (Nguyễn Minh Khôi, Team Leader / acting Product Owner), 2026-11-25 |
| Status | OWNER_DECISION, implemented on branch `Khoi/docs/source-of-truth-rule`; PR to `dev` pending |
| Scope | Process rule for every contributor and coding agent, both repositories |
| Replaces | The "docs làm chuẩn" (docs-first) precedence in `AGENTS.md`, `CLAUDE.md`, the `paspm-context` skill (both copies) and `docs/product/BRD_VISION.md` §9.3 |
| Number | `-02` because `DEC-20261125-01` is used on `dev` by the F07 product-level Layout B decision. (Corrected 2026-11-26 per DEC-20261126-03: this line said the F09 public-Store decision, which is DEC-20261125-04.) |

## Context
The agent instructions said that docs are the standard ("docs làm chuẩn"): when code breaks a recorded requirement, fix the code and never rewrite the requirement. They also ranked DEC files and merged code above `docs/product/*` and `docs/api/*`. In practice docs are written at a point in time and fall behind code and later decisions (for example the 2026-11-20 server-generated SKU and the F09 merge findings), so "the doc wins" could push agents to undo correct, newer behaviour.

## Decision
1. Docs record decisions as of their date. They are not automatically right over code.
2. The latest PO decision wins: a DEC file in `docs/decisions/`, or an explicit PO answer in the session.
3. When code and docs disagree, report the difference and ask the PO. Do not fix either side on your own. After the decision, update the docs (and the code if needed) and record a DEC.

Unchanged: anything marked **TBD** is undecided and must be asked, not guessed; Reports 1–4 and older diagrams are partly outdated.

## Consequences
- Agents stop "fixing" code to match an older doc without asking; they report the gap instead.
- A PO answer given in a session must be written down as a DEC before or with the change that applies it.
- `AGENTS.md`, `CLAUDE.md`, `.claude/skills/paspm-context/SKILL.md`, `.agents/skills/paspm-context/SKILL.md` and `docs/product/BRD_VISION.md` §9.3 state the rule. The FE repository has no agent instruction files on `dev`; the `paspm-frontend-integration` skill lives in a plugin outside both repositories.

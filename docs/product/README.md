# Product documents (agent edition)

Agent-facing summaries of the PASPM product for people and AI coding agents (Claude Code, Codex). They are **not** sources of truth: when they disagree with the [owner decisions](../decisions/), the API contracts in [`../api/`](../api/README.md) or the code on `dev`, those win, and the summary is fixed in the same change. Version 0.3.1, synced with `dev` @ `222e54f` (2026-11-19); the audit report behind it is kept outside the repository.

Read in this order:

| # | Document | Content |
|---|---|---|
| 1 | [GLOSSARY.md](GLOSSARY.md) | Terms, roles, codes and states, with their exact spelling |
| 2 | [BRD_VISION.md](BRD_VISION.md) | Problem, goals, scope, key owner decisions, open questions |
| 3 | [SRS.md](SRS.md) | Use cases, functional requirements per feature, business rules, limits |
| 4 | [ARCHITECTURE_SDD.md](ARCHITECTURE_SDD.md) | Components, packages, data design, state machines, key designs |

The skills under `.claude/skills/` and `.agents/skills/` and the root `CLAUDE.md` / `AGENTS.md` point agents here. Statuses use `DONE`, `PARTIAL`, `PLANNED`, `REMOVED` and `TBD`; anything marked TBD is an open owner question.

[Documentation index](../README.md) · [Decisions](../decisions/) · [API index](../api/README.md) · [Record of changes](../record-of-changes/README.md)

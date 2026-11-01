# F04 — Transaction Import

## Current scope

6 mapped CSV/import/history operations; source has per-order persistence/idempotency and an F03 database adapter. The approved feature scope is file-based CSV/Excel. External API connections, POS, webhooks and realtime synchronization are excluded; their absence does not make F04 incomplete. Excel format policy/support is tracked separately from the CSV correction.

[Current improvement and regression report](F04_IMPROVEMENT_REPORT.md).

- [Canonical API](../../api/F04_TRANSACTION_API.md) · [Frontend handoff](../../frontend_handoff/F04_HANDOFF.md).
- [Architecture](../../architecture/README.md) · [diagrams](../../diagrams/README.md) · [documentation map](../../DOCUMENTATION_MAP.md).

## Requirements and authority

[Design and approved CSV policy](reference/F04_DESIGN.md), [implementation plan](reference/F04_IMPLEMENTATION_PLAN.md). Original discovery statements about absent V7/provider are historical stage context.

## Workflow and implementation

Follow the endpoint/use-case sequence in the canonical contract and handoff; no API tables are duplicated here. Source availability is separate from tested/accepted status. [Review register](../../requirements/README.md#documentation-review-register) records unresolved authority and packaging gaps.

## Testing and evidence

[Swagger report](reference/F04_SWAGGER_TEST_REPORT.md), [CSV sample](reference/examples/transactions.csv), [testing index](../../testing/README.md). No new import/API mutation is executed by this reorganization.

## Historical references and known issues

[Original gap analysis](reference/F04_GAP_ANALYSIS.md) records the preimplementation baseline; do not read it as current absence of F04.

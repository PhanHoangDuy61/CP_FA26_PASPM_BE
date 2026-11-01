# F06 — Recommendation Engine

## Current scope

8 mapped operations, versioned policy, candidate review and live eligibility. Approved candidates do not automatically publish/activate layouts or reserve inventory.

- [Canonical API](../../api/F06_RECOMMENDATION_API.md) · [Frontend handoff](../../frontend_handoff/F06_HANDOFF.md).
- [Architecture](../../architecture/README.md) · [diagrams](../../diagrams/README.md) · [documentation map](../../DOCUMENTATION_MAP.md).

## Requirements and authority

[Approved v1.1 rules](../../requirements/F06_RECOMMENDATION_RULES_v1_1.md), [readiness plus appended approval](../../archive/F06/F06_IMPLEMENTATION_READINESS.md). Earlier pending labels retain their stage context.

## Workflow and implementation

Follow the endpoint/use-case sequence in the canonical contract and handoff; no API tables are duplicated here. Source availability is separate from tested/accepted status. [Review register](../../requirements/README.md#documentation-review-register) records unresolved authority and packaging gaps.

## Testing and evidence

[Current hardening and full regression report](F06_HARDENING_REPORT.md) · [F04/F06 measurements and combined proof](../../testing/F04_F06_PERFORMANCE_REPORT.md). These distinguish local results, skipped clone cases and pending hosted/human review.

[Verification](../../testing/reports/F06_VERIFICATION.md), [captured evidence](../../testing/evidence/feature_verification), [implementation summary](F06_IMPLEMENTATION_SUMMARY.md). These are dated execution records; no new test claim here.

## Historical references and known issues

Historical F03/F05 texts saying F06 absent are not authoritative current availability. Use this index and the actual F06 contract.

## Current reliability review

[Hardening report](F06_HARDENING_REPORT.md) documents the candidate cap, rollback, authorization, source-policy review and current local regression results. [Performance measurements](../../testing/F04_F06_PERFORMANCE_REPORT.md) include the measured relation-query optimization and remaining workload limits. Historical verification above keeps its original execution scope.

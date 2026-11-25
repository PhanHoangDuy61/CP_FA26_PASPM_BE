# Feature Documentation

Start with current canonical API/handoff and the feature index. `reference*` contains original requirements, plans, issues and execution records; statuses distinguish what remains current from replaced semantics. Nothing is approved merely by being moved.

| Feature | Scope | Index |
|---|---|---|
| F01 | Authentication, User Management, RBAC, refresh/CSRF/logout (local update) | [F01](F01/README.md) |
| F02 | Product, Category, Inventory | [F02](F02/README.md) |
| F03 | Utility Profiling and Weight Rules | [F03](F03/README.md) |
| F04 | Transaction Import | [F04](F04/README.md) |
| F05 | HUI-Miner / EFIM Mining | [F05](F05/README.md) |
| F06 | Recommendation Engine | [F06](F06/README.md) |
| F07/F08 | Layout simulator and campaigns (no feature README; contract only) | [API](../api/F07_F08_LAYOUT_CAMPAIGN_API.md) · [schema](../architecture/F07_F08_LAYOUT_CAMPAIGN_SCHEMA.md) · [handoff](../frontend_handoff/F07_F08_HANDOFF.md) |
| F09 | Storefront MVP | [F09](F09/README.md) · [API](../api/F09_STOREFRONT_API.md) · [handoff](../frontend_handoff/F09_HANDOFF.md) |

F07/F08 have an API contract (2026-11-12) but no feature README; F10–F11 remain **UNDEFINED_IN_AVAILABLE_DOCS**. F09 is documented on the current local feature branch; see its README for the missing-plan and verification caveats. 2026-11-15 branch status (F03 Weight v2, F05 progress): [record 004](../record-of-changes/2026-11-15/04-f03-weight-v2-expiry-season.md). F06 contains downstream exclusion names only. No empty F10–F11 folders or invented contracts are created. See [review register](../requirements/README.md#documentation-review-register).

## Shared authentication dependency

The [F01 session guide](F01/F01_SESSION_MANAGEMENT.md) describes the 2026-11-11 local update and its F02–F06 impact. Their business endpoints, roles and Store scopes were not changed by the session implementation. [Agent records](../record-of-changes/README.md) distinguish implementation, documentation and verification sessions.

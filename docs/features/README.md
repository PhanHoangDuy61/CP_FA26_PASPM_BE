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

F07–F11: **UNDEFINED_IN_AVAILABLE_DOCS** for a complete feature/API map. F06 contains downstream exclusion names only. No empty F07–F11 folders or invented contracts are created. See [review register](../requirements/README.md#documentation-review-register).

## Shared authentication dependency

The [F01 session guide](F01/F01_SESSION_MANAGEMENT.md) describes the 2026-11-11 local update and its F02–F06 impact. Their business endpoints, roles and Store scopes were not changed by the session implementation. [Agent records](../record-of-changes/README.md) distinguish implementation, documentation and verification sessions.

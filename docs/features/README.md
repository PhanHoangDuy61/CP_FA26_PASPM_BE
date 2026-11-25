# Feature Documentation

Start with current canonical API/handoff and the feature index. `reference*` contains original requirements, plans, issues and execution records; statuses distinguish what remains current from replaced semantics. Nothing is approved merely by being moved.

| Feature | Scope | Index |
|---|---|---|
| F01 | Authentication, sessions and Store management on dev (`7304e29`, PR #27) · 38 operations | [F01](F01/README.md) |
| F02 | Product, Category, Inventory on dev (PR #28) · 18 operations | [F02](F02/README.md) |
| F03 | Weight v2 and P5/P6 on dev (PRs #18/#19, `b8c9e63`) · 18 operations | [F03](F03/README.md) |
| F04 | Async CSV and Excel on dev (`28a6b27`, PR #21) · 9 operations | [F04](F04/README.md) |
| F05 | EFIM mining on dev (`2a944c5`); scheduler implementation (PR #13), activation disabled by default · 23 operations | [F05](F05/README.md) |
| F06 | Recommendation Engine with Weight v2 on dev (PRs #18/#19) · 8 operations | [F06](F06/README.md) |
| F07/F08 | Layout simulator and campaigns on dev (`8f94f94`, `5436bf7`, PR #32) · 21 operations | [API](../api/F07_F08_LAYOUT_CAMPAIGN_API.md) · [schema](../architecture/F07_F08_LAYOUT_CAMPAIGN_SCHEMA.md) · [handoff](../frontend_handoff/F07_F08_HANDOFF.md) |
| F09 | Public Storefront MVP on dev (PR #33) · 4 operations | [F09](F09/README.md) · [API](../api/F09_STOREFRONT_API.md) · [handoff](../frontend_handoff/F09_HANDOFF.md) |
| F10 | Ordinary cart implemented on the F10 Part 1 branch (5 operations); whole-bundle add **PLANNED** | [API](../api/F10_BUNDLE_CART_API.md) · [handoff](../frontend_handoff/F10_HANDOFF.md) |
| F11 | Dashboard **PLANNED** (contract merged in PR #34) | [API](../api/F11_DASHBOARD_API.md) · [handoff](../frontend_handoff/F11_HANDOFF.md) |

The [runtime export](../api/openapi.json) covers **115 paths / 144 operations** on the F10 Part 1 branch, up from 112 paths / 139 operations on dev. See the [review register](../requirements/README.md#documentation-review-register).

## Shared authentication dependency

The [F01 session guide](F01/F01_SESSION_MANAGEMENT.md) describes session authentication and its F02–F06 impact. Their business endpoints, roles and Store scopes were not changed by the session implementation. [Agent records](../record-of-changes/README.md) distinguish implementation, documentation and verification sessions.

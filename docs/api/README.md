# API Reference

Canonical contract locations stay stable in this directory. `CURRENT` describes present documentation scope, not successful execution of every endpoint; `NEEDS_REVIEW` flags unresolved prose. Source/controllers and the checked-in export remain separately traceable.

| Feature | Contract | Status / caveat | Handoff |
|---|---|---|---|
| F01 | [Authentication/User](F01_AUTH_USER_API.md) | Local session update: csrfToken, refresh/logout; 202/409 reconciled; uncommitted | [F01](../frontend_handoff/F01_HANDOFF.md) |
| F02 | [Product/Inventory](F02_PRODUCT_INVENTORY_API.md) | CURRENT scoped contract; old v3 role/expiry clauses differ | [F02](../frontend_handoff/F02_HANDOFF.md) |
| F03 | [Utility](F03_UTILITY_API.md) | NEEDS_REVIEW: final source missing; stale F05 paragraph | [F03](../frontend_handoff/F03_HANDOFF.md) |
| F04 | [Transaction](F04_TRANSACTION_API.md) | CURRENT CSV P0 contract; broader workflow acceptance separate | [F04](../frontend_handoff/F04_HANDOFF.md) |
| F05 | [Mining](F05_MINING_API.md) | NEEDS_REVIEW: dated deployment/acceptance summaries | [F05](../frontend_handoff/F05_HANDOFF.md) |
| F06 | [Recommendation](F06_RECOMMENDATION_API.md) | CURRENT Backend v1.1 contract; historical test statements retain dates | [F06](../frontend_handoff/F06_HANDOFF.md) |
| F07/F08 | [Layout and Campaign](F07_F08_LAYOUT_CAMPAIGN_API.md) | Current working-tree implementation; V23 pending deployment | [F07/F08](../frontend_handoff/F07_F08_HANDOFF.md) |

[OpenAPI export](openapi.json) was refreshed from the Springdoc endpoint in the current integration context: **96 paths and 114 operations**, including 20 F07/F08 operations. The export's server entry is the local test origin; configure the target origin in the integration environment. The session changes are uncommitted. Earlier operation counts are historical evidence, not the current export. Role prose still needs source-based interpretation; use the [RBAC matrix](F01_F02_RBAC_MATRIX.md). [RBAC matrix](F01_F02_RBAC_MATRIX.md) and [integration notes](F03_V12_F05_F06_INTEGRATION.md) have review notices. [API changelog](API_CHANGELOG.md) remains append-only history.

## API Inventory provenance

`API_INVENTORY_BY_FEATURE.md`, `.csv` and `API_FEATURE_GAP_REPORT.md` are absent from current dev. They exist only in local branch `Khoi/docs/backend-api-inventory-audit` at `39a4918`; no remote publication or merge is assumed. This reorganization does not cherry-pick/copy them. They are not presented as live links until a separately reviewed integration supplies them.

F09–F11 contracts are not defined here. F07/F08 routes are documented in the linked contract. The documented unavailable F03 `GET /api/utility/snapshots` is not an implemented route; F05 `GET /api/mining/snapshots` is a distinct selector. Framework Swagger/OpenAPI paths are not business APIs.

Current F01 reconciliation (2026-11-12): preserves published dev V20/AuthSessionService and adds V21 binding; [current evidence](../testing/reports/F01_SESSION_RECONCILIATION_2026-11-12.md). Earlier local-variant details are dated history; no source database repair/deployment was performed.

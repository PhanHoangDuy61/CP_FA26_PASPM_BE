# API Reference

Canonical contract locations stay stable in this directory. `CURRENT` describes present documentation scope, not successful execution of every endpoint; `NEEDS_REVIEW` flags unresolved prose. Source/controllers and the checked-in export remain separately traceable.

| Feature | Contract | Status / caveat | Handoff |
|---|---|---|---|
| F01 | [Authentication/User](F01_AUTH_USER_API.md) | NEEDS_REVIEW: duplicate-email 202/409 mismatch | [F01](../frontend_handoff/F01_HANDOFF.md) |
| F02 | [Product/Inventory](F02_PRODUCT_INVENTORY_API.md) | CURRENT scoped contract; old v3 role/expiry clauses differ | [F02](../frontend_handoff/F02_HANDOFF.md) |
| F03 | [Utility](F03_UTILITY_API.md) | NEEDS_REVIEW: final source missing; stale F05 paragraph | [F03](../frontend_handoff/F03_HANDOFF.md) |
| F04 | [Transaction](F04_TRANSACTION_API.md) | CURRENT CSV/XLS/XLSX file-import contract; feature-branch review pending | [F04](../frontend_handoff/F04_HANDOFF.md) |
| F05 | [Mining](F05_MINING_API.md) | NEEDS_REVIEW: dated deployment/acceptance summaries | [F05](../frontend_handoff/F05_HANDOFF.md) |
| F06 | [Recommendation](F06_RECOMMENDATION_API.md) | CURRENT Backend v1.1 contract; historical test statements retain dates | [F06](../frontend_handoff/F06_HANDOFF.md) |

[OpenAPI export](openapi.json) has 60 paths/74 operations and was regenerated from the actual Spring Boot test runtime for the new Excel route; interactive Swagger testing is not claimed. Other generic error annotations/role prose still need source-based interpretation. [RBAC matrix](F01_F02_RBAC_MATRIX.md) and [integration notes](F03_V12_F05_F06_INTEGRATION.md) have review notices. [API changelog](API_CHANGELOG.md) remains append-only history.

## API Inventory provenance

`API_INVENTORY_BY_FEATURE.md`, `.csv` and `API_FEATURE_GAP_REPORT.md` are absent from current dev. They exist only in local branch `Khoi/docs/backend-api-inventory-audit` at `39a4918`; no remote publication or merge is assumed. This reorganization does not cherry-pick/copy them. They are not presented as live links until a separately reviewed integration supplies them.

F07–F11 contracts are not defined here. The documented unavailable F03 `GET /api/utility/snapshots` is not an implemented route; F05 `GET /api/mining/snapshots` is a distinct selector. Framework Swagger/OpenAPI paths are not business APIs.

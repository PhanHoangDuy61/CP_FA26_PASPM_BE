# Frontend Integration Handoff

Use the actual Backend contract together with its feature handoff. Role checks and Store ownership are enforced by the Backend; fixtures are synthetic captures/templates, not current retailer data or credentials.

| Feature | Handoff | Contract | Notes |
|---|---|---|---|
| F01 | [F01](F01_HANDOFF.md) | [API](../api/F01_AUTH_USER_API.md) | Login/refresh return csrfToken; cookie + CSRF lifecycle, server logout; registration 202/409 |
| F02 | [F02](F02_HANDOFF.md) | [API](../api/F02_PRODUCT_INVENTORY_API.md) | Current catalog-read roles and expiry boundary |
| F03 | [F03](F03_HANDOFF.md) | [API](../api/F03_UTILITY_API.md) | Final spec/example gaps; old Store-window request is compatibility-only |
| F04 | [F04](F04_HANDOFF.md) | [API](../api/F04_TRANSACTION_API.md) | CSV P0, no stock deduction; sample CSV moved with feature references |
| F05 | [F05](F05_HANDOFF.md) | [API](../api/F05_MINING_API.md) | System Admin runs mining, Store users read; start with [OPTION_B_BACKEND_API_HANDOFF.md](OPTION_B_BACKEND_API_HANDOFF.md); System Admin reads snapshots/requests/runs (not patterns) and picks Stores via `GET /api/system/stores` *(Scheduler branch)*; scheduler activation is 503 unless the Backend enables it |
| F06 | [F06](F06_HANDOFF.md) | [API](../api/F06_RECOMMENDATION_API.md) | Candidate review is separate from downstream activation |
| F07/F08 | [F07/F08](F07_F08_HANDOFF.md) | [API](../api/F07_F08_LAYOUT_CAMPAIGN_API.md) | Layout B editing, offline simulated replay, campaign lifecycle; V23 pending deployment |

Existing `F01_examples`–`F03_examples`, `F05_examples` and `F06_examples` remain in place; the current F01 login example includes the session csrfToken field; other feature examples retain their existing contents. F04 sample remains linked from its handoff. See [missing F03 sources](../requirements/README.md#f03-v12-source-gap) and the [review register](../requirements/README.md#documentation-review-register). This task does not implement Frontend integration or rerun UI tests.

Current F01 reconciliation (2026-11-12): preserves published dev V20/AuthSessionService and adds V21 binding; [current evidence](../testing/reports/F01_SESSION_RECONCILIATION_2026-11-12.md). Earlier local-variant details are dated history; no source database repair/deployment was performed.

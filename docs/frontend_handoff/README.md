# Frontend Integration Handoff

Use the actual Backend contract together with its feature handoff. Role checks and Store ownership are enforced by the Backend; fixtures are synthetic captures/templates, not current retailer data or credentials.

| Feature | Handoff | Contract | Notes |
|---|---|---|---|
| F01 | [F01](F01_HANDOFF.md) | [API](../api/F01_AUTH_USER_API.md) | Duplicate-registration 202/409 prose needs review |
| F02 | [F02](F02_HANDOFF.md) | [API](../api/F02_PRODUCT_INVENTORY_API.md) | Current catalog-read roles and expiry boundary |
| F03 | [F03](F03_HANDOFF.md) | [API](../api/F03_UTILITY_API.md) | Final spec/example gaps; old Store-window request is compatibility-only |
| F04 | [F04](F04_HANDOFF.md) | [API](../api/F04_TRANSACTION_API.md) | CSV/XLS/XLSX, no stock deduction; approved Excel templates linked from handoff |
| F05 | [F05](F05_HANDOFF.md) | [API](../api/F05_MINING_API.md) | SPMF local runtime; dated blocker/F06 paragraphs need review |
| F06 | [F06](F06_HANDOFF.md) | [API](../api/F06_RECOMMENDATION_API.md) | Candidate review is separate from downstream activation |

Existing `F01_examples`–`F03_examples`, `F05_examples` and `F06_examples` remain in place; no response JSON is altered. F04 sample remains linked from its handoff. See [missing F03 sources](../requirements/README.md#f03-v12-source-gap) and the [review register](../requirements/README.md#documentation-review-register). This task does not implement Frontend integration or rerun UI tests.

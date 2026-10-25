# F02 — Product, Category and Inventory

## Current scope

15 mapped operations; Product metadata writes and Category CRUD now use JPA, catalog/inventory remain JDBC. Current category/product GETs include Merchandiser. Inventory is derived from batches.

- [Canonical API](../../api/F02_PRODUCT_INVENTORY_API.md) · [Frontend handoff](../../frontend_handoff/F02_HANDOFF.md).
- [Architecture](../../architecture/README.md) · [diagrams](../../diagrams/README.md) · [documentation map](../../DOCUMENTATION_MAP.md).

## Requirements and authority

[Original v3 backlog](reference_v3/PASPM_F02_Inventory_Batch_API_Swagger_Backlog_v3.md), [individual issues](reference_v3/F02_GitHub_Issues/README.md), [original ERD](reference_v3/PASPM_Complete_ERD_F02_Inventory_Batch_v3.drawio). Old all-StoreAdmin/expiry-today clauses are superseded, not silently rewritten.

## Workflow and implementation

Follow the endpoint/use-case sequence in the canonical contract and handoff; no API tables are duplicated here. Source availability is separate from tested/accepted status. [Review register](../../requirements/README.md#documentation-review-register) records unresolved authority and packaging gaps.

## Testing and evidence

[Swagger report](reference_v3/F02_SWAGGER_TEST_REPORT.md), [cases](reference_v3/F02_SWAGGER_TEST_CASES.md), [Product integration](../../architecture/PRODUCT_JPA_INTEGRATION_REPORT.md), [Category integration](../../architecture/CATEGORY_JPA_IMPLEMENTATION_REPORT.md). Historical/synthetic evidence only.

## Historical references and known issues

[Original testing prompt](reference_v3/F02_CODEX_SWAGGER_TEST_PROMPT.md) is a superseded operational prompt; old folder instructions are not current execution policy.

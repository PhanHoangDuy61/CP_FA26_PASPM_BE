# F03 — Utility Profiling and Weight Rules

## Current scope

12 mapped operations. Source uses current Product threshold/binary expiry behavior and immutable snapshot routing; legacy linear snapshot data are preserved. Final v1.2 business source is absent from tracked dev.

- [Canonical API](../../api/F03_UTILITY_API.md) · [Frontend handoff](../../frontend_handoff/F03_HANDOFF.md).
- [Architecture](../../architecture/README.md) · [diagrams](../../diagrams/README.md) · [documentation map](../../DOCUMENTATION_MAP.md).

## Requirements and authority

[Original v4 backlog](reference_v4/PASPM_F03_Three_Weights_v4_API_Swagger_Backlog.md), [issues](reference_v4/PASPM_F03_Three_Weights_v4_GitHub_Issues/README.md), [source gap](../../requirements/README.md#f03-v12-source-gap). Current API is an implementation reference, not a substitute approval of the missing source.

## Workflow and implementation

Follow the endpoint/use-case sequence in the canonical contract and handoff; no API tables are duplicated here. Source availability is separate from tested/accepted status. [Review register](../../requirements/README.md#documentation-review-register) records unresolved authority and packaging gaps.

## Testing and evidence

[Original linear report](reference_v4/F03_SWAGGER_TEST_REPORT.md), [Catalog/Utility read verification](../../testing/reports/RETAIL_CATALOG_UTILITY_VERIFICATION.md), [evidence](reference_v4/evidence), [routing/recovery](../../architecture/LEGACY_UTILITY_MATRIX_COMPATIBILITY.md).

## Historical references and known issues

[Original F03→F05 handoff](reference_v4/F03_F05_HANDOFF.md) and [cross-feature integration](../../api/F03_V12_F05_F06_INTEGRATION.md) contain stale downstream claims and need review.

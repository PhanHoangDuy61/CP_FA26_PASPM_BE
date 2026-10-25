# F01 — Authentication and User Management

## Current scope

Authentication/OTP/account/role/avatar source is implemented. User management is SystemAdmin scope; avatar is own-account scope. Registration duplicate-email 202/409 prose needs review.

- [Canonical API](../../api/F01_AUTH_USER_API.md) · [Frontend handoff](../../frontend_handoff/F01_HANDOFF.md).
- [Architecture](../../architecture/README.md) · [diagrams](../../diagrams/README.md) · [documentation map](../../DOCUMENTATION_MAP.md).

## Requirements and authority

[Original decisions](reference/DECISIONS.md), [implementation baseline](reference/IMPLEMENTATION_STATUS.md). The early JDBC-only/F01-only statements are historical; no new architecture or security decision is introduced.

## Workflow and implementation

Follow the endpoint/use-case sequence in the canonical contract and handoff; no API tables are duplicated here. Source availability is separate from tested/accepted status. [Review register](../../requirements/README.md#documentation-review-register) records unresolved authority and packaging gaps.

## Testing and evidence

[Original tests](reference/TEST_RESULTS.md), [Swagger checklist](reference/SWAGGER_CHECKLIST.md), [testing index](../../testing/README.md). These retain their dates and do not certify current dev.

## Historical references and known issues

[README baseline](../../archive/F01/ROOT_README_BASELINE.md) retains original startup/API description.

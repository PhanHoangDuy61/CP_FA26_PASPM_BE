# API Changelog

## 2026-10-18 — F01 authenticated password change

- Added `POST /api/auth/password/change` for an authenticated user to verify their current password and change only their own password.
- Reuses BCrypt policy, row locking and the existing `token_version`; all existing access tokens are revoked and no replacement token is returned.
- Kept the public `PASSWORD_RESET` OTP endpoints separate and unchanged.
- Updated the F01 API contract, RBAC matrix, frontend handoff and README. Runtime OpenAPI export is maintained in `openapi.json`.

## 2026-10-17 — F03 contract and Frontend handoff verification

- Expanded `F03_UTILITY_API.md` with field-level request/response contracts, implemented routes, source-derived role rules, validations, utility/expiry math, snapshot persistence and F04/F05 dependency gaps.
- Expanded the React handoff with screen-to-route mapping, TypeScript types, form/error behavior, setup and mining handoff details; added synthetic JSON examples for implemented endpoints.
- Extended the existing shared RBAC matrix with F03 routes instead of creating a duplicate matrix.
- Compared all 11 F03 controller operations with the existing runtime-generated OpenAPI 3.1.0 export and source DTOs. The export already included all routes, so it was validated and preserved rather than replaced with a new or partial specification.
- Recorded that MockMvc tests exercise F03 runtime routes, while interactive Swagger UI “Try it out” was NOT RUN in this documentation task.
- Recorded implementation gaps: no default F04 validated transaction provider (Preview/Snapshot return 503), no snapshot-history list route, no F05 `MINING_REQUEST` FK/export, and OpenAPI response-nullability/role metadata gaps.

No Backend API behavior, source code, or database migration was changed as part of this documentation verification.

## 2026-10-17 — F03 Utility API contract

Published the implemented F03 contract and runtime OpenAPI export in `docs/api/openapi.json`.

- Added versioned global lambda policy GET/PUT for `SYSTEM_ADMIN`.
- Added Store config GET; Merchandiser weight/lambda PUT; Store Admin integer expiry-window PUT.
- Added per-Product stock/seasonal priority score GET/PATCH for `MERCHANDISER`.
- Added utility preview, immutable snapshot create/read, and paginated snapshot matrix read for `MERCHANDISER`.
- Confirmed Store scope comes from authenticated F01 identity. Cross-Store record access returns 404.
- Confirmed expiry T is a positive JSON integer; quoted numbers and fractions are invalid.
- Confirmed enabled coefficients must sum exactly to 1, and lambda is checked against the current versioned system bounds.
- Snapshot contract freezes analysis date/configuration and reports unavailable production F04 source with 503 until that dependency is implemented.

No endpoint contract changes were made as part of this documentation export; this entry records the implemented F03 API surface for Frontend integration. Existing F01/F02 contracts were not changed by the handoff documents.

## 2026-10-17 — F01/F02 API handoff documentation (documentation only)

- Added source- and runtime-verified F01 auth/user contract and F02 category/product/inventory contract.
- Added role matrix, React handoffs and synthetic JSON examples.
- No Backend API behavior, schema or migration changed in this documentation task.
- Explicitly documented missing logout/refresh/password-change flows, F02 Store Admin-only permissions, lack of CORS configuration, and inventory summary/Product-status behavior.
- OpenAPI remains the full shared runtime export; no endpoints were fabricated.

### Contract metadata and integration gaps

- OpenAPI `info.version`: `0.1.0`; F01/F02 handoff checked against the 2026-10-17 runtime export.
- Missing F01 endpoints: logout, refresh and self-profile editing. Authenticated password change and OTP reset are implemented in source; Google operations are conditional on external Google/SMTP configuration.
- Missing F02 endpoints: deletion and stock adjustment/movement history. No cross-origin CORS mapping is present in current Backend source.
- OpenAPI does not mark nullable `UserView.name`, `UserView.storeId`, `ProductView.category`, or `BatchView.expiryDate`; handoff types document actual null behavior.

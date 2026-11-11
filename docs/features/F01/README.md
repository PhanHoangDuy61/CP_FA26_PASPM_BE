# F01 — Authentication, User Management and Sessions

Current reconciliation: 2026-11-12, based on `dev` `42b8a9c`, preserving its V20 and AuthSessionService. The earlier standalone local session implementation is historical. Current execution status is in the [reconciliation report](../../testing/reports/F01_SESSION_RECONCILIATION_2026-11-12.md); this page does not assert hosted CI or production acceptance.

## 1. Purpose and scope

Email/password and Google login, OTP self-registration/invitations/reset, password change, users/roles/Store assignment, avatars, refresh sessions and server logout. F02–F06 use F01 role/Store identity; Customer login does not grant catalog or recommendation administration.

## 2. Actors and effective RBAC

| Actor | Capability |
|---|---|
| Anonymous | Credential/OTP flows; refresh has no Bearer requirement but requires refresh cookie and server-bound CSRF |
| Any active authenticated account | Own `/me`, password change, Google link, avatar; own-session logout/logout-all additionally require family-bound CSRF |
| SYSTEM_ADMIN | User/role management across Stores |
| STORE_ADMIN / MERCHANDISER | Store-scoped business operations according to each feature's role gate; no user administration |
| END_CUSTOMER | Own authentication/profile session; no F02/F06 access |

[Endpoint RBAC matrix](../../api/F01_F02_RBAC_MATRIX.md). Wrong role is403; Store-scoped foreign/absent records are404 after authorization; invalid Bearer is401.

## 3. Implemented APIs

27 operations: 17 `/api/auth/*`, 9 user operations including avatar, and GET `/api/roles`.

| Session operation | Authentication | Result |
|---|---|---|
| POST `/api/auth/login`, `/google` | Existing credential DTO | Token `{accessToken,expiresIn,tokenType,csrfToken}` + cookies |
| POST `/api/auth/refresh` | Refresh cookie + matching CSRF cookie/header bound to that token | Rotated credentials; absolute expiry retained, idle expiry extended within it |
| POST `/api/auth/logout` | Bearer + CSRF belonging to that active family; no raw refresh cookie required | 200 `Logged out`, revoke own family and clear cookies |
| POST `/api/auth/logout-all` | Same active-session requirement | 200 `Logged out from all sessions`, revoke all own families and bump token_version |

Complete registration/OTP/user DTOs: [API contract](../../api/F01_AUTH_USER_API.md). Session details: [guide](F01_SESSION_MANAGEMENT.md).

## 4. Business and security rules

- ACTIVE, verified identity required; passwords follow existing BCrypt limits/policy.
- Registration returns202 for a new email and409 for an existing email. OTP/invitation/reset rules are unchanged.
- Token and CSRF secrets are hashed in DB; refresh raw value is only in HttpOnly cookie.
- HTTPS defaults to Secure `__Host-` cookies, SameSite=Strict, Path=/, no Domain. Local HTTP uses explicit REFRESH_COOKIE_SECURE=false with loopback hostname.
- Origin and duplicate cookie checks; refresh requires exact stored CSRF binding. Replay revokes family and its session-bound access JWTs. Password/role/status changes still invalidate account versions.
- A valid pre-rotation access JWT can log out with a stored CSRF of its family; rotation alone does not invalidate earlier access JWTs.

## 5. Main workflows

Email request → OTP → registration complete → login → me. Invite → activate → login. Reset remains OTP-based; password change requires current password. Browser session: login → keep access JWT in memory → restore via refresh cookie/CSRF → logout or logout-all. No Frontend implementation changes in reconciliation.

## 6. API documentation

[Contract](../../api/F01_AUTH_USER_API.md) · [handoff](../../frontend_handoff/F01_HANDOFF.md) · [OpenAPI](../../api/openapi.json) · [API changelog](../../api/API_CHANGELOG.md).

## 7. Entities and migrations

Original user/role/Store/verification/identity tables are unchanged. V6 avatars remain owner-only. **Published V20 is byte-identical to dev**: `auth_refresh_session`, `auth_refresh_token`. **V21** adds nullable BINARY(32) csrf_hash; old rows stay intact and unbound old refresh tokens require sign-in again. The alternative local V20 auth_session schema is not merged.

## 8. Dependencies and configuration

F02–F06 role/Store gates and business data are unchanged; internal scheduler execution does not use browser cookies. Local HTTP: REFRESH_COOKIE_SECURE=false and PASPM_SESSION_ALLOWED_ORIGINS=http://localhost:3000,http://127.0.0.1:3000. Access TTL defaults3600 seconds; absolute refresh TTL2592000; idle604800, preserving dev policy. [Setup](../../setup/LOCAL_SETUP_COMMON.md).

## 9. Tests and evidence

AuthIntegrationTest preserves dev's auth cases; SessionHttpIntegrationTest and its MySQL subclass cover rotation, CSRF, Origin, replay, logout, idle expiry and V20-token reauthentication. SessionV21MigrationTest/MySql verify preserved rows/hashes/checksum and idempotent rerun. [Current reconciliation report](../../testing/reports/F01_SESSION_RECONCILIATION_2026-11-12.md). [Earlier variant evidence](../../testing/reports/F01_SESSION_VERIFICATION.md) retains its dates and correction; it is not acceptance of the reconciled schema.

## 10. Known limitations

No shared multi-instance rate limiter. Bounded cleanup uses dev's 30-day replay retention, at most100 eligible families on issue/refresh; no separate background cleanup. CORS is not added. Real Google, production ingress and separate-JVM auth verification require their own evidence. Customer storefront data may be demo.

## 11. Upgrade and decisions

User approved keeping dev's V20/service and integrating additive protections on2026-11-12. Published V20 upgrades by V21; existing refresh cookies may need sign-in again. **A DB using the rejected local V20 must not be started with this build or repaired automatically**; conversion requires a separately verified procedure. Current source database was not modified.

## 12. Diagram backlog

Future diagram work should depict published V20 tables plus V21 binding, login/rotation/revocation and HTTP-vs-scheduler contexts. No Draw.io/ERD was edited in this task. [Diagrams](../../diagrams/README.md).

## 13. Records and history

[Current reconciliation record](../../record-of-changes/2026-11-12/01-f01-dev-reconciliation.md) · [2026-11-11 history](../../record-of-changes/2026-11-11/README.md) · [original F01 tests](reference/TEST_RESULTS.md). Historical JDBC-only/no-refresh text is not current implementation guidance.

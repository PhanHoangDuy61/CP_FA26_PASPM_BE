# F01 — Authentication and User Management

> Last verified Backend: `dev` @ `11973a0` (merge of PR #11; source tree identical to `a4f1115`, whose code passed the full verification script on `45dbd80`). This documentation task ran no new tests. Dated notes below keep their own dates.

## 1. Purpose and scope
Identity for every other feature: email/password login, OTP-verified self-registration, administrator invitations, Google sign-in, password reset/change, role and Store assignment, account status, own-account avatar. Store scope of every Store-bound API comes from the authenticated account.

## 2. Actors and effective RBAC
| Role | F01 capability |
|---|---|
| Anonymous | login, refresh, register, OTP, activate invitation, password reset, Google register/login |
| Any authenticated active account | `GET /api/auth/me`, logout current session/all sessions, change own password, link Google, own avatar |
| `SYSTEM_ADMIN` | list/read/invite/rename accounts, assign role and Store, activate/deactivate, list roles (`/api/users`, `/api/roles`) |
| `STORE_ADMIN`, `MERCHANDISER`, `END_CUSTOMER` | no user-management access (403) |

`SYSTEM_ADMIN` and `END_CUSTOMER` have no Store; `STORE_ADMIN` and `MERCHANDISER` require an existing Store. Full matrix: [RBAC matrix](../../api/F01_F02_RBAC_MATRIX.md).

## 3. Implemented functionality (27 operations)
17 `/api/auth/*`, 9 `/api/users*` (including 3 avatar operations) and `GET /api/roles`. JWT HS256, issuer `paspm`, access TTL 3600 s by default (`TOKEN_TTL_SECONDS`, 60–3600). Refresh credentials use an opaque, rotating HttpOnly cookie; `expiresIn` always describes the access token only.

## 4. Business rules
- Passwords: 12–72 characters with lower, upper and digit; at most 72 UTF-8 bytes (BCrypt).
- Login failures share one 401 message; in-memory limits: 30 per IP and 10 per email per 900 s.
- OTP: default TTL 600 s, resend cooldown 60 s, five wrong attempts invalidate the challenge.
- `POST /api/auth/register`: **202** for a new email, **409 `Email already exists`** for an existing one (this discloses registration status; `AuthIntegrationTest` pins it). Older text saying both cases return the same generic 202 is superseded.
- Changing a password, resetting it or changing status/role bumps the account's token version; older JWTs then fail with 401.
- Each login creates an independent refresh session. Refresh tokens rotate once; reuse/replay revokes that session family. Absolute lifetime defaults to 30 days, idle lifetime to 7 days, and idle expiry never exceeds absolute expiry.
- Login, Google login and refresh return `Cache-Control: no-store`; the refresh token is only in the HttpOnly cookie. Refresh/logout require the current CSRF token in both `__Host-PASPM-CSRF` and `X-CSRF-Token`.
- Logout revokes only the session identified by the current access token; that access JWT remains valid until expiry. Logout-all revokes all refresh sessions and increments `token_version`, invalidating access JWTs immediately.
- An invitation cannot be bypassed by switching status to ACTIVE; the invitee must verify the OTP.

## 5. Main workflows
Self-registration: `register` → OTP email → `otp/verify` → `register/complete` → `login` → `me`. Invitation: System Admin `POST /api/users` → OTP → `auth/activate` → login. Reset: `password-reset/request` → `password-reset/confirm`. Google: `google/register` (creates pending End Customer + PASPM OTP), `google`, `google/link`. Active clients call `refresh` before access expiry; logout is session-scoped unless `logout-all` is used.

## 6. API documentation
[F01 API contract](../../api/F01_AUTH_USER_API.md) · [Frontend handoff](../../frontend_handoff/F01_HANDOFF.md) · [runtime OpenAPI](../../api/openapi.json).

## 7. Entities and migrations
`store`, `role`, `app_user`, `verification_challenge`, `external_identity` (V1); V2 (name allowed after email verification); V6 (profile avatar); V20 (refresh sessions and hashed token rotation history). Terminal refresh sessions are cleaned in batches during login/refresh after 30 days of replay-retention.

## 8. Dependencies
Every other feature reads role and Store from F01. F02 Products/F03/F04/F05/F06 are Store-scoped through it.

## 9. Tests and evidence
`AuthIntegrationTest`, `GoogleRegistrationTest`, the Spring Security rules exercised by every feature's role tests and `F03F05RbacMatrixTest`. Authentication tests include access expiry, cookie/CSRF attributes, rotation/replay, concurrent refresh, idle/absolute expiry, logout scope and F01 revocation flows. Google flows need a real Google client and mail, so successful sign-in credential issuance is verified with test doubles.

## 10. Known limitations
Rate limits are in-memory and per process (a shared gateway is needed for several instances). The Backend has no CORS mapping, so browsers need a same-origin proxy or deployment CORS policy. Registration discloses whether an email exists. A pre-deployment access token without a session claim cannot revoke a refresh session; sign in after deployment to establish one.

## 11. Pending decisions
None recorded for F01. (F05's pending items D1–D10 do not change F01.)

## 12. Related diagrams
[Diagram index](../../diagrams/README.md) and the editable [27-page Draw.io pack](../../diagrams/PASPM_Unified_27_Diagrams_UML2_Editable.drawio) (QA: [UML27 report](../../reports/UML27_DIAGRAM_QA_2026-11-02.md)); relevant pages: Use Case (UC01, UC02), Context (A1 flows), AD01 overall workflow. F01 has no state-machine page.

## 13. Historical references
[Original decisions](reference/DECISIONS.md), [implementation baseline](reference/IMPLEMENTATION_STATUS.md), [Swagger checklist](reference/SWAGGER_CHECKLIST.md); early JDBC-only/F01-only statements are historical. [README baseline](../../archive/F01/ROOT_README_BASELINE.md).

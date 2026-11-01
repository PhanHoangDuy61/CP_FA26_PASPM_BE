# F01 — Authentication and User Management

> Last verified Backend: `dev` @ `11973a0` (merge of PR #11; source tree identical to `a4f1115`, whose code passed the full verification script on `45dbd80`). This documentation task ran no new tests. Dated notes below keep their own dates.

## 1. Purpose and scope
Identity for every other feature: email/password login, OTP-verified self-registration, administrator invitations, Google sign-in, password reset/change, role and Store assignment, account status, own-account avatar. Store scope of every Store-bound API comes from the authenticated account.

## 2. Actors and effective RBAC
| Role | F01 capability |
|---|---|
| Anonymous | login, register, OTP, activate invitation, password reset, Google register/login |
| Any authenticated active account | `GET /api/auth/me`, change own password, link Google, own avatar |
| `SYSTEM_ADMIN` | list/read/invite/rename accounts, assign role and Store, activate/deactivate, list roles (`/api/users`, `/api/roles`) |
| `STORE_ADMIN`, `MERCHANDISER`, `END_CUSTOMER` | no user-management access (403) |

`SYSTEM_ADMIN` and `END_CUSTOMER` have no Store; `STORE_ADMIN` and `MERCHANDISER` require an existing Store. Full matrix: [RBAC matrix](../../api/F01_F02_RBAC_MATRIX.md).

## 3. Implemented functionality (24 operations)
14 `/api/auth/*`, 9 `/api/users*` (including 3 avatar operations) and `GET /api/roles`. JWT HS256, issuer `paspm`, TTL 900 s by default (`TOKEN_TTL_SECONDS`, 60–3600), no refresh token.

## 4. Business rules
- Passwords: 12–72 characters with lower, upper and digit; at most 72 UTF-8 bytes (BCrypt).
- Login failures share one 401 message; in-memory limits: 30 per IP and 10 per email per 900 s.
- OTP: default TTL 600 s, resend cooldown 60 s, five wrong attempts invalidate the challenge.
- `POST /api/auth/register`: **202** for a new email, **409 `Email already exists`** for an existing one (this discloses registration status; `AuthIntegrationTest` pins it). Older text saying both cases return the same generic 202 is superseded.
- Changing a password, resetting it or changing status/role bumps the account's token version; older JWTs then fail with 401.
- An invitation cannot be bypassed by switching status to ACTIVE; the invitee must verify the OTP.

## 5. Main workflows
Self-registration: `register` → OTP email → `otp/verify` → `register/complete` → `login` → `me`. Invitation: System Admin `POST /api/users` → OTP → `auth/activate` → login. Reset: `password-reset/request` → `password-reset/confirm`. Google: `google/register` (creates pending End Customer + PASPM OTP), `google`, `google/link`.

## 6. API documentation
[F01 API contract](../../api/F01_AUTH_USER_API.md) · [Frontend handoff](../../frontend_handoff/F01_HANDOFF.md) · [runtime OpenAPI](../../api/openapi.json).

## 7. Entities and migrations
`store`, `role`, `app_user`, `verification_challenge`, `external_identity` (V1); V2 (name allowed after email verification); V6 (profile avatar). Avatars are files under `PROFILE_IMAGE_DIRECTORY`, owner-only.

## 8. Dependencies
Every other feature reads role and Store from F01. F02 Products/F03/F04/F05/F06 are Store-scoped through it.

## 9. Tests and evidence
`AuthIntegrationTest`, `GoogleRegistrationTest`, the Spring Security rules exercised by every feature's role tests and `F03F05RbacMatrixTest` (anonymous 401 on 26 routes). Historical reports: [reference](reference/TEST_RESULTS.md). Google flows need a real Google client and mail, so they are verified only with test doubles.

## 10. Known limitations
No logout, token refresh or general profile editing. Rate limits are in-memory and per process (a shared gateway is needed for several instances). The Backend has no CORS mapping, so browsers need a same-origin proxy or deployment CORS policy. Registration discloses whether an email exists.

## 11. Pending decisions
None recorded for F01. (F05's pending items D1–D10 do not change F01.)

## 12. Related diagrams
`docs/diagrams/README.md` indexes the 27-page Draw.io pack: Use Case (UC01, UC02), Context (A1 flows), AD01 overall workflow. F01 has no state-machine page.

## 13. Historical references
[Original decisions](reference/DECISIONS.md), [implementation baseline](reference/IMPLEMENTATION_STATUS.md), [Swagger checklist](reference/SWAGGER_CHECKLIST.md); early JDBC-only/F01-only statements are historical. [README baseline](../../archive/F01/ROOT_README_BASELINE.md).

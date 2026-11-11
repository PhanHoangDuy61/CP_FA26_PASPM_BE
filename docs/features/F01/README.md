# F01 — Authentication and User Management

> Feature branch baseline: implementation `9d33d88`, based on old dev `5b30eb1`; published separately on 2026-11-11. Updated origin/dev `42b8a9c` has a different V20; reconciliation is pending. Earlier merged/test baselines retain their dates in linked reports. This documentation-only follow-up reuses existing test evidence; it does not rerun application tests.

## 1. Purpose and scope
Identity for every other feature: email/password login, OTP-verified self-registration, administrator invitations, Google sign-in, password reset/change, role and Store assignment, account status, own-account avatar. Store scope of every Store-bound API comes from the authenticated account.

## 2. Actors and effective RBAC
| Role | F01 capability |
|---|---|
| Anonymous | login, register, OTP, activate invitation, password reset, Google register/login; refresh requires its cookie/CSRF credentials even without Bearer |
| Any authenticated active account | `GET /api/auth/me`, change own password, link Google, own avatar; logout/logout-all additionally require matching session cookie and CSRF |
| `SYSTEM_ADMIN` | list/read/invite/rename accounts, assign role and Store, activate/deactivate, list roles (`/api/users`, `/api/roles`) |
| `STORE_ADMIN`, `MERCHANDISER`, `END_CUSTOMER` | no user-management access (403) |

`SYSTEM_ADMIN` and `END_CUSTOMER` have no Store; `STORE_ADMIN` and `MERCHANDISER` require an existing Store. Full matrix: [RBAC matrix](../../api/F01_F02_RBAC_MATRIX.md).

## 3. Implemented functionality (27 operations including the local session update)
17 `/api/auth/*`, 9 `/api/users*` (including 3 avatar operations) and `GET /api/roles`. JWT HS256, issuer `paspm`, TTL 900 s by default (`TOKEN_TTL_SECONDS`, 60–3600), plus persisted refresh-cookie rotation, explicit CSRF/Origin checks and server logout in the 2026-11-11 working-tree update.

## 4. Business rules
- Passwords: 12–72 characters with lower, upper and digit; at most 72 UTF-8 bytes (BCrypt).
- Login failures share one 401 message; in-memory limits: 30 per IP and 10 per email per 900 s.
- OTP: default TTL 600 s, resend cooldown 60 s, five wrong attempts invalidate the challenge.
- `POST /api/auth/register`: **202** for a new email, **409 `Email already exists`** for an existing one (this discloses registration status; `AuthIntegrationTest` pins it). Older text saying both cases return the same generic 202 is superseded.
- Changing a password, resetting it or changing status/role bumps the account's token version; older JWTs then fail with 401.
- An invitation cannot be bypassed by switching status to ACTIVE; the invitee must verify the OTP.

## 5. Main workflows
Self-registration: `register` → OTP email → `otp/verify` → `register/complete` → `login` → `me`. Invitation: System Admin `POST /api/users` → OTP → `auth/activate` → login. Reset: `password-reset/request` → `password-reset/confirm`. Google: `google/register` (creates pending End Customer + PASPM OTP), `google`, `google/link`.

Session lifecycle: login → keep access token in memory → restore/rotate through `/api/auth/refresh` with cookies and CSRF → `/api/auth/logout` or `/logout-all`. The [session guide](F01_SESSION_MANAGEMENT.md) describes family revocation, legacy JWT compatibility, configuration and cross-feature impact.

## 6. API documentation
[F01 API contract](../../api/F01_AUTH_USER_API.md) · [Frontend handoff](../../frontend_handoff/F01_HANDOFF.md) · [runtime OpenAPI](../../api/openapi.json).

## 7. Entities and migrations
`store`, `role`, `app_user`, `verification_challenge`, `external_identity` (V1); V2 (name allowed after email verification); V6 (profile avatar). Avatars are files under `PROFILE_IMAGE_DIRECTORY`, owner-only. V20 adds `auth_session` and hashed `auth_refresh_token`; no existing migration is edited.

## 8. Dependencies
Every other feature reads role and Store from F01. F02 Products/F03/F04/F05/F06 are Store-scoped through it.

## 9. Tests and evidence
`AuthIntegrationTest`, `GoogleRegistrationTest`, the Spring Security rules exercised by every feature's role tests and `F03F05RbacMatrixTest` (anonymous 401 on 26 routes). Historical reports: [reference](reference/TEST_RESULTS.md). Google flows need a real Google client and mail, so they are verified only with test doubles.

Current implementation tests: `SessionHttpIntegrationTest` (11), `SessionMySqlIntegrationTest` (11 opt-in), `SessionHttpSupportTest` (3). Fresh publication Maven verify: 387 PASS / 190 SKIPPED; separate Docker group: 27 PASS, no skips. These are previous execution results, not a new documentation-run result.

Current session evidence: [F01 session verification](../../testing/reports/F01_SESSION_VERIFICATION.md), including real Customer browser login, reload/refresh and logout.

## 10. Known limitations
General profile editing remains outside this update. Session record retention cleanup is not implemented; the configurable seven-day lifetime is an engineering default. Rate limits are in-memory and per process (a shared gateway is needed for several instances). The Backend has no CORS mapping, so browsers need a same-origin proxy or deployment CORS policy. Registration discloses whether an email exists.

## 11. Pending decisions
Session lifetime/record-retention and shared replica throttling need deployment policy review; the defaults are documented in the current contract. This does not change F05 policies.

## 12. Related diagrams
[Diagram index](../../diagrams/README.md) and the editable [27-page Draw.io pack](../../diagrams/PASPM_Unified_27_Diagrams_UML2_Editable.drawio) (QA: [UML27 report](../../reports/UML27_DIAGRAM_QA_2026-11-02.md)); relevant pages: Use Case (UC01, UC02), Context (A1 flows), AD01 overall workflow. F01 has no state-machine page.

Session schema/lifecycle diagrams may need a later V20 update; no diagram was edited or certified in this documentation task. Agent records: [implementation](../../record-of-changes/2026-11-11/01-f01-session-implementation.md), [docs sync](../../record-of-changes/2026-11-11/02-f01-documentation-sync.md).

## 13. Historical references
[Original decisions](reference/DECISIONS.md), [implementation baseline](reference/IMPLEMENTATION_STATUS.md), [Swagger checklist](reference/SWAGGER_CHECKLIST.md); early JDBC-only/F01-only statements are historical. [README baseline](../../archive/F01/ROOT_README_BASELINE.md).

Publication note: this feature branch is separate from origin/dev `42b8a9c`, which contains a different refresh/V20 implementation. [Merge reconciliation blocker](../../record-of-changes/2026-11-11/04-feature-publication-and-merge-blocker.md). Earlier 388-pass totals included one stale report; use the fresh publication counts linked from the verification report.

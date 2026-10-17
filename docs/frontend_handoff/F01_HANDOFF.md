# F01 Frontend Handoff

For React + TypeScript. Contract and synthetic JSON files: [`../api/F01_AUTH_USER_API.md`](../api/F01_AUTH_USER_API.md), [`F01_examples/`](F01_examples/), and shared [`../api/openapi.json`](../api/openapi.json).

## Base URL and development setup

Backend default is `http://localhost:8080` (`PORT` overrides it). Use `VITE_PASPM_API_BASE_URL` in the Frontend's local environment, without a trailing slash, e.g. `http://localhost:8080`. Swagger UI is `/swagger`; OpenAPI is `/v3/api-docs`. QA has used port8799; that is not the default. Backend environment names used by deployment include `PORT`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `OTP_SECRET`, `TOKEN_TTL_SECONDS`, `OTP_TTL_SECONDS`, `OTP_COOLDOWN_SECONDS`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_TLS`, `MAIL_FROM`, and optional `GOOGLE_CLIENT_ID`. Configure these in the Backend environment/secrets manager; never expose them to React. For the React app, only the API origin belongs in `VITE_PASPM_API_BASE_URL`.

Send `Authorization: Bearer <accessToken>` for protected calls. F01 returns only a short-lived JWT (`expiresIn` seconds; default900), no refresh token. The current source has no logout endpoint: client logout means clear its token/session state. On 401, clear the stale token and send the user through login; role/status changes also invalidate previously issued tokens. Use HTTPS in deployment and keep JWT/OTP/database/mail secrets out of `VITE_*` and browser bundles.

Backend source has no CORS mapping or allowed-origin configuration. A browser served from a different origin may fail preflight; for local work use a same-origin dev proxy or arrange allowed origins in deployment infrastructure before browser integration. Do not assume CORS is currently configured by this API.

## Screen-to-API mapping

| Screen | Implemented API and notes |
|---|---|
| Login | `POST /api/auth/login`; active, verified account required |
| Register | `POST /api/auth/register` with email only; generic 202 response |
| Email OTP verification | `POST /api/auth/otp/verify` for SELF_REGISTER; then `POST /api/auth/register/complete` for name/password |
| Invite activation | `POST /api/auth/activate` for ADMIN_INVITE OTP + new password |
| Google login/register | `POST /api/auth/google`, `/google/register`; requires Google client configured. Google registration still uses PASPM OTP |
| Link Google identity | `POST /api/auth/google/link`; authenticated; never auto-links by matching email |
| User profile | `GET /api/auth/me`; no profile edit or password change API |
| User management | System Admin only: `GET /api/users`, `/api/users/{id}`, `GET /api/roles`, `POST /api/users`, `PUT /api/users/{id}`, `PATCH /api/users/{id}/status`, `PUT /api/users/{id}/role` |
| Create User | Admin invitation; user remains pending until `/api/auth/activate` succeeds |
| Edit User | Only name can be changed through current PUT; no email/password edit |
| Account activation | `/api/auth/activate` invitation flow or successful self-registration completion; no manual OTP bypass |
| Logout / refresh / reset password | Not implemented; mark screens/workflows unsupported |

## Real authentication sequences

Self-registration: email request → OTP email → verify → complete with password/name → login → load `/me`. Public registration has no role/store fields and creates only `END_CUSTOMER`. A duplicate email receives the same generic 202 response to avoid account enumeration.

Staff: System Admin creates an invitation with role and optional Store assignment → recipient receives `ADMIN_INVITE` OTP → recipient sets password and activates → login. For Store Admin/Merchandiser the System Admin must provide an existing `storeId`; global roles and End Customer must have null/no Store.

Google: obtain a Google ID token from Google Identity Services; send it to the appropriate endpoint. The backend verifies Google signature, issuer, configured audience, expiry and verified email. Google account registration creates a pending End Customer which still requires PASPM OTP. For account linking, require an existing PASPM login and explicitly call `/google/link`.

No API returns OTP. Default OTP TTL is 10 minutes; resend cooldown defaults60 seconds; a challenge stops accepting attempts after five incorrect codes. Login/OTP throttles are in-memory, so UI should handle `429` with a wait message. OTP request returns a generic response even when email is not eligible; do not promise an account exists.

## Role-based UI

`SYSTEM_ADMIN` sees global user/role management and may assign roles and Stores. `STORE_ADMIN`, `MERCHANDISER`, and `END_CUSTOMER` may call `/api/auth/me`; no self-service role selection exists. Store Admin and Merchandiser are Store-scoped identities; End Customer and System Admin have no Store. F01 has no customer storefront/catalog endpoint. UI navigation may reflect roles, but Backend authorization is mandatory and returns 403 otherwise.

## TypeScript types

```ts
export type RoleCode = 'SYSTEM_ADMIN' | 'STORE_ADMIN' | 'MERCHANDISER' | 'END_CUSTOMER';
export type AccountStatus = 'PENDING_VERIFICATION' | 'ACTIVE' | 'INACTIVE';
export interface LoginRequest { email: string; password: string }
export interface TokenResponse { accessToken: string; expiresIn: number; tokenType: 'Bearer' | string }
export interface UserView { userId: number; email: string; name: string | null; roleCode: RoleCode | string; storeId: number | null; status: AccountStatus | string; emailVerified: boolean }
export interface EmailRequest { email: string }
export interface RegistrationRequest { name: string; email: string; password: string }
export interface OtpRequest { email: string; purpose: 'SELF_REGISTER' | 'ADMIN_INVITE' }
export interface VerifyRequest { email: string; otp: string }
export interface ActivateRequest extends VerifyRequest { password: string }
export interface CreateUserRequest { name: string; email: string; roleCode: RoleCode | string; storeId?: number | null }
export interface UpdateUserRequest { name: string }
export interface UserStatusRequest { status: 'ACTIVE' | 'INACTIVE' }
export interface RoleDto { code: RoleCode | string; display_name: string }
export type UsersListResponse = UserView[];
export interface MessageResponse { message: string }
export interface GoogleRequest { idToken: string }
export interface RoleAssignmentRequest { roleCode: RoleCode | string; storeId?: number | null }
export interface ApiError { error: string }
```

`GET /api/users` and `GET /api/roles` return arrays, not `{data, pagination}` wrappers. User search pagination has no total count. Use OpenAPI-generated types for schema fields and preserve nullable `name`/`storeId` for pending/global users. The current OpenAPI schema does not mark these Java nullable fields as nullable; the source/data lifecycle does, as documented in the API contract.

## Loading and errors

Show pending while requests run; registration may show “check your email” after generic 202. Map 400 to field/form validation, 401 to login/session reset, 403 to access denied, 404 to missing account, 409 to duplicate/identity conflict, 429 to a wait/retry-after style message (server does not return Retry-After contract), 503 to email/Google configuration unavailable, and network/5xx to retryable server error. Handled JSON errors have `{error:string}`; a Spring Security 401 may have no JSON body.

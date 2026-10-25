<!-- docs-reorganization:notice -->
> **NEEDS_REVIEW — documentation review 2026-10-26.** Source AuthController returns409 for duplicate registration while generic202 prose below conflicts; this task changes no authentication behavior. See [current navigation](../features/F01/README.md). Original results and business-rule text below are retained; this notice is not a new execution or approval.
<!-- /docs-reorganization:notice -->

# F01 Authentication and User API Contract

Contract checked against the running application OpenAPI export and current source on 2026-10-17. In endpoint table, PASS means the operation and DTO match the runtime OpenAPI and source; it does not mean a separate Swagger UI request was executed for every operation in this documentation task. Full shared OpenAPI is [`openapi.json`](openapi.json). Every protected request uses `Authorization: Bearer <accessToken>`. Public registration never accepts a role or Store. Examples are synthetic; OTP and Google token examples are placeholders and cannot authenticate.

## Models

- `Token`: `{accessToken:string, expiresIn:integer seconds, tokenType:string}`. JWT issuer is `paspm`, token type is `Bearer`; expiry is configured by `TOKEN_TTL_SECONDS` (default 900). No refresh token is returned.
- `UserView`: `{userId:int32,email:string,name:string|null,roleCode:string,storeId:int32|null,status:string,emailVerified:boolean,avatarUrl:string|null}`. `storeId` is null for global roles and End Customer; `avatarUrl` is null before upload and otherwise points to the authenticated owner-only image endpoint.
- User status values: `PENDING_VERIFICATION`, `ACTIVE`, `INACTIVE`. Role values from migration: `SYSTEM_ADMIN`, `STORE_ADMIN`, `MERCHANDISER`, `END_CUSTOMER`.
- Handled errors use `{ "error": string }`. Validation/deserialization errors use `{"error":"Invalid request fields"}`. Framework-generated authentication errors may have no JSON body.

## Authentication and registration endpoints

| Method and path | Authentication / role | Request → response | Success / notable errors | Verification |
|---|---|---|---|---|
| `POST /api/auth/login` | Public | `Login` → `Token` | 200; 400 invalid fields/password >72 UTF-8 bytes; 401 invalid credentials or unavailable account; 429 limiter | PASS — source + runtime OpenAPI; AuthIntegrationTest covers login |
| `GET /api/auth/me` | Any valid active bearer | none → `UserView` | 200; 401 missing/invalid/expired or revoked token | PASS — source + runtime OpenAPI |
| `POST /api/auth/register` | Public | `EmailRequest` → `{message:string}` | 202; 400 invalid email; 429 registration limiter. Existing email gets same generic response | PASS — source + runtime OpenAPI; existing AuthIntegrationTest exercises self-registration flow |
| `POST /api/auth/register/complete` | Public, but only eligible verified self-registration | `Registration` → `{message:string}` | 200; 400 unless OTP verification happened first, or password invalid | PASS — source + runtime OpenAPI |
| `POST /api/auth/otp/request` | Public | `OtpRequest` → `{message:string}` | 202; 400 invalid purpose/email; 429 rate/cooldown; 503 mail unavailable. Unknown/not-eligible account returns generic 202 | PASS — source + runtime OpenAPI |
| `POST /api/auth/otp/resend` | Public | Same `OtpRequest` → same response | 202; 400 invalid fields; 429 cooldown/rate; 503 mail unavailable | PASS — source + runtime OpenAPI; alias uses same controller method as request |
| `POST /api/auth/otp/verify` | Public | `Verify` → `{message:string}` | 200; 400 invalid, expired, consumed, wrong-purpose OTP or five failed attempts | PASS — source + runtime OpenAPI |
| `POST /api/auth/activate` | Public invitation completion | `Activate` → `{message:string}` | 200; 400 invalid/expired OTP or invalid password; 429 verification limiter | PASS — source + runtime OpenAPI |
| `POST /api/auth/password/change` | Any valid active bearer; changes only its own account | `ChangePassword` → `{message:string}` | 200; 400 invalid fields, unavailable password credential or same password; 401 missing/invalid token or incorrect current password | Implemented — locks user row and increments token version; login again |
| `POST /api/auth/password-reset/request` | Public | `EmailRequest` → `{message:string}` | 202 with identical response for unknown/ineligible/existing accounts; 429 rate limit | Implemented — `PASSWORD_RESET` challenge, existing cooldown and request limits |
| `POST /api/auth/password-reset/confirm` | Public | `PasswordReset` → `{message:string}` | 200; 400 invalid/expired/consumed OTP, invalid password or five attempts | Implemented — consumes challenge and revokes prior JWTs |
| `POST /api/auth/google/register` | Public | `Google` → `{message:string}` | 202; 401 invalid Google ID token; 429 limiter; 503 Google config/mail unavailable. Creates pending `END_CUSTOMER` and requests PASPM OTP | PASS — source + runtime OpenAPI; GoogleRegistrationTest exists |
| `POST /api/auth/google` | Public | `Google` → `Token` | 200; 401 invalid/unlinked Google identity or unavailable account; 429; 503 if Google client is not configured | PASS — source + runtime OpenAPI |
| `POST /api/auth/google/link` | Any valid active bearer | `Google` → `{message:string}` | 200; 401 invalid Google token; 409 identity already belongs to another account; 503 if Google not configured | PASS — source + runtime OpenAPI |

`Login`: required `email` (valid email), `password` (nonblank, max 72 characters; service also checks BCrypt's 72-byte limit). Invalid password, unknown user, unverified/pending or inactive account intentionally share `401 Invalid credentials or account unavailable`.

`EmailRequest`: required email, max 254. `Registration`: required name (max 150), email (max 254), password (12–72 characters and at least one lowercase, uppercase, and digit; password is also limited to 72 UTF-8 bytes). `OtpRequest`: required email and `purpose` equal to `SELF_REGISTER` or `ADMIN_INVITE`. `Verify`: email plus `otp` exactly six digits. `Activate`: email, six-digit OTP, and password with the same 12–72 and complexity rules. `Google`: required `idToken` string max 8192; backend verifies signature, issuer, configured audience, expiry and Google `email_verified`.

Self-registration sequence: `POST register` with email → email OTP → `POST otp/verify` (purpose is fixed to `SELF_REGISTER`) → `POST register/complete` with name/email/password → login → `GET me`. The first request creates a pending End Customer with no password/name. Completion does not issue a token. Registration email duplication is deliberately not disclosed.

Admin invite sequence: System Admin creates a pending account with `POST /api/users`; `ADMIN_INVITE` OTP is sent → invitee calls `POST /api/auth/activate` with email/OTP/new password → login. Activation requires OTP; changing a status to ACTIVE cannot bypass it.

OTP rules: configured TTL defaults to 600 seconds (10 minutes), accepted config range 60–1800 seconds; resend cooldown defaults to 60 seconds and configured value must be at least 30 seconds. A challenge permits at most five incorrect attempts. Rate limits are in-memory, single-process: login 30/IP and 10/normalized email per 900s; registration 10/IP per 3600s; OTP request 20/IP and 5/email per 3600s; verification 30/IP per 900s. Limits reset on process restart and are not shared across replicas. Do not build a workflow that depends on knowing whether an email exists.

Password reset only sends to ACTIVE, verified accounts that already have a password credential. A different purpose replaces an older per-user OTP challenge and has a separate cooldown; request response remains generic during cooldown and on SMTP delivery failure. The replacement JWT uses the existing DB-backed `token_version`; previous access tokens fail validation on the next request.

`PasswordReset`: required email, six-digit OTP and password with the registration rules (12–72 characters, at least one lowercase, uppercase and digit, maximum 72 UTF-8 bytes). Never log or return the OTP/password. The in-memory rate limiter remains single-process and needs a shared gateway/Redis policy for replicas.

`ChangePassword`: required `currentPassword` (max 72 characters) and `newPassword` (12–72 characters, at least one lowercase, uppercase and digit; maximum 72 UTF-8 bytes). After locking the JWT owner's account row, the service checks the token version again, verifies the current password with BCrypt and rejects a new password matching the current hash. A missing password credential (for example, a Google-only account) receives 400 and must use an account flow that establishes a password first. Incorrect current password receives 401. On success the password is BCrypt-encoded and `token_version` increments in the same transaction. No replacement token is issued; sign in again. This route is distinct from public OTP reset and doesn't accept an account identifier.

## User administration endpoints

All `/api/users` and `/api/roles` operations require `SYSTEM_ADMIN` and are global, not Store filtered. System Admin can list/read accounts across Stores, invite users, edit names, assign roles/Stores, and change status.

| Method and path | Request / response | Success / errors | Verification |
|---|---|---|---|
| `GET /api/users?q=&role=&status=&page=0&size=20` | returns raw `UserView[]`; q searches email/name; no pagination envelope/total count | 200, 400 invalid page/size, 401, 403  | PASS — source + runtime OpenAPI |
| `GET /api/users/{id}` | returns `UserView` | 200, 404, 401, 403  | PASS — source + runtime OpenAPI |
| `POST /api/users` | `CreateUser` → `UserView` | 201; duplicate email 409; invalid role/Store scope 400; invitation mail may return 503  | PASS — source + runtime OpenAPI |
| `PUT /api/users/{id}` | `UpdateUser` → `UserView`; only name changes | 200, 400, 404, 401, 403  | PASS — source + runtime OpenAPI |
| `PATCH /api/users/{id}/status` | `Status` → `UserView` | 200; invalid status/self-disable/unverified activation 400; 404, 401, 403  | PASS — source + runtime OpenAPI |
| `PUT /api/users/{id}/role` | `RoleAssignment` → `UserView` | 200; invalid role/store scope or self-removal of admin access 400; 404, 401, 403  | PASS — source + runtime OpenAPI |
| `PUT /api/users/me/avatar` | authenticated owner, multipart `file` → `UserView` | 200; 400 invalid/non-image; 413 over 5 MiB; 401; 503 storage unavailable | Implemented — owner only |
| `DELETE /api/users/me/avatar` | authenticated owner → `UserView` with `avatarUrl:null` | 200; 401 | Implemented — owner only |
| `GET /api/users/{id}/avatar` | authenticated owner only → JPEG/PNG bytes | 200; 403 for another user's avatar; 404 if absent; 401; 503 storage unavailable | Implemented — owner only |
| `GET /api/roles` | returns raw array of `{code:string,display_name:string}` | 200, 401, 403  | PASS — source + runtime OpenAPI |

`CreateUser`: required name (max150), email (valid, max254), roleCode; optional nullable `storeId:int32`. `RoleAssignment`: required roleCode and optional nullable storeId. `STORE_ADMIN` and `MERCHANDISER` require an existing Store.

Avatar upload validates actual JPEG/PNG content using image readers; client filename and MIME type are ignored. Maximum upload size is 5 MiB, each image dimension is at most 4096 pixels and decoded image area at most 12 megapixels. Storage generates UUID-based `.jpg`/`.png` object keys and stores only the key in `app_user.avatar_key`. `PROFILE_IMAGE_DIRECTORY` selects a persistent local directory; default is `${user.home}/.paspm/profile-images`. Configure a private durable mount for one instance, or register a shared `AvatarObjectStorage` adapter as a Spring `@Primary` bean for replicas/object storage. Files are written before DB update and removed on transaction rollback; replaced/deleted objects are removed after DB commit. Failed cleanup is logged and may leave an unreachable orphan for operator cleanup.

`SYSTEM_ADMIN` and `END_CUSTOMER` must have no Store. Unknown role is 400. Thus an authenticated System Admin can assign privileged roles; the public register API cannot. `UpdateUser` requires nonblank name max150. `Status.status` is `ACTIVE` or `INACTIVE`. Status/role changes increment token version and invalidate prior JWTs; a System Admin cannot deactivate their own account or remove their own System Admin access.

Search parameters: `q`, `role`, `status` default empty; `page` defaults 0, range 0..100000; `size` defaults 20, range 1..100. The API returns only the current page as an array, with no total count.

## Examples

Examples are in [`../frontend_handoff/F01_examples/`](../frontend_handoff/F01_examples). Representative success response:

```json
{"accessToken":"<synthetic JWT omitted>","expiresIn":900,"tokenType":"Bearer"}
```

Representative user response:

```json
{"userId":42,"email":"planner@example.test","name":"Planner Example","roleCode":"MERCHANDISER","storeId":7,"status":"ACTIVE","emailVerified":true}
```

Representative error:

```json
{"error":"Invalid credentials or account unavailable"}
```

Change-password request (authenticated with the caller's bearer JWT):

```json
{"currentPassword":"CurrentPassword123!","newPassword":"NewPassword456!"}
```

Success response (the bearer token used for the request is now revoked):

```json
{"message":"Password changed"}
```

## Authorization and verification notes

JWT authentication maps the current database role to `ROLE_<role>` and checks account active status and token version. F01 admin endpoints require System Admin; `GET /me` requires authentication but has no role restriction. Store association is nullable for global/customer accounts and required for Store Admin/Merchandiser on creation/assignment. F01 user administration intentionally has no Store isolation because it is a global System Admin feature.

Verification status means contract/source mapping only unless a specific existing test covers behavior. The OpenAPI export is runtime-generated and includes all routes, but Springdoc does not emit `@PreAuthorize` role declarations. See the shared RBAC matrix and handoff for source-derived roles. No credentials, actual OTPs, tokens, or Google credentials are included in examples. OpenAPI currently does not mark nullable Java response values: `UserView.name` is null for a pending self-registration and `storeId` is null for global/customer accounts, although the generated schema only says `string` / `integer`. Treat those two fields as nullable in TypeScript.

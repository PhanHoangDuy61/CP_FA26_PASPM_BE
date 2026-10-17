# F01 / F02 RBAC Matrix

Derived from controller `@PreAuthorize`, `SecurityConfig`, `StoreScope`, and F01/F02 service/repository checks (2026-10-17). “Public” means the Spring security route is explicitly permitted; “authenticated” requires a valid active bearer token. An authenticated-but-wrong role is 403. F02 foreign or absent resources are hidden as 404 after the controller role gate.

| Feature | Endpoint | System Admin | Store Admin | Merchandiser | End Customer |
|---|---|---|---|---|---|
| F01 Login | POST `/api/auth/login` | Public | Public | Public | Public |
| F01 Self-registration OTP request | POST `/api/auth/register` | Public | Public | Public | Public |
| F01 Complete self-registration | POST `/api/auth/register/complete` | Public (eligible pending account only) | same | same | same |
| F01 OTP request | POST `/api/auth/otp/request` | Public (eligible pending account) | same | same | same |
| F01 OTP resend | POST `/api/auth/otp/resend` | Public (eligible pending account) | same | same | same |
| F01 Verify self-registration OTP | POST `/api/auth/otp/verify` | Public | Public | Public | Public |
| F01 Activate invited account | POST `/api/auth/activate` | Public (valid invitation OTP) | same | same | same |
| F01 Google registration | POST `/api/auth/google/register` | Public, Google config required | same | same | same |
| F01 Google login | POST `/api/auth/google` | Public, linked identity/config required | same | same | same |
| F01 Link Google identity | POST `/api/auth/google/link` | Authenticated own account | Authenticated own account | Authenticated own account | Authenticated own account |
| F01 Current user | GET `/api/auth/me` | Authenticated own account | same | same | same |
| F01 User list | GET `/api/users` | Allowed, global | Forbidden | Forbidden | Forbidden |
| F01 User detail | GET `/api/users/{id}` | Allowed, global | Forbidden | Forbidden | Forbidden |
| F01 Invite/create account | POST `/api/users` | Allowed; role/Store constraints apply | Forbidden | Forbidden | Forbidden |
| F01 Update account name | PUT `/api/users/{id}` | Allowed, global | Forbidden | Forbidden | Forbidden |
| F01 Activate/deactivate | PATCH `/api/users/{id}/status` | Allowed, global; OTP cannot be bypassed | Forbidden | Forbidden | Forbidden |
| F01 Assign role/Store | PUT `/api/users/{id}/role` | Allowed; valid role/store scope | Forbidden | Forbidden | Forbidden |
| F01 List roles | GET `/api/roles` | Allowed | Forbidden | Forbidden | Forbidden |
| F02 List categories | GET `/api/categories` | Forbidden by controller role | Allowed, own Store | Forbidden | Forbidden |
| F02 Category detail | GET `/api/categories/{id}` | Forbidden | Allowed, own Store; foreign→404 | Forbidden | Forbidden |
| F02 Create category | POST `/api/categories` | Forbidden | Allowed, own Store | Forbidden | Forbidden |
| F02 Update category | PUT `/api/categories/{id}` | Forbidden | Allowed, own Store; foreign→404 | Forbidden | Forbidden |
| F02 Category status | PATCH `/api/categories/{id}/status` | Forbidden | Allowed, own Store; foreign→404 | Forbidden | Forbidden |
| F02 List/search products | GET `/api/products` | Forbidden | Allowed, own Store only | Forbidden | Forbidden |
| F02 Product detail | GET `/api/products/{id}` | Forbidden | Allowed, own Store; foreign→404 | Forbidden | Forbidden |
| F02 Create product | POST `/api/products` | Forbidden | Allowed, Store inferred from token | Forbidden | Forbidden |
| F02 Update product | PUT `/api/products/{id}` | Forbidden | Allowed, own Store; foreign→404 | Forbidden | Forbidden |
| F02 Product status | PATCH `/api/products/{id}/status` | Forbidden | Allowed, own Store; foreign→404 | Forbidden | Forbidden |
| F02 List batches | GET `/api/products/{id}/batches` | Forbidden | Allowed, own Store; foreign→404 | Forbidden | Forbidden |
| F02 Create batch | POST `/api/products/{id}/batches` | Forbidden | Allowed, own Store product | Forbidden | Forbidden |
| F02 Batch detail | GET `/api/inventory-batches/{id}` | Forbidden | Allowed, own Store; foreign→404 | Forbidden | Forbidden |
| F02 Update batch | PATCH `/api/inventory-batches/{id}` | Forbidden | Allowed, own Store; foreign→404 | Forbidden | Forbidden |
| F02 Inventory summary | GET `/api/products/{id}/inventory-summary` | Forbidden | Allowed, own Store; foreign→404 | Forbidden | Forbidden |

## Authorization details

Role is global to the account; `STORE_ADMIN` and `MERCHANDISER` have a `store_id`, while `SYSTEM_ADMIN` and `END_CUSTOMER` are global/no Store. Store Admin and Merchandiser assignment requires an existing Store. System Admin and End Customer assignment requires no Store. Public self-registration accepts only email and always creates `END_CUSTOMER`; request fields such as `roleCode` are rejected as unknown. System Admin can invite privileged roles and assign roles/Stores through protected user management endpoints.

F01 `/api/users` is global System Admin administration, so it can list/read/manage accounts across Stores. F02 does **not** grant System Admin access: every F02 controller is restricted to `STORE_ADMIN`, even though the shared StoreScope helper contains a System Admin branch. Do not infer endpoint permission from helper behavior alone.

The database identity claims one Store and no request accepts a Store selector. Repositories scope category/product/batch reads and writes through the authenticated Store. F02 cross-Store records resolve to 404. Missing token is 401; authenticated insufficient role is 403. OpenAPI contains a global bearer scheme but Springdoc does not expose these role rules per operation; this source-derived table is authoritative for frontend navigation.

## Readiness evidence

The runtime OpenAPI export contains 18 F01 and 15 F02 HTTP operations. All listed operations are implemented in current controllers and represented in the export. Source/spec contract verification: PASS for all 33. Existing automated tests exercise F01 login/current-user/self-registration/OTP/invitation/role and token-revocation behavior, and F02 authentication, category/product, batch, summary and Store isolation behavior; not every individual operation was separately tested in a live browser for this documentation task. F02 browser verification from the prior F02 QA run is summarized in `docs/PASPM_F02_Inventory_Batch_Backlog_v3/F02_SWAGGER_TEST_REPORT.md`.

Integration-ready: F01 password/OTP/user APIs and F02 all 15 operations. Google operations are conditional on the Google client ID, mail delivery and valid Google-issued ID tokens. Browser cross-origin use is blocked until a same-origin proxy or deployment CORS policy is provided; Backend source contains no CORS mapping. Missing F01 operations: logout, refresh, password reset/change, self-profile update. F02 delete and stock-movement/adjustment history APIs do not exist and are not implied by the implemented contract.

## Endpoint readiness counts

| Feature | Implemented | Contract verified (source + runtime OpenAPI) | Ready for Frontend integration | Conditional / blocked at runtime | Not implemented workflows outside these endpoints |
|---|---:|---:|---:|---|---|
| F01 | 18 | 18 | 18 | Google register/login/link require Google client configuration and mail for OTP; in the default unconfigured setup those return 503 | logout, refresh token, password reset/change, self-profile edit |
| F02 | 15 | 15 | 15 | Browser cross-origin integration needs a same-origin proxy or deployment CORS policy; API endpoints themselves are available | delete endpoints, stock adjustment/movement history |

“Contract verified” means route, method, request/response model and security declaration were compared with the actual OpenAPI plus controller/service source. Existing MockMvc tests cover F01 authentication/OTP/invite/token revocation and F02 CRUD/Store isolation/summary. This is not a claim that each endpoint was separately called through a live Swagger UI during this doc task. `mvn -q verify` ran for this task; see final report for actual result.

## Per-operation readiness matrix

“Verified” below means implementation and runtime OpenAPI contract agree. “Test execution” counts existing Maven MockMvc/service tests from the current `mvn -q verify`, not Swagger UI activity in this documentation task. The F01 coverage count is conservative: 14 of 18 operation paths were directly requested by tests; the remaining four are called out in Notes (the OTP request alias shares a handler with resend). All 15 F02 operation paths appear in CatalogIntegrationTest requests, including authorization and cross-Store negative requests.

| Feature | Endpoint | Implemented | Verified | FE Ready | Test execution / notes |
|---|---|---|---|---|---|
| F01 | `GET /api/users/{id}` | PASS | PASS | Yes | No direct MockMvc request in current tests. |
| F01 | `PUT /api/users/{id}` | PASS | PASS | Yes | No direct MockMvc request in current tests. |
| F01 | `PUT /api/users/{id}/role` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `GET /api/products/{id}` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `PUT /api/products/{id}` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `GET /api/categories/{id}` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `PUT /api/categories/{id}` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F01 | `GET /api/users` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F01 | `POST /api/users` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `GET /api/products` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `POST /api/products` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `GET /api/products/{id}/batches` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `POST /api/products/{id}/batches` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `GET /api/categories` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `POST /api/categories` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F01 | `POST /api/auth/register` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F01 | `POST /api/auth/register/complete` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F01 | `POST /api/auth/otp/verify` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F01 | `POST /api/auth/otp/resend` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F01 | `POST /api/auth/otp/request` | PASS | PASS | Yes | Alias shares the tested OTP handler with /resend; this path was not called directly. |
| F01 | `POST /api/auth/login` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F01 | `POST /api/auth/google` | PASS | PASS | Conditional: Google/SMTP configuration. | Google disabled test returns 503; successful Google sign-in requires configuration. |
| F01 | `POST /api/auth/google/register` | PASS | PASS | Conditional: Google/SMTP configuration. | Google disabled test returns 503; registration logic also has service-level tests. |
| F01 | `POST /api/auth/google/link` | PASS | PASS | Conditional: Google/SMTP configuration. | Only unauthenticated 401 exercised; authorized link flow not tested. |
| F01 | `POST /api/auth/activate` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F01 | `PATCH /api/users/{id}/status` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `PATCH /api/products/{id}/status` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `GET /api/inventory-batches/{id}` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `PATCH /api/inventory-batches/{id}` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F02 | `PATCH /api/categories/{id}/status` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F01 | `GET /api/roles` | PASS | PASS | Yes | No direct MockMvc request in current tests. |
| F02 | `GET /api/products/{id}/inventory-summary` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |
| F01 | `GET /api/auth/me` | PASS | PASS | Yes | Covered by existing MockMvc/service suite. |

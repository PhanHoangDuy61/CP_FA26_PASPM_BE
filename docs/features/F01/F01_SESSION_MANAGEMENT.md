# F01 — Reconciled session management

Updated2026-11-12 (Asia/Ho_Chi_Minh). User decision: retain `dev` V20/AuthSessionService and integrate local protections additively. Current API contract: [F01 session contract](../../api/F01_AUTH_USER_API.md#session-integration-update--2026-11-11). Historical variant and gate evidence remain in their dated reports.

## 1. Canonical implementation

| Source | Responsibility |
|---|---|
| [AuthSessionService](../../../src/main/java/vn/edu/fpt/paspm/auth/AuthSessionService.java) | dev's issue/rotation, absolute + idle TTL, account/family locks, revoke, bounded cleanup; added stored CSRF binding |
| [AuthService](../../../src/main/java/vn/edu/fpt/paspm/auth/AuthService.java) | Existing credential validation, password changes and delegation to canonical session service |
| [AuthController](../../../src/main/java/vn/edu/fpt/paspm/auth/AuthController.java) | Existing login/google/refresh/logout routes and response payloads; duplicate-cookie rejection; invalid-refresh cookie clearing |
| [SessionOriginGuard](../../../src/main/java/vn/edu/fpt/paspm/auth/SessionOriginGuard.java) | Exact Origin/same-origin and loopback HTTP guard |
| [SecurityConfig](../../../src/main/java/vn/edu/fpt/paspm/security/SecurityConfig.java) | Existing role gates plus JWT sid family/version/revocation/absolute/idle validation |
| [V20](../../../src/main/resources/db/migration/V20__f01_refresh_sessions.sql) | Published schema, unchanged from42b8a9c |
| [V21](../../../src/main/resources/db/migration/V21__f01_refresh_csrf_binding.sql) | Add nullable csrf_hash BINARY(32), no data rewrite |

The alternative RefreshSessionService, SessionController, SessionHttpSupport and auth_session table are not retained. No duplicate routes or second refresh implementation is introduced.

## 2. Session lifecycle

Login creates a new family and opaque refresh token; credentials generate/store a CSRF hash atomically. Response JSON includes csrfToken but never raw refresh credentials. Refresh serializes using account/family/token locks; checks the exact token/CSRF binding, consumes the old token, then issues a replacement. Idle expiry extends to the lesser of now+idleTTL and the original absolute expiry.

Reuse revokes the family even when the response is401. Concurrent reuse yields one200 and one401 and then invalidates the family's sid JWTs. Normal rotation leaves older access JWTs usable while the family remains active. Logout requires valid Bearer and CSRF from a token of the same active family; consumed-token CSRF is accepted for logout to preserve dev's pre-rotation access-token semantics. Raw refresh cookie is unnecessary for logout. Logout-all revokes all own families and increments account version.

Password/reset/role/status invalidation remains dev's implementation. Legacy JWT without sid keeps its original expiry/version checks. Session-bound JWT requires nonrevoked family with matching account/version and both expiry boundaries still in the future.

## 3. Cookies, Origin and errors

Same cookie names and JSON fields as existing Frontend. Secure defaults: __Host-PASPM-REFRESH (HttpOnly) and __Host-PASPM-CSRF (readable), SameSite=Strict, Path=/, host-only. Local HTTP uses explicit REFRESH_COOKIE_SECURE=false and PASPM-* names. Keep JWT in memory; credentials:'include'; X-CSRF-Token equals the readable cookie. The server additionally verifies persisted binding.

Origin must match the server or exact PASPM_SESSION_ALLOWED_ORIGINS. Missing Origin does not bypass CSRF. Duplicated named cookies are rejected. Origin/CSRF checks occur before token lookup: missing pair403, unknown token with supplied pair401. Invalid refresh401 clears cookies; rejected CSRF403 does not rotate/clear a valid pair. No CORS change. Refresh throttle remains dev's 30/IP/900s per process.

## 4. Migration compatibility

V20 SHA-256: `534003c0baf641960aac34ff5d5ad03b35a468f46d283593bf742e692d7c0002`, unchanged. V21 adds only a nullable column. A V20 token cannot acquire an authentic historical CSRF binding during migration: NULL stays NULL. On refresh, that family is revoked and401 prompts a new sign-in. No records, token hashes, times, snapshots or source history are fabricated or removed by migration.

This upgrade applies to **published dev V20**, not the competing local V20 (different tables/hash types/checksum). No production/source database or repair was executed. Do not point this build at the incompatible local schema; use an isolated database or a separately approved conversion plan. Rolling an app back to old dev code leaves the nullable column ignored, but its weaker security behavior is not recommended or exercised here; no automatic downgrade/drop is supplied.

## 5. Tác động tới feature khác

| Feature | Authentication impact | Unchanged |
|---|---|---|
| F02 | Valid Bearer, role and StoreScope remain required; Customer gets no catalog access | Product/category/inventory business rules and persistence |
| F03 | Revoked sessions cannot read/write protected utility APIs | Calculation, expiryPushDays, immutable historical snapshots |
| F04 | Imports and reads retain role/Store authorization | Parsing, idempotency, transaction rollback/history |
| F05 | HTTP routes use active sessions; internal machine execution does not use browser cookies | Scheduler policies/gates, leases/fencing, mining configuration |
| F06 | Same Store/role checks for read/generate/review/config | Rule engine, frozen evidence, live inventory checks |

Customer UI visibility can reflect demo data and does not demonstrate purchase/public recommendation APIs.

## 6. Settings

Preserved dev values: access3600 seconds (60–3600); absolute2592000 seconds (3600–7776000); idle604800 seconds (60 through absolute). REFRESH_COOKIE_SECURE defaults true. PASPM_SESSION_ALLOWED_ORIGINS is additive. Older local PASPM_SESSION_COOKIE_SECURE / PASPM_SESSION_REFRESH_TTL_SECONDS are not canonical settings. [Local setup](../../setup/LOCAL_SETUP_COMMON.md).

Cleanup preserves dev policy: 30-day replay-retention cutoff for old absolute-expired or revoked families, maximum100 per issue/refresh. No shared limiter or independent cleanup daemon added; no new PO business decision is inferred.

## 7. Verification and release status

[Current report](../../testing/reports/F01_SESSION_RECONCILIATION_2026-11-12.md) records exact runs, failures/retests, counts, MySQL preservation and runtime OpenAPI. Earlier browser evidence and tests describe the local variant and are explicitly historical. Release/merge state: [current record](../../record-of-changes/2026-11-12/01-f01-dev-reconciliation.md); no hosted CI/production claim without actual execution.

## 8. Diagram backlog

Update diagrams in a separate task to show canonical auth_refresh_session/auth_refresh_token plus V21 csrf_hash, login/refresh/logout/reuse and the separate internal scheduler identity. No other entity or relationship change is proposed; diagram files are untouched here.

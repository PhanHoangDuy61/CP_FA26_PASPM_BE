# F01 session compatibility verification — 2026-11-11

Status: Backend implemented and browser session flow verified locally. No commit, push, merge or deployment performed. Execution timezone: Asia/Ho_Chi_Minh (UTC+07).

## Root cause and change

FE_Kien `src/services/authSession.ts` rejects credentials without `csrfToken`, restores sessions through POST `/api/auth/refresh` and uses server logout. The old BE `Contracts.Token` had only three fields and none of these session routes. Valid customer credentials therefore failed FE session validation.

Changed Backend only: `Contracts.Token`, `AuthService`, `AuthController`, `SecurityConfig`; added `RefreshSessionService`, `SessionHttpSupport`, `SessionController`, and V20 `auth_session` / `auth_refresh_token`. Existing F01 validation, roles and account token-version checks remain active. No Frontend implementation changes. Existing unrelated diagram deletions/untracked files were preserved.

The earlier avatar startup fix supplies the existing storage bean's configuration keys; the user-local application.yml already contains unrelated modifications. Do not stage that entire configuration blindly.

## Red / green and regression evidence

- Initial execution of the four session tests failed 4/4 with `No value at JSON path "$.csrfToken"` before the production session implementation. Private local log: `/private/tmp/session-red.log`.
- First targeted green run: SessionHttpIntegrationTest 4 + AuthIntegrationTest 22 = 26 PASS, zero failures/errors/skips. `/private/tmp/session-green.log`.
- Session suite extended to ten business/security scenarios below plus an OpenAPI contract test (11 total), and three cookie-support unit tests.
- Java17 full verify with the separate Java25 SPMF executable: initial successful run 573 discovered, 384 executed PASS, 189 SKIPPED, zero failures/errors (`/private/tmp/session-final-verify.log`). The final rerun including all additional tests is recorded below after completion.
- Standalone MySQL 8.4.11 copy of local V19: 10 session tests PASS, V19→V20 migration succeeded; original local DB stayed V19 during this copy verification (`/private/tmp/session-mysql-test.log`).
- Additional real Docker MySQL 8.4 on loopback port 13317: Final rerun: 27/27 PASS, zero skips/errors/failures. Container `paspm-session-acceptance-final-20261111`, stopped after testing; private log `/private/tmp/session-mysql-docker.log`.

| Final Docker group | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| SessionHttpSupportTest | 3 | 0 | 0 | 0 |
| SessionMySqlIntegrationTest | 11 | 0 | 0 | 0 |
| FlywayRecoveryTest | 2 | 0 | 0 | 0 |
| JpaPilotMigrationMySqlTest | 4 | 0 | 0 | 0 |
| MiningMigrationMySqlTest | 5 | 0 | 0 | 0 |
| RecommendationMigrationTest | 2 | 0 | 0 | 0 |

The first unspecialized full run failed because it selected the Backend Java17 for the Java25 SPMF child and still expected twelve post-V7 migrations; V20 makes that thirteen. Corrected the runtime selection and the latest-version/count expectations in affected migration tests. No applied SQL migration was edited. A subsequent optional host-MySQL group also encountered missing `PASPM_TEST_RESTORE_PROVISIONER` (one failed prerequisite and five multi-JVM skips). This was rerun on the independent disposable Docker server with the repository's restricted restore provisioner; every selected Docker case passed. These earlier failures are not relabelled as successful executions.

## Session test cases

All ten cases executed on both H2 and real MySQL:

| Test | Actual checked outcome |
|---|---|
| rotationAndReplayRevokeFamily | Login and rotation 200; raw refresh changes; replay 401; newest access JWT from that family becomes 401 |
| csrfAndOriginFailuresDoNotRotate | Missing CSRF 403; hostile Origin 403; original valid pair still rotates 200 |
| logoutOneAndLogoutAll | Logout 200; own old JWT 401; other session still refreshes; logout-all 200 invalidates other JWT |
| expiredAndChangedAccountCannotRefresh | Expired family 401; bumped account token_version 401 |
| cookieFlagsAndCacheControl | Local refresh HttpOnly, readable CSRF, Path=/, SameSite=Strict; response CSRF equals cookie; Cache-Control no-store |
| csrfCookieAndHeaderMustBothMatch | Missing CSRF cookie 403; forged cookie/header matching each other but not stored hash 403; valid original pair remains usable |
| logoutRequiresBearerAndMatchingSession | No Bearer 401; Bearer from another family 401; both valid families remain usable |
| hostileLoginOriginCannotCreateSession | Unapproved Origin 403 and no family inserted |
| accountDeactivationPreventsRefresh | Inactive account 401 |
| concurrentReuseHasOneWinnerAndRevokesFamily | Two concurrent same-credential requests yield exactly one 200 and one 401; winner's JWT then rejected after family revocation |

The additional `openApiDoesNotRequireBearerForSessionEntryPoints` test ran on H2 and MySQL: login/google/refresh operations explicitly override global Bearer security with an empty security requirement. A failing test first demonstrated the inherited-Bearer documentation bug; `@SecurityRequirements` fixed it without changing runtime access rules.

Three direct cookie-support tests: Secure __Host cookies/flags and expiry clearing; reject insecure mode for non-loopback host; exact allowed/same Origin accepted, hostile and null Origin rejected.

## Actual browser verification

Real Chromium driven by cached Playwright CLI against unmodified FE_Kien on http://localhost:3000 and BE on http://localhost:8080:

1. Fill existing demo Customer email/password from a private file; Sign in reaches `/storefront` and renders `PASPM Demo Customer`.
2. Reload document (old in-memory access token lost). Observed POST `/api/auth/refresh` 200 and GET `/api/auth/me` 200; storefront remains authenticated.
3. Browser cookies: PASPM-REFRESH HttpOnly=true; PASPM-CSRF HttpOnly=false; both SameSite=Strict, Secure=false only in the explicit local HTTP mode. JWT-pattern scan of localStorage found no persisted access token.
4. Account menu → Sign out: POST logout 200, browser returns `/login`, zero PASPM session cookies remain.

Final repeated UI execution against the last build is recorded in [redacted browser evidence](F01_SESSION_BROWSER_EVIDENCE.json).

Screenshot: [restored Customer session](../evidence/f01_session/customer-session-restored.png) (artifact copied into the repository for handoff). Browser session test is for authentication only. Storefront recommendations/prices/basket shown by FE may still be demo data; no customer purchase/business API acceptance is claimed.

A first browser instrumentation attempt crashed the Playwright daemon because `URL` was unavailable in its evaluation VM. Reopened the browser and repeated login/reload with a compatible listener; the actual successful results above came from that repeated execution, not the failed tool attempt. No authentication payloads/headers are included here.

## Database / startup / OpenAPI

A private V19 backup was taken before touching the development DB. The copy test proved V20 on MySQL 8.4.11 first. The local development Backend then applied V20 automatically and started on Java17, port8080. Existing V1–V19 migration files were not changed; no source V5 database, historical utility snapshot or mining data was repaired/reset/rewritten. The Docker recovery tests use synthetic independent schemas only.

Runtime `/v3/api-docs` explicitly marks login/google/refresh as public, and was exported to `docs/api/openapi.json`: 92 operations; Token.csrfToken and refresh/logout/logout-all present. This is OpenAPI verification and real browser login flow, not an exhaustive interactive Swagger test of every API.

## Reproduction commands

Select an installed JDK17 through JAVA_HOME/PATH; pass your own installed Java25 executable (no developer-specific paths should be committed into configuration):

```sh
mvn -Dtest=SessionHttpIntegrationTest,AuthIntegrationTest,SessionHttpSupportTest test
mvn -Dpaspm.test.spmf-java="$SPMF_JAVA" -Dpaspm.mining.java-executable="$SPMF_JAVA" verify
```

Opt-in SessionMySqlIntegrationTest uses PASPM_SESSION_MYSQL_URL / USER / PASSWORD and a disposable schema. Migration groups use PASPM_TEST_MYSQL_SERVER / USER / PASSWORD; authenticated populated restore requires PASPM_TEST_RESTORE_PROVISIONER and a genuinely disposable MySQL host. Never point these tests at a shared source database. `.github/scripts/provision-restore.py` was reused through an executable private wrapper; its guard was not weakened.

## Remaining limits

- Real Google external authentication, HTTPS ingress deployment and multi-Backend-instance auth concurrency are NOT RUN. The same email/Google controller path wraps credentials through the new session service, but no real Google login is claimed.
- Refresh concurrency was tested using independent transactional requests against real MySQL; it is not a separate JVM acceptance test.
- Opt-in Linux process, historical release-JAR and multi-container suites skipped by default Maven are not certified by this task.
- Refresh throttles are in-memory per instance. Expired/consumed records have no automatic retention cleanup yet. Seven-day family lifetime is a configurable engineering default.
- IntelliJ local HTTP needs explicit cookie/origin settings; see LOCAL_SETUP_COMMON.md. Spring Boot does not automatically load `.env`.
- No tokens, OTPs, passwords, connection strings or database dumps are included in committed documentation/evidence. Private logs/backups remain outside the repository.

## Final Maven verify

Final source `mvn verify` exit 0: **578 discovered, 388 PASS, 190 SKIPPED, zero failures/errors**. Backend Java 17.0.21 (Corretto), external SPMF Java 25.0.1 (Homebrew); pinned local JAR SHA-256 `4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a`. Private final log: `/private/tmp/session-delivery-verify.log`.

Independent post-change Docker group: **27 PASS, zero skips/failures/errors**. Of the 190 default-skipped tests, 23 were executed successfully in this independent group (SessionMySql 11, JPA migration 4, mining migration 5, recommendation migration 2, Flyway MySQL recovery 1). The remaining 167 were not rerun by this task. They include other opt-in MySQL suites, Linux-only parent-death process verification, multi-JVM/container scheduler tests and historical release-artifact checks; no claim of their passing is made. Full per-suite counts: [test evidence](F01_SESSION_TEST_COUNTS.json).

Final Backend restarted on localhost:8080; Swagger returns 200 and local development Flyway reports V20 successful. FE remains at localhost:3000 with its implementation unchanged. Final real browser login, refresh and logout each returned 200, and no session cookie remained after logout.

## Publication verification and correction — 2026-11-11

Before publishing feature commit `9d33d88`, exported the staged index to a fresh directory (no existing target, no private application.yml overrides) and ran Java17 Maven verify with the pinned local SPMF JAR and Java25 child executable. Exit 0: **577 discovered / 387 PASS / 190 SKIPPED**, zero failures/errors. [Fresh per-suite counts](F01_SESSION_PUBLISH_VERIFY_COUNTS.json).

Correction: the earlier 578/388 aggregate in this report and its original counts file included one stale `MiningAutomaticSchedulerMySqlTest` XML from target; that test class is absent from the current source. The old totals therefore are not evidence that this extra test was freshly executed. Original evidence files/dated records are retained with this explicit correction. The independent 27-case Docker run and browser evidence remain separate; no new MySQL/browser execution is claimed here.

After fetch, origin/dev `42b8a9c` already contains a different F01 refresh implementation and incompatible V20. This feature branch is published for reconciliation, not merged or deployment-ready against that new schema. [Publication and blocker record](../../record-of-changes/2026-11-11/04-feature-publication-and-merge-blocker.md).

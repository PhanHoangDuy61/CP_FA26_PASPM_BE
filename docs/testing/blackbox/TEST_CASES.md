# PASPM black-box test cases

Generated from the sources listed in [TEST_PLAN.md](TEST_PLAN.md) §2. Same content as [TEST_CASES.csv](TEST_CASES.csv) (one row per case). Every case is **NOT RUN**; results are recorded as described in TEST_PLAN.md §8. Cases titled `[PLANNED]` cover F10/F11, which are not implemented yet, and run once those features are merged.

Conventions: `T` = today in `Asia/Ho_Chi_Minh` on the execution day. Accounts (`ACC-…`), environments (`ENV-1`), setups (`SETUP-B`) and data preparations (`DP-…`) are defined in TEST_PLAN.md §4–§6. Steps prefixed `[API]` have no UI path (no screen exists, the FE screen is still mock data, or the step forces a call the UI never makes, such as a forbidden role or an extra field); run them with Swagger (`/swagger`) or curl using the role's bearer token.

## Summary

| Feature | Cases | High | Medium | Low | functional | negative | boundary | security | state transition | decision table | UI |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| [F01 Identity, access and Store management](#f01) | 51 | 28 | 17 | 6 | 13 | 14 | 11 | 9 | 3 | 0 | 1 |
| [F02 Catalogue and inventory](#f02) | 37 | 19 | 16 | 2 | 11 | 4 | 13 | 4 | 2 | 3 | 0 |
| [F03 Utility profiling (Weight v2)](#f03) | 29 | 16 | 11 | 2 | 9 | 6 | 10 | 2 | 1 | 1 | 0 |
| [F04 Transaction import](#f04) | 15 | 11 | 2 | 2 | 7 | 4 | 3 | 1 | 0 | 0 | 0 |
| [F05 EFIM mining](#f05) | 26 | 14 | 8 | 4 | 6 | 9 | 7 | 4 | 0 | 0 | 0 |
| [F06 Recommendations](#f06) | 18 | 9 | 8 | 1 | 6 | 2 | 8 | 1 | 1 | 0 | 0 |
| [F07 Layout simulator](#f07) | 15 | 11 | 3 | 1 | 3 | 6 | 3 | 1 | 2 | 0 | 0 |
| [F08 Campaigns and simulated A/B](#f08) | 19 | 13 | 4 | 2 | 1 | 6 | 3 | 1 | 7 | 0 | 1 |
| [F09 Public storefront](#f09) | 13 | 8 | 3 | 2 | 3 | 2 | 3 | 3 | 1 | 1 | 0 |
| [F10 Bundle cart (PLANNED)](#f10) | 16 | 10 | 6 | 0 | 4 | 4 | 4 | 2 | 1 | 1 | 0 |
| [F11 Dashboard (PLANNED)](#f11) | 9 | 4 | 4 | 1 | 4 | 0 | 2 | 1 | 0 | 0 | 2 |
| **Total** | **248** | **143** | **82** | **23** | **67** | **57** | **67** | **29** | **18** | **6** | **4** |

## F01

**Identity, access and Store management** — 51 cases.

| ID | Title | Priority | Type |
|---|---|---|---|
| [TC-F01-001](#tc-f01-001) | Sign in with email and password, each role lands on its own workspace | High | functional |
| [TC-F01-002](#tc-f01-002) | Wrong password and unknown email give the same generic error | High | negative |
| [TC-F01-003](#tc-f01-003) | Pending and INACTIVE accounts cannot sign in | High | negative |
| [TC-F01-004](#tc-f01-004) | Self-registration with email OTP creates an End Customer | High | functional |
| [TC-F01-005](#tc-f01-005) | Registering an existing email returns 409 | Medium | negative |
| [TC-F01-006](#tc-f01-006) | Self-registration cannot choose a role or Store | High | security |
| [TC-F01-007](#tc-f01-007) | Password length and complexity boundaries | High | boundary |
| [TC-F01-008](#tc-f01-008) | Five wrong OTP attempts lock the challenge | Medium | boundary |
| [TC-F01-009](#tc-f01-009) | OTP resend inside the 60 s cooldown is refused | Low | boundary |
| [TC-F01-010](#tc-f01-010) | Expired OTP is refused | Low | boundary |
| [TC-F01-011](#tc-f01-011) | Login rate limit per email | Medium | security |
| [TC-F01-012](#tc-f01-012) | Google sign-in without Google configuration | Low | negative |
| [TC-F01-013](#tc-f01-013) | Silent token refresh keeps the session alive | High | functional |
| [TC-F01-014](#tc-f01-014) | Refresh without the CSRF header is refused | High | security |
| [TC-F01-015](#tc-f01-015) | Reused refresh token revokes the token family | High | security |
| [TC-F01-016](#tc-f01-016) | Logout and logout-all revoke sessions | Medium | functional |
| [TC-F01-017](#tc-f01-017) | Password reset invalidates old tokens | Medium | functional |
| [TC-F01-018](#tc-f01-018) | Change password: wrong current, same password, success | Medium | negative |
| [TC-F01-019](#tc-f01-019) | System Admin invites a Store Admin who activates by OTP | High | functional |
| [TC-F01-020](#tc-f01-020) | Role and Store combinations (decision table) | High | negative |
| [TC-F01-021](#tc-f01-021) | Duplicate email invite returns 409 | Medium | negative |
| [TC-F01-022](#tc-f01-022) | Changing a user's role and Store revokes their tokens | High | functional |
| [TC-F01-023](#tc-f01-023) | System Admin cannot deactivate self or remove own System Admin role | Medium | negative |
| [TC-F01-024](#tc-f01-024) | Deactivated user loses access; pending user cannot be activated by status | High | state transition |
| [TC-F01-025](#tc-f01-025) | User administration RBAC (decision table) | High | security |
| [TC-F01-026](#tc-f01-026) | User list paging bounds | Low | boundary |
| [TC-F01-027](#tc-f01-027) | Avatar upload limits and owner-only read | Medium | boundary |
| [TC-F01-028](#tc-f01-028) | System Admin creates a Store | High | functional |
| [TC-F01-029](#tc-f01-029) | Store name is unique after trim, ignoring case | High | boundary |
| [TC-F01-030](#tc-f01-030) | Store slug must be unique | High | negative |
| [TC-F01-031](#tc-f01-031) | Slug format and length boundaries | Medium | boundary |
| [TC-F01-032](#tc-f01-032) | Store field length and phone character boundaries | Medium | boundary |
| [TC-F01-033](#tc-f01-033) | Store list filters and search | Medium | functional |
| [TC-F01-034](#tc-f01-034) | Store edit with a stale version returns 409 | High | negative |
| [TC-F01-035](#tc-f01-035) | Store detail lists assigned users read-only and allows slug edit | Medium | functional |
| [TC-F01-036](#tc-f01-036) | Stores cannot be deleted | Medium | negative |
| [TC-F01-037](#tc-f01-037) | Store state ACTIVE → INACTIVE → ACTIVE | High | state transition |
| [TC-F01-038](#tc-f01-038) | Repeating a status change is refused | Medium | state transition |
| [TC-F01-039](#tc-f01-039) | Users of an INACTIVE Store are blocked everywhere | High | security |
| [TC-F01-040](#tc-f01-040) | No user assignment to an INACTIVE Store | High | negative |
| [TC-F01-041](#tc-f01-041) | Store Admin edits own address and phone | High | functional |
| [TC-F01-042](#tc-f01-042) | Own profile cannot change name, slug or status; Merchandiser is read-only | High | security |
| [TC-F01-043](#tc-f01-043) | Upload icon and banner with preview and public URL | High | functional |
| [TC-F01-044](#tc-f01-044) | Image size limits (icon 1 MiB, banner 5 MiB) | High | boundary |
| [TC-F01-045](#tc-f01-045) | Image type is checked by content | High | negative |
| [TC-F01-046](#tc-f01-046) | Image dimension limit 4000×4000 | Medium | boundary |
| [TC-F01-047](#tc-f01-047) | Store image RBAC and cross-Store access | High | security |
| [TC-F01-048](#tc-f01-048) | Public image only for current files of ACTIVE Stores | High | security |
| [TC-F01-049](#tc-f01-049) | Image change bumps the Store version | Medium | negative |
| [TC-F01-050](#tc-f01-050) | Remove an image | Low | functional |
| [TC-F01-051](#tc-f01-051) | Screens work from 320 to 1440 px wide | Low | UI |

### TC-F01-001

**Sign in with email and password, each role lands on its own workspace**

| Field | Value |
|---|---|
| Trace | FR-01.1; FR-01.4; UC01; BR-02; NFR 5.5 |
| Role | System Admin, Store Admin, Merchandiser, End Customer |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | ACC-SA, ACC-STA-A, ACC-MER-A, ACC-EC (passwords from .env.seed) |
| Steps | 1. Open /login<br>2. Sign in as each account in turn<br>3. Open Profile (/profile) and check role and Store<br>4. Sign out |
| Expected result | Each sign-in succeeds. System Admin lands on /admin/overview, Store Admin on /store/inventory, Merchandiser on its F03/merchandiser workspace, End Customer on /storefront. Profile shows exactly one role; Store Admin and Merchandiser show Store A, System Admin and End Customer show no Store. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F01-002

**Wrong password and unknown email give the same generic error**

| Field | Value |
|---|---|
| Trace | FR-01.1; NFR 5.4 |
| Role | Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | ACC-STA-A with a wrong password; unknown email qa-nobody@paspm.example |
| Steps | 1. Open /login<br>2. Sign in with ACC-STA-A and a wrong password<br>3. Sign in with the unknown email |
| Expected result | Both attempts are refused with HTTP 401 and the same message `Invalid credentials or account unavailable`; the UI does not reveal whether the email exists. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F01-003

**Pending and INACTIVE accounts cannot sign in**

| Field | Value |
|---|---|
| Trace | FR-01.5; FR-01.1 |
| Role | Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); ACC-PENDING (invited, not activated); ACC-MER-A deactivated by the System Admin |
| Test data | ACC-PENDING; ACC-MER-A (INACTIVE) |
| Steps | 1. Sign in as ACC-PENDING<br>2. Sign in as the deactivated Merchandiser<br>3. Reactivate the Merchandiser (System Admin) and sign in again |
| Expected result | Steps 1–2: 401 `Invalid credentials or account unavailable`. Step 3: sign-in succeeds. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F01-004

**Self-registration with email OTP creates an End Customer**

| Field | Value |
|---|---|
| Trace | FR-01.2 |
| Role | Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); Mailpit at http://127.0.0.1:8025 |
| Test data | New email qa-ec-001@paspm.example; valid password (12–72 chars, upper, lower, digit) |
| Steps | 1. Open /register and submit the new email<br>2. Read the 6-digit OTP from Mailpit<br>3. Enter the OTP<br>4. Complete name and password<br>5. Sign in with the new account |
| Expected result | Register returns 202; OTP verify returns 200; completion returns 200 without issuing a token; sign-in succeeds; the account has role END_CUSTOMER and no Store. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F01-005

**Registering an existing email returns 409**

| Field | Value |
|---|---|
| Trace | FR-01.2 |
| Role | Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | customer@paspm.example (already registered) |
| Steps | 1. Open /register<br>2. Submit the existing email |
| Expected result | HTTP 409 `Email already exists`; no OTP email is sent. |
| Priority / type | Medium / negative |
| Status | NOT RUN |

### TC-F01-006

**Self-registration cannot choose a role or Store**

| Field | Value |
|---|---|
| Trace | FR-01.2; FR-01.4; NFR 5.4 |
| Role | Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | Body {"email":"qa-ec-002@paspm.example","roleCode":"SYSTEM_ADMIN"} |
| Steps | 1. [API] POST /api/auth/register with the body (no UI exposes a role field) |
| Expected result | HTTP 400 `Invalid request fields`; no account is created. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F01-007

**Password length and complexity boundaries**

| Field | Value |
|---|---|
| Trace | FR-01.2; NFR 5.4 |
| Role | Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); a verified pending self-registration |
| Test data | Passwords: 11 chars `Abcdefghij1`; 12 chars `Abcdefghijk1`; 72 chars; 73 chars; 12 chars without upper case `abcdefghijk1` |
| Steps | 1. Complete registration with each password in turn (new email per valid attempt) |
| Expected result | 11 and 73 characters, and the one without an upper-case letter, are refused with 400 and a field error. 12 and 72 characters are accepted. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F01-008

**Five wrong OTP attempts lock the challenge**

| Field | Value |
|---|---|
| Trace | FR-01.2; NFR 5.4 |
| Role | Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); pending self-registration with an OTP sent |
| Test data | Wrong OTP 000000 ×5, then the correct OTP |
| Steps | 1. Enter a wrong OTP five times<br>2. Enter the correct OTP |
| Expected result | Attempts 1–5 return 400. The correct OTP after five failures is also refused (400); a new OTP must be requested. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F01-009

**OTP resend inside the 60 s cooldown is refused**

| Field | Value |
|---|---|
| Trace | FR-01.2 |
| Role | Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); OTP just sent |
| Test data | Same email |
| Steps | 1. Click Resend OTP immediately<br>2. Wait 60 s and click Resend again |
| Expected result | First resend: 429 (cooldown). After 60 s: 202 and a new OTP in Mailpit. |
| Priority / type | Low / boundary |
| Status | NOT RUN |

### TC-F01-010

**Expired OTP is refused**

| Field | Value |
|---|---|
| Trace | FR-01.2 |
| Role | Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); OTP TTL default 600 s |
| Test data | OTP older than 10 minutes |
| Steps | 1. Request an OTP<br>2. Wait more than 600 s<br>3. Submit the OTP |
| Expected result | HTTP 400 (invalid or expired OTP). |
| Priority / type | Low / boundary |
| Status | NOT RUN |

### TC-F01-011

**Login rate limit per email**

| Field | Value |
|---|---|
| Trace | NFR 5.4 |
| Role | Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); fresh backend process (limits are in memory) |
| Test data | ACC-MER-A with a wrong password |
| Steps | 1. Submit 10 wrong-password logins for the same email within 15 min<br>2. Submit an 11th attempt |
| Expected result | Attempts 1–10 return 401; the 11th returns 429. |
| Priority / type | Medium / security |
| Status | NOT RUN |

### TC-F01-012

**Google sign-in without Google configuration**

| Field | Value |
|---|---|
| Trace | FR-01.1 |
| Role | Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); GOOGLE client ID not configured (default local stack) |
| Test data | Any Google ID token |
| Steps | 1. Click Sign in with Google on /login |
| Expected result | HTTP 503; the UI shows that Google sign-in is unavailable. (With Google configured: a verified Google email linked to an ACTIVE account signs in; an unverified email is refused 401.) |
| Priority / type | Low / negative |
| Status | NOT RUN |

### TC-F01-013

**Silent token refresh keeps the session alive**

| Field | Value |
|---|---|
| Trace | FR-01.3 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); backend started with TOKEN_TTL_SECONDS=60 |
| Test data | ACC-MER-A |
| Steps | 1. Sign in<br>2. Wait 90 s<br>3. Open /f03/configuration |
| Expected result | The page loads without a new sign-in: the client calls POST /api/auth/refresh with the HttpOnly refresh cookie, the CSRF cookie and `X-CSRF-Token`; a new access token is returned. The refresh token never appears in JSON or local storage. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F01-014

**Refresh without the CSRF header is refused**

| Field | Value |
|---|---|
| Trace | FR-01.3; NFR 5.4 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); signed-in session |
| Test data | ACC-MER-A |
| Steps | 1. [API] POST /api/auth/refresh with the cookies but without X-CSRF-Token |
| Expected result | HTTP 403; the existing session stays usable. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F01-015

**Reused refresh token revokes the token family**

| Field | Value |
|---|---|
| Trace | FR-01.3; NFR 5.4 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); signed-in session |
| Test data | ACC-MER-A; the refresh cookie value before rotation |
| Steps | 1. [API] Refresh once (cookie rotates)<br>2. [API] Replay the old refresh cookie with its CSRF pair<br>3. Use the access token from the first refresh |
| Expected result | Replay returns 401 and clears cookies; the whole family is revoked, so the token from step 1 is also rejected (401). |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F01-016

**Logout and logout-all revoke sessions**

| Field | Value |
|---|---|
| Trace | FR-01.3 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); ACC-STA-A signed in on two browsers |
| Test data | ACC-STA-A |
| Steps | 1. Browser 1: Sign out<br>2. Browser 1: press Back and reload a protected page<br>3. Browser 2: Sign out of all sessions<br>4. Browser 3 (signed in before step 3): reload |
| Expected result | After logout, browser 1 is sent to /login. After logout-all every session of the account gets 401 and must sign in again. |
| Priority / type | Medium / functional |
| Status | NOT RUN |

### TC-F01-017

**Password reset invalidates old tokens**

| Field | Value |
|---|---|
| Trace | FR-01.3 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | ACC-MER-A; new valid password |
| Steps | 1. Open /forgot-password and request a reset<br>2. Use the OTP from Mailpit to set a new password<br>3. Use the access token issued before the reset<br>4. Sign in with the new password |
| Expected result | Reset request always returns the same 202 message. After confirm, the old token gets 401 (token_version incremented). New password works. |
| Priority / type | Medium / functional |
| Status | NOT RUN |

### TC-F01-018

**Change password: wrong current, same password, success**

| Field | Value |
|---|---|
| Trace | FR-01.3 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | ACC-STA-A |
| Steps | 1. Profile → Change password with a wrong current password<br>2. Change to the same password<br>3. Change to a new valid password<br>4. Reuse the old token |
| Expected result | Wrong current: 401. Same password: 400. Success: 200, the old token is revoked and the user signs in again. |
| Priority / type | Medium / negative |
| Status | NOT RUN |

### TC-F01-019

**System Admin invites a Store Admin who activates by OTP**

| Field | Value |
|---|---|
| Trace | UC01; FR-01.2; FR-01.4 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | name QA Store Admin 2, email qa-sta-a2@paspm.example, role STORE_ADMIN, Store A |
| Steps | 1. Open /admin/users → Invite<br>2. Fill name, email, role STORE_ADMIN and Store A<br>3. Invitee reads the ADMIN_INVITE OTP in Mailpit and activates at /activate with a new password<br>4. Invitee signs in |
| Expected result | Invite returns 201 with status PENDING_VERIFICATION. Activation returns 200. Sign-in succeeds with role STORE_ADMIN and storeId of Store A. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F01-020

**Role and Store combinations (decision table)**

| Field | Value |
|---|---|
| Trace | UC01; UC02; FR-01.4; BR-02 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | Invites: STORE_ADMIN without Store; MERCHANDISER without Store; SYSTEM_ADMIN with Store A; END_CUSTOMER with Store A; STORE_ADMIN with Store A; unknown role QA_ROLE |
| Steps | 1. Invite each combination from /admin/users |
| Expected result | STORE_ADMIN/MERCHANDISER without Store, SYSTEM_ADMIN or END_CUSTOMER with a Store, and an unknown role are refused with 400. STORE_ADMIN with Store A is created (201). |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F01-021

**Duplicate email invite returns 409**

| Field | Value |
|---|---|
| Trace | UC01 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | storeadmin@paspm.example |
| Steps | 1. Invite a new account with an existing email |
| Expected result | HTTP 409; no second account. |
| Priority / type | Medium / negative |
| Status | NOT RUN |

### TC-F01-022

**Changing a user's role and Store revokes their tokens**

| Field | Value |
|---|---|
| Trace | UC02; FR-01.4 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); ACC-STA-A2 signed in |
| Test data | ACC-STA-A2 → MERCHANDISER, Store A |
| Steps | 1. Open the user in /admin/users and change the role<br>2. In the user's browser, reload a Store Admin page<br>3. User signs in again |
| Expected result | Role change returns 200. The old token is rejected (401). After sign-in the user lands on the Merchandiser workspace. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F01-023

**System Admin cannot deactivate self or remove own System Admin role**

| Field | Value |
|---|---|
| Trace | UC01; UC02 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | ACC-SA's own user ID |
| Steps | 1. Open own account in /admin/users and set INACTIVE<br>2. Change own role to STORE_ADMIN |
| Expected result | Both refused with 400; the account stays ACTIVE SYSTEM_ADMIN. |
| Priority / type | Medium / negative |
| Status | NOT RUN |

### TC-F01-024

**Deactivated user loses access; pending user cannot be activated by status**

| Field | Value |
|---|---|
| Trace | UC01; FR-01.5 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); ACC-MER-A signed in; ACC-PENDING exists |
| Test data | ACC-MER-A; ACC-PENDING |
| Steps | 1. Set ACC-MER-A INACTIVE<br>2. In ACC-MER-A's browser reload a page<br>3. Set ACC-PENDING to ACTIVE |
| Expected result | ACC-MER-A's token is rejected (401) and login fails. Activating a pending account by status returns 400 (OTP cannot be bypassed). |
| Priority / type | High / state transition |
| Status | NOT RUN |

### TC-F01-025

**User administration RBAC (decision table)**

| Field | Value |
|---|---|
| Trace | UC01; UC02; NFR 5.4 |
| Role | Store Admin, Merchandiser, End Customer, Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | GET /api/users; GET /api/roles; POST /api/users |
| Steps | 1. Check that the Users & Roles menu is absent for non-System-Admin roles<br>2. [API] Call each route with each role's token and without a token |
| Expected result | Menu absent. Store Admin, Merchandiser and End Customer get 403; no token gets 401. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F01-026

**User list paging bounds**

| Field | Value |
|---|---|
| Trace | UC01; NFR 5.2 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | size = 0, 1, 100, 101; page = -1 |
| Steps | 1. [API] GET /api/users with each value (the UI pager only offers valid sizes) |
| Expected result | size 1 and 100 return 200 with at most that many rows; size 0, 101 and page -1 return 400. |
| Priority / type | Low / boundary |
| Status | NOT RUN |

### TC-F01-027

**Avatar upload limits and owner-only read**

| Field | Value |
|---|---|
| Trace | FR-01.5 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | PNG 400×400 (200 KiB); PNG > 5 MiB; .txt renamed .png; PNG 4097×10 |
| Steps | 1. Profile → upload each file<br>2. Another user requests GET /api/users/{merchandiserId}/avatar |
| Expected result | Valid PNG: 200 and the avatar shows. > 5 MiB: 413. Fake PNG and 4097 px wide: 400. Another user's avatar: 403. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F01-028

**System Admin creates a Store**

| Field | Value |
|---|---|
| Trace | UC34; FR-01.6; FR-01.7 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | name QA Store B, slug qa-store-b, address 1 Le Loi, phone +84 28 1234 5678 |
| Steps | 1. Open /admin/stores → Create<br>2. Fill the form and save<br>3. Open the Store detail |
| Expected result | 201; the Store is ACTIVE with version 1 and appears in the list (ordered by name). Detail shows createdAt, assignedUserCount 0 and an empty user list. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F01-029

**Store name is unique after trim, ignoring case**

| Field | Value |
|---|---|
| Trace | UC34; BR-27 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | name "  foodmart BENCHMARK  ", slug qa-dup-name |
| Steps | 1. Create a Store with the name |
| Expected result | 409 `Store name already exists`. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F01-030

**Store slug must be unique**

| Field | Value |
|---|---|
| Trace | UC34; BR-28 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | name QA Dup Slug, slug foodmart-benchmark |
| Steps | 1. Create a Store with the existing slug |
| Expected result | 409 `Store slug already exists`. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F01-031

**Slug format and length boundaries**

| Field | Value |
|---|---|
| Trace | UC34; FR-01.6 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | Slugs: `a1-b2` (valid); 50 chars `a`×50 (valid); 51 chars; `Bad Slug`; `a--b`; `-a`; `a-`; `ABC` |
| Steps | 1. Create or edit a Store with each slug |
| Expected result | `a1-b2` and the 50-char slug are accepted. 51 chars, spaces, double hyphen, leading or trailing hyphen and upper case are refused with 400. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F01-032

**Store field length and phone character boundaries**

| Field | Value |
|---|---|
| Trace | UC34; FR-01.6 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | name 150/151 chars; address 255/256; phone 30/31 chars; phone `abc123` |
| Steps | 1. Create Stores with each value |
| Expected result | 150, 255 and 30 characters are accepted; 151, 256, 31 characters and a phone with letters are refused with 400. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F01-033

**Store list filters and search**

| Field | Value |
|---|---|
| Trace | UC34; UC33 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); Store C INACTIVE (DP-07) |
| Test data | q = `qa`, `QA-STORE`; status ACTIVE (default), INACTIVE, ALL |
| Steps | 1. Open /admin/stores<br>2. Search and switch the status filter |
| Expected result | Default shows ACTIVE Stores only. INACTIVE shows Store C only; ALL shows both. Search matches name or slug ignoring case. |
| Priority / type | Medium / functional |
| Status | NOT RUN |

### TC-F01-034

**Store edit with a stale version returns 409**

| Field | Value |
|---|---|
| Trace | UC34; FR-01.7; Common 6 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); Store B open in two tabs |
| Test data | Tab 1 renames to QA Store B1; tab 2 renames to QA Store B2 |
| Steps | 1. Save in tab 1<br>2. Save in tab 2 without reloading |
| Expected result | Tab 1: 200, version +1. Tab 2: 409 `Store version conflict`; nothing overwritten. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F01-035

**Store detail lists assigned users read-only and allows slug edit**

| Field | Value |
|---|---|
| Trace | UC34; FR-01.7 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | Store A |
| Steps | 1. Open Store A detail<br>2. Change slug to foodmart-bench-qa and save<br>3. Revert the slug |
| Expected result | assignedUserCount and the user list (name, email, role, status) are shown without edit controls. Slug edit succeeds; the F09 storefront then answers on the new slug only. |
| Priority / type | Medium / functional |
| Status | NOT RUN |

### TC-F01-036

**Stores cannot be deleted**

| Field | Value |
|---|---|
| Trace | UC34; BR-29 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | Store B |
| Steps | 1. Check that the Store list and detail have no Delete action<br>2. [API] DELETE /api/system/stores/{storeB} |
| Expected result | No Delete control. The API answers 404 or 405 and the Store remains. |
| Priority / type | Medium / negative |
| Status | NOT RUN |

### TC-F01-037

**Store state ACTIVE → INACTIVE → ACTIVE**

| Field | Value |
|---|---|
| Trace | UC34; FR-01.9; BR-30; R2-06.07 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); DP-07 Store C ACTIVE |
| Test data | Store C |
| Steps | 1. Deactivate Store C (confirm dialog)<br>2. Open detail<br>3. Reactivate Store C |
| Expected result | After deactivate: status INACTIVE, deactivatedAt and deactivatedBy set, version +1. After reactivate: ACTIVE, both fields cleared, version +1. |
| Priority / type | High / state transition |
| Status | NOT RUN |

### TC-F01-038

**Repeating a status change is refused**

| Field | Value |
|---|---|
| Trace | UC34; BR-30 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | Store C INACTIVE; Store A ACTIVE |
| Steps | 1. [API] PATCH status INACTIVE on Store C<br>2. [API] PATCH status ACTIVE on Store A |
| Expected result | 409 `Store is already INACTIVE` and 409 `Store is already ACTIVE`. |
| Priority / type | Medium / state transition |
| Status | NOT RUN |

### TC-F01-039

**Users of an INACTIVE Store are blocked everywhere**

| Field | Value |
|---|---|
| Trace | FR-01.9; BR-30; NFR 5.4 |
| Role | Store Admin, Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); ACC-STA-C and ACC-MER-C signed in; then Store C deactivated |
| Test data | ACC-STA-C, ACC-MER-C |
| Steps | 1. Reload any page as ACC-STA-C<br>2. Sign out and sign in again<br>3. Sign in with a wrong password<br>4. Try a silent refresh as ACC-MER-C<br>5. Reactivate Store C and refresh again |
| Expected result | Every authenticated request, sign-in and refresh returns 403 `{error:"Store is inactive", code:"STORE_INACTIVE"}` and the UI explains it. A wrong password still returns 401. After reactivation the kept refresh session works again. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F01-040

**No user assignment to an INACTIVE Store**

| Field | Value |
|---|---|
| Trace | FR-01.9; BR-30 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); Store C INACTIVE |
| Test data | Invite qa-mer-c2@paspm.example as MERCHANDISER of Store C; move ACC-MER-A to Store C |
| Steps | 1. Invite the user<br>2. Change ACC-MER-A's Store to Store C |
| Expected result | Both return 409 with code STORE_INACTIVE. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F01-041

**Store Admin edits own address and phone**

| Field | Value |
|---|---|
| Trace | UC35; FR-01.8 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | address 12 Nguyen Hue; phone 0901 234 567 |
| Steps | 1. Open My Store (/store/profile)<br>2. Edit address and phone and save<br>3. Clear the phone and save |
| Expected result | 200; values saved; version +1; a blank phone is stored as null. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F01-042

**Own profile cannot change name, slug or status; Merchandiser is read-only**

| Field | Value |
|---|---|
| Trace | UC35; FR-01.8; BR-28 |
| Role | Store Admin, Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | {"expectedVersion":n,"address":"x","phone":"1","slug":"hack"} |
| Steps | 1. Store Admin: check name and slug are read-only on /store/profile<br>2. [API] PUT /api/store/profile with slug as Store Admin<br>3. Merchandiser: open /store/profile<br>4. [API] PUT /api/store/profile as Merchandiser |
| Expected result | Name and slug are not editable. PUT with slug returns 400 `Invalid request fields`. Merchandiser sees the profile read-only; PUT returns 403. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F01-043

**Upload icon and banner with preview and public URL**

| Field | Value |
|---|---|
| Trace | UC35; FR-01.10; BR-31 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | icon.png 256×256 (50 KiB); banner.jpg 1600×400 (800 KiB) |
| Steps | 1. On /store/profile upload the icon<br>2. Upload the banner<br>3. Open iconUrl in a private window (no login) |
| Expected result | Both uploads return the Store with iconUrl/bannerUrl `/api/public/stores/{id}/images/{uuid}.png\|jpg`; version +1 each. The public URL serves the image without login with `X-Content-Type-Options: nosniff`. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F01-044

**Image size limits (icon 1 MiB, banner 5 MiB)**

| Field | Value |
|---|---|
| Trace | FR-01.10; BR-31; NFR 5.2 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | PNG icon of exactly 1,048,576 bytes; icon of 1,048,577 bytes; JPEG banner of exactly 5,242,880 bytes; banner of 5,242,881 bytes |
| Steps | 1. Upload each file to the matching slot |
| Expected result | Files at the limit are accepted. One byte over returns 413 `The icon must be at most 1 MiB` / `The banner must be at most 5 MiB`. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F01-045

**Image type is checked by content**

| Field | Value |
|---|---|
| Trace | FR-01.10; BR-31 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | GIF; WebP; SVG; text file renamed icon.png; truncated PNG; valid JPEG renamed icon.png |
| Steps | 1. Upload each file as the icon |
| Expected result | GIF, WebP and SVG: 400 `Only PNG or JPEG images are accepted`. Fake and truncated PNG: 400 `Image content is invalid` (or the type message). The JPEG renamed .png is accepted and stored as .jpg. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F01-046

**Image dimension limit 4000×4000**

| Field | Value |
|---|---|
| Trace | FR-01.10; BR-31 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | PNG 4000×4000 (under 5 MiB); PNG 4001×100 |
| Steps | 1. Upload each as the banner |
| Expected result | 4000×4000 accepted; 4001×100 refused with 400 `Image dimensions must be at most 4000×4000 pixels`. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F01-047

**Store image RBAC and cross-Store access**

| Field | Value |
|---|---|
| Trace | FR-01.10; BR-01; NFR 5.4 |
| Role | Merchandiser, Store Admin, System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); SETUP-B |
| Test data | Store A and Store B images |
| Steps | 1. [API] Merchandiser A PUT icon of Store A<br>2. [API] Store Admin B PUT and GET icon of Store A<br>3. [API] System Admin PUT banner of Store B<br>4. [API] End Customer GET /api/stores/{A}/images/icon |
| Expected result | Merchandiser: 403. Store Admin B on Store A: 404. System Admin: 200. End Customer: 403. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F01-048

**Public image only for current files of ACTIVE Stores**

| Field | Value |
|---|---|
| Trace | FR-01.10; BR-31 |
| Role | Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); Store C has an icon; then the icon is replaced; then Store C is deactivated |
| Test data | old and new icon URLs of Store C |
| Steps | 1. Open the old icon URL after replacement<br>2. Open the new URL<br>3. Deactivate Store C and open the new URL again |
| Expected result | Old file name: 404. Current file: 200 with `Cache-Control: public, max-age=31536000, immutable`. After deactivation: 404. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F01-049

**Image change bumps the Store version**

| Field | Value |
|---|---|
| Trace | FR-01.10; Common 6 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); /store/profile open with version v |
| Test data | new banner |
| Steps | 1. Upload a banner in another tab<br>2. Save address in the first tab with version v |
| Expected result | Profile save returns 409 `Store version conflict`; after reload the save works. |
| Priority / type | Medium / negative |
| Status | NOT RUN |

### TC-F01-050

**Remove an image**

| Field | Value |
|---|---|
| Trace | FR-01.10 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); Store A has an icon |
| Test data | Store A icon |
| Steps | 1. Remove the icon on /store/profile |
| Expected result | iconUrl becomes null and a placeholder is shown; the old public URL returns 404. |
| Priority / type | Low / functional |
| Status | NOT RUN |

### TC-F01-051

**Screens work from 320 to 1440 px wide**

| Field | Value |
|---|---|
| Trace | NFR 5.1; NFR 5.5 |
| Role | Store Admin, Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | viewport widths 320, 768, 1440 px |
| Steps | 1. Open /login, /store/inventory, /store/batches, /f03/configuration and /store/layout at each width |
| Expected result | No horizontal page overflow or hidden primary action; tables scroll inside their container; validation errors stay next to their field; empty lists show an explicit empty state. |
| Priority / type | Low / UI |
| Status | NOT RUN |

## F02

**Catalogue and inventory** — 37 cases.

| ID | Title | Priority | Type |
|---|---|---|---|
| [TC-F02-001](#tc-f02-001) | Create, edit and deactivate a category | High | functional |
| [TC-F02-002](#tc-f02-002) | Category name boundaries | Low | boundary |
| [TC-F02-003](#tc-f02-003) | Create a product: SKU generated by the backend | High | functional |
| [TC-F02-004](#tc-f02-004) | Removed and server-owned fields are rejected | High | negative |
| [TC-F02-005](#tc-f02-005) | Similar product name needs confirmation | High | functional |
| [TC-F02-006](#tc-f02-006) | Similar-name boundaries (length 4 and one-character edit) | High | boundary |
| [TC-F02-007](#tc-f02-007) | Renaming to a similar name also needs confirmation | Medium | functional |
| [TC-F02-008](#tc-f02-008) | Product field boundaries | Medium | boundary |
| [TC-F02-009](#tc-f02-009) | VND prices: stored with 2 decimals, displayed as whole đồng HALF_UP | Medium | boundary |
| [TC-F02-010](#tc-f02-010) | Product search, filter, sort and paging | Medium | boundary |
| [TC-F02-011](#tc-f02-011) | Category of another Store cannot be used | Medium | security |
| [TC-F02-012](#tc-f02-012) | Deactivate and reactivate a product | Medium | state transition |
| [TC-F02-013](#tc-f02-013) | Cross-Store product, category and batch access returns 404 | High | security |
| [TC-F02-014](#tc-f02-014) | F02 RBAC (decision table) | High | security |
| [TC-F02-015](#tc-f02-015) | Receive a batch: generated batch code | High | functional |
| [TC-F02-016](#tc-f02-016) | Batch code date uses Asia/Ho_Chi_Minh and the sequence continues per product | Medium | boundary |
| [TC-F02-017](#tc-f02-017) | Client batchCode is rejected | Medium | negative |
| [TC-F02-018](#tc-f02-018) | Expiry date required only for requiresExpiry products | High | negative |
| [TC-F02-019](#tc-f02-019) | Quantity boundaries | High | boundary |
| [TC-F02-020](#tc-f02-020) | Push day boundaries and no default | High | boundary |
| [TC-F02-021](#tc-f02-021) | MISSING_PUSH_DAY warning (decision table) | High | decision table |
| [TC-F02-022](#tc-f02-022) | Expiry boundary: the expiry date is still active | High | boundary |
| [TC-F02-023](#tc-f02-023) | Expiry follows the Store time zone around midnight | Medium | boundary |
| [TC-F02-024](#tc-f02-024) | EXPIRED wins over BLOCKED | High | boundary |
| [TC-F02-025](#tc-f02-025) | Batch state transitions | High | state transition |
| [TC-F02-026](#tc-f02-026) | Inventory summary buckets and the expiring-soon window | Medium | boundary |
| [TC-F02-027](#tc-f02-027) | Clear a push day | Medium | functional |
| [TC-F02-028](#tc-f02-028) | Set and clear the expiry date | Medium | functional |
| [TC-F02-029](#tc-f02-029) | Turning on requiresExpiry with an undated batch is refused | Medium | negative |
| [TC-F02-030](#tc-f02-030) | Stock is derived from batches | Medium | functional |
| [TC-F02-031](#tc-f02-031) | Soft delete a product | High | functional |
| [TC-F02-032](#tc-f02-032) | Delete refused while sellable stock exists (expiry today counts) | High | boundary |
| [TC-F02-033](#tc-f02-033) | Delete refused while the product is in a live campaign (decision table) | High | decision table |
| [TC-F02-034](#tc-f02-034) | Both refusal reasons: sellable stock wins | Medium | decision table |
| [TC-F02-035](#tc-f02-035) | Effects of a soft delete on other features | Medium | functional |
| [TC-F02-036](#tc-f02-036) | Delete RBAC and repeat | High | security |
| [TC-F02-037](#tc-f02-037) | Product image URL and description are kept by full update | Low | functional |

### TC-F02-001

**Create, edit and deactivate a category**

| Field | Value |
|---|---|
| Trace | UC18; FR-02.1 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | category QA Dairy |
| Steps | 1. Open /store/categories → Add QA Dairy<br>2. Rename it to QA Dairy & Eggs<br>3. Set it INACTIVE |
| Expected result | Created (201), renamed (200) and deactivated (status INACTIVE). There is no delete action. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F02-002

**Category name boundaries**

| Field | Value |
|---|---|
| Trace | UC18; FR-02.1 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | blank name; 150 chars; 151 chars; status `DELETED` |
| Steps | 1. Create a category with each value |
| Expected result | 150 chars accepted; blank, 151 chars and status DELETED refused with 400. |
| Priority / type | Low / boundary |
| Status | NOT RUN |

### TC-F02-003

**Create a product: SKU generated by the backend**

| Field | Value |
|---|---|
| Trace | UC17; FR-02.1; DEC-20261119-07 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | QA Milk 1L, unit bottle, requiresExpiry true, selling 32000, cost 21000, category QA Dairy |
| Steps | 1. Open /store/inventory → Add product<br>2. Fill the form (no SKU field) and save<br>3. Open the product |
| Expected result | 201; SKU is `SKU-` + 11-digit product ID (e.g. SKU-00000001560), read-only; stockQuantity 0. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F02-004

**Removed and server-owned fields are rejected**

| Field | Value |
|---|---|
| Trace | FR-02.1; FR-02.4; NFR 5.4 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | Product bodies each with one extra field: sku, expiryPushDays (null), stockPriorityScore, seasonalPriorityScore, stockQuantity, storeId |
| Steps | 1. [API] POST /api/products with each body |
| Expected result | Each returns 400 `Invalid request fields`; nothing is written. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F02-005

**Similar product name needs confirmation**

| Field | Value |
|---|---|
| Trace | FR-02.1; DEC-20261119-07 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); QA Milk 1L exists |
| Test data | name `qa milk 1l ` (case and spaces) |
| Steps | 1. Create the product<br>2. Confirm in the dialog |
| Expected result | First save: 409 code PRODUCT_NAME_CONFIRMATION_REQUIRED, nothing created, a confirmation dialog appears. Confirm (retry with confirmSimilarName:true): 201. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F02-006

**Similar-name boundaries (length 4 and one-character edit)**

| Field | Value |
|---|---|
| Trace | FR-02.1; DEC-20261119-07 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); products Milo, Tea exist |
| Test data | Milk (1 edit, 4 chars); Mile (1 edit) ; Mint (2 edits); Ten (1 edit, 3 chars); TEA (exact, 3 chars) |
| Steps | 1. Create each product without confirmation |
| Expected result | Milk, Mile and TEA return 409 PRODUCT_NAME_CONFIRMATION_REQUIRED. Mint (two edits) and Ten (3-char name, one edit) are created directly. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F02-007

**Renaming to a similar name also needs confirmation**

| Field | Value |
|---|---|
| Trace | FR-02.1 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); QA Milk 1L and QA Rice 5kg exist |
| Test data | rename QA Rice 5kg → QA Milk 1L |
| Steps | 1. Edit and save<br>2. Confirm |
| Expected result | 409 then 200 after confirmation; SKU unchanged. |
| Priority / type | Medium / functional |
| Status | NOT RUN |

### TC-F02-008

**Product field boundaries**

| Field | Value |
|---|---|
| Trace | UC17; FR-02.1; FR-09.4 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | name 180/181; unit 40/41; sellingPrice -0.01/0; costPrice 0; description 2000/2001; imageUrl https://… (2048 chars)/2049 chars; imageUrl ftp://x/a.png; imageUrl /img/a.png |
| Steps | 1. Create products with each value |
| Expected result | Limits accepted (180, 40, 0, 2000, 2048, https). Over-limit values, negative price, ftp and relative URLs are refused with 400 and a field error. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F02-009

**VND prices: stored with 2 decimals, displayed as whole đồng HALF_UP**

| Field | Value |
|---|---|
| Trace | UC19; FR-02.1; Common 6; DEC-20261126-02 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); QA Milk 1L |
| Test data | selling 32500, cost 21000.50; then cost 21000.49 |
| Steps | 1. Edit prices and save<br>2. View the product list and detail<br>3. [API] GET /api/products/{id}<br>4. Change the cost to 21000.49 and view again |
| Expected result | API and storage keep 32500.00 and 21000.50 unchanged. The UI shows `32.500 ₫` and `21.001 ₫` (HALF_UP). After the change the API returns 21000.49 and the UI shows `21.000 ₫`. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F02-010

**Product search, filter, sort and paging**

| Field | Value |
|---|---|
| Trace | UC17; NFR 5.2 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) (1,559 seeded products) |
| Test data | search `item 000`; search by a full SKU; category filter; status INACTIVE; sort by each supported column asc/desc; pageSize 1, 100, 0, 101 |
| Steps | 1. Use the search box, filters, column sort and page size on /store/inventory<br>2. [API] GET /api/products?pageSize=0 and 101 |
| Expected result | Search matches name or SKU ignoring case. Sorting is stable (ties by productId). pageSize 1 and 100 work; 0 and 101 return 400. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F02-011

**Category of another Store cannot be used**

| Field | Value |
|---|---|
| Trace | FR-02.1; BR-01 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); SETUP-B |
| Test data | categoryId of a Store B category |
| Steps | 1. [API] POST /api/products as Store Admin A with that categoryId |
| Expected result | 404; no product created. |
| Priority / type | Medium / security |
| Status | NOT RUN |

### TC-F02-012

**Deactivate and reactivate a product**

| Field | Value |
|---|---|
| Trace | UC17; FR-02.1 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); QA Rice 5kg ACTIVE |
| Test data | QA Rice 5kg |
| Steps | 1. Set INACTIVE<br>2. Set ACTIVE |
| Expected result | Status changes each time; SKU and batches unchanged. |
| Priority / type | Medium / state transition |
| Status | NOT RUN |

### TC-F02-013

**Cross-Store product, category and batch access returns 404**

| Field | Value |
|---|---|
| Trace | BR-01; NFR 5.4 |
| Role | Store Admin, Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); SETUP-B |
| Test data | IDs of a Store A product, category and batch |
| Steps | 1. Sign in as Store Admin B and open /store/edit-product?id={A product}<br>2. [API] GET, PUT, PATCH, DELETE the Store A product, category and batch as Store Admin B<br>3. [API] GET the Store A product as Merchandiser B |
| Expected result | Every call returns 404 (not 403); the UI shows not found; no data from Store A is revealed. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F02-014

**F02 RBAC (decision table)**

| Field | Value |
|---|---|
| Trace | UC17–UC20; NFR 5.4 |
| Role | Merchandiser, System Admin, End Customer, Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | Store A product, category, batch |
| Steps | 1. Merchandiser: check product and category pages are read-only and Batches is not in the menu<br>2. [API] Merchandiser: POST/PUT/PATCH/DELETE products and categories, all batch routes, inventory summary<br>3. [API] System Admin and End Customer: GET /api/products<br>4. [API] No token: GET /api/products |
| Expected result | Merchandiser may only GET products and categories; every write, batch and summary route returns 403. System Admin and End Customer get 403. No token gets 401. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F02-015

**Receive a batch: generated batch code**

| Field | Value |
|---|---|
| Trace | UC20; FR-02.2 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); QA Milk 1L (requiresExpiry) |
| Test data | receivedAt T 09:00+07:00; initialQuantity 50; expiryDate T+10; pushDay T+5 |
| Steps | 1. Open /store/batches for QA Milk 1L → Receive batch<br>2. Save |
| Expected result | 201; batchCode `BATCH-<T as YYYYMMDD>-001`; remaining 50; status and effectiveStatus ACTIVE; daysToExpiry 10; warnings []. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F02-016

**Batch code date uses Asia/Ho_Chi_Minh and the sequence continues per product**

| Field | Value |
|---|---|
| Trace | FR-02.2 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); QA Milk 1L has batch 001 |
| Test data | receivedAt 2026-11-25T18:30:00Z (= 2026-11-26 01:30 local); then receivedAt 2026-11-27T02:00:00Z |
| Steps | 1. Receive both batches |
| Expected result | Codes BATCH-20261126-002 and BATCH-20261127-003: the date is the Store-local date and the sequence does not restart per day. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F02-017

**Client batchCode is rejected**

| Field | Value |
|---|---|
| Trace | FR-02.2 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | body with "batchCode":"BATCH-20261126-999" |
| Steps | 1. [API] POST /api/products/{id}/batches with batchCode |
| Expected result | 400 `Invalid request fields`. |
| Priority / type | Medium / negative |
| Status | NOT RUN |

### TC-F02-018

**Expiry date required only for requiresExpiry products**

| Field | Value |
|---|---|
| Trace | FR-02.2 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); QA Milk 1L (requiresExpiry), QA Rice 5kg (no expiry) |
| Test data | batches without expiryDate |
| Steps | 1. Receive a batch without expiry for each product |
| Expected result | QA Milk 1L: 400. QA Rice 5kg: 201 with expiryDate null and daysToExpiry null. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F02-019

**Quantity boundaries**

| Field | Value |
|---|---|
| Trace | FR-02.2 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); a batch with initialQuantity 50 |
| Test data | initialQuantity 0 and 1; PATCH remaining -1, 0, 50, 51 |
| Steps | 1. Receive batches with initial 0 and 1<br>2. Edit remaining to each value |
| Expected result | initial 0 refused (400), 1 accepted. remaining 0 and 50 accepted; -1 and 51 refused (400 `Remaining quantity cannot exceed initial quantity` for 51). |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F02-020

**Push day boundaries and no default**

| Field | Value |
|---|---|
| Trace | FR-02.2; BR-14 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); QA Milk 1L |
| Test data | expiry T+10 with pushDay T+10; pushDay T+11; pushDay empty |
| Steps | 1. Receive a batch with each push day |
| Expected result | pushDay = expiryDate accepted. pushDay after expiry refused (400). Empty push day stays null (no expiry−7 default) and the batch shows the MISSING_PUSH_DAY warning. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F02-021

**MISSING_PUSH_DAY warning (decision table)**

| Field | Value |
|---|---|
| Trace | FR-02.2; BR-14; DEC-20261124-02 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); batches prepared per row |
| Test data | Rows: (1) requiresExpiry, no push day, expiry T+5, ACTIVE; (2) same but BLOCKED; (3) same but remaining 0; (4) requiresExpiry, no push day, expiry T-1; (5) requiresExpiry, push day set; (6) requiresExpiry=false, no push day |
| Steps | 1. Open each batch in /store/batches<br>2. Open the inventory summary of each product |
| Expected result | Warning shown for rows 1, 2 and 3 only (stored status and quantity do not matter); not for the expired row 4, row 5 or row 6. batchesMissingPushDay counts the same batches; it is 0 for row 6's product. |
| Priority / type | High / decision table |
| Status | NOT RUN |

### TC-F02-022

**Expiry boundary: the expiry date is still active**

| Field | Value |
|---|---|
| Trace | FR-02.2; BR-32; DEC-20261124-01 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); three ACTIVE batches with remaining > 0 |
| Test data | expiryDate T-1, T, T+1 (T = today in Asia/Ho_Chi_Minh) |
| Steps | 1. Open the batch list<br>2. Open the inventory summary |
| Expected result | T-1: effectiveStatus EXPIRED, daysToExpiry -1, no MISSING_PUSH_DAY. T: ACTIVE, daysToExpiry 0, counted in sellableQuantity and expiringSoonQuantity. T+1: ACTIVE, 1. Stored status of all three stays ACTIVE. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F02-023

**Expiry follows the Store time zone around midnight**

| Field | Value |
|---|---|
| Trace | BR-32; Common 6 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); run between 00:00 and 07:00 Asia/Ho_Chi_Minh (UTC date is still the previous day) |
| Test data | batch with expiryDate = previous local date |
| Steps | 1. Open the batch |
| Expected result | effectiveStatus EXPIRED and daysToExpiry -1, computed on the local date, not UTC. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F02-024

**EXPIRED wins over BLOCKED**

| Field | Value |
|---|---|
| Trace | FR-02.2; BR-32 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | BLOCKED batch, expiry T-1, remaining 10; BLOCKED batch, expiry T+3, remaining 5 |
| Steps | 1. Open the batches and the inventory summary |
| Expected result | First: effectiveStatus EXPIRED, counted in expiredQuantity (10) not blockedQuantity. Second: BLOCKED, blockedQuantity 5. Buckets do not overlap. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F02-025

**Batch state transitions**

| Field | Value |
|---|---|
| Trace | FR-02.2; R2 batch state |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); ACTIVE batch; expired batch |
| Test data | PATCH status BLOCKED, ACTIVE, EXPIRED, INACTIVE |
| Steps | 1. Block the batch<br>2. Unblock it<br>3. [API] PATCH status EXPIRED and INACTIVE<br>4. Edit remaining and push day of the expired batch |
| Expected result | ACTIVE ⇄ BLOCKED works. EXPIRED and INACTIVE are not writable (400). The expired batch can still be edited (200) and stays EXPIRED. |
| Priority / type | High / state transition |
| Status | NOT RUN |

### TC-F02-026

**Inventory summary buckets and the expiring-soon window**

| Field | Value |
|---|---|
| Trace | FR-02.2; FR-02.3 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); batches expiring T+7 and T+8 (ACTIVE) |
| Test data | expiringWithinDays 7 (default), 0, -1 |
| Steps | 1. Open the inventory summary<br>2. [API] GET …/inventory-summary?expiringWithinDays=0 and -1 |
| Expected result | Default: T+7 counted in expiringSoonQuantity, T+8 not. 0 counts only today's expiry. -1 returns 400. totalQuantity includes blocked and expired units. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F02-027

**Clear a push day**

| Field | Value |
|---|---|
| Trace | UC20; FR-02.2 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); requiresExpiry batch with push day |
| Test data | batch |
| Steps | 1. Click Clear push day<br>2. Click it again |
| Expected result | pushDay null and the MISSING_PUSH_DAY warning appears; the second clear also returns 200 without change. |
| Priority / type | Medium / functional |
| Status | NOT RUN |

### TC-F02-028

**Set and clear the expiry date**

| Field | Value |
|---|---|
| Trace | FR-02.2 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); QA Rice 5kg batch (no expiry); QA Milk 1L batch |
| Test data | expiryDate T+30 |
| Steps | 1. Set the expiry date of the QA Rice batch<br>2. Save the batch with expiryDate omitted<br>3. Clear the expiry date of the QA Rice batch<br>4. Clear the expiry date of the QA Milk batch |
| Expected result | Set: 200. Omitted: unchanged. Clear on QA Rice: 200, null. Clear on QA Milk (requiresExpiry): 400. |
| Priority / type | Medium / functional |
| Status | NOT RUN |

### TC-F02-029

**Turning on requiresExpiry with an undated batch is refused**

| Field | Value |
|---|---|
| Trace | FR-02.2 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); QA Rice 5kg with an undated batch |
| Test data | requiresExpiry true |
| Steps | 1. Edit QA Rice 5kg and tick Requires expiry |
| Expected result | 400; product unchanged. |
| Priority / type | Medium / negative |
| Status | NOT RUN |

### TC-F02-030

**Stock is derived from batches**

| Field | Value |
|---|---|
| Trace | FR-02.3; BR-03 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); QA Milk 1L with batches 50 and 30 |
| Test data | PATCH remaining 50 → 20 |
| Steps | 1. Check stock in the product list<br>2. Edit the batch remaining<br>3. Check stock again |
| Expected result | Stock shows 80, then 50. The product form has no editable stock field. |
| Priority / type | Medium / functional |
| Status | NOT RUN |

### TC-F02-031

**Soft delete a product**

| Field | Value |
|---|---|
| Trace | FR-02.5; BR-33; DEC-20261124-03 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); QA Delete Me with only an expired batch and a BLOCKED batch, in no campaign |
| Test data | QA Delete Me |
| Steps | 1. Note the product list totalElements<br>2. Delete the product (confirm dialog)<br>3. Reload the list and open the product URL<br>4. Create a new product |
| Expected result | 204. The product disappears; totalElements and totalPages drop by one (excluded before pagination); detail returns 404; there is no restore action; the new product gets a new SKU (the deleted SKU is never reused). |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F02-032

**Delete refused while sellable stock exists (expiry today counts)**

| Field | Value |
|---|---|
| Trace | FR-02.5; BR-33; BR-32 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | product whose only units are in an ACTIVE batch expiring today (remaining 7) |
| Steps | 1. Delete the product |
| Expected result | 409 code PRODUCT_HAS_SELLABLE_STOCK; the dialog shows the reason and sellableQuantity 7; the product stays. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F02-033

**Delete refused while the product is in a live campaign (decision table)**

| Field | Value |
|---|---|
| Trace | FR-02.5; BR-33 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); DP-06; products with no sellable stock placed in campaigns of status DRAFT, PUBLISHED, ACTIVE, ENDED (layout A or B) |
| Test data | one product per campaign status |
| Steps | 1. Delete each product |
| Expected result | PUBLISHED and ACTIVE: 409 PRODUCT_IN_LIVE_CAMPAIGN with campaigns [{campaignId,status}] and sellableQuantity 0. DRAFT and ENDED: 204. |
| Priority / type | High / decision table |
| Status | NOT RUN |

### TC-F02-034

**Both refusal reasons: sellable stock wins**

| Field | Value |
|---|---|
| Trace | FR-02.5 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | product with sellable units and in an ACTIVE campaign |
| Steps | 1. Delete the product |
| Expected result | 409 code PRODUCT_HAS_SELLABLE_STOCK; the campaigns list includes the ACTIVE campaign. |
| Priority / type | Medium / decision table |
| Status | NOT RUN |

### TC-F02-035

**Effects of a soft delete on other features**

| Field | Value |
|---|---|
| Trace | FR-02.5; BR-33 |
| Role | Store Admin, Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); deleted product QA Delete Me assigned to a season before deletion |
| Test data | QA Delete Me SKU |
| Steps | 1. [API] PATCH status of the deleted product<br>2. Create a product named QA Delete Me<br>3. Merchandiser: open the season's products<br>4. Merchandiser: assign the deleted product to another season<br>5. Import a CSV row with its SKU |
| Expected result | PATCH: 404. Name check ignores the deleted product (created without 409). Season list shows it with deleted:true. New assignment: 409 PRODUCT_DELETED. Import: row accepted with a PRODUCT_DELETED warning. |
| Priority / type | Medium / functional |
| Status | NOT RUN |

### TC-F02-036

**Delete RBAC and repeat**

| Field | Value |
|---|---|
| Trace | FR-02.5; NFR 5.4 |
| Role | Merchandiser, Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); SETUP-B |
| Test data | Store A product |
| Steps | 1. [API] DELETE as Merchandiser A<br>2. [API] DELETE as Store Admin B<br>3. Delete as Store Admin A twice |
| Expected result | Merchandiser 403; Store Admin B 404; first delete 204, second 404. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F02-037

**Product image URL and description are kept by full update**

| Field | Value |
|---|---|
| Trace | FR-09.4 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); product with imageUrl and description |
| Test data | edit only the price in a client that omits the two fields |
| Steps | 1. Edit the price on /store/edit-product and save<br>2. [API] PUT without imageUrl/description |
| Expected result | UI save keeps both fields. The API PUT without them clears them (full replacement), as documented. |
| Priority / type | Low / functional |
| Status | NOT RUN |

## F03

**Utility profiling (Weight v2)** — 29 cases.

| ID | Title | Priority | Type |
|---|---|---|---|
| [TC-F03-001](#tc-f03-001) | System Admin sets λmax | High | functional |
| [TC-F03-002](#tc-f03-002) | λmax policy validation | Medium | negative |
| [TC-F03-003](#tc-f03-003) | Merchandiser saves α, β, λ and N | High | functional |
| [TC-F03-004](#tc-f03-004) | α + β must equal 1 | High | boundary |
| [TC-F03-005](#tc-f03-005) | λ boundaries against λmax | High | boundary |
| [TC-F03-006](#tc-f03-006) | Sales window N boundaries | Medium | boundary |
| [TC-F03-007](#tc-f03-007) | Removed configuration fields are rejected | Medium | negative |
| [TC-F03-008](#tc-f03-008) | F03 RBAC (decision table) | High | security |
| [TC-F03-009](#tc-f03-009) | Tightening λmax below the stored λ blocks new snapshots | High | state transition |
| [TC-F03-010](#tc-f03-010) | Create and edit a season with optimistic version | High | functional |
| [TC-F03-011](#tc-f03-011) | Season validation boundaries | Medium | boundary |
| [TC-F03-012](#tc-f03-012) | A season with products cannot be deleted | High | negative |
| [TC-F03-013](#tc-f03-013) | Assign and unassign products | Medium | functional |
| [TC-F03-014](#tc-f03-014) | Season active window is inclusive | High | boundary |
| [TC-F03-015](#tc-f03-015) | Overlapping seasons: highest weight, tie lowest seasonId | Medium | decision table |
| [TC-F03-016](#tc-f03-016) | Weight v2 golden values and rounding | High | functional |
| [TC-F03-017](#tc-f03-017) | W rounds HALF_UP at exactly .xx5 | High | boundary |
| [TC-F03-018](#tc-f03-018) | Missing push day only warns and is excluded | High | functional |
| [TC-F03-019](#tc-f03-019) | No recent sales: SLOW_MOVING, both warnings together | Medium | functional |
| [TC-F03-020](#tc-f03-020) | Sales window includes the analysis date | Medium | boundary |
| [TC-F03-021](#tc-f03-021) | A batch expiring on the analysis date is eligible | Medium | boundary |
| [TC-F03-022](#tc-f03-022) | Preview the matrix | High | functional |
| [TC-F03-023](#tc-f03-023) | Create an immutable snapshot | High | functional |
| [TC-F03-024](#tc-f03-024) | Only COMPLETED lines with price ≥ cost are used | High | boundary |
| [TC-F03-025](#tc-f03-025) | Snapshot with no eligible data is refused | Medium | negative |
| [TC-F03-026](#tc-f03-026) | Another Store's snapshot returns 404 | High | security |
| [TC-F03-027](#tc-f03-027) | Due push batches list | Medium | boundary |
| [TC-F03-028](#tc-f03-028) | Removed F03 routes | Low | negative |
| [TC-F03-029](#tc-f03-029) | Old snapshots stay readable; unknown scoringVersion is refused | Low | negative |

### TC-F03-001

**System Admin sets λmax**

| Field | Value |
|---|---|
| Trace | UC32; BR-16 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | lambdaMax 0.5 → 0.6 |
| Steps | 1. Open /admin/lambda-policy<br>2. Set 0.6 and save |
| Expected result | 200; version +1; Store configurations are not changed. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F03-002

**λmax policy validation**

| Field | Value |
|---|---|
| Trace | UC32 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | lambdaMax -0.1; body with lambdaMin |
| Steps | 1. Save -0.1<br>2. [API] PUT with lambdaMin |
| Expected result | Both 400. |
| Priority / type | Medium / negative |
| Status | NOT RUN |

### TC-F03-003

**Merchandiser saves α, β, λ and N**

| Field | Value |
|---|---|
| Trace | UC09; FR-03.1; BR-16 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | α 0.6, β 0.4, λ 0.3, N 30 |
| Steps | 1. Open /f03/configuration<br>2. Enter values and save |
| Expected result | 200; config version +1; scoringVersion W2_EXPIRY_SEASON_V1; lambdaValid true. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F03-004

**α + β must equal 1**

| Field | Value |
|---|---|
| Trace | UC09; BR-05 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | (0.6, 0.39); (0.6, 0.41); (1, 0); (0, 1); (-0.1, 1.1) |
| Steps | 1. Save each pair |
| Expected result | (1,0) and (0,1) accepted; the others refused with 400. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F03-005

**λ boundaries against λmax**

| Field | Value |
|---|---|
| Trace | UC09; BR-05 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); λmax 0.5 |
| Test data | λ 0; 0.5; 0.500001; -0.1 |
| Steps | 1. Save each λ |
| Expected result | 0 and 0.5 accepted; 0.500001 and -0.1 refused with 400. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F03-006

**Sales window N boundaries**

| Field | Value |
|---|---|
| Trace | UC09; BR-16 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | N 1; 365; 0; 366; 30.5 |
| Steps | 1. Save each N |
| Expected result | 1 and 365 accepted; 0, 366 and 30.5 refused with 400. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F03-007

**Removed configuration fields are rejected**

| Field | Value |
|---|---|
| Trace | UC09; FR-02.4 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | bodies with gamma, enableStock, enableExpiry, enableSeason, expiryWindowDays |
| Steps | 1. [API] PUT /api/utility/config with each field |
| Expected result | 400 for each; configuration unchanged. |
| Priority / type | Medium / negative |
| Status | NOT RUN |

### TC-F03-008

**F03 RBAC (decision table)**

| Field | Value |
|---|---|
| Trace | UC09; NFR 5.4 |
| Role | Store Admin, System Admin, End Customer |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | config, seasons, preview, snapshots |
| Steps | 1. Store Admin: open /f03/configuration and /f03/seasons<br>2. [API] Store Admin: PUT config, POST season, POST preview, POST snapshot, GET snapshot<br>3. [API] System Admin and End Customer: GET /api/utility/config |
| Expected result | Store Admin sees config and seasons read-only; writes, preview and snapshot routes return 403. System Admin and End Customer get 403. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F03-009

**Tightening λmax below the stored λ blocks new snapshots**

| Field | Value |
|---|---|
| Trace | FR-03.5; R2-06.02 |
| Role | System Admin, Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); Store λ 0.3 |
| Test data | λmax 0.2 |
| Steps | 1. System Admin sets λmax 0.2<br>2. Merchandiser opens configuration<br>3. Merchandiser runs preview and creates a snapshot<br>4. Merchandiser sets λ 0.2 and retries |
| Expected result | Configuration shows lambdaValid false (λ not clamped). Preview/snapshot return 409. Product scores show ready false and weight null. After λ 0.2 the snapshot is created (201). |
| Priority / type | High / state transition |
| Status | NOT RUN |

### TC-F03-010

**Create and edit a season with optimistic version**

| Field | Value |
|---|---|
| Trace | UC09; FR-03.3 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | QA Tet 2027, 2027-01-20..2027-02-15, weight 0.8 |
| Steps | 1. Open /f03/seasons → Add<br>2. Edit weight to 0.9 in two tabs and save both |
| Expected result | Create 201. First edit 200 (version +1); second edit 409 (stale version). |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F03-011

**Season validation boundaries**

| Field | Value |
|---|---|
| Trace | FR-03.3; BR-15 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | weight 0, 1, -0.01, 1.01; start = end; start > end; duplicate name `qa tet 2027` |
| Steps | 1. Create seasons with each value |
| Expected result | weight 0 and 1 and start = end accepted. Out-of-range weight, start after end (400) and a duplicate name ignoring case (409) refused. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F03-012

**A season with products cannot be deleted**

| Field | Value |
|---|---|
| Trace | FR-03.3; BR-15 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); QA Tet 2027 with 2 products |
| Test data | QA Tet 2027 |
| Steps | 1. Delete the season<br>2. Unassign both products<br>3. Delete again |
| Expected result | First delete 409; after unassigning, 204. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F03-013

**Assign and unassign products**

| Field | Value |
|---|---|
| Trace | FR-03.3; BR-01 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); SETUP-B |
| Test data | QA Milk 1L; a Store B product |
| Steps | 1. Assign QA Milk 1L<br>2. Assign it again<br>3. Unassign it<br>4. Unassign it again<br>5. [API] Assign the Store B product |
| Expected result | 201, then 200 (already assigned), 204, then 404 (not assigned); Store B product 404. |
| Priority / type | Medium / functional |
| Status | NOT RUN |

### TC-F03-014

**Season active window is inclusive**

| Field | Value |
|---|---|
| Trace | FR-03.3; BR-15 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); QA product assigned to season 2026-10-02..2026-10-04, weight 0.7; no other season |
| Test data | preview asOfDate 2026-10-01, 10-02, 10-04, 10-05 |
| Steps | 1. Run preview with each asOfDate on /f03/preview |
| Expected result | seasonWeight M = 0.7 for 10-02 and 10-04; M = 0 for 10-01 and 10-05. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F03-015

**Overlapping seasons: highest weight, tie lowest seasonId**

| Field | Value |
|---|---|
| Trace | FR-03.3; BR-15 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | product in seasons S1 0.6, S2 0.8 (both active); then S2 set to 0.6 |
| Steps | 1. Preview<br>2. Change S2 to 0.6 and preview again |
| Expected result | First M = 0.8 with seasonId S2. After the tie M = 0.6 with the lower seasonId. |
| Priority / type | Medium / decision table |
| Status | NOT RUN |

### TC-F03-016

**Weight v2 golden values and rounding**

| Field | Value |
|---|---|
| Trace | FR-03.1; FR-03.2; BR-05 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); product P1: one batch 120 units, push day in 3 days, sales 25/day over N; season weight 0.7 |
| Test data | α 0.6, β 0.4, λ 0.3 |
| Steps | 1. Open P1's product scores<br>2. Preview the matrix |
| Expected result | U_expiry 0.375; W = 1 + 0.3 × (0.6 × 0.375 + 0.4 × 0.7) = 1.1515 → 1.15 (2 dp HALF_UP). WeightedUtility = BaseUtility × 1.15 at 6 dp. Second golden: 40 units at 2 days + 60 at 5 days, 15/day → U_expiry 0.25. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F03-017

**W rounds HALF_UP at exactly .xx5**

| Field | Value |
|---|---|
| Trace | FR-03.1; BR-05 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); product with no expiry, season weight 0.05 |
| Test data | α 0, β 1, λ 0.1 |
| Steps | 1. Preview |
| Expected result | W = 1 + 0.1 × 0.05 = 1.005 → 1.01 (HALF_UP, not banker's 1.00). |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F03-018

**Missing push day only warns and is excluded**

| Field | Value |
|---|---|
| Trace | FR-03.2; BR-14 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); product with two batches: one with push day, one without |
| Test data | seed product with MISSING_PUSH_DAY |
| Steps | 1. Preview and create a snapshot |
| Expected result | Warning MISSING_PUSH_DAY; the batch without push day is left out of Q_risk and Q_eligible; the snapshot is created (201). |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F03-019

**No recent sales: SLOW_MOVING, both warnings together**

| Field | Value |
|---|---|
| Trace | FR-03.2 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); product with zero sales in the window and a batch without push day |
| Test data | N 30 |
| Steps | 1. Open product scores |
| Expected result | U_expiry 0, expiryStatus SLOW_MOVING, warnings include both SLOW_MOVING_NO_RECENT_SALES and MISSING_PUSH_DAY. |
| Priority / type | Medium / functional |
| Status | NOT RUN |

### TC-F03-020

**Sales window includes the analysis date**

| Field | Value |
|---|---|
| Trace | FR-03.2; BR-16 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); product sold only on date D (10 units) |
| Test data | N 3; asOfDate D+2 and D+3 |
| Steps | 1. Preview with asOfDate D+2<br>2. Preview with asOfDate D+3 |
| Expected result | D+2: v = 10/3. D+3: v = 0 (D is outside the 3 days ending on D+3). |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F03-021

**A batch expiring on the analysis date is eligible**

| Field | Value |
|---|---|
| Trace | FR-03.2; BR-32 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | batch expiry = asOfDate with push day; batch expiry = asOfDate−1 |
| Steps | 1. Preview |
| Expected result | The first batch counts in Q_eligible; the second does not (expired). |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F03-022

**Preview the matrix**

| Field | Value |
|---|---|
| Trace | UC31; FR-03.4 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | rangeStart 2026-10-01, rangeEnd 2026-10-03, asOfDate 2026-10-03; pageSize 1, 100, 0, 101; rangeStart > rangeEnd |
| Steps | 1. Run preview on /f03/preview with each input |
| Expected result | Valid input: 200 with items, totals, sourceHash, matrixHash and warnings. pageSize 0/101 and an inverted range: 400. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F03-023

**Create an immutable snapshot**

| Field | Value |
|---|---|
| Trace | UC31; FR-03.4; BR-06 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | DP-02 inputs |
| Steps | 1. Create the snapshot on /f03/snapshot<br>2. Change α/β, a season weight and a batch push day<br>3. Reopen the snapshot detail and matrix |
| Expected result | 201 status READY. After the changes, detail, matrix, frozen seasons, hashes and totals are unchanged. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F03-024

**Only COMPLETED lines with price ≥ cost are used**

| Field | Value |
|---|---|
| Trace | FR-03.4; BR-04 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); QA import with COMPLETED, CANCELLED, REFUNDED, PARTIAL_REFUND orders and one COMPLETED line with price = cost and one with price < cost |
| Test data | range covering the QA import |
| Steps | 1. Preview the range |
| Expected result | Only COMPLETED lines with price ≥ cost appear; the price = cost line has base utility 0; the others are absent. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F03-025

**Snapshot with no eligible data is refused**

| Field | Value |
|---|---|
| Trace | FR-03.4 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | range 2025-01-01..2025-01-31 |
| Steps | 1. Create a snapshot |
| Expected result | 400 (empty matrix); no snapshot created. |
| Priority / type | Medium / negative |
| Status | NOT RUN |

### TC-F03-026

**Another Store's snapshot returns 404**

| Field | Value |
|---|---|
| Trace | FR-03.4; BR-01 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); SETUP-B; snapshot of Store A |
| Test data | snapshotId of Store A |
| Steps | 1. [API] GET /api/utility/snapshots/{id} and /matrix as Merchandiser B |
| Expected result | 404 for both. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F03-027

**Due push batches list**

| Field | Value |
|---|---|
| Trace | FR-06.5; FR-03.2 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | batches with pushDay T-1, T, T+1 and one without push day |
| Steps | 1. [API] GET /api/utility/expiry-push-batches |
| Expected result | T-1 and T are listed (ordered by pushDay, productId, batchId); T+1 is not; the batch without push day is only in warnings. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F03-028

**Removed F03 routes**

| Field | Value |
|---|---|
| Trace | UC09; DEC-20261116-03 |
| Role | Store Admin, Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | — |
| Steps | 1. [API] PUT /api/utility/config/expiry-window<br>2. [API] PATCH /api/utility/products/{id}/scores |
| Expected result | 404 and 405. No Expiry Window or Product Scores edit screen is reachable. |
| Priority / type | Low / negative |
| Status | NOT RUN |

### TC-F03-029

**Old snapshots stay readable; unknown scoringVersion is refused**

| Field | Value |
|---|---|
| Trace | FR-03.6; BR-17 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); a pre-V25 snapshot (if present) and a test-only row with scoringVersion FUTURE_UNKNOWN |
| Test data | both snapshots |
| Steps | 1. Open each snapshot |
| Expected result | The legacy snapshot reads unchanged. The unknown version returns 409 code UNSUPPORTED_SCORING_VERSION. |
| Priority / type | Low / negative |
| Status | NOT RUN |

## F04

**Transaction import** — 15 cases.

| ID | Title | Priority | Type |
|---|---|---|---|
| [TC-F04-001](#tc-f04-001) | Synchronous CSV import of a valid file | High | functional |
| [TC-F04-002](#tc-f04-002) | Wrong header is a structural failure | High | negative |
| [TC-F04-003](#tc-f04-003) | File size and row limits | High | boundary |
| [TC-F04-004](#tc-f04-004) | Row validation errors are reported per row | High | negative |
| [TC-F04-005](#tc-f04-005) | An order is committed atomically | High | functional |
| [TC-F04-006](#tc-f04-006) | Lines of one order must agree | Medium | negative |
| [TC-F04-007](#tc-f04-007) | Duplicate and conflicting orders | High | functional |
| [TC-F04-008](#tc-f04-008) | Async CSV import with progress | High | functional |
| [TC-F04-009](#tc-f04-009) | Idempotency-Key rules | High | boundary |
| [TC-F04-010](#tc-f04-010) | Excel import | High | functional |
| [TC-F04-011](#tc-f04-011) | CSV and Excel share order identity | Medium | functional |
| [TC-F04-012](#tc-f04-012) | Imports never change inventory | High | functional |
| [TC-F04-013](#tc-f04-013) | Non-CSV file is refused | Low | negative |
| [TC-F04-014](#tc-f04-014) | F04 RBAC and Store isolation | High | security |
| [TC-F04-015](#tc-f04-015) | Import and transaction lists paging | Low | boundary |

### TC-F04-001

**Synchronous CSV import of a valid file**

| Field | Value |
|---|---|
| Trace | UC21; FR-04.1 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | qa_valid.csv: 3 orders, 5 lines, exact header `source_order_id,occurred_at,sku,quantity,unit_price,unit_cost,status` |
| Steps | 1. Open /store/transactions/import<br>2. Upload the file (sync) |
| Expected result | 201 summary COMPLETED: totalCount 5, validCount 5, totalOrderCount 3, acceptedOrderCount 3. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F04-002

**Wrong header is a structural failure**

| Field | Value |
|---|---|
| Trace | UC21; FR-04.1 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | header with columns swapped; header missing status |
| Steps | 1. Upload each file |
| Expected result | 400 (or a FAILED batch) with a header error; no orders created. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F04-003

**File size and row limits**

| Field | Value |
|---|---|
| Trace | FR-04.1; NFR 5.2 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | file of exactly 5 MiB; 5 MiB + 1 byte; 10,000 non-blank rows; 10,001 rows; 10,000 rows plus blank lines |
| Steps | 1. Upload each file |
| Expected result | 5 MiB and 10,000 rows accepted (blank lines not counted). 5 MiB + 1: 413. 10,001 rows: 400. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F04-004

**Row validation errors are reported per row**

| Field | Value |
|---|---|
| Trace | UC30; FR-04.3; NFR 5.5 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); SETUP-B |
| Test data | rows: unknown SKU; Store B SKU; quantity 0; unit_price 1.234; unit_cost -1; occurred_at without offset; status SHIPPED; source_order_id of 151 chars |
| Steps | 1. Upload a file with one bad order per case plus 2 valid orders<br>2. Open the import errors |
| Expected result | 201 COMPLETED_WITH_ERRORS; valid orders accepted; each bad row listed with row number and field. The Store B SKU is treated as unknown. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F04-005

**An order is committed atomically**

| Field | Value |
|---|---|
| Trace | UC30; FR-04.3 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | order QA-ATOM-1 with 3 lines, the third with quantity 0 |
| Steps | 1. Upload the file<br>2. Open the transaction list |
| Expected result | The whole order is rejected; none of its 3 lines is stored. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F04-006

**Lines of one order must agree**

| Field | Value |
|---|---|
| Trace | UC30; FR-04.3 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | order with two occurred_at values; order with COMPLETED and CANCELLED lines; same SKU twice with different unit_price |
| Steps | 1. Upload the file |
| Expected result | Each order is rejected with a row error (e.g. `Repeated SKU lines must use the same price, cost and status`). Same SKU twice with equal price/cost/status is aggregated. |
| Priority / type | Medium / negative |
| Status | NOT RUN |

### TC-F04-007

**Duplicate and conflicting orders**

| Field | Value |
|---|---|
| Trace | FR-04.3 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); qa_valid.csv imported |
| Test data | qa_valid.csv again; qa_conflict.csv (same order IDs, one quantity changed) |
| Steps | 1. Upload qa_valid.csv again<br>2. Upload qa_conflict.csv<br>3. Open the original transaction |
| Expected result | Re-upload: duplicateOrderCount 3, nothing new. Conflict: conflictOrderCount 1; the original transaction is unchanged. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F04-008

**Async CSV import with progress**

| Field | Value |
|---|---|
| Trace | FR-04.2; NFR 5.5 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | seed Part 1 copy (~10,000 rows); Idempotency-Key = new UUID |
| Steps | 1. Upload with the async option<br>2. Watch the progress bar until it ends |
| Expected result | 202 with status QUEUED. Progress moves QUEUED → IMPORTING (status PROCESSING) → COMPLETED; percent = processed/total, never estimated. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F04-009

**Idempotency-Key rules**

| Field | Value |
|---|---|
| Trace | FR-04.2 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | key K (36 chars); same key with a different file; no key; 128-char key; 129-char key; key with `/` |
| Steps | 1. [API] POST /api/transactions/imports/csv/async with each case |
| Expected result | Same key + same bytes: the original batch. Same key + different bytes: 409. Missing key, 129 chars, `/`: 400. 128 chars: 202. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F04-010

**Excel import**

| Field | Value |
|---|---|
| Trace | FR-04.5 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | qa_valid.xlsx; qa_valid.xls; numeric order ID cell; date-serial occurred_at; formula cell; two non-empty sheets |
| Steps | 1. Upload each workbook |
| Expected result | Valid .xlsx and .xls: 201 summary. Numeric ID, date serial, formula and two non-empty sheets: 400 structural error. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F04-011

**CSV and Excel share order identity**

| Field | Value |
|---|---|
| Trace | FR-04.5; FR-04.3 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); qa_valid.csv imported |
| Test data | qa_valid.xlsx with the same orders |
| Steps | 1. Upload the workbook |
| Expected result | Orders counted as duplicates; no new transactions. |
| Priority / type | Medium / functional |
| Status | NOT RUN |

### TC-F04-012

**Imports never change inventory**

| Field | Value |
|---|---|
| Trace | FR-04.4 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | batch remaining of a product before and after importing 5 units sold |
| Steps | 1. Note batch remaining<br>2. Import<br>3. Check batch remaining |
| Expected result | Batch remaining is unchanged. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F04-013

**Non-CSV file is refused**

| Field | Value |
|---|---|
| Trace | FR-04.1 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | qa.txt; qa.pdf renamed .csv |
| Steps | 1. Upload each |
| Expected result | 400; no batch created (or a FAILED batch with an error, no totals). |
| Priority / type | Low / negative |
| Status | NOT RUN |

### TC-F04-014

**F04 RBAC and Store isolation**

| Field | Value |
|---|---|
| Trace | UC21; NFR 5.4 |
| Role | Merchandiser, System Admin, Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); SETUP-B |
| Test data | import IDs of Store A |
| Steps | 1. Merchandiser: open /store/transactions/history and an import's errors<br>2. Merchandiser: check that Import is not offered<br>3. [API] Merchandiser: POST csv, excel, csv/async<br>4. [API] System Admin: GET /api/transactions<br>5. [API] Store Admin B: GET Store A import and transaction |
| Expected result | Merchandiser reads lists and errors; import routes return 403. System Admin 403. Store Admin B 404. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F04-015

**Import and transaction lists paging**

| Field | Value |
|---|---|
| Trace | UC21; NFR 5.2 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | pageSize 20, 100, 101 |
| Steps | 1. Page through /store/transactions/history |
| Expected result | Pages and totals are consistent; pageSize 101 returns 400. |
| Priority / type | Low / boundary |
| Status | NOT RUN |

## F05

**EFIM mining** — 26 cases.

| ID | Title | Priority | Type |
|---|---|---|---|
| [TC-F05-001](#tc-f05-001) | Set the minUtil policy (versioned) | High | functional |
| [TC-F05-002](#tc-f05-002) | minUtil boundaries and stale version | High | boundary |
| [TC-F05-003](#tc-f05-003) | Algorithm cannot be chosen | Medium | negative |
| [TC-F05-004](#tc-f05-004) | Store picker lists ACTIVE Stores | Medium | functional |
| [TC-F05-005](#tc-f05-005) | System Admin runs EFIM mining with progress | High | functional |
| [TC-F05-006](#tc-f05-006) | Merchandiser runs mining on its own snapshot | High | functional |
| [TC-F05-007](#tc-f05-007) | Merchandiser submission body is strict; other roles cannot submit | High | security |
| [TC-F05-008](#tc-f05-008) | requestKey idempotency | High | negative |
| [TC-F05-009](#tc-f05-009) | Mining without a policy is refused | Medium | negative |
| [TC-F05-010](#tc-f05-010) | Snapshot λ above the current λmax | Medium | negative |
| [TC-F05-011](#tc-f05-011) | Snapshots and requests of another Store | High | security |
| [TC-F05-012](#tc-f05-012) | Distinct product limit 1,600 | High | boundary |
| [TC-F05-013](#tc-f05-013) | Matrix row limit 20,000 | High | boundary |
| [TC-F05-014](#tc-f05-014) | int32 guard: lossless, whole-đồng fallback, final refusal | High | boundary |
| [TC-F05-015](#tc-f05-015) | Whole-đồng rounding is HALF_UP | Medium | boundary |
| [TC-F05-016](#tc-f05-016) | Pattern output limit 2,000 | Medium | boundary |
| [TC-F05-017](#tc-f05-017) | Mining timeout | Low | negative |
| [TC-F05-018](#tc-f05-018) | SPMF runtime unavailable | Low | negative |
| [TC-F05-019](#tc-f05-019) | Store users read patterns; System Admin cannot | High | security |
| [TC-F05-020](#tc-f05-020) | Store Admin follows request status and progress | Medium | functional |
| [TC-F05-021](#tc-f05-021) | Create schedules: disabled, lookback default and bounds | Medium | boundary |
| [TC-F05-022](#tc-f05-022) | Enabling a schedule returns 503 | High | negative |
| [TC-F05-023](#tc-f05-023) | Occurrence history is readable | Low | functional |
| [TC-F05-024](#tc-f05-024) | No new System Admin work for an INACTIVE Store | High | negative |
| [TC-F05-025](#tc-f05-025) | Benchmark is removed | Low | negative |
| [TC-F05-026](#tc-f05-026) | F05 RBAC (decision table) | High | security |

### TC-F05-001

**Set the minUtil policy (versioned)**

| Field | Value |
|---|---|
| Trace | UC04; FR-05.1; BR-08 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | PERCENTAGE 0.12, expectedVersion = current |
| Steps | 1. Open /admin/setup<br>2. Set PERCENTAGE 0.12 and save |
| Expected result | 200; version +1; algorithm shown as EFIM (no selector); earlier requests keep their own version. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F05-002

**minUtil boundaries and stale version**

| Field | Value |
|---|---|
| Trace | UC04; FR-05.1 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | PERCENTAGE 0; 100; 100.000001; ABSOLUTE 0.000001; 0.0000001 (7 dp); stale expectedVersion |
| Steps | 1. Save each value |
| Expected result | PERCENTAGE 100 and ABSOLUTE 0.000001 accepted. 0, >100 (`Percentage minUtil must be in (0,100]`), 7 decimals: 400. Stale version: 409. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F05-003

**Algorithm cannot be chosen**

| Field | Value |
|---|---|
| Trace | UC04; BR-07; DEC-20261116-04 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | body with "algorithm":"HUI_MINER" |
| Steps | 1. Check /admin/setup has no algorithm selector<br>2. [API] PUT /api/system/mining/config with algorithm |
| Expected result | No selector. 400 `Invalid request fields`. |
| Priority / type | Medium / negative |
| Status | NOT RUN |

### TC-F05-004

**Store picker lists ACTIVE Stores**

| Field | Value |
|---|---|
| Trace | UC33; DEC-20261123-01 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); Store C INACTIVE |
| Test data | — |
| Steps | 1. Open /admin/mining/run and the Store picker |
| Expected result | Store A and B are listed; Store C is not (it appears only with status ALL in /admin/stores). |
| Priority / type | Medium / functional |
| Status | NOT RUN |

### TC-F05-005

**System Admin runs EFIM mining with progress**

| Field | Value |
|---|---|
| Trace | UC05; UC07; FR-05.2; FR-05.5 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); DP-02 snapshot S1; DP-03 |
| Test data | Store A, S1, new requestKey |
| Steps | 1. Open /admin/mining/run, choose Store A and S1, submit<br>2. Watch the progress view<br>3. Open /admin/results |
| Expected result | 202 with one EFIM run. Progress shows stages QUEUED → RUNNING → SAVING → COMPLETED with percent and elapsed time, polled. Run COMPLETED with patternCount, runtime and memory. The System Admin sees status, not raw patterns. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F05-006

**Merchandiser runs mining on its own snapshot**

| Field | Value |
|---|---|
| Trace | UC05; FR-05.2 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); S1; DP-03 |
| Test data | {"requestKey":"<uuid>","snapshotId":S1} |
| Steps | 1. [API] POST /api/mining/requests (see PO question Q-05 about the missing screen)<br>2. [API] GET /api/mining/requests/{id}/progress until terminal |
| Expected result | 202; one EFIM run; requestedBy is the Merchandiser; the request completes. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F05-007

**Merchandiser submission body is strict; other roles cannot submit**

| Field | Value |
|---|---|
| Trace | FR-05.2; NFR 5.4 |
| Role | Merchandiser, Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | body plus storeId; plus minUtilValue; plus compareAlgorithms |
| Steps | 1. [API] POST /api/mining/requests with each extra field as Merchandiser<br>2. [API] POST a valid body as Store Admin |
| Expected result | Extra fields: 400. Store Admin: 403. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F05-008

**requestKey idempotency**

| Field | Value |
|---|---|
| Trace | FR-05.2 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); request R1 exists with key K on S1 |
| Test data | key K with S1; key K with S2 |
| Steps | 1. [API] Replay K with S1<br>2. [API] Reuse K with S2 |
| Expected result | Replay returns R1 (202, no new run). Different snapshot: 409. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F05-009

**Mining without a policy is refused**

| Field | Value |
|---|---|
| Trace | FR-05.1 |
| Role | System Admin |
| Preconditions | Fresh database with no mining configuration (version 0) |
| Test data | any snapshot |
| Steps | 1. Submit mining |
| Expected result | 409 (no configured mining policy). |
| Priority / type | Medium / negative |
| Status | NOT RUN |

### TC-F05-010

**Snapshot λ above the current λmax**

| Field | Value |
|---|---|
| Trace | FR-03.5; FR-05.2 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); S1 frozen with λ 0.3; System Admin then sets λmax 0.2 |
| Test data | S1 |
| Steps | 1. [API] Merchandiser submits mining on S1 |
| Expected result | 409 `The lambda policy changed: this snapshot exceeds the current lambda maximum…`. |
| Priority / type | Medium / negative |
| Status | NOT RUN |

### TC-F05-011

**Snapshots and requests of another Store**

| Field | Value |
|---|---|
| Trace | BR-01; BR-07; NFR 5.4 |
| Role | Merchandiser, Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); SETUP-B |
| Test data | S1 (Store A), Store A request id |
| Steps | 1. [API] Merchandiser B submits with S1<br>2. [API] Store Admin B GET request, runs, progress, patterns of Store A |
| Expected result | All 404. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F05-012

**Distinct product limit 1,600**

| Field | Value |
|---|---|
| Trace | FR-05.3; BR-18; NFR 5.2 |
| Role | System Admin |
| Preconditions | DP-08: synthetic Store D snapshots with 1,600 and 1,601 distinct products |
| Test data | S-1600, S-1601 |
| Steps | 1. Submit mining for each |
| Expected result | 1,600: 202. 1,601: 400 `Snapshot exceeds 1600 distinct products`. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F05-013

**Matrix row limit 20,000**

| Field | Value |
|---|---|
| Trace | FR-05.3; BR-18 |
| Role | System Admin |
| Preconditions | DP-08: snapshots with 20,000 and 20,001 matrix rows |
| Test data | S-20000, S-20001 |
| Steps | 1. Submit mining for each |
| Expected result | 20,000: 202. 20,001: 400 `Snapshot matrix exceeds mining row limit` (or `…must contain 1..20000 rows` at snapshot creation). |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F05-014

**int32 guard: lossless, whole-đồng fallback, final refusal**

| Field | Value |
|---|---|
| Trace | FR-05.3; DEC-20261119-05 |
| Role | System Admin |
| Preconditions | DP-08: snapshot SL (fits losslessly), SW (overflows lossless, fits after 1-đồng rounding), SX (overflows even after rounding) |
| Test data | SL, SW, SX |
| Steps | 1. Submit mining for each<br>2. Open each request's detail |
| Expected result | SL: conversionMode LOSSLESS. SW: accepted with conversionVersion F03_WHOLE_UNIT_HALF_UP_INT32_DENSE_V1, conversionMode WHOLE_UNIT_HALF_UP, roundingUnit 1; the snapshot itself is unchanged. SX: 400 naming the bound and WHOLE_UNIT_HALF_UP. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F05-015

**Whole-đồng rounding is HALF_UP**

| Field | Value |
|---|---|
| Trace | FR-05.3; DEC-20261119-05 |
| Role | System Admin |
| Preconditions | DP-08 SW variant with item utilities ending .50 and .49 đồng |
| Test data | SW-round |
| Steps | 1. Submit<br>2. Compare frozen item utilities with the snapshot |
| Expected result | .50 rounds up, .49 rounds down; minUtil = ceil(value) for ABSOLUTE. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F05-016

**Pattern output limit 2,000**

| Field | Value |
|---|---|
| Trace | FR-05.4 |
| Role | System Admin |
| Preconditions | DP-08: snapshot and minUtil tuned to give exactly 2,000 and 2,001 patterns |
| Test data | S-P2000, S-P2001 |
| Steps | 1. Submit each and wait |
| Expected result | 2,000 patterns: COMPLETED. 2,001: run FAILED with failureCode OUTPUT_LIMIT. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F05-017

**Mining timeout**

| Field | Value |
|---|---|
| Trace | FR-05.4 |
| Role | System Admin |
| Preconditions | Backend started with paspm.mining.timeout-millis=1 (test instance only) |
| Test data | S1 |
| Steps | 1. Submit mining |
| Expected result | Run TIMED_OUT, request FAILED; progress stops at FAILED. |
| Priority / type | Low / negative |
| Status | NOT RUN |

### TC-F05-018

**SPMF runtime unavailable**

| Field | Value |
|---|---|
| Trace | FR-05.4 |
| Role | System Admin |
| Preconditions | Backend started without a valid SPMF_JAR_PATH (test instance only) |
| Test data | S1 |
| Steps | 1. Submit mining |
| Expected result | 503; the request is not run. |
| Priority / type | Low / negative |
| Status | NOT RUN |

### TC-F05-019

**Store users read patterns; System Admin cannot**

| Field | Value |
|---|---|
| Trace | FR-05.7; UC07 |
| Role | Merchandiser, Store Admin, System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); DP-04 completed run R1 |
| Test data | R1 |
| Steps | 1. Merchandiser/Store Admin: open the run's patterns<br>2. [API] System Admin: GET /api/system/mining/stores/{A}/runs/{R1}/patterns |
| Expected result | Store users see patterns ordered by utility descending, then canonical key. System Admin: 403. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F05-020

**Store Admin follows request status and progress**

| Field | Value |
|---|---|
| Trace | UC07; FR-05.5 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); a running or completed own-Store request |
| Test data | request id |
| Steps | 1. [API] GET /api/mining/requests, /{id}, /{id}/runs, /{id}/progress |
| Expected result | 200 for each; status matches the System Admin view. |
| Priority / type | Medium / functional |
| Status | NOT RUN |

### TC-F05-021

**Create schedules: disabled, lookback default and bounds**

| Field | Value |
|---|---|
| Trace | UC06; FR-05.6 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | WEEKLY dayOfWeek 1 08:00; MONTHLY dayOfMonth 31; lookback omitted, 7, 365, 6, 366; timeZone UTC |
| Steps | 1. [API] POST /api/system/mining/schedules with each body (no schedule screen in the FE) |
| Expected result | Created with enabled false, no nextFireAt, version 1; lookback default 30 (weekly) / 90 (monthly); 7 and 365 accepted; 6, 366 and UTC refused with 400. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F05-022

**Enabling a schedule returns 503**

| Field | Value |
|---|---|
| Trace | UC06; FR-05.6; BR-24 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) (schedule-dispatch-enabled unset) |
| Test data | schedule from the previous case |
| Steps | 1. [API] PUT with enabled true<br>2. [API] PUT edit lookback with enabled false<br>3. [API] PUT with a stale expectedVersion |
| Expected result | Enable: 503 `Scheduled mining dispatch is not available`, nothing written. Edit/disable: 200. Stale version: 409. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F05-023

**Occurrence history is readable**

| Field | Value |
|---|---|
| Trace | UC06; FR-05.6 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | schedule id |
| Steps | 1. [API] GET …/schedules/{id}/occurrences |
| Expected result | 200 with an empty page (no dispatch while gated). |
| Priority / type | Low / functional |
| Status | NOT RUN |

### TC-F05-024

**No new System Admin work for an INACTIVE Store**

| Field | Value |
|---|---|
| Trace | FR-01.9; BR-30 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); Store C INACTIVE with an earlier request (key KC) and a snapshot |
| Test data | Store C |
| Steps | 1. [API] Submit new mining for Store C<br>2. [API] Replay key KC<br>3. [API] Create a schedule for Store C<br>4. [API] Read Store C request history |
| Expected result | New request and schedule: 409 code STORE_INACTIVE. Replay of KC: 202 existing request. History: 200. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F05-025

**Benchmark is removed**

| Field | Value |
|---|---|
| Trace | UC08 (REMOVED); DEC-20261116-04 |
| Role | System Admin, Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); R1 |
| Test data | R1 |
| Steps | 1. Check there is no Benchmark screen (/admin/benchmark) in the menu<br>2. [API] GET /api/mining/runs/{R1}/benchmark |
| Expected result | No screen; 403 for every role. |
| Priority / type | Low / negative |
| Status | NOT RUN |

### TC-F05-026

**F05 RBAC (decision table)**

| Field | Value |
|---|---|
| Trace | UC04–UC07; NFR 5.4 |
| Role | End Customer, Guest (anonymous), Store Admin, Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | system and Store mining routes |
| Steps | 1. [API] End Customer and no token: GET /api/mining/requests<br>2. [API] Store Admin and Merchandiser: GET /api/system/mining/config |
| Expected result | End Customer 403; no token 401; Store roles on system routes 403. |
| Priority / type | High / security |
| Status | NOT RUN |

## F06

**Recommendations** — 18 cases.

| ID | Title | Priority | Type |
|---|---|---|---|
| [TC-F06-001](#tc-f06-001) | Generate the four recommendation types | High | functional |
| [TC-F06-002](#tc-f06-002) | Generation is idempotent | High | functional |
| [TC-F06-003](#tc-f06-003) | Generation input errors | Medium | negative |
| [TC-F06-004](#tc-f06-004) | Recommendation policy defaults and update | Medium | functional |
| [TC-F06-005](#tc-f06-005) | Policy field boundaries and RBAC | Medium | boundary |
| [TC-F06-006](#tc-f06-006) | Bundle size limit | High | boundary |
| [TC-F06-007](#tc-f06-007) | FBT support and cross-sell confidence thresholds | High | boundary |
| [TC-F06-008](#tc-f06-008) | Approve with a reason | High | functional |
| [TC-F06-009](#tc-f06-009) | Review only once, with a valid reason and version | High | boundary |
| [TC-F06-010](#tc-f06-010) | Approval blocked when not eligible; rejection allowed | High | negative |
| [TC-F06-011](#tc-f06-011) | Approved candidate becomes not ready when stock disappears | High | state transition |
| [TC-F06-012](#tc-f06-012) | Eligibility on the expiry date | Medium | boundary |
| [TC-F06-013](#tc-f06-013) | Ended-season warning | Medium | boundary |
| [TC-F06-014](#tc-f06-014) | Ranked list order within a run | Medium | functional |
| [TC-F06-015](#tc-f06-015) | Filters and paging | Low | boundary |
| [TC-F06-016](#tc-f06-016) | Expiry push alerts | Medium | boundary |
| [TC-F06-017](#tc-f06-017) | F06 RBAC (decision table) | High | security |
| [TC-F06-018](#tc-f06-018) | Approval publishes and reserves nothing | Medium | functional |

### TC-F06-001

**Generate the four recommendation types**

| Field | Value |
|---|---|
| Trace | UC10; FR-06.1; BR-09 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); DP-04 R1 |
| Test data | miningRunId R1, types BUNDLE, CROSS_SELL, FBT, BANNER |
| Steps | 1. Open /merchandiser/recommendations<br>2. Generate from R1 |
| Expected result | 200 with createdCount, reusedCount, recommendationIds and skipped reasons; candidates are PENDING_REVIEW. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F06-002

**Generation is idempotent**

| Field | Value |
|---|---|
| Trace | FR-06.2 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); recommendations from R1, one approved |
| Test data | R1 again |
| Steps | 1. Generate again |
| Expected result | createdCount 0, reusedCount = previous total, same IDs; the approved one stays APPROVED. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F06-003

**Generation input errors**

| Field | Value |
|---|---|
| Trace | UC10; BR-09 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); SETUP-B; a FAILED or running run |
| Test data | running/failed run; Store B run; types []; [BUNDLE,BUNDLE]; unknown type |
| Steps | 1. Generate with each input |
| Expected result | Unfinished or failed run: 409. Store B run: 404. Empty, duplicate or unknown types: 400. |
| Priority / type | Medium / negative |
| Status | NOT RUN |

### TC-F06-004

**Recommendation policy defaults and update**

| Field | Value |
|---|---|
| Trace | FR-06.1 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | defaults; update minConfidence 0.6 with expectedVersion |
| Steps | 1. Open the recommendation policy<br>2. Change minConfidence and save<br>3. Open an existing candidate |
| Expected result | Defaults: maxBundleSize 4, minSupportCount 2, minSupportRatio 0.05, minConfidence 0.50, minimumSellableQuantity 1. Save: version 2. Existing candidates keep the policy they captured. |
| Priority / type | Medium / functional |
| Status | NOT RUN |

### TC-F06-005

**Policy field boundaries and RBAC**

| Field | Value |
|---|---|
| Trace | FR-06.1 |
| Role | Store Admin, Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | maxBundleSize 1, 2, 256, 257; minSupportRatio 1.0000001, 0.1234567; minimumSellableQuantity 0; stale version |
| Steps | 1. Save each value<br>2. [API] Merchandiser PUT the policy |
| Expected result | 2 and 256 accepted. 1, 257, ratio > 1, 7 decimals, quantity 0: 400. Stale version: 409. Merchandiser: 403. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F06-006

**Bundle size limit**

| Field | Value |
|---|---|
| Trace | FR-06.1 |
| Role | Merchandiser |
| Preconditions | DP-05 run with patterns of 4 and 5 products |
| Test data | policy maxBundleSize 4 |
| Steps | 1. Generate BUNDLE |
| Expected result | 4-product pattern becomes a BUNDLE; 5-product pattern is skipped with BUNDLE_EXCEEDS_MAX_SIZE. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F06-007

**FBT support and cross-sell confidence thresholds**

| Field | Value |
|---|---|
| Trace | FR-06.1 |
| Role | Merchandiser |
| Preconditions | DP-05 run on a controlled dataset of 40 transactions |
| Test data | FBT pairs with supportCount 2 (ratio 0.05) and 1; cross-sell anchors with confidence 0.50 and 0.49 |
| Steps | 1. Generate FBT and CROSS_SELL |
| Expected result | support 2 / ratio 0.05: created. support 1: SUPPORT_POLICY_NOT_MET. Confidence 0.50: created. 0.49: CONFIDENCE_POLICY_NOT_MET. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F06-008

**Approve with a reason**

| Field | Value |
|---|---|
| Trace | UC11; FR-06.3; BR-10 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); ELIGIBLE PENDING_REVIEW candidate |
| Test data | reason `Checked stock` |
| Steps | 1. Open /merchandiser/approve<br>2. Approve with the reason |
| Expected result | APPROVED; version +1; readyForConsumption true; review history shows actor, time, reason and the eligibility check. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F06-009

**Review only once, with a valid reason and version**

| Field | Value |
|---|---|
| Trace | UC11; BR-10 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | approved candidate; stale version; blank reason; 1,000- and 1,001-char reasons |
| Steps | 1. Approve/reject the approved candidate<br>2. Approve another with a stale version<br>3. Use each reason |
| Expected result | Second review: 409. Stale version: 409. Blank and 1,001 chars: 400. 1,000 chars: accepted. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F06-010

**Approval blocked when not eligible; rejection allowed**

| Field | Value |
|---|---|
| Trace | FR-06.3; BR-19 |
| Role | Merchandiser, Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); candidate whose product the Store Admin set INACTIVE (or blocked all batches) |
| Test data | candidate |
| Steps | 1. Approve<br>2. Reject with a reason |
| Expected result | Approve: 409 (current eligibility not ELIGIBLE). Reject: 200 REJECTED. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F06-011

**Approved candidate becomes not ready when stock disappears**

| Field | Value |
|---|---|
| Trace | FR-06.4; BR-19 |
| Role | Merchandiser, Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); approved BUNDLE candidate |
| Test data | block every batch of one component |
| Steps | 1. Store Admin blocks the batches<br>2. Merchandiser reloads the candidate |
| Expected result | reviewStatus stays APPROVED; readyForConsumption false; currentEligibility not ELIGIBLE; inventoryChanged true. |
| Priority / type | High / state transition |
| Status | NOT RUN |

### TC-F06-012

**Eligibility on the expiry date**

| Field | Value |
|---|---|
| Trace | FR-06.4; BR-32 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | component whose only batch expires today; then (next day) expired |
| Steps | 1. Open the candidate today<br>2. Open it after the date passes |
| Expected result | Today: ELIGIBLE. After the date: not ELIGIBLE (expired). |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F06-013

**Ended-season warning**

| Field | Value |
|---|---|
| Trace | FR-06.5 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); candidate whose snapshot froze season S ending yesterday, and another with a season ending today |
| Test data | two candidates |
| Steps | 1. Open both candidates |
| Expected result | First: seasonEnded true, endedSeasons [S], explanation asks to regenerate from a new snapshot. Second: seasonEnded false. Nothing reruns automatically. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F06-014

**Ranked list order within a run**

| Field | Value |
|---|---|
| Trace | UC11; FR-06.6 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); ≥ 25 candidates from R1 |
| Test data | miningRunId R1; pageSize 10 |
| Steps | 1. Open the list filtered by R1 and page through |
| Expected result | Order: eligibility, utility desc, support, canonical key, type, anchor, id; rank is 1-based and continuous across pages. Without the run filter: newest first, rank null. |
| Priority / type | Medium / functional |
| Status | NOT RUN |

### TC-F06-015

**Filters and paging**

| Field | Value |
|---|---|
| Trace | UC10; NFR 5.2 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | type FBT; reviewStatus APPROVED; snapshotId S1; pageSize 0 and 101 |
| Steps | 1. Apply each filter<br>2. [API] pageSize 0 and 101 |
| Expected result | Filters apply; invalid page sizes return 400. |
| Priority / type | Low / boundary |
| Status | NOT RUN |

### TC-F06-016

**Expiry push alerts**

| Field | Value |
|---|---|
| Trace | FR-06.5 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | batches with pushDay T-1, T, T+1 |
| Steps | 1. [API] GET /api/recommendations/expiry-push<br>2. [API] …?asOfDate=2026-10-01 |
| Expected result | T-1 and T listed; T+1 not. asOfDate: 400. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F06-017

**F06 RBAC (decision table)**

| Field | Value |
|---|---|
| Trace | UC10; UC11; NFR 5.4 |
| Role | Store Admin, System Admin, End Customer, Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); SETUP-B |
| Test data | candidate of Store A |
| Steps | 1. [API] Store Admin: generate, approve, reject<br>2. [API] System Admin and End Customer: list<br>3. [API] Merchandiser B: detail of the Store A candidate |
| Expected result | Store Admin writes 403; System Admin and End Customer 403; Merchandiser B 404. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F06-018

**Approval publishes and reserves nothing**

| Field | Value |
|---|---|
| Trace | BR-11 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | approved candidate |
| Steps | 1. Note stock, campaigns and storefront<br>2. Approve<br>3. Check again |
| Expected result | Stock, campaigns and the storefront are unchanged. |
| Priority / type | Medium / functional |
| Status | NOT RUN |

## F07

**Layout simulator** — 15 cases.

| ID | Title | Priority | Type |
|---|---|---|---|
| [TC-F07-001](#tc-f07-001) | Campaign creation generates a read-only Baseline A (top 35) | High | functional |
| [TC-F07-002](#tc-f07-002) | Baseline A is deterministic and immutable | High | negative |
| [TC-F07-003](#tc-f07-003) | Edit Layout B with sourced and manual products | High | functional |
| [TC-F07-004](#tc-f07-004) | A product appears at most once | High | negative |
| [TC-F07-005](#tc-f07-005) | Zone capacity boundaries | High | boundary |
| [TC-F07-006](#tc-f07-006) | Invalid sources for a placement | High | negative |
| [TC-F07-007](#tc-f07-007) | Manual placement eligibility | Medium | negative |
| [TC-F07-008](#tc-f07-008) | Placement field rules | Low | negative |
| [TC-F07-009](#tc-f07-009) | Stale layout version | High | negative |
| [TC-F07-010](#tc-f07-010) | Layout B state: draft revision → READY → new revision | High | state transition |
| [TC-F07-011](#tc-f07-011) | A block that becomes invalid blocks READY, publish and activate | High | state transition |
| [TC-F07-012](#tc-f07-012) | Editing B does not change F06 evidence or Layout A | Medium | functional |
| [TC-F07-013](#tc-f07-013) | Difference against A uses A's first N products | Medium | boundary |
| [TC-F07-014](#tc-f07-014) | Priority coverage on the layout | High | boundary |
| [TC-F07-015](#tc-f07-015) | F07 RBAC (decision table) | High | security |

### TC-F07-001

**Campaign creation generates a read-only Baseline A (top 35)**

| Field | Value |
|---|---|
| Trace | UC13; FR-07.1 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); DP-05 S1 with > 35 products; two approved BUNDLE candidates |
| Test data | name QA Campaign 1, snapshot S1, those two recommendations |
| Steps | 1. Open /store/layout (F07/F08 workspace) → New campaign<br>2. Create<br>3. Open Layout A |
| Expected result | 201 DRAFT with A and B IDs. A has rule F07_BASELINE_V2, k 35, candidateProducts = snapshot products, at most 35 products ordered by transaction frequency then product ID. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F07-002

**Baseline A is deterministic and immutable**

| Field | Value |
|---|---|
| Trace | UC13; FR-07.1 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); two campaigns created from S1 |
| Test data | Layout A of both |
| Steps | 1. Compare both A layouts<br>2. [API] PUT /api/layouts/{A}/placements |
| Expected result | Same products and order. Editing A: 409 `Layout A is immutable and read-only`. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F07-003

**Edit Layout B with sourced and manual products**

| Field | Value |
|---|---|
| Trace | UC12; FR-07.2; FR-07.4; BR-22 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); QA Campaign 1 DRAFT |
| Test data | BUNDLE block 1: 2 products from an approved recommendation; CROSS_SELL block 1: 1 manual product found by search |
| Steps | 1. Open Layout B in the editor<br>2. Add the sourced products and the manual product<br>3. Save |
| Expected result | 200; new revision, version +1; sourced and manual placements are labelled differently; score = sum of snapshot weighted utility of distinct placed products, labelled `Weighted utility (snapshot)`. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F07-004

**A product appears at most once**

| Field | Value |
|---|---|
| Trace | FR-07.2; BR-20 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); Layout B |
| Test data | same product in BUNDLE and FBT |
| Steps | 1. Place the product twice and save |
| Expected result | 400; nothing saved. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F07-005

**Zone capacity boundaries**

| Field | Value |
|---|---|
| Trace | FR-07.2; DEC-20261119-06 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); default caps |
| Test data | BUNDLE 16 products / 17; BUNDLE 4 blocks / 5; BANNER 3 / 4 products; CROSS_SELL 8 / 9; FBT 8 / 9 |
| Steps | 1. Check the editor shows the caps from GET /api/layouts/policy<br>2. Save each layout |
| Expected result | At the cap: saved. One over: 400 `<ZONE> exceeds configured blocks\|products capacity <n>`. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F07-006

**Invalid sources for a placement**

| Field | Value |
|---|---|
| Trace | FR-07.2 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | sourceRecommendationId of a PENDING_REVIEW, a REJECTED, another snapshot's recommendation; a product not in the cited recommendation |
| Steps | 1. Save B with each placement |
| Expected result | Each refused (400/409 with a validation error); nothing saved. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F07-007

**Manual placement eligibility**

| Field | Value |
|---|---|
| Trace | FR-07.2 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | manual products: not in S1; INACTIVE; 0 sellable units |
| Steps | 1. Add each product in the editor and try Mark READY |
| Expected result | Each shows a per-product validation error; READY is refused. |
| Priority / type | Medium / negative |
| Status | NOT RUN |

### TC-F07-008

**Placement field rules**

| Field | Value |
|---|---|
| Trace | FR-07.2 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | zone BASELINE; blockNo 0; position 0 |
| Steps | 1. [API] PUT placements with each value |
| Expected result | 400 `Invalid request fields`. |
| Priority / type | Low / negative |
| Status | NOT RUN |

### TC-F07-009

**Stale layout version**

| Field | Value |
|---|---|
| Trace | FR-07.3; Common 6 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); Layout B open in two tabs |
| Test data | two edits |
| Steps | 1. Save in tab 1<br>2. Save in tab 2 |
| Expected result | Tab 2: 409; after reload the user resolves and saves. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F07-010

**Layout B state: draft revision → READY → new revision**

| Field | Value |
|---|---|
| Trace | FR-07.3; R2 layout state |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); valid Layout B; a decision recorded |
| Test data | B |
| Steps | 1. Mark READY<br>2. Edit B and save<br>3. Open the campaign |
| Expected result | READY after step 1. The edit creates a new draft revision, clears the campaign decision and needs READY again. |
| Priority / type | High / state transition |
| Status | NOT RUN |

### TC-F07-011

**A block that becomes invalid blocks READY, publish and activate**

| Field | Value |
|---|---|
| Trace | FR-07.3 |
| Role | Merchandiser, Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); READY B with product P; then P's stock set to 0 |
| Test data | P |
| Steps | 1. Open B preview<br>2. Mark READY<br>3. Publish<br>4. Store Admin activates (if already published) |
| Expected result | Preview shows P invalid with errors; READY, publish and activate return 409. |
| Priority / type | High / state transition |
| Status | NOT RUN |

### TC-F07-012

**Editing B does not change F06 evidence or Layout A**

| Field | Value |
|---|---|
| Trace | BR-20 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | recommendation used in B; Layout A |
| Steps | 1. Remove one sourced product from B<br>2. Open the recommendation and Layout A |
| Expected result | Recommendation evidence and Layout A are unchanged. |
| Priority / type | Medium / functional |
| Status | NOT RUN |

### TC-F07-013

**Difference against A uses A's first N products**

| Field | Value |
|---|---|
| Trace | FR-07.4; FR-08.3; DEC-20261119-06 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | B with 6 products; B with 40 products (> \|A\| = 35) |
| Steps | 1. Open B detail in each case |
| Expected result | equalSizeProductCount 6 and equalSizeBaselineScore = A's first 6 products; difference = B score − that value. With 40: equalSizeProductCount 35 (all of A). |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F07-014

**Priority coverage on the layout**

| Field | Value |
|---|---|
| Trace | FR-07.5; BR-23; DEC-20261117-02 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | B with 4 products, exactly 1 with frozen W > 1 (e.g. 1.15) and 1 with W = 1.00; empty draft B |
| Steps | 1. Open both layouts |
| Expected result | 4 products: priorityCoverage 0.25 shown as 25%, denominator 4 (W = 1.00 is not a priority product). Empty B: null shown as N/A, denominator 0. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F07-015

**F07 RBAC (decision table)**

| Field | Value |
|---|---|
| Trace | UC12; NFR 5.4 |
| Role | Store Admin, System Admin, End Customer, Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); SETUP-B |
| Test data | Layout B of Store A |
| Steps | 1. Store Admin: open the editor (read-only)<br>2. [API] Store Admin: PUT placements, POST ready<br>3. [API] System Admin and End Customer: GET /api/layouts<br>4. [API] Merchandiser B: GET the Store A layout |
| Expected result | Store Admin writes 403; System Admin and End Customer 403; Merchandiser B 404. |
| Priority / type | High / security |
| Status | NOT RUN |

## F08

**Campaigns and simulated A/B** — 19 cases.

| ID | Title | Priority | Type |
|---|---|---|---|
| [TC-F08-001](#tc-f08-001) | Run an offline A/B simulation | High | functional |
| [TC-F08-002](#tc-f08-002) | Simulation range must start after the snapshot | High | boundary |
| [TC-F08-003](#tc-f08-003) | Simulation needs current versions and a DRAFT campaign | Medium | negative |
| [TC-F08-004](#tc-f08-004) | No evaluation transactions gives N/A rates | Medium | boundary |
| [TC-F08-005](#tc-f08-005) | Reports are immutable and pinned to layout versions | High | negative |
| [TC-F08-006](#tc-f08-006) | Decide and publish | High | state transition |
| [TC-F08-007](#tc-f08-007) | Publish requires a decision and a valid READY layout | High | negative |
| [TC-F08-008](#tc-f08-008) | Withdraw a published campaign | High | state transition |
| [TC-F08-009](#tc-f08-009) | Store Admin activates a published campaign | High | state transition |
| [TC-F08-010](#tc-f08-010) | Only one ACTIVE campaign per Store | High | state transition |
| [TC-F08-011](#tc-f08-011) | Activation rechecks validity | High | negative |
| [TC-F08-012](#tc-f08-012) | End the active campaign | High | state transition |
| [TC-F08-013](#tc-f08-013) | Rollback restores the previous campaign or the default | High | state transition |
| [TC-F08-014](#tc-f08-014) | Invalid campaign transitions (state table) | High | state transition |
| [TC-F08-015](#tc-f08-015) | Stale campaign version | Medium | negative |
| [TC-F08-016](#tc-f08-016) | F08 RBAC (decision table) | High | security |
| [TC-F08-017](#tc-f08-017) | View reports and the A/B labels | Medium | UI |
| [TC-F08-018](#tc-f08-018) | Campaign list paging | Low | boundary |
| [TC-F08-019](#tc-f08-019) | Concurrent activation is serialised | Low | negative |

### TC-F08-001

**Run an offline A/B simulation**

| Field | Value |
|---|---|
| Trace | UC15; UC16; FR-08.3; FR-08.4; FR-07.5; BR-13; BR-23 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); QA Campaign 1 with A and B READY; S1 ends 2026-10-03 |
| Test data | rangeStart 2026-10-04, rangeEnd 2026-10-04, current versions |
| Steps | 1. Open the campaign in /store/layout (F07/F08 workspace) and run the simulation<br>2. Open the report comparison view |
| Expected result | 201 report with simulated true, algorithmVersion OFFLINE_REPLAY_V4, datasetHash, evaluationCount, both layout versions, hitRate, bundleConversion, capturedUtility and priorityCoverage per side, assumptions with comparedProductCountN, baselineProductCount and nExceedsBaseline. The UI labels results as simulated. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F08-002

**Simulation range must start after the snapshot**

| Field | Value |
|---|---|
| Trace | UC15; FR-08.3 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | rangeStart 2026-10-03 (= snapshot rangeEnd); 2026-09-30; start 10-05 end 10-04 |
| Steps | 1. Run each simulation |
| Expected result | All refused with 400. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F08-003

**Simulation needs current versions and a DRAFT campaign**

| Field | Value |
|---|---|
| Trace | UC15; FR-08.3; FR-08.4; Common 6 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | stale expectedLayoutBVersion; a PUBLISHED campaign |
| Steps | 1. Run with the stale version<br>2. Run on the PUBLISHED campaign |
| Expected result | 409 for both. |
| Priority / type | Medium / negative |
| Status | NOT RUN |

### TC-F08-004

**No evaluation transactions gives N/A rates**

| Field | Value |
|---|---|
| Trace | UC16; FR-08.3 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | range 2026-12-01..2026-12-02 (no data) |
| Steps | 1. Run the simulation |
| Expected result | Report created; hitRate, bundleConversion and capturedUtility rates null shown as N/A; priorityCoverage still numeric. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F08-005

**Reports are immutable and pinned to layout versions**

| Field | Value |
|---|---|
| Trace | FR-08.3; BR-13; UC25 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); report R from the B version v |
| Test data | R |
| Steps | 1. Edit B (version v+1)<br>2. Reopen R<br>3. Record a decision with report R |
| Expected result | R is unchanged and still cites version v. The decision with R is refused (409) because it no longer matches the current versions. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F08-006

**Decide and publish**

| Field | Value |
|---|---|
| Trace | UC14; FR-08.1; BR-12 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); DRAFT campaign with A and B READY and a matching report |
| Test data | decision CHOOSE_B with reportId |
| Steps | 1. Record the decision<br>2. Publish<br>3. Open the storefront home |
| Expected result | Campaign PUBLISHED; the storefront and active layout are unchanged (BR-12). |
| Priority / type | High / state transition |
| Status | NOT RUN |

### TC-F08-007

**Publish requires a decision and a valid READY layout**

| Field | Value |
|---|---|
| Trace | UC14; FR-08.1 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | campaign with no decision; campaign whose chosen B is not READY |
| Steps | 1. Publish each |
| Expected result | 409 for both. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F08-008

**Withdraw a published campaign**

| Field | Value |
|---|---|
| Trace | UC14; FR-08.1 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); PUBLISHED campaign |
| Test data | campaign |
| Steps | 1. Withdraw<br>2. Withdraw again<br>3. Publish without a new decision |
| Expected result | First: DRAFT with decision and selection cleared. Second (on DRAFT): 409. Publish: 409 until a new decision is made. |
| Priority / type | High / state transition |
| Status | NOT RUN |

### TC-F08-009

**Store Admin activates a published campaign**

| Field | Value |
|---|---|
| Trace | UC23; FR-08.1; BR-12 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); PUBLISHED campaign C1 choosing B |
| Test data | C1 |
| Steps | 1. Open the campaign in /store/layout (F07/F08 workspace) and activate C1<br>2. [API] GET /api/storefront/active-layout<br>3. [API] GET /api/storefront/foodmart-benchmark/home |
| Expected result | C1 ACTIVE; active-layout returns B; the public home shows B's zones. |
| Priority / type | High / state transition |
| Status | NOT RUN |

### TC-F08-010

**Only one ACTIVE campaign per Store**

| Field | Value |
|---|---|
| Trace | FR-08.2; BR-21 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); C1 ACTIVE; C2 PUBLISHED |
| Test data | C2 |
| Steps | 1. Activate C2<br>2. Open both campaigns |
| Expected result | C2 ACTIVE; C1 ENDED automatically; exactly one ACTIVE campaign. |
| Priority / type | High / state transition |
| Status | NOT RUN |

### TC-F08-011

**Activation rechecks validity**

| Field | Value |
|---|---|
| Trace | FR-08.2 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); PUBLISHED campaign whose B product lost all stock after publish |
| Test data | campaign |
| Steps | 1. Activate |
| Expected result | 409; the campaign stays PUBLISHED; the previous ACTIVE campaign is untouched. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F08-012

**End the active campaign**

| Field | Value |
|---|---|
| Trace | UC23; FR-08.1 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); C2 ACTIVE |
| Test data | C2 |
| Steps | 1. End C2<br>2. [API] GET active-layout |
| Expected result | C2 ENDED; active-layout returns defaultLayout true; F09 home falls back to DEFAULT_CATALOG. |
| Priority / type | High / state transition |
| Status | NOT RUN |

### TC-F08-013

**Rollback restores the previous campaign or the default**

| Field | Value |
|---|---|
| Trace | UC23; FR-08.1 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); C1 ENDED by C2's activation; C2 ACTIVE; Store B with a single ACTIVE campaign |
| Test data | C2; Store B campaign |
| Steps | 1. Roll back C2<br>2. Store Admin B rolls back its only campaign |
| Expected result | C2 ENDED and C1 ACTIVE again. Store B: the default layout is restored (no previous campaign). |
| Priority / type | High / state transition |
| Status | NOT RUN |

### TC-F08-014

**Invalid campaign transitions (state table)**

| Field | Value |
|---|---|
| Trace | FR-08.1 |
| Role | Merchandiser, Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | activate DRAFT; activate ENDED; publish ACTIVE; decide on PUBLISHED; edit B of a PUBLISHED campaign; end DRAFT; simulate ENDED |
| Steps | 1. Try each action |
| Expected result | Each returns 409; status unchanged. |
| Priority / type | High / state transition |
| Status | NOT RUN |

### TC-F08-015

**Stale campaign version**

| Field | Value |
|---|---|
| Trace | Common 6; FR-08.1 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); PUBLISHED campaign open in two tabs |
| Test data | two activate clicks |
| Steps | 1. Activate in tab 1<br>2. Activate in tab 2 |
| Expected result | Tab 2: 409; no double activation. |
| Priority / type | Medium / negative |
| Status | NOT RUN |

### TC-F08-016

**F08 RBAC (decision table)**

| Field | Value |
|---|---|
| Trace | UC14; UC23; NFR 5.4 |
| Role | Store Admin, Merchandiser, System Admin, End Customer |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); SETUP-B |
| Test data | campaign of Store A |
| Steps | 1. [API] Store Admin: POST campaign, simulate, decide, publish, withdraw<br>2. [API] Merchandiser: activate, end, rollback<br>3. [API] System Admin and End Customer: GET /api/campaigns<br>4. [API] Store Admin B: GET the Store A campaign and report |
| Expected result | Store Admin creation/simulation/decision/publish/withdraw 403; Merchandiser lifecycle 403; System Admin and End Customer 403; Store Admin B 404. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F08-017

**View reports and the A/B labels**

| Field | Value |
|---|---|
| Trace | UC25; BR-22 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); reports exist |
| Test data | campaign reports |
| Steps | 1. Open the campaign's reports and one report |
| Expected result | Both roles can read. Scores are labelled `Weighted utility (snapshot)`, not revenue or profit; results are labelled simulated; no CTR or revenue figures. |
| Priority / type | Medium / UI |
| Status | NOT RUN |

### TC-F08-018

**Campaign list paging**

| Field | Value |
|---|---|
| Trace | UC14; NFR 5.2 |
| Role | Merchandiser |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); > 20 campaigns |
| Test data | pageSize 20, 100, 101 |
| Steps | 1. Page through the campaign list |
| Expected result | Totals consistent; 101 returns 400. |
| Priority / type | Low / boundary |
| Status | NOT RUN |

### TC-F08-019

**Concurrent activation is serialised**

| Field | Value |
|---|---|
| Trace | FR-08.2; BR-21; NFR 5.3 |
| Role | Store Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); two PUBLISHED campaigns |
| Test data | C3, C4 |
| Steps | 1. [API] Activate C3 and C4 in parallel |
| Expected result | Exactly one ACTIVE campaign afterwards; the other is ENDED or its activation returns 409. |
| Priority / type | Low / negative |
| Status | NOT RUN |

## F09

**Public storefront** — 13 cases.

| ID | Title | Priority | Type |
|---|---|---|---|
| [TC-F09-001](#tc-f09-001) | Public Store list shows ACTIVE Stores only | High | functional |
| [TC-F09-002](#tc-f09-002) | Home shows the ACTIVE campaign's layout | High | functional |
| [TC-F09-003](#tc-f09-003) | Home falls back to the catalogue; draft and ended layouts never show | High | state transition |
| [TC-F09-004](#tc-f09-004) | INACTIVE or unknown Store returns 404 | High | security |
| [TC-F09-005](#tc-f09-005) | Public product list | Medium | boundary |
| [TC-F09-006](#tc-f09-006) | Public product detail hides INACTIVE, deleted and foreign products | High | security |
| [TC-F09-007](#tc-f09-007) | Availability boundaries | High | boundary |
| [TC-F09-008](#tc-f09-008) | Public DTOs expose no admin data | High | security |
| [TC-F09-009](#tc-f09-009) | Blocks with ineligible products | High | decision table |
| [TC-F09-010](#tc-f09-010) | Sourced products need a still-approved recommendation | Medium | functional |
| [TC-F09-011](#tc-f09-011) | No storefront write routes | Low | negative |
| [TC-F09-012](#tc-f09-012) | Home needs configured zone caps | Low | negative |
| [TC-F09-013](#tc-f09-013) | Storefront prices: API keeps decimals, UI shows whole đồng HALF_UP | Medium | boundary |

### TC-F09-001

**Public Store list shows ACTIVE Stores only**

| Field | Value |
|---|---|
| Trace | UC26; FR-09.1; BR-25 |
| Role | Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); Store C INACTIVE |
| Test data | — |
| Steps | 1. [API] GET /api/storefront/stores without a token (FE /storefront is still mock data) |
| Expected result | 200; Store A and B with storeSlug and name only (no storeId); Store C absent. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F09-002

**Home shows the ACTIVE campaign's layout**

| Field | Value |
|---|---|
| Trace | UC26; UC28; FR-09.2 |
| Role | Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); C1 ACTIVE with B (BUNDLE, CROSS_SELL, FBT, BANNER blocks) |
| Test data | slug foodmart-benchmark |
| Steps | 1. [API] GET /api/storefront/foodmart-benchmark/home |
| Expected result | campaignName C1; one HomePlacement per zone and block in stored order; type is the zone name; no BASELINE; no recommendation IDs, policy, review data or utility. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F09-003

**Home falls back to the catalogue; draft and ended layouts never show**

| Field | Value |
|---|---|
| Trace | FR-09.2 |
| Role | Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); no ACTIVE campaign; DRAFT, PUBLISHED and ENDED campaigns exist |
| Test data | slug foodmart-benchmark |
| Steps | 1. [API] GET …/home |
| Expected result | fallbackType DEFAULT_CATALOG; no zones; ACTIVE non-deleted products grouped by category, ordered by name then productId. |
| Priority / type | High / state transition |
| Status | NOT RUN |

### TC-F09-004

**INACTIVE or unknown Store returns 404**

| Field | Value |
|---|---|
| Trace | FR-09.1; BR-25; DEC-20261125-04 |
| Role | Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | slug qa-store-c (INACTIVE), slug no-such-store; then reactivate Store C |
| Steps | 1. [API] GET …/qa-store-c/home and /products<br>2. [API] GET …/no-such-store/home<br>3. [API] Reactivate Store C and repeat |
| Expected result | 404 `Storefront not found` for both; after reactivation 200. No campaign changed. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F09-005

**Public product list**

| Field | Value |
|---|---|
| Trace | UC27; FR-09.3; BR-33 |
| Role | Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); one soft-deleted and one INACTIVE product in Store A |
| Test data | search `item 01`; sortBy sellingPrice desc; pageSize 100 / 101; sortBy costPrice |
| Steps | 1. [API] GET …/products with each query |
| Expected result | Only ACTIVE non-deleted products; totals exclude the deleted one. pageSize 101 and sortBy costPrice: 400. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F09-006

**Public product detail hides INACTIVE, deleted and foreign products**

| Field | Value |
|---|---|
| Trace | UC27; BR-01 |
| Role | Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); SETUP-B |
| Test data | INACTIVE product; soft-deleted product; a Store B productId under slug foodmart-benchmark |
| Steps | 1. [API] GET …/products/{id} for each |
| Expected result | 404 `Product not found` for all three. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F09-007

**Availability boundaries**

| Field | Value |
|---|---|
| Trace | FR-09.3; BR-32 |
| Role | Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | products whose only batch is: ACTIVE expiring today; ACTIVE expired yesterday; BLOCKED; requiresExpiry with no expiry date; ACTIVE remaining 0 |
| Steps | 1. [API] GET each product detail |
| Expected result | Expiring today: AVAILABLE. All others: OUT_OF_STOCK. No quantity is returned. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F09-008

**Public DTOs expose no admin data**

| Field | Value |
|---|---|
| Trace | FR-09.4; BR-25; NFR 5.4 |
| Role | Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | home, list and detail responses |
| Steps | 1. [API] Inspect the three responses |
| Expected result | No costPrice, stockQuantity, batches, SKU-level inventory, recommendation evidence, review history or utility; storeId absent. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F09-009

**Blocks with ineligible products**

| Field | Value |
|---|---|
| Trace | UC28; FR-09.3; BR-19 |
| Role | Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); active B: BUNDLE block 1 with products P1 (out of stock) and P2 (available); block 2 with only out-of-stock products |
| Test data | slug foodmart-benchmark |
| Steps | 1. [API] GET …/home |
| Expected result | Block 1 shown with P2 only; block 2 omitted. |
| Priority / type | High / decision table |
| Status | NOT RUN |

### TC-F09-010

**Sourced products need a still-approved recommendation**

| Field | Value |
|---|---|
| Trace | UC28; BR-19 |
| Role | Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); active B sourced from recommendation R; then R's product stock drops below R's minimumSellableQuantity |
| Test data | R |
| Steps | 1. [API] GET …/home |
| Expected result | Products from R that fail the check are hidden; manual placements unaffected. |
| Priority / type | Medium / functional |
| Status | NOT RUN |

### TC-F09-011

**No storefront write routes**

| Field | Value |
|---|---|
| Trace | FR-09.1; DEC-20261125-04 |
| Role | System Admin |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded) |
| Test data | PUT /api/system/stores/{id}/storefront-visibility; POST /api/storefront/stores |
| Steps | 1. [API] Call both |
| Expected result | 404 or 405; Store visibility follows Store status only. |
| Priority / type | Low / negative |
| Status | NOT RUN |

### TC-F09-012

**Home needs configured zone caps**

| Field | Value |
|---|---|
| Trace | FR-09.2; DEC-20261119-06 |
| Role | Guest (anonymous) |
| Preconditions | Test instance started with a zone cap override of 0 for BUNDLE |
| Test data | slug foodmart-benchmark |
| Steps | 1. [API] GET …/home |
| Expected result | 503 `Storefront layout capacities are not configured`. |
| Priority / type | Low / negative |
| Status | NOT RUN |

### TC-F09-013

**Storefront prices: API keeps decimals, UI shows whole đồng HALF_UP**

| Field | Value |
|---|---|
| Trace | FR-09.3; FR-09.4; DEC-20261126-02 |
| Role | Guest (anonymous) |
| Preconditions | ENV-1 (local Docker stack, demo seed loaded); Store A products with sellingPrice 15000.50 and 15000.49 |
| Test data | slug foodmart-benchmark |
| Steps | 1. [API] GET …/products/{id} for both products<br>2. When the storefront FE calls the real API (Q-08): open both product pages |
| Expected result | API returns 15000.50 and 15000.49 unchanged. The storefront shows `15.001 ₫` and `15.000 ₫`. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

## F10

**Bundle cart (PLANNED)** — 16 cases.

| ID | Title | Priority | Type |
|---|---|---|---|
| [TC-F10-001](#tc-f10-001) | [PLANNED] Cart requires an End Customer | High | security |
| [TC-F10-002](#tc-f10-002) | [PLANNED] Add an item with If-Match | High | functional |
| [TC-F10-003](#tc-f10-003) | [PLANNED] If-Match header rules | High | negative |
| [TC-F10-004](#tc-f10-004) | [PLANNED] 99-unit cap per product: single-item writes are all-or-nothing | High | boundary |
| [TC-F10-005](#tc-f10-005) | [PLANNED] Quantity validation | Medium | negative |
| [TC-F10-006](#tc-f10-006) | [PLANNED] Stock is checked on add and increase only | High | boundary |
| [TC-F10-007](#tc-f10-007) | [PLANNED] Whole bundle add, all components | High | functional |
| [TC-F10-008](#tc-f10-008) | [PLANNED] Partial bundle add | High | boundary |
| [TC-F10-009](#tc-f10-009) | [PLANNED] Bundle with nothing addable is rejected | High | negative |
| [TC-F10-010](#tc-f10-010) | [PLANNED] Bundle request rules and stale keys | High | negative |
| [TC-F10-011](#tc-f10-011) | [PLANNED] Origin MANUAL when the block matches zero or several recommendations | Medium | decision table |
| [TC-F10-012](#tc-f10-012) | [PLANNED] Edit, remove and clear; one event per change | Medium | functional |
| [TC-F10-013](#tc-f10-013) | [PLANNED] Cart isolation | High | security |
| [TC-F10-014](#tc-f10-014) | [PLANNED] Hidden product line stays editable | Medium | state transition |
| [TC-F10-015](#tc-f10-015) | [PLANNED] No reservation, discount or checkout | Medium | functional |
| [TC-F10-016](#tc-f10-016) | [PLANNED] Cart money display rounds the server values HALF_UP | Medium | boundary |

### TC-F10-001

**[PLANNED] Cart requires an End Customer**

| Field | Value |
|---|---|
| Trace | FR-10.1; UC29 |
| Role | Guest (anonymous), Store Admin, End Customer |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F10 is merged. ENV-1 (local Docker stack, demo seed loaded) |
| Test data | GET /api/storefront/foodmart-benchmark/cart |
| Steps | 1. [API] GET without a token<br>2. [API] GET as Store Admin<br>3. [API] GET as End Customer with no cart yet |
| Expected result | 401; 403; 200 virtual cart: cartId null, version 0, items [], subtotal 0, updatedAt null. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F10-002

**[PLANNED] Add an item with If-Match**

| Field | Value |
|---|---|
| Trace | FR-10.1 |
| Role | End Customer |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F10 is merged. ENV-1 (local Docker stack, demo seed loaded) |
| Test data | product P1 (32000 đ), If-Match "0", {"productId":P1,"quantity":2} |
| Steps | 1. [API] POST …/cart/items |
| Expected result | 200; line P1 ×2; unitPrice = current selling price; lineTotal 64000.00 and subtotal computed by the server; version 1; currency VND. The cart UI shows `64.000 ₫`. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F10-003

**[PLANNED] If-Match header rules**

| Field | Value |
|---|---|
| Trace | FR-10.1 |
| Role | End Customer |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F10 is merged. Cart at version 3 |
| Test data | no header; W/"3"; *; "abc"; "2" (stale) |
| Steps | 1. [API] POST …/cart/items with each header |
| Expected result | Missing, weak, wildcard and malformed: 400. Stale: 409 CART_VERSION_CONFLICT; cart unchanged. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F10-004

**[PLANNED] 99-unit cap per product: single-item writes are all-or-nothing**

| Field | Value |
|---|---|
| Trace | FR-10.1; DEC-20261125-06; DEC-20261126-02 |
| Role | End Customer |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F10 is merged. P1 has ≥ 200 sellable units |
| Test data | add 99; add 100; line 98 + add 1; line 98 + add 2; PATCH 99; PATCH 100 |
| Steps | 1. [API] Run each case on a fresh cart and GET the cart after each refusal |
| Expected result | Add 99, 98+1 and PATCH 99: 200, line = 99. Add 100, 98+2 and PATCH 100: 409 CART_QUANTITY_LIMIT for the whole request; nothing is clamped (the line stays 0 or 98), the version does not change and no event is recorded. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F10-005

**[PLANNED] Quantity validation**

| Field | Value |
|---|---|
| Trace | FR-10.1 |
| Role | End Customer |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F10 is merged.  |
| Test data | quantity 0; -1; 1.5; "2"; PATCH 0 |
| Steps | 1. [API] POST and PATCH with each value |
| Expected result | 400 for each (quantity zero must use DELETE). |
| Priority / type | Medium / negative |
| Status | NOT RUN |

### TC-F10-006

**[PLANNED] Stock is checked on add and increase only**

| Field | Value |
|---|---|
| Trace | FR-10.1; FR-10.3 |
| Role | End Customer, Store Admin |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F10 is merged. P2 has 3 sellable units; line P2 ×3 |
| Test data | add 1; then Store Admin sets stock to 1; PATCH 3 → 2 |
| Steps | 1. [API] Add 1 more P2<br>2. Store Admin lowers stock<br>3. [API] Reduce the line to 2 |
| Expected result | Add: 409 INSUFFICIENT_SELLABLE_STOCK. Reduce: 200 (reductions are always allowed). |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F10-007

**[PLANNED] Whole bundle add, all components**

| Field | Value |
|---|---|
| Trace | FR-10.2; FR-10.4; UC29 |
| Role | End Customer |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F10 is merged. C1 ACTIVE; BUNDLE block (P1, P2, P3) matching exactly one APPROVED, ready BUNDLE recommendation |
| Test data | bundleKey from F09 home |
| Steps | 1. [API] POST …/cart/bundles {bundleKey} |
| Expected result | 200 BundleAddResult; components [{P1,1,1},{P2,1,1},{P3,1,1}]; lines merged; version +1; one BUNDLE_ADDED event with origin MINED and 3 units. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F10-008

**[PLANNED] Partial bundle add**

| Field | Value |
|---|---|
| Trace | FR-10.2 |
| Role | End Customer |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F10 is merged. same block; P2 out of stock; P1 already at 99 in the cart |
| Test data | bundleKey |
| Steps | 1. [API] Add the bundle |
| Expected result | 200; P1 added 0 (no room), P2 added 0 (no stock), P3 added 1; event records 1 unit. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F10-009

**[PLANNED] Bundle with nothing addable is rejected**

| Field | Value |
|---|---|
| Trace | FR-10.2; FR-10.4 |
| Role | End Customer |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F10 is merged. all components out of stock |
| Test data | bundleKey |
| Steps | 1. [API] Add the bundle |
| Expected result | 409 BUNDLE_ADD_REJECTED with components all added 0; cart and version unchanged; a BUNDLE_ADD_REJECTED event is recorded (excluded from F11). |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F10-010

**[PLANNED] Bundle request rules and stale keys**

| Field | Value |
|---|---|
| Trace | FR-10.2 |
| Role | End Customer |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F10 is merged.  |
| Test data | body with bundleQuantity; random key; key of a block after B was edited or C1 ended; key of Store B under Store A slug |
| Steps | 1. [API] Add with each |
| Expected result | bundleQuantity: 400. Random key: 404. Stale key: 409 BUNDLE_UNAVAILABLE. Foreign key: 404. No business event for these. |
| Priority / type | High / negative |
| Status | NOT RUN |

### TC-F10-011

**[PLANNED] Origin MANUAL when the block matches zero or several recommendations**

| Field | Value |
|---|---|
| Trace | FR-10.4; DEC-20261125-06 |
| Role | End Customer |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F10 is merged. block built manually; block matching two approved BUNDLE recommendations |
| Test data | two bundleKeys |
| Steps | 1. [API] Add each bundle |
| Expected result | Both events have origin MANUAL. |
| Priority / type | Medium / decision table |
| Status | NOT RUN |

### TC-F10-012

**[PLANNED] Edit, remove and clear; one event per change**

| Field | Value |
|---|---|
| Trace | FR-10.3; FR-10.4 |
| Role | End Customer |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F10 is merged. cart with 3 lines |
| Test data | PATCH line 1 to 2; PATCH it to 2 again; DELETE line 2; DELETE cart |
| Steps | 1. [API] Run each mutation |
| Expected result | Each change returns the cart and advances the version once with one event (ITEM_QUANTITY_CHANGED, ITEM_REMOVED, CART_CLEARED). The no-op PATCH keeps the version and records no event. Clear keeps the cart identity. |
| Priority / type | Medium / functional |
| Status | NOT RUN |

### TC-F10-013

**[PLANNED] Cart isolation**

| Field | Value |
|---|---|
| Trace | FR-10.1; BR-01 |
| Role | End Customer |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F10 is merged. two End Customers with carts in Store A |
| Test data | itemId of customer 2 |
| Steps | 1. [API] Customer 1 PATCH/DELETE customer 2's itemId<br>2. [API] GET cart under slug qa-store-c (INACTIVE) |
| Expected result | 404 for both. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F10-014

**[PLANNED] Hidden product line stays editable**

| Field | Value |
|---|---|
| Trace | FR-10.3 |
| Role | End Customer, Store Admin |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F10 is merged. line P4 in the cart; Store Admin soft-deletes or deactivates P4 |
| Test data | P4 |
| Steps | 1. [API] GET cart<br>2. [API] DELETE the P4 line |
| Expected result | Line state NO_LONGER_AVAILABLE, public fields and money null, subtotal null; removal succeeds. |
| Priority / type | Medium / state transition |
| Status | NOT RUN |

### TC-F10-015

**[PLANNED] No reservation, discount or checkout**

| Field | Value |
|---|---|
| Trace | FR-10.3; BR-26 |
| Role | End Customer, Store Admin |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F10 is merged.  |
| Test data | add 5 units of P1 |
| Steps | 1. [API] Add<br>2. Store Admin checks P1 stock<br>3. Look for checkout or discount |
| Expected result | Stock unchanged; no discount; no checkout, order or payment route. |
| Priority / type | Medium / functional |
| Status | NOT RUN |

### TC-F10-016

**[PLANNED] Cart money display rounds the server values HALF_UP**

| Field | Value |
|---|---|
| Trace | FR-10.1; DEC-20261126-02 |
| Role | End Customer |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F10 is merged. products P5 at 10000.50 đ and P6 at 10000.50 đ |
| Test data | add P5 ×1 and P6 ×1 |
| Steps | 1. [API] Add both<br>2. Open the cart drawer |
| Expected result | API: lineTotal 10000.50 each, subtotal 20001.00 (computed by the server). UI: each line `10.001 ₫` and subtotal `20.001 ₫` — the displayed subtotal is the rounded server subtotal, not the sum of rounded lines (20.002). |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

## F11

**Dashboard (PLANNED)** — 9 cases.

| ID | Title | Priority | Type |
|---|---|---|---|
| [TC-F11-001](#tc-f11-001) | [PLANNED] Dashboard opens for Store Admin and Merchandiser | High | functional |
| [TC-F11-002](#tc-f11-002) | [PLANNED] Dashboard RBAC and INACTIVE Store | High | security |
| [TC-F11-003](#tc-f11-003) | [PLANNED] Snapshot utility panel | Medium | functional |
| [TC-F11-004](#tc-f11-004) | [PLANNED] Saved simulation panel | Medium | UI |
| [TC-F11-005](#tc-f11-005) | [PLANNED] Bundle additions: default window and counts | High | functional |
| [TC-F11-006](#tc-f11-006) | [PLANNED] Date range boundaries (maximum 90 days inclusive) | High | boundary |
| [TC-F11-007](#tc-f11-007) | [PLANNED] Campaign filter | Medium | functional |
| [TC-F11-008](#tc-f11-008) | [PLANNED] Day boundary follows Asia/Ho_Chi_Minh | Medium | boundary |
| [TC-F11-009](#tc-f11-009) | [PLANNED] One panel failing does not hide the others | Low | UI |

### TC-F11-001

**[PLANNED] Dashboard opens for Store Admin and Merchandiser**

| Field | Value |
|---|---|
| Trace | FR-11.1; UC24 |
| Role | Store Admin, Merchandiser |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F11 is merged. ENV-1 (local Docker stack, demo seed loaded) |
| Test data | /store/dashboard |
| Steps | 1. Open /store/dashboard as each role |
| Expected result | Three panels load independently: snapshot utility, saved simulated reports, bundle additions. |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F11-002

**[PLANNED] Dashboard RBAC and INACTIVE Store**

| Field | Value |
|---|---|
| Trace | FR-11.1; NFR 5.4 |
| Role | End Customer, System Admin, Guest (anonymous), Store Admin |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F11 is merged. Store C INACTIVE |
| Test data | dashboard routes |
| Steps | 1. [API] Call the dashboard routes as each role<br>2. Store Admin C opens the dashboard |
| Expected result | End Customer and System Admin 403; no token 401; Store C user 403 STORE_INACTIVE. |
| Priority / type | High / security |
| Status | NOT RUN |

### TC-F11-003

**[PLANNED] Snapshot utility panel**

| Field | Value |
|---|---|
| Trace | FR-11.1 |
| Role | Merchandiser |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F11 is merged. snapshots S1, S2 in Store A; SETUP-B |
| Test data | snapshotId S1; a Store B snapshotId; pageSize 0 |
| Steps | 1. [API] GET /api/dashboard/snapshot-utilities<br>2. [API] …?snapshotId=S1<br>3. [API] …?snapshotId=<Store B><br>4. [API] …?pageSize=0 |
| Expected result | READY snapshots newest first with totalWeightedUtility at 6 decimals labelled as a score. Store B snapshot: 404. pageSize 0: 400. |
| Priority / type | Medium / functional |
| Status | NOT RUN |

### TC-F11-004

**[PLANNED] Saved simulation panel**

| Field | Value |
|---|---|
| Trace | FR-11.1; BR-13 |
| Role | Store Admin |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F11 is merged. V4 report with priorityCoverage 0 and one with null; a report with nExceedsBaseline |
| Test data | reports |
| Steps | 1. Open the panel |
| Expected result | 0 shows 0%; null shows unavailable; the nExceedsBaseline case is labelled; results labelled simulated. |
| Priority / type | Medium / UI |
| Status | NOT RUN |

### TC-F11-005

**[PLANNED] Bundle additions: default window and counts**

| Field | Value |
|---|---|
| Trace | FR-11.1; FR-10.4 |
| Role | Store Admin |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F11 is merged. events: MINED ×1 (2 units), MANUAL ×1 (3 units), one rejection, one partial (A ×2, B ×1) |
| Test data | no dates |
| Steps | 1. [API] GET /api/dashboard/bundle-additions |
| Expected result | Last 30 local days including today; days zero-filled; counts by origin exclude rejections; totalAddedProductUnits sums actual units (partial = 1 action, 3 units). |
| Priority / type | High / functional |
| Status | NOT RUN |

### TC-F11-006

**[PLANNED] Date range boundaries (maximum 90 days inclusive)**

| Field | Value |
|---|---|
| Trace | FR-11.1; DEC-20261126-02 |
| Role | Store Admin |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F11 is merged.  |
| Test data | from 2026-09-01 to 2026-11-29 (90 days inclusive); from 2026-08-31 to 2026-11-29 (91 days); from = to (1 day); only from; from after to |
| Steps | 1. [API] Call /api/dashboard/bundle-additions with each range |
| Expected result | 90 days and 1 day: 200 with one row per day (90 and 1 rows). 91 days: 400. Only one date and an inverted range: 400. |
| Priority / type | High / boundary |
| Status | NOT RUN |

### TC-F11-007

**[PLANNED] Campaign filter**

| Field | Value |
|---|---|
| Trace | FR-11.1 |
| Role | Merchandiser |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F11 is merged. events of C1 (now ENDED); SETUP-B |
| Test data | campaignId C1; a Store B campaignId |
| Steps | 1. [API] Filter by each |
| Expected result | C1: its events are still counted. Store B campaign: 404 `Campaign not found`. |
| Priority / type | Medium / functional |
| Status | NOT RUN |

### TC-F11-008

**[PLANNED] Day boundary follows Asia/Ho_Chi_Minh**

| Field | Value |
|---|---|
| Trace | FR-11.1; Common 6 |
| Role | Store Admin |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F11 is merged. event at 23:59 local on D (16:59 UTC) and one at 00:01 local on D+1 (17:01 UTC on D) |
| Test data | from D to D+1 |
| Steps | 1. [API] GET bundle additions |
| Expected result | The first event counts on D, the second on D+1. |
| Priority / type | Medium / boundary |
| Status | NOT RUN |

### TC-F11-009

**[PLANNED] One panel failing does not hide the others**

| Field | Value |
|---|---|
| Trace | FR-11.1 |
| Role | Store Admin |
| Preconditions | Feature PLANNED (DEC-20261125-06); run when F11 is merged. bundle-additions backend made unavailable (test stub) |
| Test data | — |
| Steps | 1. Open the dashboard |
| Expected result | That panel shows an unavailable state (not zero); the other two render. |
| Priority / type | Low / UI |
| Status | NOT RUN |

# PASPM black-box test plan

| Field | Value |
|---|---|
| Version / date | 0.6 — 2026-11-27 (Asia/Ho_Chi_Minh); 0.6 applied the smoke-test follow-ups (DEC-20261127-01 End Customer landing, benchmark routes 404, known issues KI-02, KI-03 and KI-04, data item DP-10); 0.5 — 2026-11-26; 0.2 applied DEC-20261126-02, 0.3 applied DEC-20261126-03, 0.4 its Q-05 addendum (Run mining screen) and known issue KI-01, 0.5 F10/F11 cases aligned with the implemented API after merging `dev` @ `114576d` |
| Baseline | Backend `dev` @ `114576d` (merged into this branch; F10 PR #38/#44, F11 PR #45, TDP-01 PR #47); FE `dev` @ `7f298bc` (routes used to name screens); F05 mining screen checked at FE `dev` @ `0cd57a6` |
| Deliverables | [TEST_CASES.md](TEST_CASES.md) / [TEST_CASES.csv](TEST_CASES.csv) (252 cases), [TRACEABILITY.md](TRACEABILITY.md), [PO_QUESTIONS.md](PO_QUESTIONS.md) |
| Execution status | **NOT RUN.** No case has been executed. This pack contains designs only |

## 1. Scope

**In scope: implemented on `dev`**

| Feature | What is tested |
|---|---|
| F01 | Sign-in, registration, OTP, sessions (refresh, CSRF, logout), password reset/change, user and role administration, avatar; Store management (create, edit, slug, ACTIVE/INACTIVE, central `STORE_INACTIVE` guard, icon/banner) |
| F02 | Categories, products (server SKU, similar-name confirmation, soft delete), batches (generated code, expiry boundary, `effectiveStatus`, push day and `MISSING_PUSH_DAY`), inventory summary |
| F03 | λmax policy, α/β/λ/N, seasons, Weight v2 values and rounding, preview, immutable snapshots |
| F04 | CSV (sync and async) and Excel import, row and order validation, duplicates and conflicts |
| F05 | minUtil policy, EFIM submission by System Admin (`/admin/mining/run`) and Merchandiser (`/mining?view=setup`, "Run Analysis"; Store Admin: history only), progress, input bounds and int32 / whole-đồng fallback, output limit. Scheduler: only "enabling a schedule returns 503" (TC-F05-022); schedule creation and history are `[OUT OF ROUND]` |
| F06 | Generation, policy, review, live eligibility, ranking, expiry alerts |
| F07/F08 | Baseline A, Layout B editor and caps, priority coverage, simulation, campaign lifecycle, single ACTIVE campaign, rollback |
| F09 | Public storefront API by Store slug. Accepted on the API in this round; storefront UI checks come when the FE calls the real API |

**In scope, implemented after the pack was first written:** F10 bundle cart (PR #38, #44) and F11 dashboard Backend (PR #45), following [DEC-20261125-06](../../decisions/2026-11-25/DEC-20261125-06-f10-f11-mvp-contract.md) and the [F10](../../api/F10_BUNDLE_CART_API.md) and [F11](../../api/F11_DASHBOARD_API.md) API docs. Their API steps run in this round; storefront cart and dashboard UI steps run once the FE calls the real API.

**Out of scope:** automated unit, integration and MySQL tests (they run in CI from `.github/scripts/test-groups.py`); performance targets (none defined, SRS §5.2); the scheduler beyond the 503 check — enabling it is a separate later decision (BR-24, [DEC-20261126-03](../../decisions/2026-11-26/DEC-20261126-03-blackbox-test-round-scope-and-remaining-answers.md)); internal lease, fencing and crash takeover; removed features except checks that they are absent. See TRACEABILITY.md §7.

## 2. Sources and precedence

The latest PO decision wins ([DEC-20261125-02](https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/blob/Khoi/docs/source-of-truth-rule/docs/decisions/2026-11-25/DEC-20261125-02-source-of-truth-latest-po-decision.md), on branch `Khoi/docs/source-of-truth-rule`): DEC files, then merged code, then `docs/product/*` and `docs/api/*`.

- [SRS v0.4](../../product/SRS.md) (FR, UC, BR, NFR), [ARCHITECTURE_SDD](../../product/ARCHITECTURE_SDD.md) (state machines), [GLOSSARY](../../product/GLOSSARY.md).
- `docs/api/*` contracts and the [RBAC matrix](../../api/F01_F02_RBAC_MATRIX.md); `docs/frontend_handoff/*`; feature READMEs.
- DEC files up to 2026-11-25 on `dev`, including DEC-20261123-01, DEC-20261124-01/-02/-03, DEC-20261125-01/-04/-06, DEC-20261119-05/-06/-07.
- The UML pack `PASPM_Unified_UML2_Editable.drawio` (use cases, screen flow, Store state), on `dev` since PR #37 (`fb48d55`).
- [DEC-20261126-02](../../decisions/2026-11-26/DEC-20261126-02-vnd-display-cart-cap-dashboard-window-merch-screens.md): PO answers of 2026-11-26 (VND display HALF_UP, F10 single-item cap, F11 90-day window, Merchandiser screen defect).
- [DEC-20261126-03](../../decisions/2026-11-26/DEC-20261126-03-blackbox-test-round-scope-and-remaining-answers.md): remaining PO answers of 2026-11-26 (round scope, landing pages, campaign screen, image units, similar-name threshold, F10 stock refusal, Stores B/C script, exit criteria).
- Two behaviours the docs leave open were read from the code (DEC-20261125-02 allows reporting, not silently choosing): editing Layout A returns 409 `Layout A is immutable and read-only`; repeated SKU lines with a different price, cost or status are a row error.

## 3. Design techniques

| Technique | Where |
|---|---|
| Equivalence partitioning | Valid / invalid inputs for every form and request body; strict JSON (unknown field → 400) |
| Boundary values | VND display rounding (x,50 → up, x,49 → down; displayed subtotal = rounded server subtotal); expiry date = today (T), T−1, T+1, and the local-midnight edge; push day = expiry; 99-unit cap (99 accepted, 100 refused whole); image 1 MiB / 5 MiB and 4000 px; password 12–72; slug 50; Store fields; N 1–365; λ = λmax; α + β = 1; W rounding at .xx5; season start/end; mining 1,600 products / 20,000 rows / 2,000 patterns; int32 and whole-đồng HALF_UP; lookback 7–365; Idempotency-Key 128; import 5 MiB / 10,000 rows; zone caps; F11 90 days inclusive accepted / 91 refused; paging 1–100 |
| Decision tables | Role × action per feature (RBAC); role × Store binding; `MISSING_PUSH_DAY` conditions; product delete guards × campaign status; bundle origin MINED/MANUAL; block visibility |
| State transitions | Store ACTIVE ⇄ INACTIVE; batch ACTIVE ⇄ BLOCKED with computed EXPIRED; account states; layout draft → READY → new revision; campaign DRAFT → PUBLISHED → ACTIVE → ENDED, withdraw, rollback; recommendation review; λ validity |
| Negative and security | 401 / 403 / 404 per role; cross-Store access returns 404; `STORE_INACTIVE` 403 and 409; version conflicts 409; token replay; CSRF; public DTO privacy |

## 4. Environment

**ENV-1 — local Docker stack with the demo seed.**

1. Start the stack as in [LOCAL_SETUP_MACOS.md](../../setup/LOCAL_SETUP_MACOS.md) / [LOCAL_SETUP_WINDOWS.md](../../setup/LOCAL_SETUP_WINDOWS.md) and [STAGING.md](../../setup/STAGING.md): MySQL 8.4 (Flyway V1–V30), Mailpit (SMTP 1025, UI http://127.0.0.1:8025), backend on http://127.0.0.1:8080 with SPMF 2.67a on Java 25, `REFRESH_COOKIE_SECURE=false` for local HTTP.
2. Use a **disposable** database volume. Never run the pack against a teammate's database.
3. Run [`scripts/seed/seed.sh`](../../../scripts/seed/README.md) on the empty database. It creates Store A (`FoodMart Benchmark`, slug `foodmart-benchmark`), 1,559 products with batches, 4,776 orders dated 2026-10-01 to 2026-10-04 (4,141 random FoodMart orders and 635 co-purchase orders), 4 seasons and α 0.6 / β 0.4 / λ 0.3 / N 30, and sets the System Admin mining policy to `PERCENTAGE 0.12` (see DP-03).
4. Frontend: FE `dev` started against the backend (same-origin proxy). F09 FE is still mock data, so F09 is accepted on the API in this round (DEC-20261126-03).
5. Keep `paspm.mining.schedule-dispatch-enabled` unset (scheduler enable must return 503).
6. Cases that need a special backend start (short token TTL, timeout 1 ms, no SPMF jar, zone cap 0) say so in their preconditions; use a separate test instance and restore defaults afterwards.
7. API steps: Swagger at `/swagger` or curl with the role's bearer token. Record the exact request and response status.

**Clock.** `T` means today in `Asia/Ho_Chi_Minh` on the day of execution. Expiry and push-day data are created relative to T on that day. TC-F02-023 must run between 00:00 and 07:00 local time.

## 5. Roles and accounts

| Account | Role | Store | Source |
|---|---|---|---|
| ACC-SA | System Admin | — | `SEED_SYSADMIN_EMAIL` (bootstrap account) |
| ACC-STA-A | Store Admin | A | `storeadmin@paspm.example` (seed) |
| ACC-MER-A | Merchandiser | A | `merchandiser@paspm.example` (seed) |
| ACC-EC | End Customer | — | `customer@paspm.example` (seed) |
| ACC-STA-B, ACC-MER-B | Store Admin, Merchandiser | B | `qa-storeadmin-b@paspm.example`, `qa-merchandiser-b@paspm.example` (created by TDP-01) |
| ACC-STA-C, ACC-MER-C | Store Admin, Merchandiser | C | `qa-storeadmin-c@paspm.example`, `qa-merchandiser-c@paspm.example` (created by TDP-01) |
| ACC-PENDING | Store Admin, not activated | A | invited in TC-F01-019 setup, never activated |
| ACC-EC2 | second End Customer | — | self-registered (F10 isolation) |

Passwords come from the tester's local `scripts/seed/.env.seed` (git-ignored) or are typed when the account is created. Never write a password, token, OTP or cookie value into this pack or into result records.

## 6. Test data setup

| ID | Setup | How |
|---|---|---|
| SETUP-B | Store B `QA Store B` (slug `qa-store-b`), ACC-STA-B and ACC-MER-B, 1 category, 3 products with batches | Store and accounts: **TDP-01** (below; testers do not create Stores by hand). Catalogue: Store Admin B in the UI |
| DP-01 | QA products in Store A: `QA Milk 1L` (requiresExpiry, category `QA Dairy`), `QA Rice 5kg` (no expiry) | Store Admin A, TC-F02-001/003 |
| DP-02 | Snapshot S1: rangeStart 2026-10-01, rangeEnd 2026-10-03, asOfDate 2026-10-03 | Merchandiser A on `/f03/snapshot`. 2026-10-04 stays free for simulation (TC-F08-001) |
| DP-03 | Mining policy `PERCENTAGE 0.12` | **Set by the seed** (ROC-20261127-004): the tester does not set it by hand; check that the System Admin screen or `GET /api/system/mining/config` shows `PERCENTAGE 0.12` before mining. Measured on 2026-11-27 on a fresh seed: 182 patterns on the full range 2026-10-01..04 and **183 patterns on S1** (DP-02), `LOSSLESS`, about 0.5 s; the same multi-product patterns from 0.14 to 0.08 (SRS §11 item 4, ROC-20261127-002). Record the pattern count for S1; a different count means the seed or the policy differs |
| DP-04 | Completed mining request R1 on S1 | System Admin or Merchandiser A |
| DP-05 | F06 candidates from R1 (all four types; the demo seed gives 17 BUNDLE, 41 CROSS_SELL, 5 FBT and 183 BANNER on S1 at 0.12, ROC-20261127-002), at least two BUNDLE approved; controlled datasets for the F06 threshold cases | Merchandiser A; the threshold cases need a small hand-built Store (40 transactions) |
| DP-06 | Campaigns in each status (DRAFT, PUBLISHED, ACTIVE, ENDED) using DP-05 | Merchandiser A and Store Admin A |
| DP-07 | Store C `QA Store C` (slug `qa-store-c`) with ACC-STA-C, ACC-MER-C, a snapshot, a mining request (key KC) and an icon; deactivated when a case says so | Store and accounts: **TDP-01**. Snapshot, request and icon: the Store C users and the System Admin in the UI / API |
| DP-08 | Synthetic Store D for F05 bounds: snapshots with 1,600 / 1,601 products, 20,000 / 20,001 rows, an int32 lossless / whole-đồng / overflow trio, and a 2,000 / 2,001 pattern pair | Generated CSV imports; build once and keep the files outside the repository |
| DP-09 | Import files `qa_valid.csv`, `qa_conflict.csv`, `qa_valid.xlsx/.xls`, boundary files (5 MiB, 10,000 / 10,001 rows), files with bad rows | Created by the tester from the F04 template |
| DP-10 | **Matrix-row budget of the demo Store (Store A)** | On a fresh seed the full range 2026-10-01..04 holds **19,932 of the 20,000 mining matrix rows**. Any extra order dated 2026-10-01..04 in Store A (an F04 import case, a manual order) can push a snapshot over the limit, and F05 then answers 400 `Snapshot matrix exceeds mining row limit`. Therefore: F04 import cases run on the TDP-01 Stores B and C, or use dates outside 2026-10-01..04; and snapshots on Store A that use the full range (DP-02, DP-03, DP-04) are made on a **fresh seed only**, before any import case touches Store A (ROC-20261127-002) |

### TDP-01 — test-data preparation script (implemented: `scripts/seed/seed_test_stores.py`, PR #47, ROC-20261126-013)

Decided by DEC-20261126-03 (Q-16). The script is a separate small code task and is **not** part of this documentation branch. Required behaviour:

- Runs against the disposable ENV-1 backend through the public API only (no SQL), signed in as the System Admin.
- Creates Store B (`QA Store B`, `qa-store-b`) and Store C (`QA Store C`, `qa-store-c`) with `POST /api/system/stores`, and one Store Admin and one Merchandiser per Store (accounts in §5) through `POST /api/users`, activating each invitation with the OTP read from Mailpit (the `You're invited to PASPM` e-mail, DEC-20261128-01).
- Reads passwords from environment variables (as `scripts/seed/.env.seed` does); never prints or writes them.
- Is safe to run twice: finds existing Stores by slug and accounts by email before creating anything.
- Prints the created Store IDs and account emails for the run record.

## 7. Entry and exit criteria

**Entry**

- ENV-1 is up, Flyway reports V31, the seed run finished without error and its record counts match `scripts/seed/README.md`.
- **TDP-01 has run** on the ENV-1 database (Stores B and C with their accounts exist), and the other DP items a case needs are prepared.
- **DEC-20261125-02 is merged into `dev`** (source-of-truth rule, Q-01).
- **Known defect KD-01 fixed and verified on FE `dev`** (see §9): a Merchandiser can open every screen the role is allowed to use (F03, F05 mining reads, F06 recommendations, F07/F08 workspace). DEC-20261126-02 §2 makes this an entry criterion.
- **Known defect KD-02 fixed and verified on FE `dev`** (see §9): the removed screens `/f03/expiry-window`, `/f03/product-scores` and `/admin/benchmark` cannot be reached (DEC-20261126-03).
- All PO questions are answered (PO_QUESTIONS.md; DEC-20261126-02 and DEC-20261126-03).
- F10/F11 UI steps: run only when the FE calls the real cart and dashboard APIs; until then record those steps as NOT RUN with the reason "FE not integrated".

**Exit (accepted by the PO, DEC-20261126-03)**

- Every case has a recorded result (PASS, FAIL, BLOCKED or NOT RUN with a reason). `[OUT OF ROUND]` cases (TC-F05-021, TC-F05-023) are recorded as NOT RUN with the reason "out of round (DEC-20261126-03)".
- 100 % of High cases PASS; at least 95 % of Medium cases PASS.
- No open defect of severity Critical or Major; Minor defects have an owner and a target.
- Every FAIL links to a defect, and every requirement in TRACEABILITY.md has at least one executed case or a documented reason.

## 8. Recording results

- Do not edit the case definitions to record results. Copy `TEST_CASES.csv` to `docs/testing/blackbox/runs/<YYYY-MM-DD>-<tester>.csv` and add the columns `Result` (PASS / FAIL / BLOCKED / NOT RUN), `Executed at` (local date-time), `Build` (backend and FE commits), `Evidence` (relative path to screenshots or request/response logs under `docs/testing/blackbox/runs/evidence/<date>/`) and `Defect`.
- Keep local, CI and manual evidence separate, as `docs/testing/README.md` requires. A result without evidence stays NOT RUN.
- Redact tokens, cookies, OTP codes and passwords from every screenshot and log before saving it.
- A failed case means a defect: open a GitHub issue with the case ID, steps, actual vs expected result and the build. If the contract itself is unclear, raise a PO question instead of "fixing" the expected result.
- After each cycle, add a record-of-changes entry with the counts per feature and result.

## 9. Known defects and risks

**Known defects (must be fixed before the run)**

| ID | Defect | Source | Blocks |
|---|---|---|---|
| KD-01 | FE `dev`: the Merchandiser cannot open several screens its role allows. Post-login role mapping grants F03 only and the sidebar is disabled, although the screen owner map assigns Merchandiser routes (noted on the R2-03 screen flow). The backend already allows the role | DEC-20261126-02 §2 (PO: defect, not intended) | UI steps of the Merchandiser cases in F05–F08 |
| KD-02 | FE `dev` still has routes for removed features: `/f03/expiry-window`, `/f03/product-scores`, `/admin/benchmark` | DEC-20261126-03 (PO: tests assert they are unreachable; fixed together with KD-01) | TC-F03-028, TC-F05-025 |

**Known issues (do not block the round)**

| ID | Issue | Source | Affects | Workaround |
|---|---|---|---|---|
| KI-01 | FE: the browser test for selecting a Store **with the keyboard** on Users & Roles (`/admin/users`) fails | Reported by Codex (FE browser test); not reproduced in this pack | Store selection in TC-F01-019, TC-F01-020, TC-F01-022, TC-F01-040; keyboard use in TC-F01-051 | Select the Store with the mouse and note KI-01 in the result; retest keyboard selection when fixed |
| KI-02 | Backend: the **first e-mail sent after a cold start** can fail with `503 {"error":"Email delivery unavailable; retry later"}` (admin invitation `POST /api/users`, OTP request or resend). The same request succeeds when repeated a moment later. The cause is not investigated | Live smoke test 2026-11-27 (defect #5); seen again on the first TDP-01 invitation after a backend restart (ROC-20261127-002) | Any case or script that sends an e-mail first after a backend start: TC-F01-009, TC-F01-019, TC-F01-021, TDP-01 | **Demo and test tip: after every backend start, send one warm-up invitation or OTP request to a throwaway address before the real work.** If a case still gets the 503, repeat it once and note KI-02 in the result. TDP-01 is safe to run again (idempotent) |
| KI-03 | Backend (by design): sign-in is rate-limited **per e-mail (10) and per IP (30) in 15 minutes** (900 s). Every attempt counts, **successful sign-ins too**. The 11th attempt for one e-mail returns `429 {"error":"Too many attempts; try later"}`. The counters are in memory, per instance, and a backend restart clears them | `auth/AuthService.java` (`login-ip`, `login-user`), SRS §5.4; seen during the smoke test (repeated sign-ins of the same account) | TC-F01-011 (the intended test); any case or demo that signs the same account in more than 10 times in 15 minutes | Reuse one signed-in session per role instead of signing in again; do not run TC-F01-011 on an account another case needs in the same 15 minutes (use a throwaway e-mail); wait 15 minutes or restart a **disposable** test backend |
| KI-04 | Demo data (by design of the limits): the full range 2026-10-01..04 of Store A uses 19,932 of the 20,000 matrix rows (about 68 rows of room). Adding orders in that range, or reusing a Store A that other cases changed, makes mining return `400 Snapshot matrix exceeds mining row limit` | `scripts/seed/README.md`, ROC-20261127-002; SRS §3.6.1 (20,000 rows) | F04 import cases (TC-F04-*) that import into Store A in 2026-10-01..04; DP-02, DP-03, DP-04 and every case that needs snapshot S1 or R1 | Run F04 imports on Store B or C (TDP-01), or with dates outside 2026-10-01..04; prepare S1 and R1 first on a fresh seed; if the limit is hit, reseed on a new disposable database |

**Risks**

- TDP-01 is not written yet; every isolation and INACTIVE case is BLOCKED until it exists.
- If KD-01 or KD-02 is not fixed in time, the affected UI cases are BLOCKED (not run with API steps instead). F09 uses API steps by decision, not as a workaround.
- Date-dependent cases must be prepared on the day they run.
- DP-08 datasets take time to build; F05 boundary cases may be BLOCKED until they exist.

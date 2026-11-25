# ROC-20261126-009 — TDP-01 test-data preparation script (Store B / Store C)

| Field | Value |
|---|---|
| Record ID | ROC-20261126-009 |
| Date / timezone | 2026-11-26 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Opus 5.5), requested by Nguyễn Minh Khôi |
| Type | Implementation (test tooling) |
| Features | None in the backend runtime; supports the black-box test round (SETUP-B, DP-07) |
| Branch / base | `Khoi/feat/tdp-01-test-store-seed` / `origin/dev` @ `fb48d55` |
| Execution status | COMPLETED |
| Release | UNRELEASED (not committed to a remote; no PR) |
| Daily index | [2026-11-26](README.md) |

## 1. Request and scope

[DEC-20261126-03](../../decisions/2026-11-26/DEC-20261126-03-blackbox-test-round-scope-and-remaining-answers.md) (Q-16, on branch `Khoi/docs/blackbox-test-cases`) decided that black-box testers do not create Stores by hand; the script TDP-01 of TEST_PLAN §6 does. Scope: the script, its tests, README and env example only. No backend code, API or migration change.

## 2. Change details

### Added

- `scripts/seed/seed_test_stores.py` and `seed_test_stores.sh`: through the public REST API only, signed in as the System Admin, creates Store B `QA Store B` (`qa-store-b`) and Store C `QA Store C` (`qa-store-c`) with `POST /api/system/stores`, and per Store a Store Admin and a Merchandiser (`qa-storeadmin-b|c@paspm.example`, `qa-merchandiser-b|c@paspm.example`) with `POST /api/users`, activated by `POST /api/auth/activate` with the `ADMIN_INVITE` OTP read from Mailpit (same pattern as `seed_demo.py`, whose module cannot be imported because it runs at import time, so the few helpers are repeated).
- Idempotent: Store found by slug (`status=ALL`), account found by a login with the configured password; a pending account gets a new OTP instead of a second invitation; an INACTIVE Store, or an existing account that rejects the configured password, stops the script. It never changes a Store status.
- Passwords from `SEED_SYSADMIN_PASSWORD` and the new `SEED_TEST_ACCOUNT_PASSWORD`; never printed or written. Output: one line per Store (with `storeId`) and account.
- `scripts/seed/tests/test_seed_test_stores.py` (8 tests, HTTP layer faked).

### Changed

- `scripts/seed/.env.seed.example`: placeholder `SEED_TEST_ACCOUNT_PASSWORD=CHANGE_ME`. `scripts/seed/README.md`: TDP-01 section and test note.

## 3. Files changed

| Path | Added / Modified | Note |
|---|---|---|
| [seed_test_stores.py](../../../scripts/seed/seed_test_stores.py) | Added | TDP-01 script |
| [seed_test_stores.sh](../../../scripts/seed/seed_test_stores.sh) | Added | wrapper (loads `.env.seed`) |
| [test_seed_test_stores.py](../../../scripts/seed/tests/test_seed_test_stores.py) | Added | unit tests |
| [.env.seed.example](../../../scripts/seed/.env.seed.example) | Modified | one placeholder |
| [README.md](../../../scripts/seed/README.md) | Modified | usage |

## 4. API and compatibility impact

None. Uses existing endpoints only.

## 5. Verification executed in this session

| Command / method | Environment | Actual result | Evidence |
|---|---|---|---|
| `python3 -I -m unittest discover -s scripts/seed/tests` | local Python 3 | PASS, 49 tests (8 new) | this session |
| `seed_test_stores.sh` on an empty database, then again | disposable stack: MySQL 8.4 container, Mailpit container, backend from this worktree (`sh ./mvnw spring-boot:run`) on private ports, one-off generated secrets | Run 1: 2 Stores (`storeId` 1, 2) and 4 accounts created, exit 0. Run 2: all 6 found, 0 created, exit 0. After both runs the `store` table had 2 rows. The output of a third run was searched for both passwords: 0 matches | this session |
| INACTIVE Store path, pending-account path | unit tests only | PASS (fake backend); NOT RUN against the real backend | test file |
| CI (`dev` PR) | GitHub | NOT RUN (no PR) | n/a |

The stack and its secrets were deleted after the run. No existing local container or database was touched.

## 7. Limitations and next actions

- `app_user` held 6 rows after the runs; I did not count it before run 1, so I do not claim the exact number of users the script added (4 accounts were created per its output).
- Not committed to a remote and no PR; opening it needs the user's request, and `dev` needs a green CI.
- TEST_PLAN §6 says TDP-01 is "planned"; update that wording on the docs branch once this lands.

## 8. Delivery

Local commit only on `Khoi/feat/tdp-01-test-store-seed`; not pushed, no PR, no deploy.

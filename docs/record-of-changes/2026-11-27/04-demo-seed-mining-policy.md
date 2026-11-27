# ROC-20261127-004 — Demo seed sets the System Admin mining policy to `PERCENTAGE 0.12`

| Field | Value |
|---|---|
| Date / timezone | 2026-11-27 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Sonnet 5.5), requested by Nguyễn Minh Khôi |
| Type | Demo seed (no Java product code, no migration, no scoring change, no API change) |
| Features | F05 mining policy (demo data); seed |
| Branch / base | `Khoi/fix/seed-demo-mining-policy` / `origin/dev` @ `1fa37ce` |
| Decision | None new. The value `PERCENTAGE 0.12` was measured in [ROC-20261127-002](02-demo-seed-copurchase-baskets.md) and SRS §11 item 4; the PO asked on 2026-11-27 that the seed set it so testers do not set it by hand |
| Release | UNRELEASED |
| Daily index | [2026-11-27](README.md) |

## Why

ROC-20261127-002 left one manual step: the System Admin had to set `PERCENTAGE 0.12` before the first mining run, otherwise the run used another (or no) policy and the measured result (182 patterns; 17 BUNDLE, 41 CROSS_SELL, 5 FBT over 2026-10-01..04) did not appear.

## What changed

- **`scripts/seed/mining_policy.py`** (new, pure, stdlib): `ensure_mining_policy(call, admin_token)`.
  1. `GET /api/system/mining/config` (version, mode, value).
  2. If the mode is `PERCENTAGE` and the value equals `0.12` as a decimal (`0.12`, `0.120000`, `"0.120000"` all count): **no write**, the version stays.
  3. Otherwise one `PUT /api/system/mining/config` with `{"minUtilMode":"PERCENTAGE","minUtilValue":0.12,"expectedVersion":<the version just read>}` (the strict request has no other field; `expectedVersion` is 0 before the first configuration). That creates one new policy version.
  4. Any error on the GET or the PUT stops with `PolicyError`: `ERROR: PUT /api/system/mining/config failed (HTTP …); the stored policy is unchanged, run the seed again`. There is no retry loop; a concurrent change (409) is reported, not overwritten blindly.
- **`seed_demo.py`** calls it at the end of `main()` with the seed's own `api` function and the System Admin token it already has (the same login as the other System Admin steps), and prints a `mining policy:` line in the seed summary. It runs even with `SEED_WEIGHT_V2=0`.
- **Not touched:** mining requests, runs, snapshots (they keep their frozen input), the F06 policy, the lambda policy, any Java code.
- **Behaviour to know:** a seed rerun sets the policy back to `PERCENTAGE 0.12` (a new version) if somebody changed it after seeding. It is stated in the README and STAGING.md.
- **Docs:** `scripts/seed/README.md` (new "Mining policy" section, table row, recipe paragraph, tests), STAGING.md, TEST_PLAN (ENV-1 step 3 and DP-03: the manual step is removed), SRS §11 item 4 and BRD_VISION open question 9 (the policy is now set by the seed).

## Files

| Path | Change |
|---|---|
| `scripts/seed/mining_policy.py` | Added |
| `scripts/seed/seed_demo.py` | Import, one call, docstring line |
| `scripts/seed/tests/test_mining_policy.py` | Added (12 tests) |
| `scripts/seed/README.md`, `docs/setup/STAGING.md`, `docs/testing/blackbox/TEST_PLAN.md`, `docs/product/SRS.md`, `docs/product/BRD_VISION.md` | Wording |
| `docs/record-of-changes/2026-11-27/04-…`, `README.md`, `docs/record-of-changes/README.md` | This record and its indexes |

## Verification

No live stack was run (a separate Codex session does the end-to-end walk-through on its own ports).

- **Seed unit tests:** `python3 -I -m unittest discover -s scripts/seed/tests`: **76 tests, 0 failures** (64 existing + 12 new).
- **The new tests** (fake `GET`/`PUT` endpoint with the real optimistic version): a different value gives exactly one `PUT` carrying the current `expectedVersion` and the new version is stored; before the first configuration `expectedVersion` is 0; a different mode with the same number is updated; already `PERCENTAGE 0.12` (several JSON spellings) makes no write; a close value (0.121) is updated; three runs in a row make exactly one write; a failing `GET` or a failing `PUT` (400, 403, 409, 500) raises `PolicyError` with the path and the status, makes no second call and leaves the stored policy unchanged; a version that changes between the read and the write is reported as 409; the seed source calls the step with its `api` function and the System Admin token. **Mutation check:** replacing the equality test by `False` made 3 tests fail; the file was restored.
- **CI backend group:** `./mvnw -B clean verify -Dtest="$(python3 .github/scripts/test-groups.py backend)"`, Java 17 (Temurin 17.0.21): **496 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS (H2; no Java test class added or changed).
- **NOT RUN:** any live seed run against a backend (idempotency is proven with the fake endpoint, not on a real stack, by instruction); the MySQL group and the integration job locally (the pull request's CI runs them; no Java changed).

## Limitations

- The idempotency proof is against a fake that mirrors the endpoint's `expectedVersion` rule, not against the real backend.
- A concurrent change between the read and the write stops the seed (409); the seed is safe to run again.

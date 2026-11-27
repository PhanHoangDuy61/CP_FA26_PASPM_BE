# ROC-20261127-003 — Smoke-test follow-ups: End Customer landing, benchmark 404, known issues

| Field | Value |
|---|---|
| Date / timezone | 2026-11-27 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Sonnet 5.5), requested by Nguyễn Minh Khôi |
| Type | Documentation (decision record, contract wording, black-box cases, known issues). No code, no migration, no API change |
| Features | F01 (landing, sign-in rate limit, invitation e-mail), F05 (removed benchmark routes), F06 (case wording) |
| Branch / base | `Khoi/docs/smoke-followups` / `origin/dev` |
| Decision | New [DEC-20261127-01](../../decisions/2026-11-27/DEC-20261127-01-end-customer-landing-customer-home.md) (End Customer landing); the other items correct documents to the code and record known issues |
| Release | UNRELEASED |
| Daily index | [2026-11-27](README.md) |

## Why

The live smoke test of 2026-11-27 compared the running system with the black-box pack and the contracts. Defects #3, #4 and #5 are documentation gaps or known issues and are handled here. Defect #1 (demo data) is handled in [ROC-20261127-002](02-demo-seed-copurchase-baskets.md), and #2 (legacy recommendations screen) and #6 (mixed-language screens) in the FE pull request #18.

## What changed

1. **Defect #3 — End Customer landing (DEC-20261127-01).** After sign-in the End Customer lands on `/customer-home` (the Store chooser), not `/storefront`. The DEC supersedes only the End Customer part of Q-06 in DEC-20261126-03; the Q-06 row there and in `PO_QUESTIONS.md` is struck through with a pointer. TC-F01-001 expects `/customer-home` and cites the new DEC.
2. **Defect #4 — benchmark routes are removed, so 404.** `F01_F02_RBAC_MATRIX.md`: both benchmark rows (`GET /api/mining/runs/{id}/benchmark` and the System Admin Store route) now say "Not mapped → 404" for every role (they said `denyAll` 403 and "Allowed"), with a dated note that the earlier `denyAll` wording and "2 legacy routes forbidden" are historical. The current contract (`F05_MINING_API.md`, "Benchmark routes … return 404") and `SecurityConfig` (no benchmark mapping) already agreed with the 404. TC-F05-025 expects 404 on both routes (it expected 403; the smoke run was the FAIL that found this).
3. **Defect #5 — known issues.** TEST_PLAN §9 adds **KI-02** (the first e-mail after a cold start can return `503 Email delivery unavailable`; repeat succeeds; tip: send one warm-up invitation or OTP request after every backend start) and **KI-03** (sign-in is limited to 10 attempts per e-mail and 30 per IP in 15 minutes, successful sign-ins included; a restart clears the counters; reuse sessions in demos and tests). SRS §5.4 now states the limit numbers. KI-02 was seen twice: the smoke test, and the first TDP-01 invitation after a backend restart (ROC-20261127-002). The cause is not investigated.
4. **TC-F06-001** step 1 opens `/recommendations` (the old `/merchandiser/recommendations` URL redirects there since the FE fix of the same day). TEST_PLAN version 0.6.
5. **Black-box pack regenerated** with `outputs/blackbox-tools` (`gen.py`, then `trace.py`, `missing: []`): still 249 cases, no ID changed; only TC-F01-001, TC-F05-025, TC-F06-001 and one TRACEABILITY line (the new DEC reference) differ. The generator reproduced the committed files byte for byte before the edit.

## Files

| Path | Change |
|---|---|
| `docs/decisions/2026-11-27/DEC-20261127-01-end-customer-landing-customer-home.md` | Added |
| `docs/decisions/2026-11-26/DEC-20261126-03-…md` | Q-06 End Customer text struck through, pointer to the new DEC |
| `docs/testing/blackbox/PO_QUESTIONS.md` | Q-06 updated |
| `docs/testing/blackbox/TEST_PLAN.md` | Version 0.6; KI-02 and KI-03 |
| `docs/testing/blackbox/TEST_CASES.csv`, `TEST_CASES.md`, `TRACEABILITY.md` | Regenerated (3 cases + 1 line) |
| `docs/api/F01_F02_RBAC_MATRIX.md` | Benchmark rows and a dated note |
| `docs/product/SRS.md` | §5.4 rate-limit numbers |

## Verification

Documentation only. Checked by reading the code and the smoke evidence: `SecurityConfig` has no benchmark mapping; the smoke result for `/api/mining/runs/1/benchmark` was `404` with a Merchandiser token (a System Admin call on the second route was not executed; the route is documented as not mapped); `AuthService.login` calls `limits.hit("login-ip:…", 30, 900)` and `limits.hit("login-user:…", 10, 900)` before authenticating; the FE sets `defaultScreen: 'customer-home'` for the End Customer. No test was run for this record (no code changed). The black-box cases remain designs and were not executed.

## Limitations

- KI-02's cause is unknown; no fix is proposed here.
- The 404 on the System Admin benchmark route is stated from the code and the contract, not from a live call.
- Anonymous requests to the removed routes were not tried (the Spring Security default may answer 401 before the 404).

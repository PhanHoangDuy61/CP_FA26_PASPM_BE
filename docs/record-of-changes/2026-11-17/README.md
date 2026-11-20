# ROC-20261117 — F05 dense acceptance and demo seed

2026-11-17 / Asia/Ho_Chi_Minh. UNRELEASED, local-only.

> **Status update 2026-11-19:** Records 013–015 are merged into `dev`: 013 by `705e3d7`, 014 by `ac29811`, 015 by `530651b` (all 2026-11-17). Statements below such as "local commits only", "nothing pushed" or "uncommitted" describe the state when each record was written. See [ROC-20261119-001](../2026-11-19/01-record-owner-decisions.md).

## Record 013 — F05 dense fixture, pattern cap, JPA clone and output bound

[Record 013](13-f05-dense-acceptance.md) follows [Record 012](../2026-11-16/12-f05-foodmart-scale.md) without editing its historical conclusions. It adds a 4,100-order / 18,040-row / 1,559-product synthetic F04 → F03 → F05 → F06 acceptance, proves the 2,000-pattern failure behavior and F06 rejection, runs the five JPA clone cases (5/5), and adds a Linux/macOS kernel-level 4 MiB SPMF output ceiling. No configured limits were raised.

The later owner-requested follow-up added a realistic-utility variant: it was rejected before enqueueing because Product TWU and global total exceeded int32 under the then-current guard; EFIM/F06 for that fixture were NOT RUN. Record 015 records the owner-approved guard change and clarifies that the fixture still correctly fails on Product TWU alone. The Windows fallback was tested with mocked OS detection and a real Java child process (1/1 PASS); native Windows remains NOT RUN. All three previously skipped multi-JVM tests now have passing class evidence (6/6, 12/12, 5/5); the first combined invocation had six environment-configuration failures, followed by a corrected 6/6 rerun. See the dated addendum in Record 013 and the final implementation evidence in Record 015.

At the Record 013 checkpoint, the then-current full verifier was not rerun after those follow-up test additions. Record 015 later ran the full verifier against the EFIM-aware implementation and reports its results separately. Authentic FoodMart and target-server/hosted CI acceptance remain NOT RUN. Record 013 had two proposed local commits; Record 015 is still uncommitted. No push, merge or deployment occurred.

## Record 014 — Demo seed for F03 Weight v2 and F06

[Record 014](14-demo-seed-weight-v2.md) makes the demo seed exercise Weight v2: 551 products that require expiry with expiry dates and push days, four seasons assigned to product blocks, and the weight configuration α 0.6 / β 0.4 / λ 0.3 / N 30; deterministic, idempotent, switchable (`SEED_WEIGHT_V2=0`), 29 unit tests. Verified on a disposable MySQL 8.4 stack: W > 1 for 624 products (853 with a 48-day window, W up to 1.28), 91 due push batches, 0 `NEEDS_RECHECK`. Findings: no seeded product required expiry, existing batches cannot be dated through the API (recreate the database), the orders are only dated 2026-10-01..04, and F05 accepts only single-day snapshots (int32); recommended range 2026-10-02 with as-of 2026-10-05. Local commits only; nothing pushed.

## Record 015 — F05 EFIM-aware lossless int32 guard

[Record 015](15-f05-efim-aware-int32-guard.md) implements owner decision DEC-20261117-01: preserve exact scale-6/GCD conversion, remove the global-total int32 guard, and bound EFIM's transaction utility, Product TWU and minUtil accumulators. Global total and F06 validation use `long`; the API total widens to OpenAPI int64. Demo seed F03→F05→F06 passed with a total above int32; a separate realistic dense fixture remains rejected because its Product TWU exceeds int32. Full local verifier, optional 23/23 multi-JVM, and JPA 5/5 results are recorded with skips and limitations. No migration, commit, push or merge.

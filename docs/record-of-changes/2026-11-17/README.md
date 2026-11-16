# ROC-20261117 — F05 dense acceptance and demo seed

2026-11-17 / Asia/Ho_Chi_Minh. UNRELEASED, local-only.

## Record 013 — F05 dense fixture, pattern cap, JPA clone and output bound

[Record 013](13-f05-dense-acceptance.md) follows [Record 012](../2026-11-16/12-f05-foodmart-scale.md) without editing its historical conclusions. It adds a 4,100-order / 18,040-row / 1,559-product synthetic F04 → F03 → F05 → F06 acceptance, proves the 2,000-pattern failure behavior and F06 rejection, runs the five JPA clone cases (5/5), and adds a Linux/macOS kernel-level 4 MiB SPMF output ceiling. No configured limits were raised.

The later owner-requested follow-up added a realistic-utility variant: the 1,559-product snapshot was rejected before enqueueing because item TWU and total converted utility exceed int32; EFIM/F06 for that variant are NOT RUN. The Windows fallback was tested with mocked OS detection and a real Java child process (1/1 PASS); native Windows remains NOT RUN. All three previously skipped multi-JVM tests now have passing class evidence (6/6, 12/12, 5/5); the first combined invocation had six environment-configuration failures, followed by a corrected 6/6 rerun. See the dated addendum in Record 013 for exact counts and evidence.

The full verifier's earlier result remains 24/24 stages PASS and was not rerun after follow-up test additions. Authentic FoodMart and target-server/hosted CI acceptance remain NOT RUN. Two local commits are being created for this work; no push, merge or deployment is authorized.

## Record 014 — Demo seed for F03 Weight v2 and F06

[Record 014](14-demo-seed-weight-v2.md) makes the demo seed exercise Weight v2: 551 products that require expiry with expiry dates and push days, four seasons assigned to product blocks, and the weight configuration α 0.6 / β 0.4 / λ 0.3 / N 30; deterministic, idempotent, switchable (`SEED_WEIGHT_V2=0`), 29 unit tests. Verified on a disposable MySQL 8.4 stack: W > 1 for 624 products (853 with a 48-day window, W up to 1.28), 91 due push batches, 0 `NEEDS_RECHECK`. Findings: no seeded product required expiry, existing batches cannot be dated through the API (recreate the database), the orders are only dated 2026-10-01..04, and F05 accepts only single-day snapshots (int32); recommended range 2026-10-02 with as-of 2026-10-05. Local commits only; nothing pushed.

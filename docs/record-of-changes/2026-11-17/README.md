# ROC-20261117 — F05 dense acceptance

2026-11-17 / Asia/Ho_Chi_Minh. UNRELEASED, local-only.

## Record 013 — F05 dense fixture, pattern cap, JPA clone and output bound

[Record 013](13-f05-dense-acceptance.md) follows [Record 012](../2026-11-16/12-f05-foodmart-scale.md) without editing its historical conclusions. It adds a 4,100-order / 18,040-row / 1,559-product synthetic F04 → F03 → F05 → F06 acceptance, proves the 2,000-pattern failure behavior and F06 rejection, runs the five JPA clone cases (5/5), and adds a Linux/macOS kernel-level 4 MiB SPMF output ceiling. No configured limits were raised.

The later owner-requested follow-up added a realistic-utility variant: the 1,559-product snapshot was rejected before enqueueing because item TWU and total converted utility exceed int32; EFIM/F06 for that variant are NOT RUN. The Windows fallback was tested with mocked OS detection and a real Java child process (1/1 PASS); native Windows remains NOT RUN. All three previously skipped multi-JVM tests now have passing class evidence (6/6, 12/12, 5/5); the first combined invocation had six environment-configuration failures, followed by a corrected 6/6 rerun. See the dated addendum in Record 013 for exact counts and evidence.

The full verifier's earlier result remains 24/24 stages PASS and was not rerun after follow-up test additions. Authentic FoodMart and target-server/hosted CI acceptance remain NOT RUN. Two local commits are being created for this work; no push, merge or deployment is authorized.

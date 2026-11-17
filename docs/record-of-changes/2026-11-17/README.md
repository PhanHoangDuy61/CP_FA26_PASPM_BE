# ROC-20261117 — F05 dense acceptance

2026-11-17 / Asia/Ho_Chi_Minh. UNRELEASED, local-only.

## Record 013 — F05 dense fixture, pattern cap, JPA clone and output bound

[Record 013](13-f05-dense-acceptance.md) follows [Record 012](../2026-11-16/12-f05-foodmart-scale.md) without editing its historical conclusions. It adds a 4,100-order / 18,040-row / 1,559-product synthetic F04 → F03 → F05 → F06 acceptance, proves the 2,000-pattern failure behavior and F06 rejection, runs the five JPA clone cases (5/5), and adds a Linux/macOS kernel-level 4 MiB SPMF output ceiling. No configured limits were raised.

The later owner-requested follow-up added a realistic-utility variant: it was rejected before enqueueing because Product TWU and global total exceeded int32 under the then-current guard; EFIM/F06 for that fixture were NOT RUN. Record 015 records the owner-approved guard change and clarifies that the fixture still correctly fails on Product TWU alone. The Windows fallback was tested with mocked OS detection and a real Java child process (1/1 PASS); native Windows remains NOT RUN. All three previously skipped multi-JVM tests now have passing class evidence (6/6, 12/12, 5/5); the first combined invocation had six environment-configuration failures, followed by a corrected 6/6 rerun. See the dated addendum in Record 013 and the final implementation evidence in Record 015.

At the Record 013 checkpoint, the then-current full verifier was not rerun after those follow-up test additions. Record 015 later ran the full verifier against the EFIM-aware implementation and reports its results separately. Authentic FoodMart and target-server/hosted CI acceptance remain NOT RUN. Record 013 had two proposed local commits; Record 015 is still uncommitted. No push, merge or deployment occurred.

## Record 015 — F05 EFIM-aware lossless int32 guard

[Record 015](15-f05-efim-aware-int32-guard.md) implements owner decision DEC-20261117-01: preserve exact scale-6/GCD conversion, remove the global-total int32 guard, and bound EFIM's transaction utility, Product TWU and minUtil accumulators. Global total and F06 validation use `long`; the API total widens to OpenAPI int64. Demo seed F03→F05→F06 passed with a total above int32; a separate realistic dense fixture remains rejected because its Product TWU exceeds int32. Full local verifier, optional 23/23 multi-JVM, and JPA 5/5 results are recorded with skips and limitations. No migration, commit, push or merge.

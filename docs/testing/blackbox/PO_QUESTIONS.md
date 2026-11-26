# Questions for the Product Owner — black-box test pack

Raised while writing [TEST_CASES.md](TEST_CASES.md) on 2026-11-26. **All questions are answered.** Answers are recorded in [DEC-20261126-02](../../decisions/2026-11-26/DEC-20261126-02-vnd-display-cart-cap-dashboard-window-merch-screens.md) (Q-02, Q-03, Q-13, Q-14) and [DEC-20261126-03](../../decisions/2026-11-26/DEC-20261126-03-blackbox-test-round-scope-and-remaining-answers.md) (all others), and applied to the cases, [TEST_PLAN.md](TEST_PLAN.md) and [TRACEABILITY.md](TRACEABILITY.md).

| ID | Question | Answer | Decision | Applied to |
|---|---|---|---|---|
| Q-01 | Is DEC-20261125-02 (latest PO decision wins; branch `Khoi/docs/source-of-truth-rule`) the baseline for testing? | Yes. Its PR is merged into `dev` before the round | DEC-20261126-03 | TEST_PLAN.md §7 entry criteria |
| Q-02 | How is a non-integer VND amount displayed? | Whole đồng, HALF_UP (21,000.50 → `21.001 ₫`); stored values unchanged | DEC-20261126-02 §1 | TC-F02-009, TC-F09-013, TC-F10-002, TC-F10-016 |
| Q-03 | A Merchandiser cannot open several screens on FE `dev`. Intended? | No: defect KD-01, fixed before the round | DEC-20261126-02 §2 | TEST_PLAN.md §7, §9 |
| Q-04 | Should the tests assert that removed screens (`/f03/expiry-window`, `/f03/product-scores`, `/admin/benchmark`) are unreachable? | Yes. Their presence on FE `dev` is defect KD-02, fixed with KD-01 | DEC-20261126-03 | TC-F03-028, TC-F05-025; TEST_PLAN.md §9 |
| Q-05 | Is a Merchandiser "Run mining" screen in scope? | Yes — it exists on FE `dev`: "Product Combination Analysis" → `/mining?view=setup` → "Run Analysis". The Merchandiser runs mining for its own Store there; the Store Admin sees the history only. (Updated answer; the first answer "API only" is superseded) | DEC-20261126-03 addendum | TC-F05-006, TC-F05-007, TC-F05-010, TC-F05-020 |
| Q-06 | What is the landing page per role? | System Admin `/admin/overview`, Store Admin `/store/inventory`, Merchandiser its F03 workspace, End Customer `/storefront` | DEC-20261126-03 | TC-F01-001 |
| Q-07 | Which screen does the Store Admin use to activate, end and roll back a campaign? | The F07/F08 workspace `/store/layout` | DEC-20261126-03 | TC-F08-009 to TC-F08-015 |
| Q-08 | Accept F09 on the API only in this round? | Yes; storefront UI checks are added when the FE calls the real API | DEC-20261126-03 | TC-F09-001 to TC-F09-013 |
| Q-09 | Are image limits binary MiB? Is a 413 from the multipart limit with another message acceptable? | Yes and yes (1 MiB = 1,048,576 bytes; 5 MiB = 5,242,880 bytes) | DEC-20261126-03 | TC-F01-027, TC-F01-044 |
| Q-10 | Build a fixture for the 10,000-candidate cap? | No; the automated test is the evidence | DEC-20261126-03 | TRACEABILITY.md §7 |
| Q-11 | Does the 4-character similar-name threshold apply to both names or the shorter one? | The shorter one: `Tea` vs `Teas` needs no confirmation | DEC-20261126-03 | TC-F02-006 |
| Q-12 | Is CTR still expected (UC16, diagram FR08.04)? | No. CTR and estimated revenue are not part of PASPM; FR08.04 is relabelled "Compare simulated A/B metrics" | DEC-20261126-03 | Diagram FR page; TC-F08-017; TRACEABILITY.md §5, §7 |
| Q-13 | F10 single-item add or change above the 99-unit cap: reject or clamp? | Reject the whole request (409 `CART_QUANTITY_LIMIT`); bundle add still adds as much as fits | DEC-20261126-02 §3 | TC-F10-004 |
| Q-13b | F10 single-item add or increase above the sellable stock: reject or add the available part? | Reject the whole request (409 `INSUFFICIENT_SELLABLE_STOCK`), nothing added | DEC-20261126-03 | TC-F10-006; F10 API and handoff |
| Q-14 | F11: is exactly 90 days allowed? | Yes, 90 days inclusive; 91 returns 400 | DEC-20261126-02 §4 | TC-F11-006 |
| Q-15 | Test scheduled dispatch (SKIPPED, retries, STORE_INACTIVE skip)? | Not in this round. The scheduler stays gated; only "enabling returns 503" is tested. Enabling is a separate later decision | DEC-20261126-03 | TC-F05-022 (only check); TC-F05-021, TC-F05-023 `[OUT OF ROUND]`; TC-F05-024 |
| Q-16 | Who creates Stores B and C? | A test-data preparation script (separate small code task, not on this branch) creates them through the API with one Store Admin and one Merchandiser each; it must have run before the round | DEC-20261126-03 | TEST_PLAN.md §6, §7 |
| Q-17 | Exit criteria? | Accepted: every case recorded; 100 % High PASS; ≥ 95 % Medium PASS; no open Critical or Major defect | DEC-20261126-03 | TEST_PLAN.md §7 |

## Documentation inconsistencies found — resolved (DEC-20261126-03)

| Inconsistency | Resolution |
|---|---|
| `docs/setup/STAGING.md` said the seed creates the Store with a direct `INSERT INTO store` | Fixed: the seed uses `POST /api/system/stores` |
| `docs/api/F05_MINING_API.md` historical sections call `POST /api/mining/requests` forbidden (`denyAll`) | Those sections are labelled **Superseded** |
| DEC-20261125-02 says DEC-20261125-01 is the F09 public-Store decision | Fixed on branch `Khoi/docs/source-of-truth-rule` (where the file lives): DEC-20261125-01 is the F07 product-level Layout B decision |
| F07/F08 contract says invalid input returns 400; editing Layout A returns 409 | The contract now states the 409 `Layout A is immutable and read-only` |

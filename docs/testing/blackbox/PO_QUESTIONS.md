# Questions for the Product Owner — black-box test pack

Raised while writing [TEST_CASES.md](TEST_CASES.md) on 2026-11-26. Under [DEC-20261125-02](https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/blob/Khoi/docs/source-of-truth-rule/docs/decisions/2026-11-25/DEC-20261125-02-source-of-truth-latest-po-decision.md) the latest PO decision wins, so each answer is recorded as a DEC (or in the feature contract) before the affected cases are executed. Until then, the affected cases expect what the current contract says.

**Answered on 2026-11-26:** Q-02, Q-03, Q-13 and Q-14, recorded in [DEC-20261126-02](../../decisions/2026-11-26/DEC-20261126-02-vnd-display-cart-cap-dashboard-window-merch-screens.md). Q-13b was split off from Q-13 and is still open.

| ID | Status | Question | Why it matters | Affected cases |
|---|---|---|---|---|
| Q-01 | OPEN | This pack uses DEC-20261125-02 (branch `Khoi/docs/source-of-truth-rule`, not on `dev` yet). The UML pack `PASPM_Unified_UML2_Editable.drawio` has been on `dev` since PR #37 (`fb48d55`). Is DEC-20261125-02 the agreed rule for testing? | It decides which source wins when docs and code differ. | All |
| Q-02 | **ANSWERED** (DEC-20261126-02 §1) | How is a non-integer VND amount displayed? | — | TC-F02-009, TC-F09-013, TC-F10-002, TC-F10-016 |
| | | **Answer:** every UI shows whole đồng rounded HALF_UP (21,000.50 → `21.001 ₫`); stored and returned values are unchanged. | | |
| Q-03 | **ANSWERED** (DEC-20261126-02 §2) | A Merchandiser cannot open several screens on FE `dev`. Intended? | — | F05–F08 Merchandiser cases |
| | | **Answer:** it is a defect, to be fixed before the test run. Listed in TEST_PLAN.md §7 (entry criterion) and §9 (known defects). | | |
| Q-04 | OPEN | FE `dev` still has routes for removed features (`/f03/expiry-window`, `/f03/product-scores`, `/admin/benchmark`) and the screen flow still draws Expiry Window and Product Scores. Should the tests assert these screens are unreachable? | Today TC-F03-028 and TC-F05-025 only check the API and the menu. | TC-F03-028, TC-F05-025 |
| Q-05 | OPEN | SRS §8 lists a Merchandiser "Run mining" screen, but FE `dev` only has `/admin/mining/run` (System Admin). Is a Merchandiser screen in scope, or is an API-only test acceptable for this cycle? | TC-F05-006 uses API steps for now. Related to the Q-03 defect. | TC-F05-006 |
| Q-06 | OPEN | What is the landing page per role after sign-in? The test expects System Admin → `/admin/overview`, Store Admin → `/store/inventory`, Merchandiser → its F03 workspace, End Customer → `/storefront`. | NFR 5.5 has no route list. | TC-F01-001 |
| Q-07 | OPEN | Which screen does the Store Admin use to activate, end and roll back a campaign? The cases use the F07/F08 workspace `/store/layout`. | UI steps for UC23. | TC-F08-009 to TC-F08-015 |
| Q-08 | OPEN | The F09 storefront FE is still mock data. Should F09 be accepted on the API only in this cycle, with UI checks added once the FE calls the real API? | F09 cases use API steps. | TC-F09-001 to TC-F09-013 |
| Q-09 | OPEN | Image limits: is 1 MiB = 1,048,576 bytes and 5 MiB = 5,242,880 bytes? If the multipart size limit triggers first, is a 413 with a different message acceptable? | Exact boundary values and message. | TC-F01-044, TC-F01-027 |
| Q-10 | OPEN | The 10,000-candidate cap (FR-06.2) cannot be reached by hand because runs are capped at 2,000 patterns. Is a special fixture wanted, or is the automated test enough? | Listed as untested in TRACEABILITY.md §7. | — |
| Q-11 | OPEN | Similar-name rule "names of 4+ characters": does the threshold apply to both names or the shorter one? Example: `Tea` (3) vs `Teas` (4). | Boundary of TC-F02-006. | TC-F02-006 |
| Q-12 | OPEN | UC16 and the diagram's FR08.04 still say "Compare CTR / conversion rate", while SRS says CTR and estimated revenue are not implemented. Should the diagram label change, or is CTR still expected? | The tests assert that no CTR or revenue is shown. | TC-F08-001, TC-F08-017 |
| Q-13 | **ANSWERED** (DEC-20261126-02 §3) | F10: single-item add or change above the 99-unit cap — reject or clamp? | — | TC-F10-004 |
| | | **Answer:** reject the whole request with 409 `CART_QUANTITY_LIMIT`; whole-bundle add keeps adding as much as possible per component. | | |
| Q-13b | OPEN | F10: a single-item add or increase above the **sellable stock** returns 409 `INSUFFICIENT_SELLABLE_STOCK`. Is the whole request refused (like the cap), or is the available part added? | TC-F10-006 expects the whole request refused. | TC-F10-006 |
| Q-14 | **ANSWERED** (DEC-20261126-02 §4) | F11: is exactly 90 days allowed? | — | TC-F11-006 |
| | | **Answer:** 90 days inclusive accepted; 91 rejected with 400. (Only-`from` and an inverted range stay 400 per the contract.) | | |
| Q-15 | OPEN | Scheduled dispatch must stay off (BR-24), so SKIPPED occurrences, retries and the STORE_INACTIVE skip cannot be tested black-box. Is a separate gated acceptance run planned, or is the automated opt-in evidence enough? | Listed as untested in TRACEABILITY.md §7. | TC-F05-021 to TC-F05-023 |
| Q-16 | OPEN | The demo seed creates one Store. Cross-Store and INACTIVE tests need Store B and C (TEST_PLAN.md §6). Should the seed create them, or may testers create them through the API on the disposable stack? | Test data setup. | All isolation cases |
| Q-17 | OPEN | Exit criteria in TEST_PLAN.md §7 propose: all High cases passed, at least 95 % of Medium passed, no open Critical or Major defect. Agreed? | Release decision. | TEST_PLAN.md §7 |

## Documentation inconsistencies found (for information, no decision needed)

- `docs/setup/STAGING.md` says the seed creates the Store with one direct `INSERT INTO store`; `scripts/seed/README.md` (newer) says it uses `POST /api/system/stores`.
- `docs/api/F05_MINING_API.md` keeps historical sections that call `POST /api/mining/requests` forbidden (`denyAll`); the current contract at the top of the same file allows the Merchandiser. The tests follow the current contract.
- DEC-20261125-02 explains its number by saying DEC-20261125-01 is the F09 public-Store decision; on `dev`, DEC-20261125-01 is the F07 product-level Layout B decision and the F09 decision is DEC-20261125-04.
- The F07/F08 contract says invalid input returns 400, but editing Layout A returns 409 `Layout A is immutable and read-only` (an invalid transition). TC-F07-002 expects the 409.

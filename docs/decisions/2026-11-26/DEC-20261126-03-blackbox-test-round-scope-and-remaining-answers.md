# DEC-20261126-03 — Black-box test round: scope and the remaining PO answers

| Field | Value |
|---|---|
| Date | 2026-11-26 (Asia/Ho_Chi_Minh) |
| Source | Product Owner answers (Nguyễn Minh Khôi, Team Leader / acting Product Owner) to the open questions of `docs/testing/blackbox/PO_QUESTIONS.md`, given in the task conversation on 2026-11-26 |
| Status | OWNER_DECISION. Documentation updated on branch `Khoi/docs/blackbox-test-cases`. Code and FE follow-ups listed below are not done |
| Number | `-03`: a new set of answers, so [DEC-20261126-02](DEC-20261126-02-vnd-display-cart-cap-dashboard-window-merch-screens.md) stays as recorded. Checked across `origin/dev` and every `origin/*` branch (-01 and -02 were used) |
| Related | DEC-20261126-02 (first answers), [DEC-20261125-06](../2026-11-25/DEC-20261125-06-f10-f11-mvp-contract.md) (F10/F11 MVP), DEC-20261116-04 (EFIM-only), BR-24 (scheduler gate) |

## Decision

| Question | Decision |
|---|---|
| Q-01 Source-of-truth rule | DEC-20261125-02 (latest PO decision wins; branch `Khoi/docs/source-of-truth-rule`) is the baseline for the test round. Its PR is merged into `dev` before the round starts (entry criterion) |
| Q-04 Removed screens on FE `dev` | Tests assert that `/f03/expiry-window`, `/f03/product-scores` and `/admin/benchmark` cannot be reached (no menu entry; the route shows not-found or redirects). Their presence on FE `dev` is a defect fixed together with the Merchandiser screen defect (KD-01) before the round |
| Q-05 Merchandiser "Run mining" screen | ~~This round tests Merchandiser mining submission through the API. The screen comes later~~ — superseded by the addendum below |
| Q-06 Landing page per role | System Admin → `/admin/overview`; Store Admin → `/store/inventory`; Merchandiser → its F03 workspace; End Customer → `/storefront` |
| Q-07 Campaign activation screen | The Store Admin activates, ends and rolls back campaigns in the F07/F08 workspace `/store/layout` |
| Q-08 F09 storefront | F09 is accepted on the public API in this round. Storefront UI checks are added when the FE calls the real API |
| Q-09 Image limits | Binary units: 1 MiB = 1,048,576 bytes, 5 MiB = 5,242,880 bytes. If the multipart size limit rejects the upload first, a 413 with a different message is accepted |
| Q-10 10,000-candidate cap | No manual fixture. The automated test is the evidence |
| Q-11 Similar-name threshold | The 4-character threshold applies to the **shorter** name: `Tea` vs `Teas` does not ask for confirmation; exact matches always do |
| Q-12 CTR on the diagram | CTR and estimated revenue are not part of PASPM. The diagram's FR08.04 is relabelled "Compare simulated A/B metrics"; UC16 compares hit rate, bundle conversion, captured utility and priority coverage |
| Q-13b F10 add above sellable stock | A single-item add or increase above the sellable stock refuses the **whole** request with `409 INSUFFICIENT_SELLABLE_STOCK` (nothing added, version unchanged), like the 99-unit cap |
| Q-15 Scheduler | **Not in this round.** The scheduler stays gated; the round only checks that enabling a schedule returns 503. Enabling the scheduler is a separate later decision |
| Q-16 Stores B and C | Testers do not create them by hand. A test-data preparation script (a separate small code task, not on this branch) creates Stores B and C through the API, with one Store Admin and one Merchandiser per Store. The script having run is an entry criterion |
| Q-17 Exit criteria | Accepted: every case has a recorded result; 100 % of High cases PASS; at least 95 % of Medium cases PASS; no open Critical or Major defect |

## Addendum (2026-11-26, later the same day) — Q-05 updated by the PO

The Run mining screen exists on FE `dev` (checked at `0cd57a6`): sidebar "Product Combination Analysis" → `/mining?view=setup` → tab "Run Analysis". A **Merchandiser** runs EFIM mining there for its own Store; a **Store Admin** sees "Analysis History" only (no Run Analysis tab; the setup URL shows the history). The F05 Merchandiser and Store Admin cases use UI steps on that route; checks the UI cannot make (extra request fields, a forbidden role posting, another Store's snapshot) stay API steps.

## Documentation conflicts resolved

1. `docs/setup/STAGING.md`: the seed creates the Store with `POST /api/system/stores`, not a direct `INSERT`.
2. `docs/api/F05_MINING_API.md`: historical sections that call `POST /api/mining/requests` forbidden are labelled **Superseded**.
3. DEC-20261125-02: its numbering note says DEC-20261125-01 is the F07 product-level Layout B decision (fixed on its own branch `Khoi/docs/source-of-truth-rule`, where the file lives).
4. `docs/api/F07_F08_LAYOUT_CAMPAIGN_API.md`: editing Layout A returns **409** `Layout A is immutable and read-only`.

## Follow-ups (not done by this decision)

- FE: fix KD-01 (Merchandiser screens) and KD-02 (removed routes) before the round.
- Backend F10 (when implemented): apply Q-13b.
- Test-data preparation script for Stores B and C (Q-16).
- FE known issue KI-01 (not a decision): the browser test for selecting a Store with the keyboard on Users & Roles fails (reported by Codex). Recorded in the test plan; not an entry criterion.
- Merge the DEC-20261125-02 PR into `dev` (Q-01).

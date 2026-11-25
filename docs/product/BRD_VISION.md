# PASPM — Vision and Business Requirements (BRD)

| Field | Value |
|---|---|
| Project | PASPM — Product Arrangement Support Platform |
| Code / course | FA26SE048 · FPT University · SWP490 capstone (Sep–Dec 2026, W1–W15) |
| Official title | Research and Development of an Online Product Arrangement Support System through a Comparative Study of High Utility Pattern Mining Algorithms |
| Document | Vision / BRD for humans and AI agents |
| Version / date | 0.4 — 2026-11-20 (synced with `dev` @ `636952b`) |
| Baseline | Backend `dev` @ `530651b`; decisions up to DEC-20261117-01 |
| Audited against code | `530651bad5b02d90bcb48ecffaf7fa5a0f6b0b7e`, see (audit report kept outside the repo, 2026-11-19) |
| Synced with | `dev` @ `636952b` (2026-11-20): F02 update `d22f735`; PR #20 (decisions); PR #21 (F04 Excel); PR #22 (zone caps, top-K baseline, equal-size simulation, DEC-20261119-06); PR #23 (SPMF whole-đồng fallback, DEC-20261119-05); PR #25 (agent kit). Checked for these changes only, not a full re-audit |
| Companion files | [SRS.md](SRS.md) · [ARCHITECTURE_SDD.md](ARCHITECTURE_SDD.md) · [GLOSSARY.md](GLOSSARY.md) |

> **Source precedence.** When sources disagree, a higher item overrides a lower one:
> 1. Owner decisions in `docs/decisions/` and the merged code on `dev`.
> 2. `FA26_SE048.pdf`, the supervisor-approved brief.
> 3. Reports 1–4, which are partly outdated. Items they get wrong are listed in §10.
>
> Anything marked **TBD** has not been decided. Ask the owner; do not guess.

---

## 1. Product summary

PASPM is a web-based **decision-support system for online merchandising in food & beverage retail**. It does four things:

1. It turns a Store's sales transactions into **weighted utility**: profit adjusted by how urgently a product must be sold (expiry risk) and seasonal relevance.
2. It mines **high-utility itemsets** with EFIM.
3. It converts them into **reviewable merchandising proposals**: bundles, cross-sell, frequently-bought-together and banners.
4. It lets staff arrange, simulate and activate a **storefront layout** that customers then see and shop from.

The system is a prototype for an academic capstone. It is not a production retail system.

## 2. Business problem

| # | Problem | Why it matters |
|---|---|---|
| P1 | F&B stores must sell **near-expiry** and **seasonal** products in time (for example hot pot in winter, ice cream in summer), but product placement is decided mostly by experience. | Expired stock is lost margin. Unsold seasonal stock loses its value once the season ends. |
| P2 | Frequent-itemset analysis counts co-occurrence only. It ignores quantity, margin and business priority. | The "popular" combinations it finds are not necessarily valuable or urgent. |
| P3 | A raw itemset does not define a layout: no zones, order, stock validity or approval. | Mining output cannot be put on a storefront directly. Rules and human review are needed. |
| P4 | Mining algorithms are offline tools. Running them inside a web app needs validated input, thresholds, asynchronous jobs and reproducibility. | Without these, results cannot be trusted or traced back to their data. |
| P5 | Historical orders contain no impressions or clicks, and the team has no customer base for surveys or live tests. | Layout effectiveness can only be **simulated** under stated assumptions. It must never be presented as real sales lift. |

A team review in November 2026 found problems with the earlier weight design. It needed too many manual weights, which brought back experience-based arrangement. The weights were not tied to a concrete store problem. Monthly mining could not keep up with food expiry. **Weight v2** (§8) replaced it.

## 3. Vision

> A store planner can see which products and product groups deserve display space *now*, because they are profitable, about to expire or in season. The planner can explain why, adjust the proposal and put it on the storefront through an auditable review-and-activate workflow.

## 4. Goals, objectives and research questions

### 4.1 Business goals

| ID | Goal |
|---|---|
| G1 | Prioritise display of products at expiry risk or in season, without making staff hand-score every product. |
| G2 | Provide explainable, reviewable merchandising proposals derived from transaction evidence. |
| G3 | Keep humans in control: nothing reaches the storefront without Merchandiser review and Store Admin activation. |
| G4 | Compare a proposed layout against a baseline under documented, reproducible simulation assumptions. |

*The G-numbering above is new in this document. Map it to the team's official goal list if one exists (**TBD**).*

### 4.2 Research questions (revised November 2026)

| ID | Question | Status |
|---|---|---|
| RQ1 | How can priority weighting (expiry risk and seasonality) be built into utility so that mining surfaces products the store must push? | Adopted. Replaces the old HUI-Miner vs EFIM comparison RQ. |
| RQ2 | How can high-utility itemsets support merchandising proposals that users review, edit, activate and display on a storefront? | Adopted |
| RQ3 | How do baseline and proposed layouts differ under documented simulation assumptions? | **Kept** (decided 2026-11-19). Answered with the F08 offline replay using equal-size comparison (B vs top-N of A) and sensitivity runs at 20 / 35 / 50 displayed products. Evidence: `docs/testing/F08_LAYOUT_SIZE_SENSITIVITY_20261119.md`. On the demo data, B reached only 21 distinct products. At equal size, B had a lower hit rate than A (0.0555 vs 0.0600 at N = 21) but a higher captured utility (0.0192 vs 0.0171). Simulated, one snapshot, no confidence intervals |

The HUI-Miner vs EFIM comparison RQ and its benchmark were dropped. The EFIM paper already covers that comparison, and mining is now EFIM-only (2026-11-16). Reports 1, 2 and 4 still describe the comparison and must be updated.

## 5. Stakeholders and users

| Stakeholder | Interest / responsibility |
|---|---|
| Supervisor — Vũ Thị Thùy Dương | Approves direction (`FA26_SE048.pdf`) and evaluates reviews. |
| Team Leader & Backend — Nguyễn Minh Khôi | Owner decisions, F03/F05 backend, docs, prompts for AI agents. |
| Backend — Phan Hoàng Duy | Backend features (F04 import → utility snapshot workflow). |
| Frontend — Phạm Trung Kiên | Frontend; F07/F08 layout and campaign screens (workflow WF-03). |
| Frontend — Nguyễn Thị Thảo Hiền | Frontend; F09–F11 storefront, cart and dashboard (workflow WF-04). |
| System Administrator *(user role)* | Platform configuration, mining policy, accounts. |
| Store Admin / Operations Manager *(user role)* | Catalogue, inventory and push days, imports, campaign activation. |
| Merchandiser / Retail Planner *(user role)* | Weights, seasons, snapshots, mining, review, layouts, campaigns. |
| End Customer *(user role)* | Browses the storefront and adds bundles to the cart. |
| AI coding agents (Claude Code, Codex) | Implement and test from these documents under the team's rules (§9.3). |

Workflow ownership (Review 2):
- WF-01 import → snapshot: Duy.
- WF-02 mining: Khôi.
- WF-03 recommendation → layout/A-B: Kiên.
- WF-04 storefront → cart → dashboard: Hiền.

## 6. Scope

### 6.1 In scope: eleven feature groups (all mandatory)

| ID | Feature | Status on `dev` @ `530651b` |
|---|---|---|
| F01 | User & RBAC: accounts, email OTP, Google sign-in, JWT plus refresh cookie/CSRF. **Store management** (2026-11-23, DEC-20261123-01): System Admin creates, edits and deactivates/reactivates Stores (no delete); own Store profile with icon and banner | Implemented. Store management merged in PR #27 (`3d51f5b`) |
| F02 | Product & Inventory: categories, products, prices/costs, batches, **push day** | Implemented. 2026-11-19 update (`d22f735`): SKU format, similar-name confirmation, generated batch codes, editable/clearable batch expiry date |
| F03 | Utility Profiling: Weight v2, seasons, immutable snapshots | Implemented |
| F04 | Transaction Pipeline: CSV import (sync and async), Excel import, validation, preprocessing | Implemented. Excel (`POST /api/transactions/imports/excel`, synchronous, `STORE_ADMIN`) merged 2026-11-19 (PR #21) |
| F05 | Mining Engine: EFIM via SPMF, minUtil policy, async worker, progress, schedules | Implemented. **Schedule enable stays HTTP 503** until sign-off |
| F06 | Recommendation Engine: BUNDLE / CROSS_SELL / FBT / BANNER, review, live eligibility | Implemented |
| F07 | Layout Simulator: baseline A, editable B, zones, utility score, preview | Backend API implemented. `priorityCoverage` merged in PR #32 (`516183d`). Frontend in progress (mock screens on the local branch `feature/f07-f11-mock`, not pushed) |
| F08 | Campaign & A/B (simulated): campaign lifecycle, offline replay report | Backend API implemented. Frontend in progress |
| F09 | Storefront: homepage, product detail, bundles | **Backend MVP** (Duy Phan, branch `Duy/Feature/F09_Storefront`, 2026-11-21) merged with `dev` through `Khoi/merge/f09-storefront-be` (V29; public = Store ACTIVE, DEC-20261125-04). Related products and the frontend storefront are still open |
| F10 | Bundle Cart | **PLANNED.** Whole-bundle action adds eligible components to ordinary Product lines, with current price, stock validation and If-Match (DEC-20261125-06). |
| F11 | Retail Performance Dashboard | **PLANNED.** Same-Store dashboard for STORE_ADMIN and MERCHANDISER with frozen snapshot utility, saved simulated A/B including priority coverage, and bundle add counts by origin plus actual units added (DEC-20261125-06). |

### 6.2 Out of scope

- Checkout, payment, shipping, orders, subscription and billing.
- Demand forecasting, physical shelf-coordinate optimisation, ML personalisation.
- A new mining algorithm.
- Live POS, external transaction APIs, polling and webhooks. Ingestion is by file only.
- Production deployment to a retailer. A Linux Docker staging environment is planned, but no staging environment exists yet.
- Native mobile apps. The web client is responsive.
- Real-user A/B testing. All A/B results are simulated and labelled as such.
- Bundle discounts, guest carts and stock reservation (deferred by the F09/F10 MVP decisions).

## 7. Closed-loop business workflow

```mermaid
flowchart LR
  A[Store Admin<br/>catalogue, batches, push days] --> B[Store Admin<br/>import CSV transactions]
  B --> C[Merchandiser<br/>λ, α, β, N, seasons → utility snapshot]
  C --> D[Merchandiser or System Admin<br/>EFIM mining on snapshot]
  D --> E[Merchandiser<br/>generate & review recommendations]
  E --> F[Merchandiser<br/>layout B vs baseline A, simulate, decide, publish]
  F --> G[Store Admin<br/>activate campaign]
  G --> H[End Customer<br/>storefront & bundle cart — planned]
  H --> I[Dashboard — planned]
  I -.new sales / inventory.-> B
```

## 8. Key business decisions (summary)

| Date | Decision | Effect |
|---|---|---|
| 2026-11-15 | **Weight v2**: `W = 1 + λ(α·U_expiry + β·M)` replaces `1 + λ(αS + βE + γM)` | No manual stock score. U_expiry is computed by the system from batch push days and the sales rate. M comes from seasons. |
| 2026-11-15 | The System Admin sets only **λmax** (default 0.5). The Merchandiser sets λ ∈ [0, λmax] and α + β = 1 | Simpler governance |
| 2026-11-15 | Push day is set **per batch** by the Store Admin. A missing push day gives a warning only, with no backfill | Ties weight to a concrete store task |
| 2026-11-15 | Seasons table (CRUD, weight [0,1], start/end). A season with assigned products cannot be deleted. Recommendations warn when the season has ended | Reusable seasonal priority |
| 2026-11-15 | Sales window N = 30 days (editable). Zero sales gives U_expiry = 0 plus a "slow-moving" warning. W rounded to 2 decimals | Deterministic scoring |
| 2026-11-15 | The Merchandiser may also submit mining for its own Store | Removes the System Admin bottleneck |
| 2026-11-16 | P2 highest active season weight wins. P3 season active by analysis date, inclusive. P4 a push day that has been reached counts the whole batch as risk | Approved as built |
| 2026-11-16 | Legacy product score fields and columns removed (V26). The two obsolete routes deleted | Breaking API change, communicated to frontend |
| 2026-11-16 | **EFIM-only** mining. HUI-Miner, compare mode and benchmarks removed | Research RQ changed |
| 2026-11-17 | Lossless EFIM-aware int32 guard (no rounding of utilities), DEC-20261117-01 | A global utility total above int32 is now accepted. A request still fails with 400 when a transaction utility, a product TWU or the converted minUtil exceeds int32: superseded in part by DEC-20261119-05 (whole-đồng fallback), with which the realistic 4,100-order fixture is now accepted |
| 2026-11-17 | `priorityCoverage` = products with W > 1 on the layout ÷ products on the layout | Retail must sometimes push single priority items ("daily deal"), not only bundles. Recorded as DEC-20261117-02. Merged in PR #32 (`516183d`) |
| 2026-11-12 | A/B compares **2** layouts. F07 only opens layouts created by F08. A is read-only | DEC-20261112-01 |
| 2026-11-01 | Scheduled mining dispatch stays disabled (503) pending tests and owner policy | Safety gate |
| 2026-11-17 | F09/F10 go ahead as the proposed MVP; the plans are expected to be revised | See SRS §4. Recorded as DEC-20261117-03 |
| 2026-11-19 | The UML baseline is the **36-page** pack (27 core diagrams + R2-05 Package + 8 class diagrams R2-07.x), not the 27-page copy on `dev` | The 36-page pack is updated to V26 and then committed to `docs/diagrams/` (DEC-20261119-02; committed with the V1–V26 ERD by DEC-20261126-01) |
| 2026-11-19 | F02 rules from Duy's update (`d22f735`, then `24b9b01` on 2026-11-20): the SKU is **generated by the backend** as `SKU-%011d` from the product ID (read-only, unique per Store; clients must not send it). A product name that equals, or is one character away from, another product's normalized name in the Store (names of 4+ characters) returns 409 `PRODUCT_NAME_CONFIRMATION_REQUIRED` until the client retries with `confirmSimilarName: true`; batch codes `BATCH-YYYYMMDD-NNN` are generated by the backend | Recorded as [DEC-20261119-07](../decisions/2026-11-19/DEC-20261119-07-server-generated-product-sku.md) (2026-11-25). Existing SKUs, including `FOODMART-xxxx`, are kept without migration |
| 2026-11-23 | Store management belongs to F01: Store name unique (trim, ignoring case), `slug` = F09 `storeSlug` (System Admin only), status ACTIVE/INACTIVE with one central guard (403 `STORE_INACTIVE`), no hard delete, picker lists ACTIVE Stores by default, PNG/JPEG icon and banner on a shared media storage | DEC-20261123-01; V27 |
| 2026-11-24 | F02: a batch is expired only when today (`Asia/Ho_Chi_Minh`) is **after** its expiry date, computed on read (`effectiveStatus`, `daysToExpiry`; EXPIRED wins over BLOCKED; no stored INACTIVE); the push day has no default and the batch API warns `MISSING_PUSH_DAY`; products are soft-deleted, refused with 409 while they have sellable stock or sit in a PUBLISHED/ACTIVE campaign layout, no restore | DEC-20261124-01, -02, -03; V28 |
| 2026-11-25 | F09: a Store is public exactly when it is ACTIVE; `storeSlug` = `store.slug`; no separate storefront visibility switch. F09/F10 exclude INACTIVE and soft-deleted products | DEC-20261125-04; F09 migration renumbered to V29 |
| 2026-11-25 | SPMF (GPL v3) 2.67a is redistributed through the public GitHub Release `spmf-2.67a` with its license and source, the image carries them, the image builds for linux/amd64, `THIRD_PARTY_NOTICES.md` lists SPMF only, and backend code never imports SPMF classes (it runs only as a separate process) | DEC-20261125-03 |
| 2026-11-19 | Merging into `dev` goes **only through a PR with green CI** | DEC-20261119-01. Binding from PR #20; earlier merges (including F02 `d22f735` on 2026-11-19) were pushed directly |
| 2026-11-19 | Layout zone capacities (`paspm.layout.*`) must be configured **before F09**: BANNER 3 blocks / 3 products, BUNDLE 4 / 16, CROSS_SELL 2 / 8, FBT 2 / 8, BASELINE 35 / 35 | Planned (prompt issued). Configurable through `application.yml` |
| 2026-11-19 | Baseline A keeps only the **top-K** products by frequency (K = BASELINE capacity, 35; `F07_BASELINE_V2`). F08 compares B with the **top-N of A**, where N = distinct products in B | Showing every product made A's hit rate and captured utility about 100%. The layout score grows with size, so only same-size layouts are comparable. **DONE** (PR #22, DEC-20261119-06): `OFFLINE_REPLAY_V2`, additive `LayoutView.equalSizeBaselineScore` / `equalSizeProductCount` |
| 2026-11-19 | int32 option B: SPMF input is built losslessly first. **Only if** an EFIM int32 bound is exceeded, it falls back to rounding each item utility to **1 đồng** (HALF_UP), recorded as a new conversion version. Snapshots stay exact. Amends DEC-20261117-01 | **DONE** (PR #23, DEC-20261119-05): `conversionVersion F03_WHOLE_UNIT_HALF_UP_INT32_DENSE_V1`, `conversionMode WHOLE_UNIT_HALF_UP`. The realistic fixture is accepted (max product TWU 321,751). The demo seed stays `LOSSLESS`. Forced whole-đồng gave the same 180 patterns at 0.12 %, with the top-50 identical |
| 2026-11-19 | The A/B (2026-11-12), `priorityCoverage` (2026-11-17) and F09/F10 MVP (2026-11-17) decisions are recorded as DEC files in `docs/decisions/` | Done (PR #20) |

## 9. Constraints, assumptions and working rules

### 9.1 Constraints

- **Stack:** React/TypeScript SPA; Java 17 / Spring Boot 3.5.7; MySQL 8.4 with Flyway; SPMF 2.67a on Java 25 in a child process.
- **Timeline:** W1–W15. Software handover W13, defence W14–W15. The exact calendar dates are **TBD**.
- **Data:** FoodMart (SPMF public set, 4,141 transactions) plus a derived test set and demo seeds. Missing cost, stock, expiry or season data uses clearly labelled demo values.
- **Evaluation:** simulation only. No customer base for surveys.

### 9.2 Assumptions

- One currency per Store: **VND**, shown as whole đồng with no decimals in F09/F10 (decided 2026-11-19). Stored money keeps 2 decimals.
- Store-local time is Asia/Ho_Chi_Minh.
- Weighted utility is a priority score, never reported as profit.

### 9.3 Working rules for contributors and agents

- `FA26_SE048.pdf` and owner decisions are the standard. Fix code to match requirements; do not rewrite requirements to justify code ("docs làm chuẩn").
- Never edit historical Flyway migrations V1–V26. Add a new version.
- Never delete or repair a teammate's local database. Tests use disposable databases.
- No commit, push, merge or deploy without an explicit instruction.
- Do not fabricate test evidence. Mark tests **NOT RUN** / **NOT_READY**.
- Keep scheduled-mining enable at 503.
- Keep frozen JSON (snapshots, requests, recommendations) byte-identical, and read it by `scoringVersion`.
- After a verified change, update the feature docs, OpenAPI, frontend handoff, RBAC matrix and record of changes.

## 10. Success criteria

| # | Criterion | Evidence |
|---|---|---|
| S1 | Complete data-to-storefront flow is demonstrable: import → snapshot → mining → review → layout → activate → storefront → bundle cart | End-to-end demo script and test record |
| S2 | Weight v2 is reproducible: golden values match (for example 120 units, push in 3 days, 25/day → U_expiry = 0.375) | `WeightV2CalculationTest` and snapshot hashes |
| S3 | Mining correctness: EFIM output matches independently checked fixtures, and failures (timeout, OUTPUT_LIMIT, int32) are recorded, not hidden | Fixture tests and run records |
| S4 | Traceability: every recommendation links to its run, pattern, snapshot and rule version; every report pins its layout revisions and dataset hash | API responses and DB |
| S5 | Governance: every role is limited to its permitted operations; cross-Store access returns 404; nothing reaches the storefront without activation | RBAC tests |
| S6 | Simulation is deterministic and labelled `simulated:true`, with its assumptions stored | F08 report |
| S7 | Teammates on Windows and macOS can set up from the README | Setup docs and troubleshooting |

Quantitative targets are **TBD**: no response-time, throughput or availability target has been set. Reports show measured values only.

## 11. Risks

| Risk | Mitigation |
|---|---|
| Low minUtil causes output explosion or timeouts | Limits of 2,000 patterns, 4 MiB output, 60 s and 256 MiB. Choose minUtil from pilot runs. On the demo seed, the single day 2026-10-02 at `PERCENTAGE 0.25` gave 179 patterns, inside the demo target of about 100–300. The full range 2026-10-01..04 at 0.25 gave 0 patterns. For the multi-day demo (2026-10-01..04), `PERCENTAGE 0.12` gives 180 patterns in 385 ms (LOSSLESS) |
| High-price data overflows SPMF int32 | EFIM-aware guard returns 400 with a clear reason. Since PR #23, the input falls back to whole-đồng rounding only on overflow. The 4,100-order fixture (lossless product TWU 3,217,593,703) is accepted. If whole-đồng still overflows, the request gets 400; coarser units need a new decision |
| Reports 1–4 and diagrams drift from code | This document set plus the record of changes; update the reports before Review 3 (**TBD**) |
| The FoodMart dataset uses its own product codes (`FOODMART-xxxx`) while the backend generates SKUs (DEC-20261119-07) | The demo seed creates each product as `FoodMart Item NNNN`, reads back the server SKU, writes `mapping.csv` and imports CSV copies whose `sku` column holds the server SKUs; the original files are unchanged (ROC-20261125-004). Databases seeded before 2026-11-20 keep their `FOODMART-xxxx` SKUs with no migration; their products can be edited normally because updates never touch the SKU |
| Frontend uses removed APIs or fields | Breaking-change list #15–#18 in the frontend handoff |
| F09–F11 are not fully specified | MVP plans for F09/F10; planned F10/F11 contracts recorded in DEC-20261125-06 |
| CI repository variable `PASPM_CI_SPMF_ARTIFACT_URL` missing (recorded 2026-11-16; on PR #28 the `integration` job failed for this reason on 2026-11-25; current value UNVERIFIED in dev's record) | Since DEC-20261125-03 the approved URL is the Release asset `spmf-2.67a`; the owner creates the Release and sets the variable; repo admin to configure it |

## 12. Open questions (ask the owner)

1. ~~RQ3 and G4~~: kept (2026-11-19).
2. F11 scope and planned contract are recorded in DEC-20261125-06. Implementation is pending.
3. ~~Currency~~: VND, whole đồng (2026-11-19).
4. ~~F04 Excel import~~: merged on 2026-11-19 (PR #21).
5. ~~Sign-off~~: the Team Leader (Nguyễn Minh Khôi) acts as Product Owner and signs off owner decisions; the supervisor reviews them at Review 3 (2026-11-19). Enabling the scheduler still needs that sign-off after its acceptance tests.
6. Timeline: the exact dates of Review 3, handover and defence.
7. ~~Realistic VND prices vs int32~~: decided 2026-11-19 (whole-đồng fallback). Open: what to do if whole-đồng still overflows (thousands of đồng needs a new decision).
8. ~~Layout zone capacities~~: decided 2026-11-19 (BANNER 3 blocks / 3 products, BUNDLE 4 / 16, CROSS_SELL 2 / 8, FBT 2 / 8, BASELINE 35 / 35). Optional: sensitivity runs at 20 / 35 / 50 products for RQ3.
9. ~~Multi-day demo minUtil~~: `PERCENTAGE 0.12` gives 180 patterns on 2026-10-01..04 (measured 2026-11-19). Setting it as the demo policy is an owner action.
10. Cleanup task (not part of DEC-20261123-01): drop the unused `store.mining_schedule` column in a later migration.
11. ~~`scoringVersion` for the 2026-11-24 expiry boundary~~: decided 2026-11-24, **not bumped** (`W2_EXPIRY_SEASON_V1` is the whole Weight v2 model tag and every stored reader accepts only that string). Snapshots taken after the merge follow the new boundary and experiment snapshots must be regenerated, never mixed ([DEC-20261124-01](../decisions/2026-11-24/DEC-20261124-01-batch-expiry-boundary-effective-status.md) §6).

## 13. Sources

- `FA26_SE048.pdf` (approved brief).
- `PASPM_11_Features_Overview.docx`.
- Reports 1–4 (FA26SE048).
- Backend repo `docs/decisions/**`, `docs/requirements/F03_WEIGHT_V2_REQUIREMENTS.md`, `docs/api/**`.
- F09/F10 plan drafts v2 (2026-11-17).
- Owner decisions recorded in project chats up to 2026-11-17.

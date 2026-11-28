# PASPM black-box test traceability

Maps every SRS v0.4 functional requirement, use case and business rule to the black-box cases in [TEST_CASES.md](TEST_CASES.md) / [TEST_CASES.csv](TEST_CASES.csv). Requirement IDs, names and statuses come from [SRS.md](../../product/SRS.md) §2–§4; newer DEC files win where they differ ([DEC-20261125-02](https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/blob/Khoi/docs/source-of-truth-rule/docs/decisions/2026-11-25/DEC-20261125-02-source-of-truth-latest-po-decision.md), branch `Khoi/docs/source-of-truth-rule`, not yet on `dev`). All cases are **NOT RUN**.

Coverage: 57/57 FR, 33/33 UC, 33/33 BR have at least one case.

## 1. Functional requirements → test cases

| FR | Requirement | Status | Test cases |
|---|---|---|---|
| FR-01.1 | Sign-in by email/password or verified Google ID token | DONE | TC-F01-001, TC-F01-002, TC-F01-003, TC-F01-012 |
| FR-01.2 | Self-registration and invitation with email OTP (202 new / 409 existing) | DONE | TC-F01-004, TC-F01-005, TC-F01-006, TC-F01-007, TC-F01-008, TC-F01-009, TC-F01-010, TC-F01-019, TC-F01-052 |
| FR-01.3 | Access JWT, HttpOnly refresh cookie, CSRF, logout, logout-all, token_version | DONE | TC-F01-013, TC-F01-014, TC-F01-015, TC-F01-016, TC-F01-017, TC-F01-018 |
| FR-01.4 | One role per account; Store Admin and Merchandiser have one Store | DONE | TC-F01-001, TC-F01-006, TC-F01-019, TC-F01-020, TC-F01-022 |
| FR-01.5 | Profile with avatar; account states | DONE | TC-F01-003, TC-F01-024, TC-F01-027 |
| FR-01.6 | Store fields (name, slug, status, address, phone, version, images) | DONE | TC-F01-028, TC-F01-031, TC-F01-032 |
| FR-01.7 | System Admin creates, edits, (de)activates Stores; no delete; assigned users | DONE | TC-F01-028, TC-F01-034, TC-F01-035 |
| FR-01.8 | Store Admin edits own address/phone; Merchandiser views own Store | DONE | TC-F01-041, TC-F01-042 |
| FR-01.9 | INACTIVE Store: central 403 guard, 409 for new SA work/assignment, history readable | DONE | TC-F01-037, TC-F01-039, TC-F01-040, TC-F05-024 |
| FR-01.10 | Store icon/banner: PNG/JPEG, size and dimension limits, public GET for ACTIVE | DONE | TC-F01-043, TC-F01-044, TC-F01-045, TC-F01-046, TC-F01-047, TC-F01-048, TC-F01-049, TC-F01-050, TC-F09-014 |
| FR-02.1 | Category/product CRUD, server SKU, similar-name confirmation | DONE | TC-F02-001, TC-F02-002, TC-F02-003, TC-F02-004, TC-F02-005, TC-F02-006, TC-F02-007, TC-F02-008, TC-F02-009, TC-F02-011, TC-F02-012 |
| FR-02.2 | Batches: expiry, push day, effectiveStatus, daysToExpiry, MISSING_PUSH_DAY, batch code | DONE | TC-F02-015, TC-F02-016, TC-F02-017, TC-F02-018, TC-F02-019, TC-F02-020, TC-F02-021, TC-F02-022, TC-F02-024, TC-F02-025, TC-F02-026, TC-F02-027, TC-F02-028, TC-F02-029 |
| FR-02.3 | Stock derived from sellable batches | DONE | TC-F02-026, TC-F02-030 |
| FR-02.4 | No legacy score/expiry fields in product requests | DONE | TC-F02-004, TC-F03-007 |
| FR-02.5 | Product soft delete with 409 guards | DONE | TC-F02-031, TC-F02-032, TC-F02-033, TC-F02-034, TC-F02-035, TC-F02-036 |
| FR-03.1 | W = 1 + λ(α·U_expiry + β·M), 2 dp HALF_UP | DONE | TC-F03-003, TC-F03-016, TC-F03-017 |
| FR-03.2 | U_expiry by FEFO over push days; MISSING_PUSH_DAY; SLOW_MOVING | DONE | TC-F03-016, TC-F03-018, TC-F03-019, TC-F03-020, TC-F03-021, TC-F03-027 |
| FR-03.3 | Seasons CRUD, assignment, delete guard, inclusive active window | DONE | TC-F03-010, TC-F03-011, TC-F03-012, TC-F03-013, TC-F03-014, TC-F03-015 |
| FR-03.4 | Preview and immutable snapshot | DONE | TC-F03-022, TC-F03-023, TC-F03-024, TC-F03-025, TC-F03-026 |
| FR-03.5 | λ above λmax → lambdaValid=false, 409 for new snapshots | DONE | TC-F03-009, TC-F05-010 |
| FR-03.6 | Frozen JSON read by scoringVersion | DONE | TC-F03-029 |
| FR-04.1 | CSV template, 5 MiB / 10,000 rows | DONE | TC-F04-001, TC-F04-002, TC-F04-003, TC-F04-013 |
| FR-04.2 | Async import, progress, Idempotency-Key, leases | DONE | TC-F04-008, TC-F04-009 |
| FR-04.3 | Orders atomic; duplicate vs conflict | DONE | TC-F04-004, TC-F04-005, TC-F04-006, TC-F04-007, TC-F04-011 |
| FR-04.4 | Only COMPLETED, price ≥ cost are F03-eligible; inventory untouched | DONE | TC-F04-012 |
| FR-04.5 | Excel import (synchronous) | DONE | TC-F04-010, TC-F04-011 |
| FR-05.1 | Versioned minUtil policy; EFIM fixed | DONE | TC-F05-001, TC-F05-002, TC-F05-009 |
| FR-05.2 | SA (selected Store) and Merchandiser (own Store) submission; requestKey idempotency | DONE | TC-F05-005, TC-F05-006, TC-F05-007, TC-F05-008, TC-F05-010 |
| FR-05.3 | Input bounds and int32 guard with whole-đồng fallback | DONE | TC-F05-012, TC-F05-013, TC-F05-014, TC-F05-015 |
| FR-05.4 | Worker runtime limits: 60 s, 256 MiB, 4 MiB, 2,000 patterns | DONE | TC-F05-016, TC-F05-017, TC-F05-018 |
| FR-05.5 | Progress endpoint | DONE | TC-F05-005, TC-F05-020 |
| FR-05.6 | Schedules; enable returns 503 | PARTIAL | TC-F05-021, TC-F05-022, TC-F05-023 |
| FR-05.7 | Store users read patterns; System Admin cannot | DONE | TC-F05-019 |
| FR-06.1 | Generate BUNDLE / CROSS_SELL / FBT / BANNER under the Store policy | DONE | TC-F06-001, TC-F06-004, TC-F06-005, TC-F06-006, TC-F06-007 |
| FR-06.2 | Idempotent generation; 10,000 candidate cap | DONE | TC-F06-002 |
| FR-06.3 | Review once with reason and version; approval needs ELIGIBLE | DONE | TC-F06-008, TC-F06-010 |
| FR-06.4 | Live eligibility; readyForConsumption | DONE | TC-F06-011, TC-F06-012 |
| FR-06.5 | Expiry alerts; seasonEnded warning | DONE | TC-F03-027, TC-F06-013, TC-F06-016 |
| FR-06.6 | Ranked list order | DONE | TC-F06-014 |
| FR-07.1 | Deterministic read-only Baseline A, top-35 | DONE | TC-F07-001, TC-F07-002 |
| FR-07.2 | Per-product Layout B, provenance, once per layout, zone caps | DONE | TC-F07-003, TC-F07-004, TC-F07-005, TC-F07-006, TC-F07-007, TC-F07-008 |
| FR-07.3 | Revisions; invalid blocks block READY/publish/activate | DONE | TC-F07-009, TC-F07-010, TC-F07-011 |
| FR-07.4 | Utility score = sum of snapshot weighted utility | DONE | TC-F07-003, TC-F07-013 |
| FR-07.5 | Priority coverage | DONE | TC-F07-014, TC-F08-001 |
| FR-08.1 | Campaign lifecycle and role split | DONE | TC-F08-006, TC-F08-007, TC-F08-008, TC-F08-009, TC-F08-012, TC-F08-013, TC-F08-014, TC-F08-015 |
| FR-08.2 | At most one ACTIVE campaign; activation rechecks | DONE | TC-F08-010, TC-F08-011, TC-F08-019 |
| FR-08.3 | Offline replay simulation V2+ (equal size), simulated:true | DONE | TC-F07-013, TC-F08-001, TC-F08-002, TC-F08-003, TC-F08-004, TC-F08-005 |
| FR-08.4 | All F07/F08 operations synchronous | DONE | TC-F08-001, TC-F08-003 |
| FR-09.1 | Public API by storeSlug; ACTIVE Stores only | DONE | TC-F09-001, TC-F09-004, TC-F09-011, TC-F09-014, TC-F09-015 |
| FR-09.2 | Home renders the ACTIVE campaign layout or the default catalogue | DONE | TC-F09-002, TC-F09-003, TC-F09-012, TC-F09-015 |
| FR-09.3 | Product detail, availability, bundle filtering | DONE (related products deferred) | TC-F09-005, TC-F09-007, TC-F09-009, TC-F09-013, TC-F09-016 |
| FR-09.4 | No admin fields in public DTOs; product image URL and description | DONE | TC-F02-008, TC-F02-037, TC-F09-008, TC-F09-013 |
| FR-10.1 | END_CUSTOMER cart, 99 cap, If-Match versioning | DONE (API) | TC-F10-001, TC-F10-002, TC-F10-003, TC-F10-004, TC-F10-005, TC-F10-006, TC-F10-013, TC-F10-016 |
| FR-10.2 | Whole-bundle add by bundleKey with per-component results | DONE (API) | TC-F10-007, TC-F10-008, TC-F10-009, TC-F10-010 |
| FR-10.3 | Lines editable/removable; no reservation, discount, checkout | DONE (API) | TC-F10-006, TC-F10-012, TC-F10-014, TC-F10-015 |
| FR-10.4 | Cart events; MINED/MANUAL origin | DONE (API) | TC-F10-007, TC-F10-009, TC-F10-011, TC-F10-012, TC-F11-005 |
| FR-11.1 | Dashboard: three panels, 30/90-day window, campaign filter | DONE (API); UI pending | TC-F11-001, TC-F11-002, TC-F11-003, TC-F11-004, TC-F11-005, TC-F11-006, TC-F11-007, TC-F11-008, TC-F11-009 |

## 2. Use cases → test cases

UC03 and UC22 were removed before Report 3 and are not reused.

| UC | Name | Status | Test cases |
|---|---|---|---|
| UC01 | Manage User Accounts | DONE | TC-F01-001, TC-F01-019, TC-F01-020, TC-F01-021, TC-F01-023, TC-F01-024, TC-F01-025, TC-F01-026 |
| UC02 | Manage Roles & Permissions | DONE | TC-F01-020, TC-F01-022, TC-F01-023, TC-F01-025 |
| UC04 | Set Mining Threshold | DONE | TC-F05-001, TC-F05-002, TC-F05-003, TC-F05-026 |
| UC05 | Submit Async Mining | DONE | TC-F05-005, TC-F05-006, TC-F05-026 |
| UC06 | Schedule Mining / Occurrence History | PARTIAL | TC-F05-021, TC-F05-022, TC-F05-023, TC-F05-026 |
| UC07 | View Mining Request / Run Status | DONE | TC-F05-005, TC-F05-019, TC-F05-020, TC-F05-026 |
| UC08 | View HUI-Miner / EFIM Benchmark | REMOVED | TC-F05-025 |
| UC09 | Configure Utility Formula & Priority (incl. Seasons) | DONE | TC-F03-003, TC-F03-004, TC-F03-005, TC-F03-006, TC-F03-007, TC-F03-008, TC-F03-010, TC-F03-028 |
| UC10 | Generate Bundle / Cross-sell / FBT / Banner | DONE | TC-F06-001, TC-F06-003, TC-F06-015, TC-F06-017 |
| UC11 | Review / Approve / Reject | DONE | TC-F06-008, TC-F06-009, TC-F06-014, TC-F06-017 |
| UC12 | Arrange Layout | DONE (API) | TC-F07-003, TC-F07-015 |
| UC13 | Create A/B Layout Variants | DONE (API) | TC-F07-001, TC-F07-002 |
| UC14 | Prepare / Publish Campaign | DONE (API) | TC-F08-006, TC-F08-007, TC-F08-008, TC-F08-016, TC-F08-018 |
| UC15 | Run A/B Simulation | DONE (API) | TC-F08-001, TC-F08-002, TC-F08-003 |
| UC16 | Compare Simulated Results | PARTIAL | TC-F08-001, TC-F08-004, TC-F08-017 |
| UC17 | Manage Products | DONE | TC-F02-003, TC-F02-008, TC-F02-010, TC-F02-012, TC-F02-014 |
| UC18 | Manage Categories | DONE | TC-F02-001, TC-F02-002, TC-F02-014 |
| UC19 | Manage Selling Prices / Costs | DONE | TC-F02-009, TC-F02-014 |
| UC20 | Manage Inventory | DONE | TC-F02-014, TC-F02-015, TC-F02-027 |
| UC21 | Import Transaction Files | DONE | TC-F04-001, TC-F04-002, TC-F04-014, TC-F04-015 |
| UC23 | Activate Approved Campaign | DONE (API) | TC-F08-009, TC-F08-012, TC-F08-013, TC-F08-016 |
| UC24 | View Retail Performance Dashboard | DONE (API); UI pending | TC-F11-001 |
| UC25 | View A/B Reports | DONE (API read) | TC-F08-005, TC-F08-017 |
| UC26 | View Merchandising Homepage | DONE (API, MVP) | TC-F09-001, TC-F09-002, TC-F09-014, TC-F09-015 |
| UC27 | View Product Details / Related | PARTIAL (API) | TC-F09-005, TC-F09-006, TC-F09-016 |
| UC28 | View Recommended Bundles | DONE (API, MVP) | TC-F09-002, TC-F09-009, TC-F09-010, TC-F09-015, TC-F09-016 |
| UC29 | Add Whole Bundle to Cart | DONE (API) | TC-F10-001, TC-F10-007 |
| UC30 | Validate / Preprocess Orders | DONE | TC-F04-004, TC-F04-005, TC-F04-006 |
| UC31 | Create Frozen Utility Snapshot | DONE | TC-F03-022, TC-F03-023 |
| UC32 | Set Global Utility Lambda Policy | DONE | TC-F03-001, TC-F03-002 |
| UC33 | Select Store | DONE | TC-F01-033, TC-F05-004 |
| UC34 | Manage Stores | DONE | TC-F01-028, TC-F01-029, TC-F01-030, TC-F01-031, TC-F01-032, TC-F01-033, TC-F01-034, TC-F01-035, TC-F01-036, TC-F01-037, TC-F01-038 |
| UC35 | Manage own Store profile | DONE | TC-F01-041, TC-F01-042, TC-F01-043 |

## 3. Business rules → test cases

| BR | Rule | Test cases |
|---|---|---|
| BR-01 | Store isolation; foreign resource 404 | TC-F01-047, TC-F02-011, TC-F02-013, TC-F03-013, TC-F03-026, TC-F05-011, TC-F09-006, TC-F10-013 |
| BR-02 | One role per account; Store binding | TC-F01-001, TC-F01-020 |
| BR-03 | Stock derived, never stored | TC-F02-030 |
| BR-04 | Only COMPLETED, price ≥ cost for utility | TC-F03-024 |
| BR-05 | Weight v2 formula and rounding | TC-F03-004, TC-F03-005, TC-F03-016, TC-F03-017 |
| BR-06 | Snapshot immutable | TC-F03-023 |
| BR-07 | Only READY snapshots; EFIM-only | TC-F05-003, TC-F05-011 |
| BR-08 | Mining configuration versioned | TC-F05-001 |
| BR-09 | Candidates from COMPLETED runs of the same Store | TC-F06-001, TC-F06-003 |
| BR-10 | Review once, by a Merchandiser, with a reason | TC-F06-008, TC-F06-009 |
| BR-11 | Approval does not publish, activate or reserve | TC-F06-018 |
| BR-12 | Storefront changes only on Store Admin activation | TC-F08-006, TC-F08-009 |
| BR-13 | A/B results simulated and labelled | TC-F08-001, TC-F08-005, TC-F11-004 |
| BR-14 | Push day ≤ expiry; missing only warns; no default | TC-F02-020, TC-F02-021, TC-F03-018 |
| BR-15 | Season weight [0,1]; inclusive; highest wins; delete guard | TC-F03-011, TC-F03-012, TC-F03-014, TC-F03-015 |
| BR-16 | SA sets λmax; Merchandiser sets λ, α, β, N | TC-F03-001, TC-F03-003, TC-F03-006, TC-F03-020 |
| BR-17 | Frozen JSON read by version; unknown rejected | TC-F03-029 |
| BR-18 | Mining input bounds and int32 | TC-F05-012, TC-F05-013 |
| BR-19 | Consume only APPROVED ∧ ready, rechecked | TC-F06-010, TC-F06-011, TC-F09-009, TC-F09-010, TC-F09-016 |
| BR-20 | Per-product B; once per layout; F06 and A untouched | TC-F07-004, TC-F07-012 |
| BR-21 | One ACTIVE campaign per Store | TC-F08-010, TC-F08-019 |
| BR-22 | Layout score is a priority score, not revenue | TC-F07-003, TC-F08-017 |
| BR-23 | Priority product = W > 1 | TC-F07-014, TC-F08-001 |
| BR-24 | Scheduler enable 503 | TC-F05-022 |
| BR-25 | Only ACTIVE Store's active layout is public; no cost/batch data | TC-F09-001, TC-F09-004, TC-F09-008, TC-F09-014 |
| BR-26 | Bundle add merges eligible lines; no reservation/discount | TC-F10-015 |
| BR-27 | Store name unique (trim, ignore case) | TC-F01-029 |
| BR-28 | Store slug unique; SA only | TC-F01-030, TC-F01-042 |
| BR-29 | Store never hard-deleted | TC-F01-036 |
| BR-30 | INACTIVE Store users 403; no new SA work 409 | TC-F01-037, TC-F01-038, TC-F01-039, TC-F01-040, TC-F05-024 |
| BR-31 | Store image rules | TC-F01-043, TC-F01-044, TC-F01-045, TC-F01-046, TC-F01-048, TC-F09-014 |
| BR-32 | Expired only after the expiry date | TC-F02-022, TC-F02-023, TC-F02-024, TC-F02-032, TC-F03-021, TC-F06-012, TC-F09-007 |
| BR-33 | Product soft delete with guards | TC-F02-031, TC-F02-032, TC-F02-033, TC-F02-035, TC-F09-005 |

## 4. Non-functional and common requirements → test cases

| Requirement | Test cases |
|---|---|
| NFR 5.1 — External interfaces (SPA widths, REST/JSON) | TC-F01-051 |
| NFR 5.2 — Limits (import, mining, paging, images) | TC-F01-026, TC-F01-044, TC-F02-010, TC-F04-003, TC-F04-015, TC-F05-012, TC-F06-015, TC-F08-018 |
| NFR 5.3 — Reliability | TC-F08-019 |
| NFR 5.4 — Security (RBAC, 401/403/404, strict JSON, rate limits, cookies) | TC-F01-002, TC-F01-006, TC-F01-007, TC-F01-008, TC-F01-011, TC-F01-014, TC-F01-015, TC-F01-025, TC-F01-039, TC-F01-047, TC-F02-004, TC-F02-013, TC-F02-014, TC-F02-036, TC-F03-008, TC-F04-014, TC-F05-007, TC-F05-011, TC-F05-026, TC-F06-017, TC-F07-015, TC-F08-016, TC-F09-008, TC-F11-002 |
| NFR 5.5 — Usability (role landing, per-field errors, empty states, progress) | TC-F01-001, TC-F01-051, TC-F04-004, TC-F04-008 |
| Common 6 — Common requirements (time zone, money and utility scale, expectedVersion) | TC-F01-034, TC-F01-049, TC-F02-009, TC-F02-023, TC-F07-009, TC-F08-003, TC-F08-015, TC-F11-008 |

## 5. Diagram FR page (UML pack) → SRS

The FR page of `PASPM_Unified_UML2_Editable.drawio` (on `dev` since PR #37, `fb48d55`; FR08.04 relabelled on this branch) numbers requirements as `FRxx.yy`. They map to SRS IDs as follows; the test cases are those of the SRS IDs in §1.

| Diagram FR | SRS requirement(s) |
|---|---|
| FR01.01 Manage user accounts | FR-01.2, FR-01.4, FR-01.5 |
| FR01.02 Assign user roles | FR-01.4 |
| FR01.03 Control access by role (RBAC) | NFR 5.4, BR-01 |
| FR01.04 Manage Stores (create, edit, slug) | FR-01.6, FR-01.7 |
| FR01.05 Deactivate / reactivate Stores | FR-01.9 |
| FR01.06 Manage own Store profile & images | FR-01.8, FR-01.10 |
| FR02.01 Manage product details; soft delete | FR-02.1, FR-02.4, FR-02.5 |
| FR02.02 Manage product categories | FR-02.1 |
| FR02.03 Manage selling prices | FR-02.1 (UC19) |
| FR02.04 Manage product costs | FR-02.1 (UC19) |
| FR02.05 Manage inventory batches and expiry status | FR-02.2, FR-02.3 |
| FR03.01 Configure the Utility formula | FR-03.1 |
| FR03.02 Configure α, β, λ (λ ≤ λmax) and seasons | FR-03.3, FR-03.5 |
| FR03.03 Calculate weighted product Utility (Weight v2) | FR-03.1, FR-03.2 |
| FR03.04 Create a frozen Utility Snapshot / matrix | FR-03.4, FR-03.6 |
| FR04.01 Import CSV transaction files | FR-04.1, FR-04.2, FR-04.5 |
| FR04.04 Retain cancelled / refunded history | FR-04.3, FR-04.4 |
| FR04.05 Validate, group and report invalid orders | FR-04.3 |
| FR04.06 Prepare eligible transactions for utility profiling | FR-04.4 |
| FR05.01 System Admin sets minUtil | FR-05.1 |
| FR05.02 Mine with EFIM only | FR-05.1, FR-05.3, FR-05.4 |
| FR05.03 Run async mining and inspect patterns | FR-05.2, FR-05.7 |
| FR05.04 [GATED] Schedule periodic mining | FR-05.6 |
| FR05.05 Monitor mining request / run status | FR-05.5 |
| FR05.06 [REMOVED] Algorithm benchmark | UC08 (REMOVED) |
| FR06.01–FR06.04 Generate candidates (bundle, cross-sell, FBT, banner) | FR-06.1, FR-06.2 |
| FR06.05 Review / approve / reject | FR-06.3, FR-06.4 |
| FR07.01 Arrange products by drag-and-drop | FR-07.2, FR-07.3 |
| FR07.02 Show Utility Scores for placement | FR-07.4, FR-07.5 |
| FR08.01 Create baseline / suggested layouts | FR-07.1, FR-07.2 |
| FR08.02 Publish / manage layout campaigns | FR-08.1 |
| FR08.03 Simulate tests from historical data | FR-08.3 |
| FR08.04 Compare simulated A/B metrics (relabelled from "Compare CTR / conversion rate", DEC-20261126-03) | FR-08.3 |
| FR08.05 Activate approved layout campaign | FR-08.1, FR-08.2 |
| FR09.01 Display optimized homepage blocks | FR-09.2 |
| FR09.02 Display product details and availability | FR-09.3 |
| FR09.03 Display recommended bundle offers | FR-09.3 |
| FR10.01 [PLANNED] Add the whole bundle with one click | FR-10.2 |
| FR11.01 [PLANNED] Frozen snapshot weighted utility | FR-11.1 |
| FR11.02 [PLANNED] BUNDLE_ADDED actions: MINED / MANUAL | FR-10.4, FR-11.1 |
| FR11.03 [PLANNED] Saved simulated A/B reports | FR-11.1 |
| FR11.04 [REMOVED] Algorithm benchmark charts | — |

## 6. Owner decisions → test cases

Decisions cited directly in a case's trace. Decisions not listed here are covered through the FR/BR they changed.

| Decision | Test cases |
|---|---|
| DEC-20261116-03 | TC-F03-028 |
| DEC-20261116-04 | TC-F05-003, TC-F05-025 |
| DEC-20261117-02 | TC-F07-014 |
| DEC-20261119-05 | TC-F05-014, TC-F05-015 |
| DEC-20261119-06 | TC-F07-005, TC-F07-013, TC-F09-012 |
| DEC-20261119-07 | TC-F02-003, TC-F02-005, TC-F02-006 |
| DEC-20261123-01 | TC-F05-004, TC-F09-014 |
| DEC-20261124-01 | TC-F02-022 |
| DEC-20261124-02 | TC-F02-021 |
| DEC-20261124-03 | TC-F02-031 |
| DEC-20261125-04 | TC-F09-004, TC-F09-011 |
| DEC-20261125-06 | TC-F10-004, TC-F10-011 |
| DEC-20261126-02 | TC-F02-009, TC-F09-013, TC-F10-004, TC-F10-016, TC-F11-006 |
| DEC-20261126-03 | TC-F01-001, TC-F01-027, TC-F01-044, TC-F02-006, TC-F03-028, TC-F05-006, TC-F05-007, TC-F05-020, TC-F05-021, TC-F05-022, TC-F05-023, TC-F05-024, TC-F05-025, TC-F08-009, TC-F08-017, TC-F09-001, TC-F09-013, TC-F09-016, TC-F10-006 |
| DEC-20261127-01 | TC-F01-001, TC-F09-015 |

[DEC-20261126-02](../../decisions/2026-11-26/DEC-20261126-02-vnd-display-cart-cap-dashboard-window-merch-screens.md) (PO answers of 2026-11-26) sets: VND displayed as whole đồng HALF_UP; F10 single-item writes above 99 refused whole with 409 `CART_QUANTITY_LIMIT`; F11 window of 90 days inclusive accepted, 91 refused; the Merchandiser screen gap on FE `dev` is a defect to fix before the run (TEST_PLAN.md §7 and §9).

[DEC-20261126-03](../../decisions/2026-11-26/DEC-20261126-03-blackbox-test-round-scope-and-remaining-answers.md) (remaining PO answers of 2026-11-26) sets the scope of this round: scheduler limited to the 503 check, F09 on the API, Merchandiser mining in the UI at `/mining?view=setup` (addendum), removed FE routes must be unreachable (KD-02), binary MiB limits, similar-name threshold on the shorter name, whole-request refusal above sellable stock in F10, Stores B/C from a preparation script, and the exit criteria.

## 7. Requirements with no black-box test, and why

| Requirement | Why there is no executable black-box case | Covered instead by |
|---|---|---|
| UC08 / diagram FR05.06 / FR11.04 — benchmark | REMOVED on 2026-11-16 (DEC-20261116-04). Only its absence is tested. | TC-F05-025 (negative) |
| UC27 — Related Products | Deferred after the F09 MVP; no API exists. | — |
| UC16 / diagram FR08.04 — CTR and estimated revenue | Not part of PASPM (DEC-20261126-03); the diagram label FR08.04 was changed to "Compare simulated A/B metrics". | TC-F08-001/017 check that no CTR or revenue is shown |
| FR-05.6 — scheduled dispatch behaviour (SKIPPED occurrences, retries, RETRY_EXHAUSTED, STORE_INACTIVE skip) | Not in this test round (DEC-20261126-03): the scheduler stays gated (BR-24) and the round only checks that enabling returns 503 (TC-F05-022). TC-F05-021 (schedule creation) and TC-F05-023 (history) are kept as [OUT OF ROUND]; the schedule part of FR-01.9 (409 for an INACTIVE Store) is not run either. | Automated opt-in tests `MiningScheduledActivationE2EMySqlTest`, `MiningSchedulerMultiJvmMySqlTest` (not part of this pack) |
| FR-04.2 / FR-05.4 / NFR 5.3 — leases, fencing, takeover after a crash; 256 MiB heap; 64 KiB log cap | Internal mechanisms with no user-visible input; they need process kills or memory probes. | Automated backend tests; out of black-box scope |
| FR-06.2 — 10,000-candidate cap | Needs a run with more than 10,000 candidates, which the 2,000-pattern cap makes impractical to build by hand. The PO decided the automated test is the evidence (DEC-20261126-03). | — |
| NFR 5.2 — response time, throughput, availability | No targets defined (TBD in SRS §5.2). | Measurements only, in separate performance reports |
| NFR 5.4 — per-IP rate limits (30/IP login, 10/IP registration, 20/IP OTP) | Needs many source IPs or a proxy; only the per-email login limit is in this pack. | TC-F01-011 (per-email) |
| FR-01.1 — successful Google sign-in | Needs a configured Google client ID and real Google accounts; the default local stack has none. | TC-F01-012 (503 path); conditional note in the case |
| §7 Messages — MSG numbers for new messages | MSG numbers are TBD in the SRS; cases check the message text instead. | — |

F10 (PR #38, #44) and F11 (PR #45) are implemented on `dev`; their cases follow the implemented API docs. Storefront cart and dashboard UI steps wait until the FE calls the real API; the API steps run now. The diagram labels above are quoted as drawn.

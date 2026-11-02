# Project report ↔ Backend ↔ UML — consistency review (2026-11-04)

**Result: the project report documents found in the workspace do not describe the system that the Backend implements and the UML pack draws.** The mismatch is large, concentrated on the mining algorithms, the roles and the run lifecycle, and it must be resolved (or explicitly explained) before the review. This document lists the mismatches with evidence and gives paste-ready replacement wording. **No report file was edited**; nothing here changes an approved decision, and no test result or release status is invented.

## 1. What was reviewed

| Document (workspace path) | Last modified | Notes |
|---|---|---|
| `sprint_2/Report2_Research_Proposal_and_Plan_v5_1_Architecture_Update.docx` | 2026-09-24 | latest Report 2 found; "v5.1 Architecture Update" |
| `sprint_2/Report1_Project_Introduction_v5_Official_Template.docx` (a non-template `…v5.docx` of the same date also exists) | 2026-09-23 | latest Report 1 found |
| `sprint_2/ERD_Description_v4.docx` (Vietnamese) + `ERD_Conceptual_Logical_v4.drawio` | 2026-09-21 | conceptual/logical design, 32 logical tables |
| Backend repository documents (`docs/api`, `docs/features/F01–F06`, `docs/architecture`, decision register, reports) | current at `c65daf2` | consistent with code per the 2026-11-02 audit; used as the reference |

Compared against: Backend source at `c65daf2` (OpenAPI 89 operations, Flyway V1–V19, roles in `V1`), the 27-page UML pack and the F05 ERD. **Assumption to confirm:** these are the report versions that will be reviewed. If newer versions exist outside this workspace, apply this review to them.

## 2. Mismatches (highest impact first)

| # | Report says (section) | Backend / UML reality (evidence) | Severity | Action |
|---|---|---|---|---|
| R1 | The mining algorithms are **TKQ** (top-k quantitative) and **FEACP** (cross-level, taxonomy), benchmarked against FHUQI-Miner and CLH-Miner (Report 1 objectives; Report 2 §2.2–§2.4, §3, WBS WP5–WP6) | The Backend implements **HUI-Miner and EFIM** through pinned SPMF 2.67a. There is **no TKQ, FEACP, CLH-Miner or FHUQI-Miner code or documentation** (searched `src/` and `docs/`: none). The comparison implemented is HUI-Miner vs EFIM (`comparisonStatus` MATCH / FAILED / BLOCKED). Report 2's own history says the Capstone Project Register approved HUI-Miner and EFIM | **critical** | Decide which is authoritative. Either (a) add an "Implemented system" section/addendum (text in §4), state TKQ/FEACP as the research direction **not implemented in the prototype**; or (b) revise the objectives. Do not present TKQ/FEACP results or capabilities as delivered |
| R2 | Roles: **Admin** and **Store User (Viewer)** plus a Third-Party Mining Service actor (Report 1 scope; Report 2 "Roles and access"; ERD description: `ROLE` = ADMIN, VIEWER) | Four business roles: `SYSTEM_ADMIN`, `STORE_ADMIN`, `MERCHANDISER`, `END_CUSTOMER` (V1) plus a hidden machine principal `SYSTEM_SCHEDULER` (V19). UML Use Case: 4 actor roles | high | Align the actor list with the Use Case diagram; map Viewer → Merchandiser/Store Admin; remove the third-party actor or mark it "not used" |
| R3 | A **Store User starts, monitors and cancels** their own mining runs and selects parameters within Admin limits (Report 2 "Roles and access") | Mining is submitted by **SYSTEM_ADMIN** for an explicitly selected Store (`POST /api/system/mining/requests`); the algorithm and `minUtil` come from a global System Admin policy; Store Admin and Merchandiser **read** own-Store requests, runs and patterns. **No cancel operation exists** (OpenAPI has none) | high | Rewrite the role/task paragraph; list cancellation as not implemented |
| R4 | Run lifecycle states **QUEUED, PREPARING, RUNNING, PARSING, SUCCEEDED, FAILED, TIMED_OUT, CANCELLED** (Report 2 §7.3 "Runtime contract") | Run: `PENDING, RUNNING, COMPLETED, FAILED, TIMED_OUT`; request: `PENDING, RUNNING, COMPLETED, FAILED` plus `comparisonStatus`; scheduled occurrence: six states (`SKIPPED` added by V18). UML state pages R2-06.03/04 and DB `CHECK` constraints agree with each other | high | Replace the state list with the implemented ones (diagrams R2-06.03, R2-06.04) |
| R5 | Execution: **in-process in the backend JVM is preferred; a child JVM is the fallback**; run-scoped workspace and artifact retention (Report 2 §7.3 options A/B/C) | Always **option B**: SPMF runs in an isolated **Java 25 child process** (Backend is Java 17), private temp workspace per run, parent-death kill on Linux, DB-polled queue with leases and fencing tokens. No network microservice (option C) — that part agrees | medium | State option B as the implemented mode; keep A as rejected |
| R6 | Persistence: "MySQL with **Spring Data JPA**" (Report 2 §7.3) | **Hybrid**: Product/Category use JPA; imports, utility, mining, scheduler and recommendation use JDBC (`docs/architecture/HYBRID_PERSISTENCE_DECISIONS.md`) | low | Say "hybrid JPA/JDBC" |
| R7 | Data model: 32 logical tables incl. `USER_ACCOUNT`, `STORE_MEMBERSHIP`, `DATASET_VERSION`, `ALGORITHM_VERSION`, `RESOURCE_POLICY`, taxonomy tables (ERD description v4) | Implemented schema (V1–V19): `app_user`, `role`, `store`, `product`, `category`, `inventory_batch`, `import_batch`, `import_staging_row`, `sales_transaction`, `transaction_item`, `utility_snapshot`, `utility_matrix_item`, `mining_config_*`, `mining_request`, `mining_run`, `high_utility_pattern`, `pattern_item`, `mining_schedule`, `mining_schedule_occurrence`, `recommendation*`, … (29 `CREATE TABLE` statements in the SQL migrations). No taxonomy-version, membership, quota or algorithm-version tables | high | Present ERD v4 as the **conceptual design baseline** and cite the implemented ERDs (F02, F03, F05 scheduler) and `F05_SCHEDULER_SCHEMA_V14_V19.md`; note there is no single V1–V19 ERD yet |
| R8 | Admin features: algorithm enable/disable, version management, quotas, system audit log, run-health views; Store User exports and dashboards (Report 2 "Roles and access", presentation layer) | No such operations in OpenAPI (searched for quota, audit, export, algorithm, member, dashboard, report: none). Dashboards/A-B are PLANNED (F07–F11) | high | Mark as not implemented / planned; keep out of the "delivered" claims |
| R9 | Inputs: frozen **product taxonomy**, quantity-aware inputs for TKQ, benchmark datasets as data source (Report 2 §2.2, §7.1) | Inputs are F04 CSV transaction imports → F03 **immutable utility snapshots** (weights S/M/E, lambda policy) → normalized frozen utility matrix; categories are flat; no taxonomy versions | high | Add the F03/F04 pipeline description; drop or defer taxonomy |
| R10 | Report 1 says execution is by a "**Third-Party Mining Service** through a versioned integration contract"; Report 2 v5.1 says the network microservice is **out of scope** and mining is local | Code agrees with Report 2 (local), not with Report 1 | medium | Make Report 1 consistent with Report 2 (internal inconsistency) |
| R11 | Feature coverage: the reports do not mention F01 auth/Google sign-in, F02 inventory batches with expiry, F04 CSV import rules, F03 utility weights, **scheduled mining**, Store picker, the scheduler principal, `SKIPPED` | All implemented (F05 scheduler gated) and drawn in the UML pack | medium | Add the addendum below so the report covers the implemented scope |
| R12 | Branching: Git Flow with `main`/`develop` (Report 2 §7.2) | Integration branch is `dev`; work happens on `Khoi/*` feature branches merged by pull request | low | Update the branch names |
| R13 | "Java 17, pinned" (Report 2 §7.3) | true for the Backend; the SPMF child additionally needs **Java 25** | low | Add the child runtime |

## 3. What already agrees

Stack ReactJS + Java Spring Boot + MySQL (Java 17 Backend) · Store-scoped data and authorization on every business table · asynchronous mining with a database-backed queue claimed atomically (implemented with leases/fencing) · pinned algorithm implementation with checksum (SPMF SHA-256) · timeouts and failure handling recorded per run · patterns are separate from recommendations; F06 candidates keep source evidence and review history; approval does not publish · no live-storefront changes, no real store data claims · network microservice out of scope · F07–F11 not claimed as implemented in the UML pack.

> **Decision recorded 2026-11-02 (project owner):** keep the reports and align them through an addendum. The adopted wording for algorithms, roles and status is in [`REPORT_ADDENDUM_ALGORITHMS_ROLES.md`](REPORT_ADDENDUM_ALGORITHMS_ROLES.md) with a check against the code and two precision edits. R1–R4 and R10 are resolved by that wording once it is inserted in the documents; R5–R9, R11–R13 still need their own sentences (the addendum below covers them).

## 4. Paste-ready addendum: "Implemented system as of 2026-11-02 (Backend `c65daf2`)"

> The prototype implements a Spring Boot (Java 17) / MySQL 8.4 backend with six delivered feature areas. **F01** authentication and users with four business roles (System Administrator, Store Administrator, Merchandiser, End Customer). **F02** products, categories and inventory batches. **F04** CSV transaction import with per-order validation. **F03** immutable, hashed utility snapshots derived from imported transactions using configurable S/M/E weights. **F05** asynchronous high-utility mining over a frozen snapshot with the two SPMF algorithms HUI-Miner and EFIM, executed in an isolated Java 25 child process with database leases and fencing tokens, producing runs, patterns and a HUI-Miner/EFIM comparison. **F06** reviewable recommendation candidates generated from completed F05 runs, with approve/reject decisions and audit history; approving a candidate never publishes a layout.
>
> A System Administrator may also define weekly or monthly **scheduled mining** per Store. This capability is implemented but **disabled by default** and has not been approved for release: after downtime only the latest missed run executes and earlier ones are recorded as skipped; each run creates its own fresh snapshot and executes under a dedicated machine identity. TKQ and FEACP, taxonomy-based mining, run cancellation, quotas, report export, dashboards, A/B layouts and the customer storefront (F07–F11) are **not implemented**. Automated tests were run locally (see the acceptance matrix); continuous integration and native-Linux runs have not been executed and no release sign-off exists.

## 5. Consistency of the report with the UML pack (after the diagram work)

| UML page | Report agreement |
|---|---|
| Use Case (31 use cases, 4 actors) | disagrees with report roles (R2, R3) |
| AD01–AD05 workflows | disagree with TKQ/FEACP branches (R1, R9); AD02/AD05 agree with F04/F06 code |
| R2-06.0x state machines | disagree with Report 2 §7.3 states (R4); agree with the database constraints |
| R2-04 architecture | agrees on stack; adds the child process, scheduler (gated), principal |
| Sequence AD01–AD05 | disagree on who starts runs and how (R3, R5) |
| ERD (F05 scheduler) | not covered by the report (R7, R11) |

## 6. Not done

The `.docx` files were not edited; the Vietnamese ERD description was not re-derived from the code; no PDF of the report was produced. Which wording to adopt (R1 especially) is a project decision, not mine.

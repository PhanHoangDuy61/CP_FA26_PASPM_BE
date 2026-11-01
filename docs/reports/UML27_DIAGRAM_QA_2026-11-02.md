# 27-page UML 2.0 diagram audit — 2026-11-02

File: [`docs/diagrams/PASPM_Unified_27_Diagrams_UML2_Editable.drawio`](../diagrams/PASPM_Unified_27_Diagrams_UML2_Editable.drawio) (editable diagrams.net source, 27 pages, page ids unchanged). Baseline code: `dev` @ `11973a0` (tree identical to the verified Core commit `a4f1115`). Starting point: branch `Khoi/docs/diagrams-27-uml2-f03-f05-sync` (`ae55fd9`, itself derived from `3e369f0`), imported file-by-file; neither old branch was modified. Notation reference: `uml2_diagrams.pdf` (33 pages, read in full: activity, state machine, sequence with combined fragments, use case with include/extend, component and deployment notation).

## How each page was judged
1. Text and connectors extracted from the XML and compared with the controllers, `SecurityConfig`, DTOs, status constraints in the Flyway SQL and the worker/lease code.
2. Rendered with the installed draw.io CLI (all 27 pages) and looked at; pages changed later were re-rendered.
3. UML semantics checked against the PDF notation. Result vocabulary: **CORRECTED** (defect fixed here), **VERIFIED** (reviewed, no defect found), **PLANNED** (design of features F07–F11 that do not exist in the Backend; labelled as such, not code-checked), **INFORMAL** (not UML notation: requirements list, context/data-flow view, screen map, block architecture; labelled as such).

## Page matrix
| # | Page (id) | Type | Feature | Result | What was checked / changed |
|---|---|---|---|---|---|
| 1 | FR (`polished-fr`) | INFORMAL requirements overview | F01–F11 | CORRECTED | FR04.01 no longer claims Excel import (CSV on dev, Excel pending); FR05.04 already shows DISABLED · 503 |
| 2 | Context (`polished-context`) | INFORMAL context view | all | CORRECTED | M2 flows were Merchandiser submitting mining → now own-Store status/pattern queries; new A3 IN/OUT for System Admin (policy, manual run, schedule settings / benchmarks); S2 and footer: Excel approved, not on dev; D1 noted |
| 3 | Use Case (`polished-usecase`) | UML use case | all | CORRECTED | UC07 (view status) associated with Merchandiser and Store Admin via actor aliases (System Admin read is D1-gated, noted); new UC32 *Set Global Utility Lambda Policy* (System Admin); UC21 label: Excel pending; numbering of UC01–UC31 untouched; include/extend directions verified |
| 4 | AD01 overall workflow | UML activity | F02–F06 | CORRECTED | roles note (mining belongs to System Admin), Excel wording, AD03 node names the requester and policy minUtil; initial/final/decision/guards verified |
| 5 | AD02 transaction processing | UML activity | F04 | CORRECTED | Excel branch marked "not on dev"; decision guards and fatal-error path verified against the import pipeline; CSV limits unchanged |
| 6 | AD03 mining execution | UML activity | F05 | CORRECTED | System Admin lane; schedule branch `[schedule · DISABLED]`; note: full queue keeps the request PENDING, expired lease reclaimed (fence + 1) |
| 7 | AD04 benchmarking | UML activity | F05 | CORRECTED | lane is System Admin (was Merchandiser); final step reads `BenchmarkView` incl. comparisonStatus, runId from a Store user or D1 gate |
| 8 | AD05 recommendation and approval | UML activity | F06 | VERIFIED | generation cap 10,000 (`MAX_GENERATED_CANDIDATES`), 409 paths, review guards (`expectedVersion`, reason, eligibility), loop merge |
| 9 | AD06 layout and campaign | UML activity | F07/F08 | PLANNED | consistent with "F06 approval does not publish" |
| 10 | AD07 A/B simulation | UML activity | F08 | PLANNED | simulation-only labelling kept |
| 11 | AD08 customer shopping | UML activity | F09/F10 | PLANNED | not an implemented Backend flow |
| 12 | Screen Flow (`r2-01-page`) | INFORMAL FE navigation map (not UML) | FE | CORRECTED note | added: Expiry Window screen ↔ deprecated endpoint, API Connections has no Backend API. **Not compared with the Frontend repository (out of scope): the screen list is NOT VERIFIED against FE source** |
| 13 | System Architecture (`r2-02-page`) | INFORMAL block view with `«component»`/`«database»`/`«process»` keywords (not a full UML component diagram: no ports or interfaces) | all | CORRECTED | Flyway V1–V17, MySQL 8.4, mining worker (polling, lease, fencing), Java 25 SPMF child, scheduler component OFF by default (503), D1 gate in the security box |
| 14 | State: Import Batch | UML state machine | F04 | VERIFIED | `PROCESSING → COMPLETED / COMPLETED_WITH_ERRORS / FAILED` equals the CHECK constraint |
| 15 | State: Utility Snapshot | UML state machine | F03 | CORRECTED | final transition relabelled: the snapshot stays READY and immutable (schema allows FAILED, never persisted) |
| 16 | State: Mining Request | UML state machine | F05 | CORRECTED | false `PENDING → FAILED` removed; `RUNNING → RUNNING` lease reclaim (fence + 1); claim label; note that queue rejection keeps PENDING |
| 17 | State: Mining Run | UML state machine | F05 | CORRECTED | false `PENDING → FAILED` replaced by `RUNNING → PENDING` (reset on reclaim); note: COMPLETED runs are kept |
| 18 | State: Recommendation Review | UML state machine | F06 | VERIFIED | `PENDING_REVIEW → APPROVED/REJECTED` only, guards match the review service |
| 19 | State: Staging Row | UML state machine | F04 | VERIFIED | `PENDING/VALID/REJECTED/PROCESSED` equals the CHECK constraint |
| 20 | Sequence AD01 | UML sequence | F02–F06 | CORRECTED | the mining request (message 9) came from the Merchandiser lifeline and the failure notice (14) went to it; both now use the System Admin lifeline; messages 2 and 12 reworded (CSV, D1 gate) |
| 21 | Sequence AD02 | UML sequence | F04 | CORRECTED | message 2 marked "not on dev"; break/loop/alt fragments verified |
| 22 | Sequence AD03 | UML sequence | F05 | CORRECTED | asynchronous wake message, lease claim (fence + 1), note on queue rejection, reclaim, heartbeat 5 s / lease 30 s; reads gated by D1 |
| 23 | Sequence AD04 | UML sequence | F05 | CORRECTED | participant and note: benchmarks are System Admin only; lease claim message |
| 24 | Sequence AD05 | UML sequence | F06 | VERIFIED | self messages, alt/break fragments, atomic persist and rollback |
| 25–27 | Sequence AD06, AD07, AD08 | UML sequence | F07–F10 | PLANNED | labelled PLANNED/SIMULATED |

Counts: 16 pages corrected (1–7, 12, 13, 15, 16, 17, 20–23; some of those corrections were made earlier on `ae55fd9` and re-verified here), 5 verified without change (8, 14, 18, 19, 24), 6 PLANNED (9–11, 25–27). Total 27.

## Validation
* The XML parses; 27 pages in the original order with the original ids; no cell id removed; 15 cells were added relative to `3e369f0` (UC32, two actor aliases with their labels, three associations, two context flows, the scheduler node with two edges, two state-page notes); this pass added none.
* No duplicate ids and no dangling `source`/`target`/`parent`; the 95 unconnected lines (lifelines, fragment dividers, FR rules) are pre-existing free-standing shapes.
* diagrams.net desktop (v29.6.1) exported all 27 pages to PNG, which proves the file opens; every page was viewed. Defects found while viewing and fixed: label overlapping a transition (state request page), note colliding with a transition (run page), text overflow in the scheduler node and in a sequence note, alias edges running inside an existing edge bundle.
* The file remains the editable source; no PDF, PNG or SVG replaced it. Temporary PNG renders are outside the repository.

## Remaining discrepancies
1. Page 13 is not a UML component or deployment diagram (no provided/required interfaces or ports, no «node» deployment view of Linux Docker, Java 17 backend, Java 25 child and MySQL). Adding one would be a 28th page and was not done; recommended.
2. No state machine for the schedule occurrence (`PENDING`, `PROCESSING`, `DISPATCHED`, `COMPLETED`, `FAILED`): decisions D6/D8 may change the states, so it is not drawn.
3. Page 12 is unverified against the Frontend source; planned pages 9–11 and 25–27 describe features that do not exist.
4. The legacy Mermaid pack (`docs/diagrams/*.mmd` and `rendered/`) was **not** regenerated and is stale in places (use-case file lets Store roles create mining requests; architecture says Flyway V1–V11; the ERD stops at V11). It is marked as such in the diagrams README and left unchanged because its SVG renders cannot be regenerated here.
5. F01 and F02 have no dedicated state machine or sequence page; they appear in the use case, context, AD01 and architecture pages only.

# UML 2.0 pack — F05 Scheduler synchronisation QA (2026-11-02)

Scope: [`PASPM_Unified_27_Diagrams_UML2_Editable.drawio`](../diagrams/PASPM_Unified_27_Diagrams_UML2_Editable.drawio) (27 pages, no page added or removed) brought in line with the **implemented** behaviour of Scheduled Mining Option B on the Backend branch `Khoi/feat/f05-scheduled-mining-option-b-completion` @ `89e6ee5` (decisions D1–D10 confirmed 2026-11-02, [register](../decisions/PO_DECISION_REGISTER_F05_2026-11-01.md)). This diagram branch (`Khoi/docs/f05-scheduler-uml2-final-sync`) starts from that Backend commit, **not** from `dev`: the Backend branch is not merged. Rebase or merge accordingly once it lands. Status: `UML_SYNCED` locally; nothing pushed, no PR.

Only behaviour that exists in code and tests was drawn. Nothing was added for F07–F11 (still PLANNED), and nothing is shown as released: scheduling is drawn as **gated, off by default**.

## Pages changed (11) and pages left byte-identical (16)

| Page (id) | Change | Why |
|---|---|---|
| FR (`polished-fr`) | FR05.04 now `[GATED · off by default]`, black (implemented), legend updated | was `[DISABLED · 503]`, grey |
| Context (`polished-context`) | F05 note: System Admin reads Store snapshots/requests/runs, never raw patterns; scheduling implemented but gated | D1, D10 |
| Use Case (`polished-usecase`) | UC06 `[GATED · off by default] Schedule Mining Jobs / Review Occurrence History` with its System Administrator association (it had none); UC07 notes System Admin has no raw patterns; new **UC33 Select Store** (Store picker, D2) with its association; subtitle 31 use cases; note rewritten | D1, D2, D10 |
| AD01 (`paspm-unified-ad01`) | ROLES and BOUNDARIES notes: Store picker, Store reads (no patterns), gated schedules, scheduler creates its own fresh F03 snapshot | F03 → F05 → F06 integration |
| AD03 (`paspm-unified-ad03`) | The `DISABLED … 503` stub is replaced by a third swimlane **PASPM SCHEDULER (DISPATCHER) · GATED**: reserve latest due fire (older → SKIPPED) → claim lease and create fresh F03 snapshot + Mining Request in one transaction → `Prepared?` (`[failed]` → FAILED + failureMessage, retryable 3 attempts 15 s / 30 s; `[prepared]` → the existing worker flow); preconditions, subtitle and note updated; page widened | D3, D6, D7, D9, D10 |
| AD04 (`paspm-unified-ad04`) | benchmark step reads `runId` from the System Admin run list | D1 |
| Screen Flow (`r2-01-page`) | Role-gap note: Store picker and Store snapshot/request/run reads are System Admin only, patterns are Store-only | D1, D2 |
| System Architecture (`r2-02-page`) | Security node (no System Admin pattern reads), `Flyway V1–V19`, scheduler component `GATED` | D1, D10, V18/V19 |
| R2-06.03 (`r2-05-page`) | Mining Request state machine unchanged; new panel **R2-06.03b Schedule Occurrence State**: `PENDING`, `PROCESSING` (self-loop: lease reclaim), `DISPATCHED`, `COMPLETED`, `FAILED`, `SKIPPED`, with every transition, the retry rule and the disable/`HTTP_409` note. Page title and id unchanged; page widened | D6, D7, D8 (no 28th page) |
| Sequence AD01 (`r2-09-page`) | `[Scheduled run — gated]` operand: `S1` activate, `S2` fresh F03 snapshot at the fire time (older SKIPPED), `S3` snapshot or explicit failure, note; step 12 wording; the following `opt` guard covers immediate **or scheduled** requests | F03 → F05 → F06 |
| Sequence AD03 (`r2-11-page`) | `[Scheduled mode — gated]` operand: gate note and messages `S1`–`S7` (PUT schedule, persist, reserve with SKIPPED, claim and fresh snapshot, snapshot or `HTTP_400`, persist request in the same transaction, wake worker) and a retry/disable note; participant renamed `Scheduler (dispatcher, gated)`; message 19 wording; the rest is shifted down | D1, D6, D7, D8, D9, D10 |

Unchanged and byte-identical: AD02, AD05–AD08, R2-06.01/02/04/05/06, Sequence AD02, AD04–AD08 (Mining Run state needed no change: runs, leases and fencing are as before).

Identities: all 27 `<diagram id>` values and names are unchanged and in the same order; no existing cell id was removed except the retired scheduler stub (`schedule`, `ad03_schedule_end`, `ad03_schedule_end_dot`, `e5` on AD03; `r2-09-note-18`); `e3` kept its id and gained a new target. New ids are unique per page.

The two ERD files (`PASPM_Complete_ERD_F02_Inventory_Batch_v3.drawio`, `PASPM_Complete_ERD_F03_Three_Weights_v4.drawio`) and the second F02 copy under `docs/features/F02/reference_v3/` were **not touched**. They do not show V18/V19 (`skip_reason`, `failure_message`, `SYSTEM_SCHEDULER`); that is unchanged by request and noted as a gap.

## Checks run

| Check | Result |
|---|---|
| XML well-formed (`xml.dom.minidom`) | PASS |
| 27 pages, ids/names/order identical to the base | PASS |
| Unique cell ids per page; no dangling `source` / `target` / `parent` | PASS (0) |
| Draw.io desktop CLI export of every page (`-x -f png -p 1..27`) and of the whole file to PDF | PASS (27/27 exported, exit 0) — same engine as diagrams.net; the file was **not** opened by hand in the diagrams.net web editor |
| Visual review of every changed page at readable scale | done for FR, Context, Use Case, AD01, AD03, R2-03, R2-04, R2-06.03, Sequence AD01, Sequence AD03; AD04 text change is one label. Fixes made after review: UC33 alias actor removed (overlapped other actors), UC06/FR05.04 styling, two overflowing architecture node texts shortened, transition routing on the occurrence machine |
| UML semantics | Activity: one initial node per flow, partitions, guarded decision edges, flow finals, merge into the existing `run` action. State machine: initial → PENDING / SKIPPED guarded, every non-final state has an exit, final reached from COMPLETED / FAILED / SKIPPED, self-transition for lease reclaim. Sequence: `alt` operands with guards, solid synchronous and dashed return messages, messages numbered `S1…` inside the scheduled operand so the existing numbers 2–23 stay valid. Conventions were checked by eye against the UML 2.0 reference PDF (`uml2_diagrams.pdf`, present locally); there is no automated UML conformance tool in this repository |

## Not done
No new page, no ERD edit, no diagram for F07–F11, no Mermaid (`.mmd`) refresh (that pack is documented as stale), no Frontend screen. The diagrams state `RELEASE_SIGNOFF_PENDING` implicitly by drawing scheduling as gated; they must be revisited after release sign-off and when the Backend branch is merged.

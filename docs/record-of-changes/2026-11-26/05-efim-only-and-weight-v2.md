# ROC-20261126-005 — EFIM-only mining and Weight v2 in the UML pack

| Field | Value |
|---|---|
| Date / timezone | 2026-11-26 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Sonnet 5.5), requested by Nguyễn Minh Khôi (unattended overnight task) |
| Type | Documentation (diagrams) |
| Branch / base | `Khoi/docs/diagrams-update-20261126` / `origin/dev` @ `b94584b` |
| Rules drawn | [DEC-20261116-04](../../decisions/2026-11-16/DEC-20261116-04-f05-efim-only-mining.md) (EFIM-only; benchmark and compare mode removed 2026-11-16); SRS FR-03.1–FR-03.6 (Weight v2, seasons, λmax), FR-05.1 |
| Release | UNRELEASED |
| Daily index | [2026-11-26](README.md) |

## Scope and changes

**EFIM-only (HUI-Miner, compare mode, benchmark removed)**
- **Tombstone pages.** AD04 "Algorithm Benchmarking", R2-08.04 "Sequence AD04" and R2-07.04 "Class Diagram Sequence AD04" keep their number (so the numbering of the other 33 pages does not change) and their page ids, are renamed "… [REMOVED]", and now hold one note: removed on 2026-11-16 by DEC-20261116-04, one EFIM run per request, benchmark endpoints gone, `compareAlgorithms` rejected with 400, historical runs stay readable.
- **FR:** FR05 header "Mining Configuration" (no "Benchmark Hub"); FR05.01 minUtil only; FR05.02 "Mine with EFIM only"; FR05.03 "inspect patterns"; FR05.06 and FR11.04 marked `[REMOVED]`.
- **Context:** A3 OUT no longer lists benchmarks. **Use Case:** UC04 "Set Mining Threshold (minUtil policy)", UC05 "Submit Async EFIM Mining", UC08 `[REMOVED]` (the SRS marks it REMOVED).
- **AD01, AD03:** the mining step says EFIM; the policy supplies minUtil only; the read step no longer mentions comparison MATCH / FAILED / BLOCKED.
- **R2-03 Screen Flow:** S06 Benchmark marked `[REMOVED]`; the role-gap note no longer lists benchmark among the System Admin routes.
- **R2-04 Architecture:** the SPMF node is "SPMF Java 25 child process (EFIM only)". **R2-05 Package:** the unconnected element "result comparison" of the Mining package is removed. **R2-06.03:** the two request-completion guards no longer mention comparison.
- **Sequence and class pages of AD01 and AD03** (R2-08.01, R2-07.01, R2-08.03, R2-07.03): the `compare` / `compareAlgorithms` parameters are removed from the messages and the operations; the enumerations are marked "HUI_MINER (legacy runs)" and "ComparisonStatus (legacy)".
- **ERD:** no change. The V26 ERD already carries the legacy note for `algorithm`, `comparison_status` and `compare_algorithms` (columns are kept for history, V24 is not edited).

**Weight v2**
- **New UML page AD11 "Utility Weight v2 & Snapshot"** (swimlanes Merchandiser and PASPM / Utility Service): set α, β (α + β = 1) and λ; check λ ≤ λmax (otherwise 409, `lambdaValid = false`); U_expiry by FEFO over batch push days with the sales rate v of the last N days; M = highest active season weight (start and end dates included); warnings `MISSING_PUSH_DAY` and `SLOW_MOVING`; W = 1 + λ(α·U_expiry + β·M) rounded to 2 decimals HALF_UP; WeightedUtility = Base × W (scale 6); an immutable snapshot read by `scoringVersion`.
- **FR03.02 / FR03.03, UC09, R2-04 utility node** use the Weight v2 wording.

## Not done

- The ERD already holds V24–V26 (season, product_season, snapshot season copies); the V24–V26 tables were not redrawn.
- No class or sequence page for Weight v2 (the baseline has none for F03).
- The Mermaid mirrors (`mermaid/state-mining-request-and-schedule-occurrence.mmd`, `mermaid/architecture.mmd`, activity mirrors) were not regenerated.
- Pages with "compare" in the sense of the F08 A/B simulation (AD07, R2-08.06/07, R2-07.06/07, UC16) are unrelated and unchanged.

## Verification

| Check | Result |
|---|---|
| `validate_drawio.py` on the pack (42 pages) | PASS |
| Pages not listed above | byte-identical |
| PNG export, looked at: AD11 (after one layout fix), R2-04; the other changed pages are label edits | `outputs/diagrams-overnight/png/G-efim-weightv2/` |
| Maven | NOT RUN |

## Questions for the PO

- Should the tombstone pages AD04, R2-08.04 and R2-07.04 (and UC08, FR05.06, FR11.04, S06) be deleted and the pages renumbered? They were kept as marked tombstones, the conservative choice.

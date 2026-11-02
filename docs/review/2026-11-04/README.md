# Review package — 2026-11-04 (academic diagram and report review)

Everything here is documentation prepared locally on branch `Khoi/docs/f05-scheduler-uml2-final-sync`, based on Backend commit **`c65daf2`**. Nothing was pushed, merged or deployed for this package; production Backend code was not touched.

| Deliverable | File |
|---|---|
| Review-ready PDF (29 pages: cover with legend and contents, the 27 pack pages, F05 scheduler ERD; bookmarked) | [`PASPM_UML27_Review_Pack_2026-11-04.pdf`](PASPM_UML27_Review_Pack_2026-11-04.pdf) |
| Editable 27-page UML 2.0 source (diagrams.net / draw.io) | [`../../diagrams/PASPM_Unified_27_Diagrams_UML2_Editable.drawio`](../../diagrams/PASPM_Unified_27_Diagrams_UML2_Editable.drawio) |
| Editable F05 scheduler ERD (separate file; not a 28th page) | [`../../diagrams/PASPM_F05_Scheduler_ERD_V14_V19.drawio`](../../diagrams/PASPM_F05_Scheduler_ERD_V14_V19.drawio) |
| Concise UML QA report | [`UML_QA_REPORT_2026-11-04.md`](UML_QA_REPORT_2026-11-04.md) |
| Summary of the major Backend changes with tested commits | [`BACKEND_CHANGES_SUMMARY.md`](BACKEND_CHANGES_SUMMARY.md) |
| Known limitations and likely reviewer questions | [`LIMITATIONS_AND_REVIEWER_QUESTIONS.md`](LIMITATIONS_AND_REVIEWER_QUESTIONS.md) |
| Project report ↔ code ↔ UML consistency review | [`PROJECT_REPORT_CONSISTENCY_REVIEW.md`](PROJECT_REPORT_CONSISTENCY_REVIEW.md) |

Statuses that hold everywhere in this package: scheduling is **implemented but off by default**; GitHub CI **NOT RUN**; native Linux **NOT RUN**; release sign-off **PENDING** (nobody recorded as approver). F07–F11 are **PLANNED**.

## Before Wednesday

1. **Decide the report position on TKQ/FEACP vs HUI-Miner/EFIM and on the roles** (consistency review R1–R4). This is the largest open risk; the diagrams and code agree with each other, the `.docx` reports do not. Nothing in the reports was edited.
2. Open the two `.drawio` files once in the diagrams.net web editor (they were only rendered with the desktop engine) and print or share the PDF.
3. Optional: re-run the PDF after any wording change (`draw.io -x -f pdf -p N --crop`, merge; cover is built from a small draw.io page).
4. If the pull request for the Backend branch is opened or merged before the review, update the cover/README wording ("not merged") accordingly.

## Integrity

| File | SHA-256 |
|---|---|
| `PASPM_Unified_27_Diagrams_UML2_Editable.drawio` | `f6f2c024979f5763e5c3f1e643f40531fa20e171a01032730e879882c4918c82` |
| `PASPM_F05_Scheduler_ERD_V14_V19.drawio` | `9da4a38937cd8978170127524a703382ea791eb02c2c2c3fdc3e735793136f25` |
| `PASPM_Complete_ERD_F02_Inventory_Batch_v3.drawio` (unchanged) | `b8cc996bed7fea07d45c228587dec3009ca0a05621a7d351cd15dcb0e6184568` |
| `PASPM_Complete_ERD_F03_Three_Weights_v4.drawio` (unchanged) | `33722f431f7af5310cc20f0976afe5b792ebb0946a829f67393261331791c068` |

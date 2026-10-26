# Requirements and Authority

## Reading order

Use the [feature index](../features/README.md) for scoped requirements, [API index](../api/README.md) for current exposed contracts, source/migrations for observed implementation, and dated reports only for their recorded execution. Implementation does not automatically approve a conflicting requirement. No rule, permission or calculation is changed by this reorganization.

| Scope | Tracked reference | Authority / limitation |
|---|---|---|
| F01 | [Original decisions](../features/F01/reference/DECISIONS.md), [API](../api/F01_AUTH_USER_API.md) | Early JDBC-only scope is historical; registration 202/409 prose needs reconciliation |
| F02 | [Original v3 backlog](../features/F02/reference_v3/PASPM_F02_Inventory_Batch_API_Swagger_Backlog_v3.md), [current API](../api/F02_PRODUCT_INVENTORY_API.md) | Original all-StoreAdmin/expiry-today clauses are superseded for current source |
| F03 | [Original v4](../features/F03/reference_v4/PASPM_F03_Three_Weights_v4_API_Swagger_Backlog.md), [current API](../api/F03_UTILITY_API.md) | Current Product-T/binary behavior differs from retained linear history; final approved source is missing |
| F04 | [Original design](../features/F04/reference/F04_DESIGN.md), [approved Excel format](../features/F04/F04_EXCEL_FORMAT_CONTRACT.md), [API](../api/F04_TRANSACTION_API.md) | File-based CSV/Excel scope; external API/POS excluded; original discovery statements are historical |
| F05 | [API](../api/F05_MINING_API.md), [original gap/scope](../features/F05/reference/F05_GAP_ANALYSIS.md) | Working source differs from dated pre-fix/deferred notes; wider research acceptance separate |
| F06 | [Approved v1.1 rules](F06_RECOMMENDATION_RULES_v1_1.md), [approval history](../archive/F06/F06_IMPLEMENTATION_READINESS.md) | Implemented Backend scope; no publication/activation side effects |

## F03 v1.2 source gap

`docs/requirements/F03_WEIGHT_RULES_v1_2.md` is **not tracked on this branch**. Its verification companion and `PASPM_Weight_Report.pdf` are also absent. Original-checkout copies exist as untracked user work; this task does not copy, approve or merge them. The six formerly broken links now lead to this explicit gap, not a fabricated replacement specification.

Current [F03 API](../api/F03_UTILITY_API.md) and [handoff](../frontend_handoff/F03_HANDOFF.md) describe observed source behavior. Preserve old frozen linear results; do not use a missing authority link as approval to change scoring.

## Documentation review register

| ID | Unresolved group | Required action |
|---|---|---|
| DG01 | Missing tracked F03 final spec, verification and weight report | Owner reviews provenance and submits the approved sources separately |
| DG02 | Missing tracked legacy-linear Snapshot/Matrix example JSONs | Restore approved original examples via reviewed change; do not synthesize historical responses |
| DG03 | F01 duplicate registration generic 202 prose vs source 409 | Resolve API/handoff authority; no authentication change in this task |
| DG04 | Mixed stale F05/F06 and JPA/CI progression prose | Review NEEDS_REVIEW notices and current indexes; do not rewrite old executions |
| DG05 | Editable ERD lacks V11 archive/routing | Update diagram in a separately scoped schema-documentation task |
| DG06 | F07–F11 full feature map/SRS unavailable | Supply authoritative definitions and required API contracts; no empty feature folders created |
| DG07 | API Inventory package only on local unmerged audit branch | Review/submit that branch separately; this branch starts from dev without importing it |
| DG08 | Hosted CI/artifact/human/real-data acceptance not refreshed | Obtain current evidence independently; local SPMF hosting is not a prerequisite for documentation work |

F06 exclusions mention F07 layout, F08 campaign, F09 publishing and F10 discount. Those names do not establish complete specifications or endpoint counts. Eleven research work packages are not evidence of eleven feature definitions.

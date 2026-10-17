# F03 API Contract — confirmed decisions 2026-10-17

All operations use F01 bearer authentication and the existing error body `{"error":"message"}`. Store identity comes only from the authenticated account and StoreScope. Foreign records return 404. `Retail Planner` maps to existing MERCHANDISER; no new role is introduced.

| Method / path | Role | Body / result |
|---|---|---|
| GET /api/system/utility/lambda-policy | SYSTEM_ADMIN | lambdaMin, lambdaMax, version |
| PUT /api/system/utility/lambda-policy | SYSTEM_ADMIN | `{"lambdaMin":0,"lambdaMax":0.5}`; returns incremented version |
| GET /api/utility/config | STORE_ADMIN, MERCHANDISER | Store config plus current permitted bounds/version and lambdaValid |
| PUT /api/utility/config | MERCHANDISER | enableStock, enableExpiry, enableSeason, alpha, beta, gamma, lambda; preserves T; returns config view |
| PUT /api/utility/config/expiry-window | STORE_ADMIN | `{"expiryWindowDays":30}`; preserves weights/selected lambda |
| GET /api/utility/products/{productId}/scores | MERCHANDISER | productId, stockPriorityScore, seasonalPriorityScore (nullable if unset) |
| PATCH /api/utility/products/{productId}/scores | MERCHANDISER | stockPriorityScore and/or seasonalPriorityScore; omitted field is preserved; explicit null rejected |
| POST /api/utility/preview | MERCHANDISER | rangeStart, rangeEnd, asOfDate, page=0, pageSize=20; matrix page, totals and warnings |
| POST /api/utility/snapshots | MERCHANDISER | rangeStart, rangeEnd, asOfDate; 201 frozen snapshot metadata |
| GET /api/utility/snapshots/{id} | MERCHANDISER | snapshot metadata including frozen config and policy |
| GET /api/utility/snapshots/{id}/matrix | MERCHANDISER | page/pageSize; immutable matrix rows and transaction totals |

Canonical shared score endpoints follow the repository v4 backlog; the earlier proposed separate stock/season routes are superseded to avoid duplicate endpoints.

Validation: alpha/beta/gamma and S/M in [0,1], six fractional digits maximum. Effective disabled coefficients are zero; enabled coefficients must sum exactly to 1. No normalization. All disabled means W=1. Lambda must satisfy current system policy even if criteria are disabled. Policy min >= 0 and max >= min. T is a positive JSON integer (int32); fractional, quoted numeric and null values are rejected. Default config disables all criteria, selects lambda=0 and T=30. Config reads flag invalid stored lambda after a policy change; preview/new snapshots reject with 409 until corrected. T/bounds cannot be supplied to the Merchandiser config endpoint.

Range endpoints require rangeStart <= rangeEnd. Range is inclusive in Asia/Ho_Chi_Minh; asOfDate is explicit. Pagination uses page>=0 and 1<=pageSize<=100. Expiry eligibility follows v4 strictly: ACTIVE, remaining>0 and expiryDate>asOfDate. This differs from the F02 current-stock rule that includes expiry today; F02 is unchanged.

Preview includes warnings for missing enabled S/M or required expiry metadata. Such errors prevent READY snapshot creation (400). Negative, duplicate or malformed source utility is rejected (400). Missing F04 production provider returns 503; fixtures require explicit dev+utility-fixture profiles. No user upload route.

Snapshot config freezes expiryWindowDays, lambda policy min/max/version, selected lambda, effective coefficients, flags, config/scoring versions, analysis date and rounding. Existing snapshots remain unchanged by all later settings or inventory edits.

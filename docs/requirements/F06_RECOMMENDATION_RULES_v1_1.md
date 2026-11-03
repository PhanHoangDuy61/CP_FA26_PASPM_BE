# F06 Recommendation Rules v1.1

Status: **IMPLEMENTED AND VERIFIED — Backend API + Swagger; Frontend handoff delivered.**
Approved policy package: explicit user reply “Duyệt bộ đề xuất trên”, 2026-10-20. Verification: [F06_VERIFICATION.md](../testing/reports/F06_VERIFICATION.md).

## Approved rules and configurable initial policy

| Rule | Implemented behavior |
|---|---|
| Four types | BUNDLE, CROSS_SELL, FBT, BANNER; no automatic approval |
| Bundle | Full source pattern with2..maxBundleSize Products; initial maximum4; no invented subset, price or discount |
| Cross-sell | Each Product is a separate single anchor; other group Products are targets; positive co-occurrence and directional confidence>=0.50 initially |
| FBT | At least2 Products; supportCount>=2 AND supportRatio>=0.05 initially; utility is not frequency |
| Banner | Eligible reviewable source Product group, including singleton; no generated artwork/layout/publication |
| Minimum inventory | Initially1 current sellable unit per Product; no reservation |
| Ranking | For one miningRunId only: current eligibility ELIGIBLE, NEEDS_RECHECK, INELIGIBLE; utility DESC; support DESC, unknown last; canonical Product key; type; anchor; identity tie. General history uses identity/creation order DESC and rank=null |
| Rule version | F06_RULES_V1_1; policy version1 defaults, first update version2 |
| Policy ownership | Own-Store STORE_ADMIN config write; MERCHANDISER/STORE_ADMIN read; only MERCHANDISER generate/approve/reject; SystemAdmin/Customer no management access |

Store Admin may update the full policy with expectedVersion: maxBundleSize integer2..256 (256 is F05 technical item limit), minSupportCount integer>=1, minSupportRatio/minConfidence decimal0..1 with <=6 fractional digits, minimumSellableQuantity integer>=1. Initial values are4/2/0.05/0.50/1. Policies are captured on candidates and do not rewrite past evidence or decisions. No business threshold remains unresolved.

## Immutable source and transaction evidence

Only completed own-Store F05 runs produce candidates. Validate request/snapshot/hash/configuration/conversion identity, frozen input mapping, source matrix membership, recorded pattern count and actual pattern_item mappings. Missing/foreign records404; incomplete/invalid source409. Real zero-pattern runs return an empty success, not an error.

Membership is read from the same persisted frozen_input represented by immutable utility_matrix_item rows. F06 never reads live sales for historical support. One set of real Product IDs per transaction; quantities and repeated presence do not multiply support. Zero-utility membership is preserved. F05 supportCount remains null and is not rewritten.

support(X)=number of source transactions containing X; supportRatio=support(X)/totalTransactions; confidence(A→B)=support(A∪B)/support(A). Exact counts decide thresholds by cross multiplication. JSON ratios use sixdecimal HALF_UP for display only; unavailable evidence and zero anchor denominator remain explicit/null.

Fixture AB,AB,A,B,B,C: A=3,B=4,AB=2; supportRatioAB=1/3 (.333333); A→B=2/3 (.666667); B→A=1/2 (.500000). A rounded .666667 does not satisfy an exact .666667 threshold for2/3. Historical association does not establish causality.

## Live sellability and independent expiry

Product must be ACTIVE. Count only ACTIVE batches with remainingQuantity>0 and expiryDate strictly after the current Store-local date; non-expiry Products may use undated batches. Required missing expiry metadata makes eligibility NEEDS_RECHECK. Inactive/out-of-stock/expired-only/blocked-only Products are INELIGIBLE. Product/current batch data are read only.

Independent expiry uses the shared ExpiryScoreCalculator and UtilityTimeZone (Asia/Ho_Chi_Minh default), not a Mining Run. Product T=expiryPushDays: null effective0;0 disabled; negative invalid; no Store window fallback. E=1 iff T>0 and0<D_min<=T for the earliest future-expiry eligible batch. Individual push flags require their own0<D_batch<=T; far batches are not flagged just because another batch is near expiry. Missing/non-expiring/no-sellable states preserve F03 v1.2 semantics. Alerts contain checkedAt, actual batch IDs and warnings; no artificial source pattern is persisted.

## Deduplication, review and downstream use

Identity separates Store, snapshot, input, converted threshold, exact utility, rule/policy version, canonical group, type and anchor. Equivalent HUI-Miner/EFIM evidence can reuse candidates while preserving every run/pattern source. Different snapshots/contexts are not merged blindly; repeated generation never reopens resolved decisions. Store-lock generation and uniqueness protect concurrent submissions.

PENDING_REVIEW→APPROVED or REJECTED only, Merchandiser only, reason required, expectedVersion, actor/time and old/new-state history. Recheck live eligibility before approval. Resolved states cannot be reopened, and stale/conflicting versions return409. Read/detail exposes current eligibility and readyForConsumption; consumers must GET again and require APPROVED+readyForConsumption at use time. Approval is a decision record, not proof of future inventory or a publishing command.

No mining, Weight/minUtil recalculation, snapshot/pattern mutation, inventory change, F07 layout, F08 campaign, F09 publishing or F10 discount side effect is implemented. Frontend implementation is unchanged; the API handoff is the integration deliverable. The approved HUI-Miner/EFIM scope remains unchanged; no newer supplied authoritative research decision conflicts with it.

## Verification and remaining limits

210 automated tests PASS, including63 new F06 tests and147 regression;27 actual Swagger UI cases PASS; fresh MySQL V1–V10 and V9→V10 preserve historical data. See linked verification and [API contract](../api/F06_RECOMMENDATION_API.md).

Technical safety cap:10000 candidates per generation, transactional rollback on excess; ranked scope above10000 returns409 and requires narrower filters. No scheduler, auto-activation, reopening workflow or reservation is part of v1.1. Existing environments with old V7 checksums still require the separate approved operator recovery procedure; F06 does not perform repair on deployed databases.

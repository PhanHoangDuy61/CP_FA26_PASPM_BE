# F06 Recommendation API

Source-derived contract, 2026-10-20. F06 turns a completed own-Store F05 run into persisted, reviewable candidates. It reads frozen F03/F05 evidence and current F02 inventory without executing SPMF again. Actual exported request/response examples from executed integration tests are linked below. Verified: Java17 clean verify 210/210 (63 new F06 +147 regression), isolated MySQL migrations and27 actual Swagger UI cases. See [verification report](../requirements/F06_VERIFICATION.md).

Local default base URL: `http://localhost:8080` (override using Spring Boot `server.port` / `SERVER_PORT`; a deployment-specific `PORT` requires existing application mapping). Swagger entry: `/swagger`, UI: `/swagger-ui/index.html`, runtime OpenAPI: `/v3/api-docs`. The shared [OpenAPI export](openapi.json) was refreshed from the actual isolated backend at `http://localhost:18086/v3/api-docs`: 59 paths,73 operations,8 F06 operations. Its server entry is that test origin; configure the target origin in the integration environment.

Authenticate with F01 `POST /api/auth/login` (`email`, `password`), use returned `accessToken` as `Authorization: Bearer <token>`, and load `GET /api/auth/me` for role/Store context. See [F01](F01_AUTH_USER_API.md). Store scope comes from the token; request bodies do not accept `storeId`. No F06 aliases are implemented. The existing F03 `/api/utility/expiry-push-batches` is a separate API, not an F06 response-shape alias.

## Operations and permissions

There are **eight operations total**: seven in RecommendationController and one independent expiry operation. All success responses below are HTTP 200 JSON. STORE_ADMIN and MERCHANDISER can read; only MERCHANDISER can generate/review; only STORE_ADMIN can update configuration. SYSTEM_ADMIN and END_CUSTOMER have no F06 access. Missing/invalid bearer: 401; forbidden role: 403. Own-Store lookup hides foreign records with 404.

| Method/path | Input | Response | Mutation role |
|---|---|---|---|
| POST `/api/recommendations/generate` | RecGenerateRequest | RecGenerationResult | MERCHANDISER |
| GET `/api/recommendations` | optional miningRunId:int64, snapshotId:int32, type:RecType, reviewStatus:ReviewStatus; page=0, pageSize=20 | RecPage | Read |
| GET `/api/recommendations/{id}` | id:int64 | RecView | Read |
| POST `/api/recommendations/{id}/approve` | RecReviewRequest | RecView | MERCHANDISER |
| POST `/api/recommendations/{id}/reject` | RecReviewRequest | RecView | MERCHANDISER |
| GET `/api/recommendations/config` | none | RecPolicy | Read |
| PUT `/api/recommendations/config` | RecPolicyRequest | RecPolicy | STORE_ADMIN |
| GET `/api/recommendations/expiry-push` | optional productId:int32; page=0, pageSize=20 | RecExpiryPage | Read |

Both pages require page >= 0, pageSize 1..100. Expiry rejects `asOfDate` with 400: it always reads the current Store-local date. Unknown JSON request properties are rejected. Unsupported filters/sort options are not part of the contract.

## Request contracts

All listed properties are required and non-null. IDs/versions must be positive JSON integer tokens; quoted numbers and fractional numbers are invalid. Integer configuration fields likewise require integer tokens.

- `RecGenerateRequest`: `miningRunId:int64`; `types:RecType[]`, 1..4 unique values. RecType = `BUNDLE | CROSS_SELL | FBT | BANNER`.
- `RecReviewRequest`: `expectedVersion:int64`; `reason:string`, nonblank and max 1000 characters.
- `RecPolicyRequest`: `expectedVersion:int64`; `maxBundleSize:int32` 2..256; `minSupportCount:int32` >=1; `minSupportRatio:decimal` and `minConfidence:decimal` each 0..1 inclusive, at most six fractional digits; `minimumSellableQuantity:int32` >=1. PUT replaces the full configuration using the version read from GET, rather than patching selected properties.

Illustrative requests (IDs must be replaced with actual own-Store results):

```json
{"miningRunId":1,"types":["BUNDLE","CROSS_SELL","FBT","BANNER"]}
```

```json
{"expectedVersion":1,"reason":"Reviewed evidence and current sellable stock"}
```

```json
{"expectedVersion":1,"maxBundleSize":4,"minSupportCount":2,"minSupportRatio":0.05,"minConfidence":0.50,"minimumSellableQuantity":1}
```

Executed examples: [generation input](../frontend_handoff/F06_examples/generate_request.json), [generation output](../frontend_handoff/F06_examples/generate_response.json), [detail](../frontend_handoff/F06_examples/detail_response.json), [ranked list](../frontend_handoff/F06_examples/list_response.json), [configuration](../frontend_handoff/F06_examples/config_response.json), [approval input](../frontend_handoff/F06_examples/approve_request.json), [approval output](../frontend_handoff/F06_examples/approve_response.json), [version/state conflict](../frontend_handoff/F06_examples/conflict_response.json), [expiry push](../frontend_handoff/F06_examples/expiry_push_response.json). These contain fixture IDs, not credentials; replace IDs with target Store results. They demonstrate executed test responses, not a browser Swagger session.

## Response contracts

The TypeScript wire declarations below enumerate every nested record. `Int32`, `Int64` and `Decimal` are JSON **numbers**, not quoted strings. The aliases document distinct precision requirements; `number` is suitable only while values remain safely representable. Use lossless JSON parsing/serialization for int64 beyond 2^53−1 and exact decimal values; never blindly convert lossless IDs back through Number. Strings representing dates are ISO `YYYY-MM-DD`; instants are ISO-8601. Nullable fields are explicit `| null`, not optional/missing properties.

```ts
type Int32 = number;
type Int64 = number; // lossless numeric adapter required outside JS safe range
type Decimal = number; // preserve original exact numeric value; format for display only
type ISODate = string;
type Instant = string;
type RecType = 'BUNDLE' | 'CROSS_SELL' | 'FBT' | 'BANNER';
type ReviewStatus = 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED';
type EligibilityStatus = 'ELIGIBLE' | 'NEEDS_RECHECK' | 'INELIGIBLE';
interface RecPolicy {
  version: Int64; maxBundleSize: Int32; minSupportCount: Int32;
  minSupportRatio: Decimal; minConfidence: Decimal;
  minimumSellableQuantity: Int32; ruleVersion: string;
}
interface RecTransactionEvidence {
  totalTransactions: Int32; supportCount: Int32 | null;
  supportRatio: Decimal | null; anchorSupportCount: Int32 | null;
  confidence: Decimal | null; status: string;
}
interface RecProductEligibility {
  productId: Int32; productStatus: string; sellableQuantity: Int64;
  status: EligibilityStatus; expiryPriorityScore: Decimal | null;
  expiryStatus: string; expiryPushDays: Int32; warnings: string[];
}
interface RecEligibility {
  status: EligibilityStatus; products: RecProductEligibility[];
  checkedAt: Instant; asOfDate: ISODate; timeZone: string;
  warnings: string[]; fingerprint: string;
}
interface RecSource {
  runId: Int64; patternId: Int64; requestId: Int64; snapshotId: Int32;
  algorithm: string; libraryVersion: string; conversionVersion: string;
  inputHash: string; sourceHash: string; matrixHash: string;
  minUtilMode: string; minUtilValue: Decimal; minUtilSpmf: Int32;
  effectiveMinUtility: Decimal; patternUtility: Decimal; analysisDate: ISODate;
}
interface RecSkipped {
  patternId: Int64; type: RecType; anchorProductId: Int32 | null; reason: string;
}
interface RecGenerationResult {
  miningRunId: Int64; snapshotId: Int32; policyVersion: Int64;
  ruleVersion: string; createdCount: Int32; reusedCount: Int32;
  recommendationIds: Int64[]; skipped: RecSkipped[];
}
interface RecReview {
  oldStatus: ReviewStatus; newStatus: ReviewStatus; reviewedBy: Int32;
  reviewedAt: Instant; reason: string; resultingVersion: Int64;
  eligibilityCheck: RecEligibility;
}
interface RecView {
  recommendationId: Int64; storeId: Int32; snapshotId: Int32; type: RecType;
  productIds: Int32[]; anchorProductId: Int32 | null; utility: Decimal;
  reviewStatus: ReviewStatus; version: Int64; ruleVersion: string;
  policy: RecPolicy; evidence: RecTransactionEvidence; generationEligibility: RecEligibility;
  currentEligibility: RecEligibility; inventoryChanged: boolean;
  readyForConsumption: boolean; sources: RecSource[]; explanation: string[];
  reviewHistory: RecReview[]; reviewedBy: Int32 | null;
  reviewedAt: Instant | null; reviewReason: string | null;
  createdAt: Instant; rank: Int32 | null;
}
interface RecPage {
  items: RecView[]; page: Int32; pageSize: Int32; totalElements: Int64;
  ordering: string;
}
interface RecExpiryBatch {
  productId: Int32; batchId: Int32; expiryDate: ISODate;
  daysRemaining: Int64; remainingQuantity: Int64; expiryPushDays: Int32;
  pushRequired: boolean; checkedAt: Instant; status: string; warnings: string[];
}
interface RecExpiryPage {
  items: RecExpiryBatch[]; page: Int32; pageSize: Int32; totalElements: Int64;
  asOfDate: ISODate; timeZone: string; checkedAt: Instant; warnings: string[];
}
```

Evidence status is `AVAILABLE`, `EVIDENCE_UNAVAILABLE`, or `ZERO_ANCHOR_SUPPORT`; absent evidence is null, never a fabricated zero. Product expiry status can be `AVAILABLE`, `NOT_APPLICABLE`, `NO_SELLABLE_BATCH`, `MISSING_METADATA`, or `UNAVAILABLE` for a missing Product. Expose warnings alongside status.

## Rules, evidence and ranking

Default policy version 1: max bundle size 4; FBT support count >=2 **and** support ratio >=0.05; cross-sell confidence >=0.50; minimum sellable quantity 1. Rule version is `F06_RULES_V1_1`. First configuration update returns version 2. Existing candidates keep their captured policy; configuration updates do not rewrite their evidence, eligibility policy, history or review state.

- BUNDLE: at least two Products, at most captured maxBundleSize. FBT: at least two Products and both support tests. CROSS_SELL: at least two Products; creates one candidate per anchor passing confidence. BANNER: permits singleton or larger patterns. BUNDLE/BANNER do not require support/confidence threshold acceptance.
- Support counts distinct frozen transaction baskets containing every Product, independent of quantity and current inventory. `supportRatio = supportCount / totalTransactions`; confidence is supportCount / anchorSupportCount. Cross-sell suggested targets are **productIds excluding anchorProductId**. Do not count the anchor itself as a suggested target.
- Threshold decisions use exact count comparisons: supportCount >= minSupportRatio × totalTransactions, and supportCount >= minConfidence × anchorSupportCount. Returned ratios use six decimals HALF_UP for display only; never reapply thresholds to rounded ratios in the frontend.
- Utility is frozen weighted utility, not probability, revenue or a newly computed business score. Historical co-occurrence does not establish causality. Product expiry priority is displayed separately and does not change utility ranking.
- With `miningRunId`, list ordering is `RANKED_WITHIN_SOURCE_RUN`: live eligibility ELIGIBLE, NEEDS_RECHECK, INELIGIBLE; utility descending; support descending/null last; canonical comma-joined Product key lexicographic ascending; type name; anchor null first/numeric ascending; recommendation ID. Rank is one-based across the filtered result before pagination and can change with inventory. Do not rerank each page locally.
- Without `miningRunId`, ordering is `CREATED_DESC` (recommendation ID descending), rank null. This is history, not a global utility leaderboard across unrelated snapshots. Detail and review response rank is null.
- Repeated generation reuses identity for the same snapshot, input hash, converted threshold, exact utility, rule/policy version, canonical Products, type and anchor. Equivalent HUI-Miner/EFIM sources remain in sources without duplicating candidates. Different snapshots/input/threshold/utility/policies stay distinct. Reuse never resets a decision. createdCount/reusedCount describe generation work; recommendationIds is unique, so do not derive counts from its length.

Skipped reasons: `PATTERN_REQUIRES_AT_LEAST_TWO_PRODUCTS`, `BUNDLE_EXCEEDS_MAX_SIZE`, `TRANSACTION_EVIDENCE_UNAVAILABLE`, `SUPPORT_POLICY_NOT_MET`, `CONFIDENCE_POLICY_NOT_MET`. Generation can succeed with no candidates; show skipped reasons or an empty-result explanation.

## Live inventory and expiry

Generation captures generationEligibility. GET list/detail rechecks currentEligibility using the candidate's captured minimum quantity policy. Product must be ACTIVE. Only ACTIVE batches with remainingQuantity >0 count; expiry-required batches need metadata and expiryDate **strictly after** the current Store-local date. Expiry today is unsellable. Non-expiry Products can use undated active batches. A missing required expiry on any active positive batch gives NEEDS_RECHECK even if another batch supplies stock; missing/inactive/insufficient Products can make the aggregate INELIGIBLE.

`inventoryChanged` compares fingerprints (date, captured minimum quantity and Product/batch metadata); it does not mean stock alone changed and can become true on a date rollover. `checkedAt`, `asOfDate`, and `timeZone` describe the live check. Display old and current checks separately. Historical utility/evidence remain frozen.

Independent expiry-push needs no snapshot or mining run. Product `expiryPushDays` T defaults to 0 with no Store fallback; T=0 disables push. Only expiry-required active Products and active positive, dated, still-sellable batches qualify; `0 < daysRemaining <= T`. Order: expiryDate, productId, batchId ascending. Page warnings can flag missing metadata even when no push row appears. An expiry alert neither guarantees mining pattern inclusion nor approves a recommendation.

## Review, concurrency and downstream safety

Only PENDING_REVIEW can become APPROVED or REJECTED. Review uses a locked current row plus compare-and-set version/state update; expectedVersion must match. Approval performs a fresh eligibility check and fails if not ELIGIBLE. Rejection remains possible with ineligible inventory. Successful review increments version, persists actor/time/reason and the check in append-only reviewHistory. No reopening, editing a final review, or rewriting history API exists.

Downstream must GET `/api/recommendations/{id}` immediately before use and require **reviewStatus=APPROVED AND readyForConsumption=true**. Ready is true only when APPROVED and currently ELIGIBLE. An approved candidate can later become unready without losing its historical approval. Recheck again during real activation/fulfillment: the response reserves no inventory. Generation/review never publishes, creates a campaign, changes a discount, activates anything, or mutates stock.

## Errors and limits

Common handled body: `{"error":"message"}`; authentication middleware may have an empty body. 400 covers invalid JSON/unknown fields, invalid bounds/enums, duplicate types, invalid pagination and expiry asOfDate. 404 covers unavailable/foreign IDs. 409 covers unfinished run, inconsistent/unavailable frozen mining source, configuration version conflict, review version/state conflict, approval blocked by current eligibility, generation exceeding the 10,000-candidate safety cap, or a ranked run scope exceeding 10,000 candidates (narrow type or reviewStatus filters). Frozen source validation verifies persisted hashes/membership/mapping/utility/threshold; a completed but inconsistent source is not accepted. A generation safety-cap failure rolls back generation; request fewer types. Ranked lists check the scope before full live ranking and require narrower filters above 10,000 candidates. General history reads paginate before live checks; both preserve the documented response shape/order.

On 409, reload detail/configuration and show the new state; do not blindly retry approval with a freshly substituted version. There is no scheduler, automatic activation, publish endpoint, reservation, reopen endpoint, or cross-snapshot global score. See [frontend workflow](../frontend_handoff/F06_HANDOFF.md).

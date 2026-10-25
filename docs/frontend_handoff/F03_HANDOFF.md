<!-- docs-reorganization:notice -->
> **NEEDS_REVIEW — documentation review 2026-10-26.** Final v1.2 source and some referenced examples are absent from tracked dev; untracked originals are not imported. See [current navigation](../features/F03/README.md). Original results and business-rule text below are retained; this notice is not a new execution or approval.
<!-- /docs-reorganization:notice -->

# F03 React/TypeScript handoff — v1.2

Use [F03 API contract](../api/F03_UTILITY_API.md), [actual OpenAPI](../api/openapi.json) and [business rules](../requirements/README.md#f03-v12-source-gap). This handoff supersedes earlier Store-window/linear examples for new results. Frontend code was not modified.

## Environment

`VITE_PASPM_API_BASE_URL=http://localhost:8080`; Backend PORT may override8080. Swagger `/swagger`, OpenAPI `/v3/api-docs`. Send `Authorization: Bearer <accessToken>` from F01. Never put DB, mail, JWT/OTP secrets into VITE variables. Configure a same-origin development proxy/deployment gateway: current Backend has no CORS mapping. Use HTTPS after deployment. Backend configuration remains private server environment values.

The normal transaction provider is now F04_DATABASE_V1, reading validated persisted COMPLETED transactions and historical prices/costs. The optional dev + utility-fixture profiles are synthetic QA-only; do not enable them in normal integration. No download/upload of a Weight PDF is required.

## Role ownership and integration order

1. Authenticate with F01 and read `/api/auth/me` roleCode/storeId. Backend enforces permissions independently of UI controls.
2. Store Admin and Merchandiser list/read their own Store Products and Categories through F02. Only Store Admin creates/updates Products. Add `expiryPushDays` to the existing full Product form: integer >=0; omitted/null becomes0. Store Admin controls this Product-level T. There is no Store default/fallback. Full PUT must include the intended threshold to avoid resetting it to0.
3. System Admin uses GET/PUT `/api/system/utility/lambda-policy`; Store Admin/Merchandiser read policy bounds through GET `/api/utility/config`. Merchandiser uses PUT `/api/utility/config` to save criteria, coefficients and selected lambda. Bounds changes can make lambdaValid=false; require an explicit valid selection, never clamp.
4. Both Store roles GET `/api/utility/products/{productId}/scores`; only Merchandiser PATCHes manual S/M in [0,1]. Omit unchanged fields, never submit null. The response now includes current Backend-calculated expiryScore/weightValue, expiryStatus/evidence, Product T, asOfDate/timeZone, configVersion, lambdaValid, ready and warnings. Products need no transaction history. Display null E/W as unavailable, never0. Do not send any added response field in PATCH. S is not derived from stock. After Save, render the returned result and refresh affected list rows.
5. Store Admin/Merchandiser can independently query `/api/utility/expiry-push-batches` to display current expiry pushes, even when there are no transactions or Mining results. Default date is current Store-local date. Explicit asOfDate is for analysis; requery current state at recommendation/presentation time.
6. Merchandiser selects transaction range and fixed asOfDate, then POSTs `/api/utility/preview`. Save settings/scores first; Preview accepts dates/page fields only. Display warnings/ready and null fields explicitly. E is binary for new scoring; W follows the three-weight formula.
7. If ready and nonempty, POST `/api/utility/snapshots` with the same date range/asOfDate. Backend recalculates atomically against current saved state, so handle400/409 if inputs changed since Preview.
8. Save returned snapshotId, then GET metadata and matrix pages. There is no snapshot-history list endpoint. Preserve/display scoringVersion; old F03_LINEAR_V1 snapshots remain decimal and must not be relabeled or recalculated in React.
9. Pass the frozen snapshot identity to the implemented F05 Mining APIs. Mining/Recommendation execution uses their separate contracts, not the Product score endpoints. Never hide the independent push list because an item was absent from HUI/EFIM output.

SYSTEM_ADMIN cannot edit Store/Product utility inputs. STORE_ADMIN may read current Product scores/E/W but cannot configure weights, edit manual scores or use Preview/snapshots. MERCHANDISER cannot edit Product expiryPushDays or global bounds. END_CUSTOMER has no F03 permissions. Missing token401, wrong role403, foreign record404. No request accepts storeId.

## Breaking changes

- Product threshold replaces Store expiryWindowDays. The legacy Store PUT remains callable and deprecated but has no effect on scoring; remove that form from the active weight workflow.
- New scoring version is EXPIRY_PRODUCT_EARLIEST_BATCH_BINARY_V1. E is either0/1 when valid, using the earliest future-expiry sellable batch. No quantity averaging.
- Batch expiring today is excluded; F02 summary now uses this strict future-date boundary too. The summary's expiringSoonQuantity is still a requested lookahead statistic, not the configured Product push quantity.
- MatrixItem adds expiryEvidence; SnapshotView adds warnings; frozen configuration adds timeZone. Legacy responses may have null evidence/timeZone. Store config legacy expiryWindowDays can be null.

## TypeScript additions and nullability

Generate the shared base from openapi.json, then account for nullable values described in the contract. Do not use floating-point reimplementation as the source of business results.

```ts
export interface PushBatch {
  productId: number; batchId: number; expiryDate: string;
  remainingQuantity: number; expiryPushDays: number; daysRemaining: number;
  pushRequired: boolean; pushRequiredQuantity: number; asOfDate: string;
}
export interface ExpiryEvidence {
  expiryPushDays: number; earliestEligibleExpiryDate: string | null;
  daysRemaining: number | null; asOfDate: string; reason: string;
  batches: PushBatch[];
}
export interface PushPage {
  items: PushBatch[]; page: number; pageSize: number; totalElements: number;
  asOfDate: string; timeZone: string; warnings: string[];
}
// Existing ProductRequest: add expiryPushDays?: number | null.
// ProductView: add expiryPushDays: number.
// MatrixItem: add expiryEvidence: ExpiryEvidence | null.
// FrozenConfiguration: add timeZone: string | null.
// WeightConfig.expiryWindowDays: number | null, deprecated.
// SnapshotView: add warnings: string[].
```

Existing matrix fields: transactionId/productId/algorithmItemId/quantity numbers; baseUtility:number; stockScore/expiryScore/seasonalScore:number|null; expiryStatus:string; weightValue/weightedUtility/transactionUtility:number|null. MatrixPage contains items, page/pageSize/totalElements, totalWeightedUtility:number|null, sourceHash/matrixHash, warnings, ready, configuration. Fetch every page for F05; transactionUtility repeats per item and must not be summed repeatedly.

## Form requests and sample data

Store Admin full F02 Product request:

```json
{"sku":"MILK-01","name":"Fresh Milk","categoryId":null,"unit":"bottle","requiresExpiry":true,"sellingPrice":10000,"costPrice":6000,"status":"ACTIVE","expiryPushDays":7}
```

Merchandiser weight request:

```json
{"enableStock":true,"enableExpiry":true,"enableSeason":true,"alpha":0.5,"beta":0.2,"gamma":0.3,"lambda":0.5}
```

Manual scores: `{"stockPriorityScore":0.8,"seasonalPriorityScore":0.9}`. Preview: `{"rangeStart":"2026-10-01","rangeEnd":"2026-10-31","asOfDate":"2026-10-19","page":0,"pageSize":20}`. Snapshot request omits page/pageSize. Synthetic complete examples live in F03_examples; identities are illustrative, not production IDs.

For a fixed date, eligible batches +5 days/20 units and +40 days/80 units, plus expired10 units: with historical sold quantity3, price10000/cost6000, Base=12000. T=7 → E=1, W=1.435, weighted utility17220; T=3 or0 → E=0, W=1.335, weighted utility16020. Display the backend response; changing Product T never edits batch quantity or historical sale values.

## Empty, warning and error states

- Empty push list is normal. It is independent of an empty transaction dataset or Mining output.
- AVAILABLE + THRESHOLD_DISABLED: show Product expiry priority disabled. OUTSIDE_THRESHOLD: valid inventory but not due. WITHIN_THRESHOLD: show exact push batches/quantities.
- MISSING_METADATA: show warning. Positive T gives null E; T=0 gives0 but remains invalid metadata. Enabled expiry prevents ready Snapshot; do not replace null utility with0.
- NO_SELLABLE_BATCH: E=0 with warning; historical utility is not proof of availability. NOT_APPLICABLE: non-expiring Product.
- Missing enabled S/M makes Preview incomplete; missing disabled score need not block.
- 400: validate payload and show `{error:string}`; no retry unchanged data. 409: refetch policy/config and require a valid lambda. 401: sign in again. 403: deny action. 404: missing/foreign resource without disclosing ownership. Transient5xx/network: explicit retry and avoid duplicate snapshot submissions.

## Ready APIs and limitations

All12 documented F03 HTTP operations are implemented, including current independent push reads and legacy compatibility PUT. F04 real transaction source is implemented. F05 Mining APIs and F06 Recommendations are implemented; use [F05 contract](../api/F05_MINING_API.md) and [F06 contract](../api/F06_RECOMMENDATION_API.md) for their actual operations and fresh-eligibility requirements. Snapshot IDs are only discoverable from creation or a trusted external workflow. Historical inventory cannot be reconstructed without movement history; analysis uses currently stored batches evaluated at the fixed date. Current push query scans candidate Products and paginates calculated batches in memory; large Stores may require a future measured optimization. No claim of full interactive Swagger coverage is made; see the v1.2 verification report.

## Backend-only Catalog integration update — 2026-10-21

This change does not modify either Frontend. `Retail` is a portal label, not a new Backend role. Derive permissions from F01 `/api/auth/me` roleCode/storeId. Use real F01 Bearer tokens; demo role selectors/mock tokens do not authorize Backend access. Read Product/Category list/detail for Store Admin/Merchandiser, then GET scores for visible rows or the selected Product. Only Merchandiser sees S/M Save and Weight Config Save. Keep E/W/T read-only in that form; Store Admin may separately update Product T through F02. Existing batch/summary endpoints remain StoreAdmin-only; ProductView.stockQuantity already provides derived total stock for Catalog.

GET/PATCH scores are current previews. Current lambda outside policy or missing enabled data gives weightValue=null, ready=false and warnings. `ready` does not mean inventory is sellable. Store config changes require a new GET of current scores; do not recompute authoritative W in React or rewrite historical displays. Existing immutable snapshots continue to show their original scores/configuration/Weight. Existing API error shapes and401/403/404/400 handling remain. There is no new route or migration.

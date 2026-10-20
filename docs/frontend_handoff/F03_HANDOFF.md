# F03 React/TypeScript handoff — v1.2

Use [F03 API contract](../api/F03_UTILITY_API.md), [actual OpenAPI](../api/openapi.json) and [business rules](../requirements/F03_WEIGHT_RULES_v1_2.md). This handoff supersedes earlier Store-window/linear examples for new results. Frontend code was not modified.

## Environment

`VITE_PASPM_API_BASE_URL=http://localhost:8080`; Backend PORT may override8080. Swagger `/swagger`, OpenAPI `/v3/api-docs`. Send `Authorization: Bearer <accessToken>` from F01. Never put DB, mail, JWT/OTP secrets into VITE variables. Configure a same-origin development proxy/deployment gateway: current Backend has no CORS mapping. Use HTTPS after deployment. Backend configuration remains private server environment values.

The normal transaction provider is now F04_DATABASE_V1, reading validated persisted COMPLETED transactions and historical prices/costs. The optional dev + utility-fixture profiles are synthetic QA-only; do not enable them in normal integration. No download/upload of a Weight PDF is required.

## Role ownership and integration order

1. Authenticate with F01 and read `/api/auth/me` roleCode/storeId. Backend enforces permissions independently of UI controls.
2. Store Admin lists/creates/updates Products through F02. Add `expiryPushDays` to the existing full Product form: integer >=0; omitted/null becomes0. Store Admin controls this Product-level T. There is no Store default/fallback. Full PUT must include the intended threshold to avoid resetting it to0.
3. System Admin uses GET/PUT `/api/system/utility/lambda-policy`; Store Admin/Merchandiser read policy bounds through GET `/api/utility/config`. Merchandiser uses PUT `/api/utility/config` to save criteria, coefficients and selected lambda. Bounds changes can make lambdaValid=false; require an explicit valid selection, never clamp.
4. Merchandiser reads/patches S/M through `/api/utility/products/{productId}/scores`. Both are manual scores in [0,1]; omit unchanged fields, never submit null. S is not derived from stock.
5. Store Admin/Merchandiser can independently query `/api/utility/expiry-push-batches` to display current expiry pushes, even when there are no transactions or Mining results. Default date is current Store-local date. Explicit asOfDate is for analysis; requery current state at recommendation/presentation time.
6. Merchandiser selects transaction range and fixed asOfDate, then POSTs `/api/utility/preview`. Save settings/scores first; Preview accepts dates/page fields only. Display warnings/ready and null fields explicitly. E is binary for new scoring; W follows the three-weight formula.
7. If ready and nonempty, POST `/api/utility/snapshots` with the same date range/asOfDate. Backend recalculates atomically against current saved state, so handle400/409 if inputs changed since Preview.
8. Save returned snapshotId, then GET metadata and matrix pages. There is no snapshot-history list endpoint. Preserve/display scoringVersion; old F03_LINEAR_V1 snapshots remain decimal and must not be relabeled or recalculated in React.
9. Pass frozen matrix identity to a future F05 adapter. Mining/Recommendation execution is not part of these APIs. Never hide the independent push list because an item was absent from HUI/EFIM output.

SYSTEM_ADMIN cannot edit Store/Product utility inputs. STORE_ADMIN cannot configure weights, manual scores or Preview/snapshots. MERCHANDISER cannot edit Product expiryPushDays or global bounds. END_CUSTOMER has no F03 permissions. Missing token401, wrong role403, foreign record404. No request accepts storeId.

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

All12 documented F03 HTTP operations are implemented, including current independent push reads and legacy compatibility PUT. F04 real transaction source is implemented. F05 has only MiningEngine interface; no Mining Request/export/execution APIs. F06 has no Recommendation Engine; [integration specification](../api/F03_V12_F05_F06_INTEGRATION.md) defines required fresh availability checks. Snapshot IDs are only discoverable from creation or a trusted external workflow. Historical inventory cannot be reconstructed without movement history; analysis uses currently stored batches evaluated at the fixed date. Current push query scans candidate Products and paginates calculated batches in memory; large Stores may require a future measured optimization. No claim of full interactive Swagger coverage is made; see the v1.2 verification report.

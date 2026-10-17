# F03 React + TypeScript Integration Handoff

This guide maps the implemented F03 API for the Frontend team. All examples are synthetic development fixture data. Exact DTOs, endpoint validation, HTTP outcomes and business rules are in [`F03_UTILITY_API.md`](../api/F03_UTILITY_API.md); the complete shared contract is [`openapi.json`](../api/openapi.json).

## Environment and authentication

Set the API origin in the React build environment. There is no `/api/v1` prefix.

```dotenv
VITE_PASPM_API_BASE_URL=http://localhost:8080
```

The Backend defaults to port 8080 (`PORT` can override it). Swagger UI is `/swagger`; runtime OpenAPI is `/v3/api-docs`. Each F03 request requires the F01 access token:

```http
Authorization: Bearer <accessToken>
Content-Type: application/json
```

Backend-only configuration uses environment placeholders, never browser variables: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `OTP_SECRET`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_TLS`, `MAIL_FROM`, and optionally `GOOGLE_CLIENT_ID`. JWT and OTP secrets must be supplied outside source control. The normal Backend transaction source is not connected yet. The explicit `dev` + `utility-fixture` profile is isolated QA-only, requires a separate fixture analysis date and password, and must use an isolated development database.

The Backend source has no CORS mapping. For local cross-origin Vite development, use a same-origin development proxy or configure CORS at the deployment gateway before browser calls. Do not put DB, mail, JWT, OTP or Google credentials in `VITE_*` values. Use HTTPS after deployment.

## Screen-to-API mapping

| Frontend Screen | User Action | API | Required Role | API Status |
|---|---|---|---|---|
| Global Lambda Policy Management | View/update allowed minimum and maximum | GET/PUT `/api/system/utility/lambda-policy` | `SYSTEM_ADMIN` | Implemented; PUT MockMvc verified, GET operation is source/OpenAPI verified but has no direct MockMvc request. |
| Store Expiry Window Configuration | View current T; update integer T | GET `/api/utility/config`; PUT `/api/utility/config/expiry-window` | `STORE_ADMIN` | Implemented; MockMvc verified. |
| Utility Weight Configuration | View Store settings; update enabled criteria, coefficients and lambda | GET/PUT `/api/utility/config` | GET: `STORE_ADMIN`, `MERCHANDISER`; PUT: `MERCHANDISER` | Implemented; MockMvc verified. |
| Product Stock Priority | Read or update S | GET/PATCH `/api/utility/products/{productId}/scores` | `MERCHANDISER` | Implemented as combined Stock/Season score API; MockMvc verified. |
| Product Seasonal Priority | Read or update M | GET/PATCH `/api/utility/products/{productId}/scores` | `MERCHANDISER` | Implemented as combined Stock/Season score API; MockMvc verified. |
| Utility Preview | Preview selected transaction range and analysis date | POST `/api/utility/preview` | `MERCHANDISER` | Implemented; conditional on F04 data source. Default source returns 503. |
| Utility Snapshot History | Discover snapshots | `GET /api/utility/snapshots` | — | NOT IMPLEMENTED. No list endpoint; Frontend cannot discover IDs. |
| Utility Snapshot Details | Load known snapshot ID | GET `/api/utility/snapshots/{id}` | `MERCHANDISER`, own Store | Implemented; MockMvc verified. Requires snapshot ID from creation or another trusted handoff. |
| Utility Matrix Viewer | Read matrix pages for known snapshot | GET `/api/utility/snapshots/{id}/matrix?page=0&pageSize=20` | `MERCHANDISER`, own Store | Implemented; MockMvc verified. |
| Create Utility Snapshot | Save a ready Preview | POST `/api/utility/snapshots` | `MERCHANDISER` | Implemented; conditional on F04 data source and a non-empty ready matrix. |

## Recommended integration order

1. Authenticate through F01, call `GET /api/auth/me`, and use the returned `roleCode` and `storeId` to select the permitted F03 screens. Do not treat UI role checks as authorization; the API checks every request.
2. Load F02 Product identities and inventory context. Product listing comes from F02; F03 score requests use the numeric Product ID. F03 derives E from F02 `INVENTORY_BATCH`; it does not accept a stock value from the browser.
3. For System Admin, load the lambda policy and expose the global bounds form. For Store Admin or Merchandiser, load `GET /api/utility/config`; it includes T, Store weight config, policy bounds/version and `lambdaValid`.
4. Render role-specific forms. Store Admin changes only T. Merchandiser changes criteria, coefficients and lambda. System Admin changes only system lambda bounds.
5. Load and save S/M through the combined Product scores endpoint. Missing score values are null until configured.
6. Call Preview after the user selects `rangeStart`, `rangeEnd` and `asOfDate`. Display its `ready` and `warnings`; a null total is not zero. Preview uses saved config and Product scores, so save configuration/score edits first.
7. When Preview is ready and has rows, let Merchandiser explicitly create a snapshot. The Backend rebuilds against current config/policy/source, so handle 400/409 if state changed after preview.
8. Store the returned `snapshotId` and read its metadata/matrix pages. Continue fetching pages while `page * pageSize < totalElements`; F05 will need every row.
9. Pass snapshot identity and its immutable matrix to F05 only after that feature adds its `MINING_REQUEST.utility_snapshot_id` relationship. F03 does not invoke mining.

There is no snapshot history list endpoint, so an application cannot build a complete history screen from these APIs alone. It can show a snapshot just created or one whose ID was provided by an external workflow.

## Role-based UI behavior

| Role | UI controls to expose | Controls to keep unavailable |
|---|---|---|
| `SYSTEM_ADMIN` | Read/update global `lambdaMin`, `lambdaMax`; show policy version. | Store T, Store weights, Product scores, Preview and snapshot endpoints are forbidden. |
| `STORE_ADMIN` | Read Store config/policy; read/update own Store's T. | Lambda bounds, weights/lambda selection, Product scores, Preview and snapshots are forbidden. |
| `MERCHANDISER` | Read Store config/policy; set alpha/beta/gamma, criteria flags and lambda; read/update Product S/M; Preview and create/read snapshots/matrix. | Cannot change global bounds or T. |
| `END_CUSTOMER` | No F03 controls. | All F03 routes are forbidden. |

The role is `roleCode` from F01. Store scope comes from the authenticated account, not a client-supplied ID. For a Product/snapshot belonging to another Store, the API returns 404. A wrong role returns 403; no/invalid token returns 401.

## TypeScript contracts

Use OpenAPI generation for the shared route/model base, then apply these source-derived nullability corrections: Springdoc omits nullability for unassigned Product scores and incomplete Preview fields.

```ts
export type LocalDate = string; // YYYY-MM-DD
export type RoleCode = "SYSTEM_ADMIN" | "STORE_ADMIN" | "MERCHANDISER" | "END_CUSTOMER";

export interface LambdaPolicy {
  lambdaMin: number;
  lambdaMax: number;
  version: number; // Java long / OpenAPI int64
}

export interface WeightConfig {
  enableStock: boolean;
  enableExpiry: boolean;
  enableSeason: boolean;
  alpha: number;
  beta: number;
  gamma: number;
  lambda: number;
  expiryWindowDays: number;
  version: number;
  scoringVersion: string;
}

export interface ConfigView {
  config: WeightConfig;
  lambdaPolicy: LambdaPolicy;
  lambdaValid: boolean;
}

export interface ScorePatch {
  stockPriorityScore?: number; // omit to preserve; do not send null
  seasonalPriorityScore?: number; // omit to preserve; do not send null
}

export interface Scores {
  productId: number;
  stockPriorityScore: number | null;
  seasonalPriorityScore: number | null;
}

export interface RangeRequest {
  rangeStart: LocalDate;
  rangeEnd: LocalDate;
  asOfDate: LocalDate;
}

export interface PreviewRequest extends RangeRequest {
  page?: number;
  pageSize?: number;
}

export interface FrozenConfiguration {
  config: WeightConfig;
  lambdaPolicy: LambdaPolicy;
  effectiveCoefficients: { alpha: number; beta: number; gamma: number };
  analysisDate: LocalDate;
  source: string;
  utilityScale: number;
  roundingMode: string;
  itemMapping: string;
}

export type ExpiryStatus = "AVAILABLE" | "NOT_APPLICABLE" | "NO_SELLABLE_BATCH" | "MISSING_METADATA";

export interface MatrixItem {
  transactionId: number;
  productId: number;
  algorithmItemId: number;
  quantity: number;
  baseUtility: number;
  stockScore: number | null;
  expiryScore: number | null;
  seasonalScore: number | null;
  expiryStatus: ExpiryStatus;
  weightValue: number | null;
  weightedUtility: number | null;
  transactionUtility: number | null;
}

export interface MatrixPage {
  items: MatrixItem[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalWeightedUtility: number | null;
  sourceHash: string;
  matrixHash: string;
  warnings: string[];
  ready: boolean;
  configuration: FrozenConfiguration;
}

export interface SnapshotView {
  snapshotId: number;
  storeId: number;
  createdBy: number;
  rangeStart: LocalDate;
  rangeEnd: LocalDate;
  asOfDate: LocalDate;
  configuration: FrozenConfiguration;
  sourceHash: string;
  matrixHash: string;
  totalWeightedUtility: number;
  roundingScale: number;
  status: string;
  createdAt: string;
}

export type UtilityConfig = ConfigView;
export type ProductPriorityScores = Scores;
export type UtilityPreviewRequest = PreviewRequest;
export type UtilityMatrixItem = MatrixItem;
export type UtilityPreviewResponse = MatrixPage;
export type UtilityMatrixPage = MatrixPage;
export type UtilitySnapshot = SnapshotView;
export interface ValidationError { error: string }
```

There is no separate `ExpiryPriorityResult` or `UtilityPreviewRequest` configuration body. E/status are returned per matrix item; preview reads saved settings. `PaginatedResponse<T>` is not a generic F03 response: the actual matrix page uses the fields above. `totalElements` counts matrix items, not distinct transactions or Products.

## Form validation and display rules

Use client-side checks for fast feedback; Backend validation is authoritative.

- T must be an integer greater than zero. Show the current value from `config.expiryWindowDays` (default fallback is 30); the calculator reads the saved value.
- Lambda must be in the current `lambdaPolicy` inclusive bounds. If `lambdaValid=false`, block Preview/Snapshot actions and ask the Merchandiser to save an allowed value. Never silently clamp it.
- Alpha, beta and gamma must each be [0,1] with no more than six fractional digits. Enabled criteria's coefficients must sum exactly to 1; if all flags are disabled, Backend allows the config and computes Weight 1.
- S and M each must be [0,1] with at most six fractional digits. Product selection uses F02 Product IDs. At least one of the two score fields must be sent; omit the other field to preserve it.
- `rangeStart`, `rangeEnd`, and `asOfDate` are required dates; start cannot be after end. Preview page is >=0 and page size is 1..100.
- No separate Product selection list, transaction dataset ID, or manual E field is accepted in Preview. F03 obtains Products/scores/batches and transaction rows on the server.

Display S and M as user-configured values and E as Backend-calculated. W is the final weight. T defines the expiry scoring window. Weighted utility is `BaseUtility × W`; it is a prioritization measure and does not guarantee revenue or actual profit improvement. Expiry on or before `asOfDate` is excluded in F03; this boundary is not inferred from F02's sellability summary.

## Loading, empty and error states

| State / response | Frontend behavior |
|---|---|
| Loading config/policy/scores | Keep controls disabled and show a loading state; do not submit guessed defaults. |
| Empty Product list | Show F02 empty state; score APIs need an existing Product ID. |
| Null Product score | Show “not configured”; allow setting it. Preview is incomplete only when that score's criterion is enabled. |
| Missing expiry metadata | Preview returns warning, `expiryStatus="MISSING_METADATA"` and null E. When expiry is enabled, show incomplete state and prevent snapshot submit. |
| No eligible batch | E is 0 with `NO_SELLABLE_BATCH`; Backend also warns, but this status alone does not make the matrix incomplete. |
| `lambdaValid=false` / 409 | Bounds changed since the saved lambda. Refetch config/policy, require the Merchandiser to choose a valid lambda, then retry. |
| 400 `{error:"Invalid request fields"}` | Show field-level validation based on submitted form; this envelope is used for Bean Validation and JSON parsing failures. |
| 400 business `{error:string}` | Show the returned message, e.g. coefficient sum, invalid range, empty matrix, or invalid score. Do not retry unchanged data. |
| 401 | Clear local authentication state and return to the F01 sign-in flow. |
| 403 | Hide/disable unavailable action and show permission message; do not repeatedly retry. |
| 404 | Treat Product/snapshot as missing or outside the authenticated Store; do not reveal whether another Store owns it. |
| 503 on Preview/Snapshot | Show “transaction data unavailable”; this is expected with the default F04 provider until F04 integration is complete. Never replace it with demo math in the normal app. |
| Network/5xx | Show retry action for transient failure; avoid duplicate snapshot submits, and only show success after receiving `snapshotId`. |

## Dependencies and F05 handoff

F01 supplies JWT authentication, current user, active-account checks, role authority and Store identity. F02 owns Product identity, Product Store ownership, `requires_expiry`, inventory batches, quantities/status/expiry dates and `stockQuantity`. F03 owns the manual score columns on Product, system lambda policy, Store utility config, E calculation, preview and immutable snapshot/matrix. F04 must supply validated/preprocessed transaction rows with historical quantity, unit price/cost or a precomputed item total and eligibility. F03 does not import CSV.

F05 does not exist in this scope. F03's `itemMapping` is `PRODUCT_ID_V1`; `algorithmItemId` equals `productId`. Matrix rows expose decimal `weightedUtility` at scale 6 and transaction totals repeated as `transactionUtility`; there is no F05 integer file/export or `MINING_REQUEST` FK yet. F05 must request all matrix pages for a snapshot and convert each item utility to the agreed signed integer scale (`utilityScale=6`) with range checking. Do not assume a snapshot list endpoint or F05 relationship exists.

## Examples and implementation gaps

All example request/response files are in [`F03_examples/`](F03_examples/). They correspond to real DTOs and route shapes; example values use synthetic IDs and the explicit QA fixture. There is no API for independent E retrieval, Store config history, snapshot listing, mining input export or mining execution. The production transaction provider is unimplemented; current endpoint success with generated transactions is limited to the isolated `dev` + `utility-fixture` profile. The API cannot report a dataset ID because none exists in the contract or V5 schema. Snapshot behavior is immutable through the API; no database trigger prevents direct SQL edits.

## Verification status

CodeGraph was available and used for source/dependency investigation. The code graph includes Java and TypeScript/TSX. The runtime OpenAPI document lists 11 F03 operations and uses the shared Bearer scheme. Existing `UtilityIntegrationTest` directly requests 10 of 11 F03 HTTP operations; only `GET /api/system/utility/lambda-policy` lacks a direct MockMvc request. The exercised routes cover Store/role boundaries, validation, Preview, snapshot create/read/matrix and snapshot stability under policy/T/score changes. Calculation and fixture tests cover expiry/math behavior. Maven tests were run with throwaway local `JWT_SECRET` and `OTP_SECRET` values, not stored in this documentation. This validates automated MockMvc/service behavior; **interactive Swagger UI “Try it out” was NOT RUN for this handoff**. See the endpoint checklist in [`../api/F03_UTILITY_API.md`](../api/F03_UTILITY_API.md).

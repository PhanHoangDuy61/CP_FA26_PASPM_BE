# F03 Frontend Integration Handoff

Audience: React / TypeScript developer. This handoff describes the implemented API as of 2026-10-17. No Frontend code is included in this change.

## Base URL and environment

The API has no hardcoded `/api/v1` prefix. Set one deployment-specific `VITE_PASPM_API_BASE_URL` in the React application's private build environment, for example:

```dotenv
VITE_PASPM_API_BASE_URL=http://localhost:8080
```

The backend defaults to port 8080 when `PORT` is unset. The isolated QA run used port 8799; that is not the application default. Swagger UI is `/swagger` and the exported OpenAPI document is [`../api/openapi.json`](../api/openapi.json); runtime endpoints are `/v3/api-docs` and `/swagger`.

Never put database, mail, OTP, or JWT signing secrets in `VITE_*` variables or the browser bundle. Frontend stores only the access token received through F01 and sends `Authorization: Bearer <token>` over HTTPS in deployed environments. On 401, follow the existing F01 login/session flow; do not retry protected requests indefinitely. API errors use `{ "error": string }` for handled errors.

## Roles and permissions

Role names are the exact backend authorities:

| Role | F03 UI capability |
|---|---|
| `SYSTEM_ADMIN` | View and change global permitted lambda bounds. |
| `STORE_ADMIN` | View own Store utility config and policy; change only own Store's expiry window T. |
| `MERCHANDISER` | View Store config; set effective criteria/coefficients and selected lambda; read/write Product S/M scores; preview, create, and read snapshots/matrix. |

The UI can hide controls by role for clarity, but the backend remains authoritative. No request accepts a `storeId` to select scope. A cross-Store resource behaves like a missing resource (404).

## Suggested API integration order

1. Complete F01 sign-in and obtain a bearer token.
2. Fetch `GET /api/utility/config` for a Store Admin or Merchandiser. Render `lambdaPolicy`, Store config, and `lambdaValid`.
3. If the account is `SYSTEM_ADMIN`, use `GET /api/system/utility/lambda-policy`; updates use the corresponding PUT. If policy is tightened so `lambdaValid=false`, let the Merchandiser choose a new lambda within the displayed range before calculating.
4. Store Admin optionally sets T with `PUT /api/utility/config/expiry-window`.
5. Merchandiser configures criteria and coefficients with `PUT /api/utility/config`; read/confirm the saved response. Configure Product scores with GET/PATCH `/api/utility/products/{productId}/scores` where needed.
6. Call `POST /api/utility/preview` with an explicit transaction range and analysis date. Display warnings and incomplete state; don't present null totals as zero. Resolve missing enabled scores/expiry data before creating a snapshot.
7. On user confirmation, call `POST /api/utility/snapshots`. Save the returned `snapshotId`, then load metadata and pages with GET `/api/utility/snapshots/{id}` and `/matrix?page=0&pageSize=20`.

Always display the saved analysis date for a historical snapshot. Do not refresh an old snapshot from current config or batches; the snapshot API is immutable and returns its captured values.

## Recommended UI workflow

A reasonable configuration screen has three permission-aware sections:

- **System policy:** System Admin edits `lambdaMin` / `lambdaMax`; show policy `version`. Initial allowed range is [0,0.5].
- **Store settings:** Store Admin edits integer T (default 30 days). Merchandiser sees T but cannot edit it.
- **Utility profile:** Merchandiser toggles Stock/Expiry/Season, enters alpha/beta/gamma and lambda, and gets inline validation. Enabled coefficients must total exactly 1. When all criteria are off, backend computes Weight=1. The score editor stores Stock Priority Score and Seasonal Priority Score per Product; backend derives Expiry Priority Score from inventory batches.

The preview should show `ready`, warnings, hashes, total and rows with `baseUtility`, S/E/M, `weightValue`, `weightedUtility`, and `transactionUtility`. Disable snapshot creation until preview is ready and has rows. The endpoint validates again during create, so handle a 400 or 409 if data/policy changed after preview. After creation, use only the snapshot read APIs for that result.

For API paths, DTO shapes, exact status codes, examples and validation details see [`../api/F03_UTILITY_API.md`](../api/F03_UTILITY_API.md). Use the exported OpenAPI JSON for generated TypeScript types if desired; role enforcement is described in this handoff because OpenAPI doesn't include `@PreAuthorize` metadata.

## Example test data

These values are safe examples for development data; actual IDs depend on seed/database state.

Initial policy:

```json
{"lambdaMin":0,"lambdaMax":0.5,"version":1}
```

System Admin policy update:

```json
{"lambdaMin":0.1,"lambdaMax":0.5}
```

Store Admin expiry window:

```json
{"expiryWindowDays":30}
```

Merchandiser three-weight config:

```json
{"enableStock":true,"enableExpiry":true,"enableSeason":true,"alpha":0.5,"beta":0.2,"gamma":0.3,"lambda":0.5}
```

Product score patch:

```json
{"stockPriorityScore":0.8,"seasonalPriorityScore":0.9}
```

Preview and snapshot date input (choose dates relative to actual data and the Store's local date):

```json
{"rangeStart":"2026-10-01","rangeEnd":"2026-10-31","asOfDate":"2026-10-17"}
```

A calculation example with T=100: expiry batch 40 days after `asOfDate` has E=0.6. With base utility 12000, S=0.8, M=0.9, alpha=.5, beta=.2, gamma=.3 and lambda=.5, Weight=1.395 and weighted utility=16740. This illustrates the math, not a seeded Product ID.

## Known limitations and integration status

- **Ready:** all 11 F03 routes are implemented and represented in the exported OpenAPI document. Config, score, preview, snapshot, and snapshot matrix behavior was exercised against the backend and isolated QA fixture.
- **F04 data dependency:** a production transaction source is not present yet. Without the explicit QA fixture profile, preview/create return 503 with an F04 provider-unavailable error. The UI should show a service-unavailable state and avoid fabricating a result. Use only a properly configured development fixture environment for local UI development.
- **F05:** no mining engine, `MINING_REQUEST` relationship, or mining execution endpoint is implemented here. Matrix item ID mapping is currently `algorithmItemId = productId` (`PRODUCT_ID_V1`), which is a documented handoff mapping rather than an F05 run.
- Tightening lambda bounds does not change or clamp existing Store lambda. The config read returns `lambdaValid=false`; new preview/snapshot requests fail with 409 until the Merchandiser updates lambda.
- Expiry-date boundary is strict: expiry date equal to analysis date is excluded from the F03 eligible set. Historical results use the supplied `asOfDate`, not today's date.
- Snapshot creation rejects incomplete enabled scores/expiry metadata and empty source rows. Preview may return warnings and `ready=false` with a null total.
- Error response is `{ "error": "..." }` for handled errors. OpenAPI has the global JWT scheme but does not list role requirements per operation; follow the role matrix above.

## APIs ready for integration

| Method | Path | Role |
|---|---|---|
| GET | `/api/system/utility/lambda-policy` | SYSTEM_ADMIN |
| PUT | `/api/system/utility/lambda-policy` | SYSTEM_ADMIN |
| GET | `/api/utility/config` | STORE_ADMIN, MERCHANDISER |
| PUT | `/api/utility/config` | MERCHANDISER |
| PUT | `/api/utility/config/expiry-window` | STORE_ADMIN |
| GET | `/api/utility/products/{productId}/scores` | MERCHANDISER |
| PATCH | `/api/utility/products/{productId}/scores` | MERCHANDISER |
| POST | `/api/utility/preview` | MERCHANDISER |
| POST | `/api/utility/snapshots` | MERCHANDISER |
| GET | `/api/utility/snapshots/{id}` | MERCHANDISER |
| GET | `/api/utility/snapshots/{id}/matrix` | MERCHANDISER |

# F06 React / TypeScript integration handoff

This handoff describes the implemented backend contract, checked against current recommendation controllers, records, rules, frozen-source reader and inventory services on 2026-10-20. It provides a workflow for frontend implementation; it does not claim implementation in FE or FE_Kien. The detailed wire types, exact field nullability and all eight operations are in [F06_RECOMMENDATION_API.md](../api/F06_RECOMMENDATION_API.md). No new F06 authentication flow is needed. Use backend Java17; the [existing runtime setup](../setup/F05_SPMF_RUNTIME_SETUP.md) applies when creating new F05 source runs. Independent F06 expiry and reading/generating from existing completed runs do not execute SPMF. Existing database V7 checksum reconciliation remains the separate [operator recovery workflow](../setup/FLYWAY_MYSQL_V7_RECOVERY.md).

## Connect and authorize

Configure the API base URL to `http://localhost:8080` for default local development, or the deployed backend origin. Swagger entry is `http://localhost:8080/swagger`; UI convention `/swagger-ui/index.html`; live OpenAPI `/v3/api-docs`. Use a same-origin development proxy if needed; do not assume browser cross-origin access is configured. Follow the [F01 handoff](F01_HANDOFF.md): POST `/api/auth/login` with email/password, keep `accessToken` in the existing session mechanism, send `Authorization: Bearer <token>`, and GET `/api/auth/me` to identify role. Send `Content-Type: application/json` for JSON mutations. Never submit a Store ID; the server derives it from the identity.

| Capability | MERCHANDISER | STORE_ADMIN |
|---|---|---|
| List/detail, config read, independent expiry alerts | Yes | Yes |
| Generate, approve, reject | Yes | No |
| Edit rule configuration | No | Yes |

SYSTEM_ADMIN and END_CUSTOMER cannot access F06. UI guards improve usability but backend authorization remains authoritative. Clear Store-scoped cached data when session/Store identity changes.

## Screens and end-to-end flow

1. **Completed mining run selection.** Reuse the [F05 workflow](F05_HANDOFF.md) and its own-Store run detail. Only a COMPLETED run is a generation source. Display run ID, snapshot, algorithm and provenance. F06 does not rerun SPMF or automatically create F03 snapshots.
2. **Generate dialog (Merchandiser).** Load GET `/api/recommendations/config` to explain current thresholds. Choose one or more unique BUNDLE/CROSS_SELL/FBT/BANNER values (maximum four); POST `/api/recommendations/generate` with `{miningRunId,types}`. Disable duplicate submission while pending. Show createdCount, reusedCount and skipped reasons. An HTTP 200 with zero candidates is a valid empty outcome.
3. **Run recommendation table.** GET `/api/recommendations?miningRunId=<id>&page=0&pageSize=20`. Offer only supported optional filters `snapshotId`, `type`, `reviewStatus`. Reset page to zero when filters/run change. Show server rank, type, Products, anchor, frozen utility, support/confidence, review state, current eligibility and warnings. Read totalElements for pagination, not items.length. Preserve server order; rank is calculated before pagination and may move after inventory changes.
4. **History view.** Omit miningRunId to request CREATED_DESC chronological history; rank is null. Label utility as belonging to its snapshot/source. Do not present a global utility ranking or add a client-generated business score.
5. **Detail/review drawer.** GET `/api/recommendations/{id}` on opening. Show generationEligibility beside currentEligibility; explain inventoryChanged; show all sources, snapshot/analysis date, policy/rule version, evidence, explanation and reviewHistory. For CROSS_SELL, render suggested targets as `productIds.filter(id => id !== anchorProductId)`. Resolve names through existing authorized Product data where available; keep IDs visible if a Product is unavailable.
6. **Review submission.** Only show decisions to Merchandiser while PENDING_REVIEW. Require a nonblank reason <=1000 characters. Send `{expectedVersion:detail.version,reason}` to `/{id}/approve` or `/{id}/reject`. Disable approval for non-ELIGIBLE current state, while allowing rejection; the backend performs another live approval check. Replace detail with the returned RecView and invalidate relevant lists. A successful response has rank null; do not overwrite table rank with a fabricated number.
7. **Rule settings (Store Admin).** GET config, edit the complete form, PUT config with its original expectedVersion. Bounds: maxBundleSize 2..256; minSupportCount >=1; minSupportRatio/minConfidence 0..1 and at most six decimals; minimumSellableQuantity >=1. All are required. Defaults: 4, 2, 0.05, 0.50, 1. Display 0.05 as 5% only in presentation; send fractional units. Retain exact decimal input rather than multiplying binary floats. On success replace the form's version with the returned version; existing recommendations retain their captured policy.
8. **Independent expiry panel.** GET `/api/recommendations/expiry-push?productId=<optional>&page=0&pageSize=20` without requiring mining. Show batch/Product, expiryDate, daysRemaining, remainingQuantity, Product expiryPushDays, status/warnings and server date/timezone/check time. No asOfDate selector is supported. Render page warnings even with no rows.
9. **Downstream use.** GET detail immediately before handing a recommendation to another feature; require APPROVED and readyForConsumption=true. Recheck at actual activation/fulfillment. Show unready approved records as approved but currently blocked. There is no F06 publish, campaign creation, discount, activation or reservation action to call.

## Suggested client structure

Reuse the application's existing HTTP/session layer. Put the exact wire types from the API contract in the existing API types location; separate transport, query/cache hooks and presentation. Suggested query keys include session Store identity and every applied filter/page: `['f06','list',storeContext,params]`, `['f06','detail',storeContext,id]`, `['f06','config',storeContext]`, `['f06','expiry',storeContext,params]`. These are frontend design suggestions, not backend endpoints.

The following transport skeleton is illustrative for safely representable fixture numbers. Production clients must replace the numeric parser/serializer with the existing lossless adapter if int64/decimal values can exceed JavaScript precision. Never stringify an exact ID through Number; positive IDs/versions must still be emitted as JSON integer tokens, not quoted strings.

```ts
// RecView, RecPage, RecPolicy, RecGenerationResult and RecExpiryPage:
// use the exact interfaces in F06_RECOMMENDATION_API.md.
async function call<T>(path: string, token: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${apiBase}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init.headers,
      Authorization: `Bearer ${token}` },
  });
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok) {
    throw Object.assign(new Error(body?.error ?? `Request failed (${response.status})`),
      { status: response.status });
  }
  return body as T;
}
const detail = await call<RecView>(`/api/recommendations/${id}`, token);
// Run from an explicit human decision handler, not an effect.
const approved = await call<RecView>(`/api/recommendations/${id}/approve`, token, {
  method: 'POST',
  body: JSON.stringify({ expectedVersion: detail.version, reason }),
});
```

React lifecycle: cancel stale GET requests using AbortController or the existing query library when run/filter/detail changes; preserve the last successful page during a refresh with a visible loading indicator. Submit mutations in event handlers, not mount effects (React StrictMode can replay effects). Repeated generation is deduplicated by the backend but must not be used to trigger automatic review. Refresh on drawer opening, after review/generation and when returning to the screen; no scheduler or push subscription is provided. Eligibility is checked at request time, so do not imply a freshness guarantee from polling.

## Rendering and business semantics

Utility is historical frozen weighted utility, not purchase probability. FBT support counts distinct frozen baskets; support and confidence ratios are six-decimal display values. Backend acceptance uses exact count comparisons. Do not compute score, threshold eligibility, stock availability, expiry E_i/Weight, rank or minUtil conversion in React. Display backend fields and explanations.

Null means unavailable/not applicable: render an em dash or explicit “unavailable”, never zero. Specifically account for nullable anchorProductId, reviewedBy/reviewedAt/reviewReason, rank, evidence supportCount/supportRatio/anchorSupportCount/confidence and per-Product expiryPriorityScore. Arrays can be empty. Review status and eligibility are separate dimensions; readyForConsumption combines APPROVED with current ELIGIBLE. generationEligibility and historical review checks are immutable; currentEligibility changes on reads.

Stock is current sellable quantity, not historical transaction quantity. Expiry today is excluded (expiryDate must be strictly after Store-local today). Missing required active-batch metadata gives NEEDS_RECHECK. Product expiryPushDays defaults to 0, with no Store fallback; 0 disables expiry push. Expiry priority is separate from mining utility and cannot guarantee pattern inclusion. A changed fingerprint can reflect date rollover or metadata, not only quantity.

Equivalent completed algorithm runs on identical snapshot/input/threshold/utility and policy can reuse recommendation IDs and retain every provenance source. Different contexts remain distinct. Regeneration does not reopen APPROVED/REJECTED decisions. Show all source algorithms; do not infer that duplicate IDs indicate lost results.

## Error states and acceptance checks

- 400: show field/request error and retain editable input. JSON integer fields reject strings/fractions; unknown properties are invalid. pageSize is 1..100. No custom sort, search, scheduler or historical expiry-date parameter exists.
- 401: use the existing F01 session recovery and support an empty response body. 403: hide unavailable action and retain readable content when permitted. 404: report unavailable record without leaking other Store details.
- 409 on review: refetch detail and explain version/state change or current inventory block. Ask for a new deliberate human decision; never automatically substitute the new version and resubmit. APPROVED/REJECTED are final with no reopen/history-edit endpoint.
- 409 on config: refetch and let Store Admin reconcile changes before resubmitting. 409 on generation: display unfinished/inconsistent frozen source or the 10,000-candidate safety limit; for generation cap, choose fewer types; for a ranked scope above 10,000, narrow type or reviewStatus filters. Do not offer a frontend override.
- Network failure: retain inputs and refetch state before presenting a retry. Avoid claiming a review failed merely because its response was lost; it may already have persisted.

Frontend acceptance should cover both roles, forbidden roles, cross-Store 404, stale version conflict, stock changing between detail and approval, approved becoming unready, rejection while ineligible, repeated generation preserving final status, source dedup across algorithms, empty candidate/page/expiry results, null fields and timezone midnight boundary. This is a recommended integration checklist, not a claim that these UI scenarios have already executed.

Actual executed integration-test fixtures are available: [generate request](F06_examples/generate_request.json), [generate response](F06_examples/generate_response.json), [detail](F06_examples/detail_response.json), [list](F06_examples/list_response.json), [config](F06_examples/config_response.json), [approve request](F06_examples/approve_request.json), [approve response](F06_examples/approve_response.json), [conflict](F06_examples/conflict_response.json), [expiry push](F06_examples/expiry_push_response.json). Replace fixture IDs with target Store results. The exported generation fixture creates eight recommendations and records singleton skips. It is not a credential bundle or a claim of browser Swagger execution.

Verified on2026-10-20: Java17 clean verify210 PASS (63 F06 +147 regression), isolated MySQL V1–V10 and V9→V10,27 actual Swagger UI cases. The [verification report](../testing/reports/F06_VERIFICATION.md) separates these channels. The shared OpenAPI was exported from the actual test origin `http://localhost:18086`; set the Frontend target base URL for your environment. Frontend integration itself was NOT RUN; existing demo code was not changed. Inline requests are illustrative; linked JSON files are actual automated test exports.

# F11 — Dashboard frontend handoff

**Backend implemented on the stacked `Khoi/feat/f11-dashboard-api` branch.** The target frontend route `/store/dashboard` is shared by `STORE_ADMIN` and `MERCHANDISER`. See the [F11 API](../api/F11_DASHBOARD_API.md). Use the existing staff shell, role guard and bearer client. Independent panel requests allow valid panels to render when one request fails.

## TypeScript types

```ts
type Int64 = number; // Use a lossless adapter if values exceed the safe integer range.
type LocalDate = string;
type BundleOrigin = 'MINED' | 'MANUAL';
type CountsByOrigin = Record<BundleOrigin, Int64>;
interface Page<T> { items: T[]; page: number; pageSize: number; totalElements: Int64 }
interface SnapshotUtility {
  snapshotId: number;
  rangeStart: LocalDate;
  rangeEnd: LocalDate;
  analysisDate: LocalDate;
  createdAt: string;
  totalWeightedUtility: number;
}
interface BundleAdditions {
  from: LocalDate;
  to: LocalDate;
  timeZone: string;
  campaignId: Int64 | null;
  totalAddActions: Int64;
  addActionsByOrigin: CountsByOrigin;
  totalAddedProductUnits: Int64;
  days: Array<{ date: LocalDate; addActionsByOrigin: CountsByOrigin; addedProductUnits: Int64 }>;
}
interface ReportLayoutCoverage {
  priorityCoverage: number | null;
  priorityCoverageDenominator: number | null;
}
```

Extend the existing F07/F08 report DTO with the coverage typing if required. Keep stored versions, selection counts and applicable fields from the original report.

## Panel behavior

- **Snapshot weighted utility:** call `GET /api/dashboard/snapshot-utilities` with `page` and `pageSize` to page through READY same-Store snapshots. Use optional `snapshotId` for a selected READY snapshot. Show the snapshot window, analysis date and frozen score using up to six decimals. Utility is not VND. Do not add scores across snapshots with overlapping windows.
- **F08 A/B:** reuse `GET /api/campaigns`, `GET /api/campaigns/{id}/reports` and `GET /api/campaign-reports/{reportId}`. Label the view “Mô phỏng”. Show both layout revisions, sample sizes, hit rate, bundle conversion, captured utility and priority coverage. A null rate displays “Chưa có”, and numeric zero displays 0%. A null denominator is historical or unavailable, while zero is a known empty denominator. If `nExceedsBaseline=true`, show each actual product count. Keep synthetic fixture results visibly separate from runtime reports.
- **Bundle additions:** call `GET /api/dashboard/bundle-additions`. Show total actions, split MINED/MANUAL, and a secondary total of Product units added. Default to 30 Store-local days, allow a custom window of at most 90 inclusive days (90 accepted, 91 returns 400; limit the date picker accordingly, [DEC-20261126-02](../decisions/2026-11-26/DEC-20261126-02-vnd-display-cart-cap-dashboard-window-merch-screens.md)) and optional campaign filter. Send `from` and `to` together in `YYYY-MM-DD` or omit both to get the 30 days ending today. Plot real zeros from a successful response. If the event panel is unavailable, show a separate retry state.

On 400 keep current inputs and report filter errors. Use F01 session recovery for 401 and a role/Store message for 403; 404 clears the selected resource and reloads the relevant same-Store list. Preserve other panels when a request fails. Guest carts, checkout/order/payment, bundle discounts, view events, daily deals and benchmark charts are outside this MVP.

# DEC-20261124-01 — Batch expiry boundary and `effectiveStatus` (F02, with F03/F06 consumers)

| Field | Value |
|---|---|
| Date | 2026-11-24 (Asia/Ho_Chi_Minh) |
| Source | Product Owner requirement R1 and the PO answers at Checkpoint 1 of the F02 expiry / push-day / product-delete task (2026-11-24) |
| Status | OWNER_DECISION, implemented on branch `Khoi/feat/f02-expiry-pushday-product-delete`; PR to `dev` pending. `scoringVersion` is not bumped (§6, PO decision at Checkpoint 2) |
| Feature | F02 (batches, inventory summary); consumers F03 (U_expiry, push-day rule), F06 (eligibility, expiry-push lists) |
| Related | DEC-20261115-01 (push day per batch), DEC-20261116-01 (P4), DEC-20261124-02, DEC-20261124-03 |

## Context
- Before this decision a batch counted as expired **on** its expiry date (`expiry_date <= today`). The code did so in `InventorySummaryRepository`, `ExpiryRiskCalculator`, `PushDayRule` and `ProductEligibilityService`, the current docs said so, and the frontend repeated it (`expiryDate <= today`).
- The older F02 reference (`docs/features/F02/reference_v3/F02_TECHNICAL_DECISIONS.md`) said the opposite ("a batch expires at the start of the next local date"). No DEC had recorded the strict rule, so the code and the reference disagreed.
- Expiry has never been stored: `inventory_batch.status` allows only `ACTIVE` and `BLOCKED`, and no job flips batches.

## Decision

### 1. One rule
A batch is **expired only when today's date in `Asia/Ho_Chi_Minh` is after its `expiry_date`** (`today > expiry_date`). On the expiry date itself the batch is still active and sellable. A batch without an expiry date never expires.
One helper, `BatchExpiryRules`, holds the rule; the F02 summary, F03 and F06 all call it.

### 2. Computed on read; no INACTIVE, no job, no migration
- The stored `status` stays `ACTIVE | BLOCKED` and is the only writable status. `PATCH` rejects `EXPIRED` and `INACTIVE` with 400.
- Every `BatchView` carries `effectiveStatus = ACTIVE | BLOCKED | EXPIRED`. **EXPIRED wins over BLOCKED.** Otherwise it is the stored status.
- Every `BatchView` carries `daysToExpiry`: `expiryDate − today` in whole days. **0 on the expiry date**, negative once expired, `null` without an expiry date.
- "Expiring soon" is `effectiveStatus != EXPIRED` and `0 <= daysToExpiry <= 7`. The frontend applies this to the integers and never compares dates itself. The summary keeps `expiringWithinDays` (default 7).
- An expired batch can still be edited (`expiryDate`, `remainingQuantity`, `BLOCKED`). Moving `expiryDate` into the future makes it active again, because nothing was stored.

### 3. Inventory summary buckets (they do not overlap)
| Field | Definition |
|---|---|
| `totalQuantity` | every remaining unit (unchanged) |
| `expiredQuantity` | `expiry_date < today`, **whatever the stored status** (BLOCKED + expired counts here) |
| `blockedQuantity` | stored BLOCKED **and not expired** |
| `sellableQuantity` | stored ACTIVE **and not expired** (a batch expiring today is sellable) |
| `expiringSoonQuantity` | stored ACTIVE with `today <= expiry_date <= today + expiringWithinDays` |

`F02_TECHNICAL_DECISIONS.md` (reference v3) is updated to match.

### 4. F03 / F06
- `ExpiryRiskCalculator`, `PushDayRule` and `ProductEligibilityService` use the same rule. On the expiry date the batch is eligible; its push day is on or before that date, so the push-day-reached rule (DEC-20261116-01, P4) puts the whole remaining batch at risk (`PUSH_DAY_REACHED`). A batch without a push day only warns (`MISSING_PUSH_DAY`).
- **Effect:** a new snapshot, or a live F06 list, taken on a batch's expiry date now includes that batch (before, it was excluded as `EXPIRED`), so U_expiry can be higher on that day.
- **Frozen data is untouched.** Existing snapshots, matrices, mining requests, recommendations and their evidence are never recomputed. Evidence rows keep their `asOfDate`; read an `EXPIRED` row in a snapshot taken before 2026-11-24 as "expiry on or before the analysis date".
- `ExpiryScoreCalculator` (the v1 binary E score) is a legacy reader for frozen JSON and is not changed.
- F03 still ignores non-ACTIVE and empty batches before it looks at expiry. A BLOCKED batch is never a candidate, expired or not.

### 5. Frontend
The frontend stops computing "expired", "expiring soon" and the push-day state from dates, and reads `effectiveStatus`, `daysToExpiry` and `warnings` ([DEC-20261124-02](DEC-20261124-02-push-day-missing-warning.md)). The frontend repository had `isExpired`, `isExpiringSoon` and `batchPushState` in `features/store/f02.forms.ts` (checked 2026-11-24 on FE `dev` @ `7def287`); that change belongs to the frontend task.

### 6. `scoringVersion` is NOT bumped (PO decision at Checkpoint 2, 2026-11-24)
The PO asked to bump `scoringVersion` for the new boundary **unless that blocks comparing or mining across versions**. It would, so **`W2_EXPIRY_SEASON_V1` is kept**.
- `scoringVersion` is not a trace label. `W2_EXPIRY_SEASON_V1` (`UtilityVersionedContracts.V2`, line 10) is the tag of the whole Weight v2 model, stamped into the Store `utility_config` (`WeightV2Conversion.java:53`, `UtilityConfigurationService.java:34`), the λ policy (`UtilityConfigurationService.java:21`), the frozen snapshot configuration, FEFO evidence (`ExpiryRiskCalculator.java:86,95`) and F06 eligibility evidence (`ProductEligibilityService.java:29,57`).
- Every reader accepts **only that exact string**: the constructors `requireV2(...)` in `UtilityVersionedContracts.java:11,15,25,27,46` and `RecommendationVersionedContracts.java:19`; the dispatch `UtilityJsonVersions.java:25-31` (anything else throws `UnsupportedScoringVersion`, HTTP 409 `UNSUPPORTED_SCORING_VERSION`); `RecommendationJsonVersions.java:20-23`; `WeightV2Conversion.java:34-35`.
- Stored snapshots, policies and recommendations carry the current string and are immutable. A bumped constant would make them unreadable (409) unless every reader and constructor accepted both tags and the writers knew which to emit. A Store would also mix tags between its config and its older snapshots.

**What replaces the version bump (recorded here instead):**
1. **Effective date.** The new boundary applies from the day this decision is merged into `dev` (decision date 2026-11-24). *Merge commit and date: to be written here after the merge.*
2. **How to tell which rule a snapshot used.** The evidence carries `asOfDate`, and `asOfDate` before the effective date means the old rule (`EXPIRED` = expiry on or before the analysis date); on or after it means the new rule (`EXPIRED` = expiry before the analysis date). Because `asOfDate` is a request field of snapshot creation (`UtilitySnapshotService.java:21`) and not always the creation day, also compare the snapshot's creation time with the effective date: a snapshot created before the merge used the old rule whatever its `asOfDate`.
3. **No mixing.** Experiment snapshots (F03 snapshots and the F05 runs, F06 recommendations and F07/F08 layouts built on them) **must be regenerated after the merge**. Do not compare utility, U_expiry or mining results across the effective date.
4. Frozen data is never rewritten (BR-17); the old snapshots stay readable as history.
5. If a new tag is wanted later (dual-read, as its own task), it needs a new DEC; it is not planned.

## Consequences
- **API (additive):** `BatchView.effectiveStatus`, `daysToExpiry`, `warnings`; `InventorySummary.batchesMissingPushDay`. No field removed or renamed.
- **Behaviour change for clients:** the expiry boundary, and BLOCKED + expired moving from `blockedQuantity` to `expiredQuantity`.
- No migration for this decision.
- Tests: `BatchExpiryRulesTest`, `ExpiryBoundaryF03F06Test`, `CatalogIntegrationTest` (summary buckets, effectiveStatus, daysToExpiry), and the existing F03/F06 tests that pinned the old boundary (`WeightV2CalculationTest`, `PushDayIntegrationTest`, `RetailCatalogUtilityIntegrationTest`, `RecommendationExpiryIntegrationTest`, `RecommendationIntegrationTest`).

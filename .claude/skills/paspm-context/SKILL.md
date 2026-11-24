---
name: paspm-context
description: Use at the start of any PASPM (FA26SE048) task, and whenever a task touches business rules, roles, Store isolation, Weight v2 utility, EFIM mining, recommendations, layouts and campaigns, storefront, cart or dashboard. It gives the read order, the source-of-truth precedence, the vocabulary and the hard rules every agent must follow.
---

# PASPM project context

PASPM (Product Arrangement Support Platform, FPT capstone FA26SE048) is a decision-support web app for food and beverage retail. It turns a Store's sales into weighted utility (profit × expiry and season priority), mines high-utility itemsets with EFIM, turns them into reviewable merchandising proposals, and lets staff simulate and activate a storefront layout.

## 1. Read before you change anything

The product docs live in the backend repo (`PhanHoangDuy61/CP_FA26_PASPM_BE`) under `docs/product/`. Read in this order, and only the parts your task needs:

1. `docs/product/GLOSSARY.md`. Use its terms exactly, in code, prompts and messages.
2. `docs/product/SRS.md`: the feature's section in §3, the business rules in §4, the limits in §5.
3. `docs/product/ARCHITECTURE_SDD.md`: modules, tables, state machines and the key design for the area you touch.
4. `docs/product/BRD_VISION.md`: scope, the decision log (§8), the working rules (§9) and the open questions (§12).
5. The feature's own contract:
   - `docs/api/<Fxx>_*.md`;
   - `docs/frontend_handoff/<Fxx>_HANDOFF.md`;
   - `docs/features/<Fxx>/README.md`.
6. The newest files in `docs/decisions/` and `docs/record-of-changes/` related to the area.

## 2. Which source wins

When sources disagree, the higher one wins:

1. Owner decisions in `docs/decisions/` (DEC-yyyymmdd-nn) and merged code on `dev`.
2. `docs/product/*` and the feature contracts in `docs/api/`.
3. Reports 1–4 and older diagrams. They are partly outdated, and each product doc lists what they get wrong.

The project principle is **"docs làm chuẩn"**:

- If code breaks a recorded requirement or decision, fix the code or report the gap. Never rewrite the requirement to match the code.
- Anything marked **TBD** is undecided. Ask the owner, Nguyễn Minh Khôi (Team Leader, acting Product Owner). Do not guess.

Status labels in the docs:

| Label | Meaning |
|---|---|
| `DONE` | Merged on `dev` |
| `PARTIAL` | Merged, with a documented gap |
| `PLANNED` | Not coded yet |
| `REMOVED` | Superseded by a decision |
| `TBD` | Undecided |

## 3. Hard rules, for every agent and every repo

- **Git**
  - `dev` changes **only through a pull request with green CI** (DEC-20261119-01).
  - Work on a new branch `<member>/<type>/<topic>`.
  - No direct push to `dev`. No force-push.
  - Commit, push or merge only when the human asked for it.
- **Database**
  - Never edit Flyway migrations V1–V26. Schema changes go in a new version.
  - Never delete, reset or "repair" a teammate's local database. Tests use disposable databases or containers.
- **Evidence**
  - Never fabricate test results.
  - Anything not executed is reported as `NOT RUN`. Anything without evidence stays `NOT_READY`.
  - Keep local, CI and manual evidence separate.
- **Secrets:** never print, commit or copy them. `application.yml` holds only `${…}` placeholders, and real values come from environment variables.
- **Frozen JSON:** snapshots, requests, runs, recommendations, layout revisions and reports are immutable. Read stored JSON by its version tag. Never rewrite history.
- **Scheduler:** enabling scheduled mining keeps returning **HTTP 503** until owner sign-off.
- **SPMF (GPL v3):** it runs **only as a separate process** (`java -jar spmf.jar run EFIM …`). Never import an SPMF class, add SPMF to `pom.xml` or bundle it into the backend jar (`SpmfProcessBoundaryTest`, DEC-20261125-01, `THIRD_PARTY_NOTICES.md`).

## 4. Facts agents most often get wrong

The product docs give the details; these are the traps.

- **Store isolation**
  - The Store comes from the JWT, never from a request field.
  - Another Store's resource returns **404**, not 403.
  - Roles: `SYSTEM_ADMIN` (no Store), `STORE_ADMIN`, `MERCHANDISER`, `END_CUSTOMER` (no Store), and `SYSTEM_SCHEDULER` (internal, cannot log in).
  - **Store status** (DEC-20261123-01, V27): a Store is `ACTIVE` or `INACTIVE` and is never deleted. Users of an INACTIVE Store get **403 `STORE_INACTIVE`** from one central guard (JWT converter, login, refresh); do not add per-service checks. System Admin new work and user assignment for an INACTIVE Store return **409 `STORE_INACTIVE`**. The picker `GET /api/system/stores` lists ACTIVE Stores by default.
  - Store names are unique after trimming, ignoring case (service rule, no DB UNIQUE). `slug` is unique and System-Admin-only. Store images: PNG/JPEG on `MediaStorage` under `PASPM_MEDIA_ROOT`; the DB keeps relative keys, never URLs.
- **Strict JSON:** the backend rejects unknown or removed request fields with 400 `Invalid request fields`. Never send extra fields.
- **Weight v2:** `W = 1 + λ(α·U_expiry + β·M)`.
  - α + β = 1, and λ ∈ [0, λmax]. The System Admin sets only λmax.
  - W is rounded to 2 decimals HALF_UP.
  - Weighted utility is a priority score, **not revenue or profit**.
  - Push day is set per inventory batch. Seasons give M.
- **Mining** is **EFIM-only**. HUI-Miner, compare mode and benchmarks were removed on 2026-11-16.
  - SPMF uses int32, which the EFIM-aware guard enforces. The input is lossless first and falls back to 1-đồng HALF_UP only on overflow (DEC-20261119-05). The mode is frozen in the request as `conversionVersion`.
  - Limits: 1,600 products and 20,000 matrix rows; 60 s and 256 MiB; 4 MiB output and at most 2,000 patterns, otherwise the run fails with `OUTPUT_LIMIT`.
- **Recommendations:** downstream features may use a recommendation only when `reviewStatus=APPROVED` **and** `readyForConsumption=true`, rechecked at the moment of use.
- **Layout & campaign**
  - Baseline A is read-only and frequency-ordered. B is built from whole recommendation blocks, and a product appears once per layout.
  - Campaign lifecycle: `DRAFT → PUBLISHED → ACTIVE → ENDED`. A Store has at most one ACTIVE campaign.
  - Zone caps are configured (DEC-20261119-06). Baseline A holds only the top-35 by frequency (`F07_BASELINE_V2`). A/B compares B with A's first N products, where N = the number of products in B (`OFFLINE_REPLAY_V2`).
  - A/B results come from an offline replay and are always labelled **simulated**.
- **F02 since 2026-11-19**
  - SKU must match `^SKU-[A-Z0-9-]{11}$`.
  - A similar product name gets 409 `PRODUCT_NAME_CONFIRMATION_REQUIRED`; retry with `confirmSimilarName: true`.
  - Batch codes `BATCH-YYYYMMDD-NNN` are generated by the backend, so clients must not send `batchCode`.
- **Money:** VND. Display it as whole đồng; it is stored with 2 decimals.
- **Time:** instants are stored in UTC, and Store-local dates use `Asia/Ho_Chi_Minh`.

## 5. Known open issues

Do not "fix" these silently. Each needs its own task.

- The demo seed `scripts/seed/seed_demo.py` fails on the 2026-11-19 F02 rules (old `FOODMART-xxxx` SKUs, near-identical names, `batchCode`). The fix is deferred by the owner.
- `priorityCoverage` is decided (DEC-20261117-02) but not coded. The API returns `null`.
- F09 Storefront, F10 Bundle Cart and F11 Dashboard are PLANNED. F09/F10 follow the MVP plans (DEC-20261117-03). F11 has an MVP scope only.

## 6. When you finish

State what you changed, the evidence (commands and counts, or `NOT RUN`), and what is still open.

If your change alters a fact in `docs/product/*`, update that file in the same PR, together with the feature contract and a record-of-changes entry.

# F03 Swagger Test Cases — confirmed decisions 2026-10-17

No result below is PASS until executed and recorded in F03_SWAGGER_TEST_REPORT.md. Use isolated H2, explicit dev fixtures, two Stores, Store Admin, Merchandiser and System Admin. Redact credentials and tokens. All successful writes require a follow-up GET.

| ID | Case | Expected |
|---|---|---|
| CFG-01 | Read initial policy | 200, min=0/max=0.5/version=1 |
| CFG-02 | System Admin changes policy then GET | 200; new bounds and incremented version persist |
| CFG-03 | Negative min / max below min | 400; old policy preserved |
| CFG-04 | Merchandiser or Store Admin updates policy | 403 |
| CFG-05 | Store Admin sets T=45 then GET | 200; T=45, weights unchanged |
| CFG-06 | T=0/-1/fraction/null/string; Merchandiser edits T | 400 for invalid input, 403 for wrong role |
| CFG-07 | Merchandiser config weights/lambda then GET | 200; values persist and T preserved |
| CFG-08 | Coefficient out of range / enabled sum not 1 / negative lambda | 400, no normalization |
| CFG-09 | Merchandiser forges bounds/T/storeId in config | 400; no privilege bypass |
| CFG-10 | Tighten bounds outside selected lambda | config GET flags invalid; new snapshot/preview 409; no clamp |
| CFG-11 | Correct lambda to permitted value | New preview/snapshot accepted |
| SCR-01 | PATCH S=.8; PATCH M=.9; GET | Both persist; omitted score and inventory unchanged |
| SCR-02 | Score -0.1/1.1/null/empty body | 400; old values preserved |
| SCR-03 | Cross-store score GET/PATCH | 404; no changes |
| CALC-01 | All criteria disabled | W=1, weighted=base |
| CALC-02 | Only stock / expiry / season enabled | Enabled coefficient=1, correct contribution |
| CALC-03 | Base=12000 S=.8 E=.6 M=.9, coefficients .5/.2/.3 lambda=.5 | W=1.395; weighted=16740 |
| EXP-01 | 20 units +5 days, 80 units +40 days, T=30 | E=0.166667 |
| EXP-02 | Blocked/expired/today/zero-remaining batches | Excluded from weighted eligible mean |
| EXP-03 | requiresExpiry=false | NOT_APPLICABLE, E=0 |
| EXP-04 | No eligible quantity | NO_SELLABLE_BATCH, E=0; historical rows retained |
| EXP-05 | Missing required expiry | Preview warning; expiry-enabled snapshot rejected |
| UTIL-01 | Historical quantity=3/price=10000/cost=6000 | Base=12000 despite current Product prices |
| UTIL-02 | Negative/missing/duplicate prepared source item | Clear 400; no silent conversion |
| UTIL-03 | Same source/date/config | Stable ordering, source/matrix hashes and totals |
| UTIL-04 | Invalid date range/pagination | 400 |
| SNAP-01 | Preview then create/get metadata/get matrix | Identical row values, totals; 201 persisted snapshot |
| SNAP-02 | Change T, bounds, selected lambda, scores and batches | Existing snapshot/config/hash/rows unchanged |
| SNAP-03 | Tighten bounds then snapshot with stale lambda | 409; no partial snapshot/items |
| SNAP-04 | Matrix totals/quantity/mapping/rounding | TU=sum rounded item utilities; algorithmItemId=productId |
| SNAP-05 | Cross-store snapshot metadata/matrix | 404 |
| SEC-01 | No token / invalid token | 401 |
| SEC-02 | Wrong role for each configuration operation | 403 |
| DEP-01 | F04 absent outside fixture profiles | 503, no fake production data |
| DB-01 | Flyway V4 fresh and second startup | Migration and validation succeed |
| DB-02 | Invalid score/check/unique matrix rows | Database rejects invalid writes |

Execution completed on 2026-10-17. See F03_SWAGGER_TEST_REPORT.md for actual results, methods and integration blockers. Designed groups include automated DB/math checks; do not interpret all36 as exclusively UI.

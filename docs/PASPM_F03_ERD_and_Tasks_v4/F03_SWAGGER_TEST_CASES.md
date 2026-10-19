# F03 Swagger Test Cases — confirmed decisions 2026-10-17

## Current v1.2 acceptance cases

Use Product T, not Store window. Current full contract: ../api/F03_UTILITY_API.md; execution status: ../requirements/F03_WEIGHT_RULES_v1_2_VERIFICATION.md.

| ID | Scenario | Expected |
|---|---|---|
| V12-01 | Store Admin Product POST/PUT T7, then GET | 201/200; persisted7, stock unchanged |
| V12-02 | T absent/null; negative/fraction/string/boolean | effective0; invalid400 |
| V12-03 | Merchandiser Product write; foreign Store write | 403;404 |
| V12-04 | Official historical qty3/price10000/cost6000, S.8/M.9, .5/.2/.3, lambda.5; eligible +5/+40 days | T7→E1/W1.435/17220; T3/0→E0/W1.335/16020 |
| V12-05 | D1/T/T+1/today, blocked/empty/expired, FEFO differs from received order | inclusive1..T only, E independent of quantity ratios |
| V12-06 | Missing metadata, no eligible inventory, requiresExpiry=false | explicit statuses/warnings; enabled missing blocks READY |
| V12-07 | Change deprecated Store T with Product T0 | no Store fallback, E0 |
| V12-08 | Snapshot create/read/matrix; mutate threshold/batches/config afterward | original evidence, scores, hash and utility unchanged |
| V12-09 | Read stored old F03_LINEAR_V1 row after migration | original decimal E/utility/config; null new evidence |
| V12-10 | Independent push query with no transactions/mining | known eligible batches, exact quantities, FEFO; own Store only |
| V12-11 | No/invalid token, System Admin/End Customer push query, forged Store |401,403,400 or no client Store selection |
| V12-12 | Runtime OpenAPI schemas and deprecated operation | Product integer T, Evidence/PushPage, legacy PUT deprecated |

Automated MockMvc/calculator results are separate from interactive Swagger. The earlier cases below are historical linear-v4 references and must not drive new v1.2 calculations.


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
| WGT-01 | Three enabled criteria, T=30; batches 20 at +5d, 80 at +40d, 10 expired; S=.8/M=.9; alpha=.5/beta=.2/gamma=.3/lambda=.5; historical qty=3/price=10000/cost=6000 | Preview 200/ready; E=.166666666666666667; Base=12000; W=1.351666666666666667; weighted/total=16220 |
| WGT-02 | Same fixture/config as WGT-01, Store Admin changes T to 10 | Preview 200/ready; E=.100000000000000000; Base remains 12000; W=1.345000000000000000; weighted/total=16140 |
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
| SNAP-06 | Capture snapshot with S=.8/M=.9/T=30/lambda=.5, then change both scores, T and policy; update lambda within new bounds and create another snapshot | First snapshot keeps original scores/config/row values; second snapshot uses updated S/M/T/lambda and recomputed Weight/utility |
| SEC-01 | No token / invalid token | 401 |
| SEC-02 | Wrong role for each configuration operation | 403 |
| DEP-01 | F04 absent outside fixture profiles | 503, no fake production data |
| DB-01 | Flyway V4 fresh and second startup | Migration and validation succeed |
| DB-02 | Invalid score/check/unique matrix rows | Database rejects invalid writes |

Execution history for the existing cases completed on 2026-10-17. WGT-01, WGT-02 and expanded SNAP-06 were added on 2026-10-19 and verified through Maven MockMvc/integration tests; they are not new interactive Swagger UI executions. See F03_SWAGGER_TEST_REPORT.md for actual results, methods and integration blockers. Designed groups include automated DB/math checks; do not interpret all cases as exclusively UI.

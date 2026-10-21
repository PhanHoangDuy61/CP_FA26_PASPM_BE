# JPA Pilot — Source Read-Only & Simulated Food-Store Verification

> Latest verification status: see §6 below. **REAL_DATA_BLOCKED; SWAGGER_UI_TESTED18/18; fresh Maven274/274.** Earlier sections preserve the historical pilot run.


Ngày thực thi: **2026-10-22, Asia/Ho_Chi_Minh**. Branch `Khoi/Fear/jpa-product-metadata-pilot`; base `3d58a73`. Scope mới nhất: người dùng xác nhận chưa có actual populated catalog và **duyệt simulated food-store database riêng** để thực thi CORE-00 + F02-01. Không gọi dữ liệu mô phỏng là dữ liệu thật.

## 1. Database đang cấu hình — READ ONLY

Đọc cấu hình bằng program, không in credential/URL. Trên dedicated read-only session của connection được môi trường hiện hữu cho phép, chạy SELECT metadata/counts/history; không start SpringApplication/Flyway trên nguồn. Credential nguồn không phải account read-only chuyên dụng: session Connection.readOnly=true, transaction repeatable-read; SQL helper nguồn chỉ SELECT rồi rollback/end session. Không INSERT/UPDATE/DDL/repair/baseline/clean vào nguồn.

MySQL8.4.11. Flyway history **V1–V5 success**, checksums đối chiếu; Store0, Category0, Product0, InventoryBatch0, UtilitySnapshot0, MatrixItem0. F04/F05/F06 tables chưa có. Product schema thiếu `expiry_push_days` do V8 chưa apply. Vì vậy không chạy app JPA mới vào source; không tự thêm cột hoặc nâng V6–V10.

Repo có V1–V10; toàn bộ migration hashes giữ nguyên đầu/cuối task. Source SELECT probe trước/sau giống nhau cả columns/counts/Flyway versions/checksums. Private application.yml cũng giữ nguyên SHA256; không stage secrets. [Persistent sanitized evidence](evidence/jpa_pilot/JPA_PILOT_RESULTS.json).

## 2. Isolated copy và dữ liệu mô phỏng

Target trên **local loopback**, schema alias **dedicated disposable JPA pilot schema**; tên thật/connection string/credential không xuất trong docs. Tạo credential riêng chỉ có quyền trên schema riêng. Private target properties ở `/tmp/paspm-jpa-pilot/clone.properties`, permission0600, không đưa Git.

Quy trình đã chạy: tạo reference schema V5 tương ứng source → SELECT catalog Store/Category/Product/Batch để copy → **0 rows được copy vì source rỗng** → migrate target riêng V6–V10 → validate10migrations. Không copy auth user, password/hash, OTP/challenge, external identity hay transaction customer information. Copy utility có quy tắc pseudonymize Store/category/product names, SKU/batch identifiers; lần này không có actual row để anonymize. Không có production PII bị đưa vào target.

Sau đó API tests seed synthetic StoreA/B, roles test và food products **milk, bread, coffee**. Mỗi fixture có tên/UUID nhận biết simulation, email chỉ `example.test`, không real credential. Food batches chứa future/expired/blocked cases theo Store local current date. Những dòng mới này cố ý tồn tại trên disposable target; không restore/import về nguồn. Regular regression dùng MySqlTestDatabase schemas khác, tạo mới độc lập mỗi suite.

Trước DML: yêu cầu `PASPM_JPA_PILOT_MUTATIONS_ALLOWED=true`, explicit opt-in `-Dpaspm.test.jpa-clone=true`, marker disposable, schema/nonce pattern, loopback host, target≠source, username theo nonce; kiểm actual DATABASE()/CURRENT_USER() và SHOW GRANTS. Refuse global/outside-schema grants. Không có fallback vào app/source config. Safety tests xác nhận reject missing consent, source collision, remote/unmarked targets.

## 3. Ma trận thực thi

| Group | Status | Evidence / actual behavior |
|---|---|---|
| Source connectivity/history/columns/counts | PASS — READ ONLY | V5 success, catalog rỗng, missing V8 field; không coi nguồn tương thích JPA V10 |
| Source preservation | PASS | Before/after aggregate metadata/Flyway checksums bằng nhau; 10 SQL files + private config SHA256 unchanged |
| Existing real-product parity/write tests | NOT RUN | Không có actual Product/Category rows. Người dùng đã duyệt simulation thay thế, không giấu coverage limitation |
| Disposable copy/target-only upgrade | PASS | Empty-source copy, V5→V10 trên target riêng, dedicated credential/guard |
| Clone target safety | PASS — 4 tests | Reject unsafe flags/source/remote/unmarked; actual grants confined |
| Simulated Product API + food stock | PASS — 5 tests | CRUD/status/search/errors/RBAC/scope, F03 S/M preserved, batch/summary invariants |
| Focused Product persistence H2 | PASS —16tests | Metadata mapping/writer, flush/native stale refresh, commit/rollback, concurrency, FK |
| Focused Product persistence fresh MySQL | PASS —17tests | Inherits16 + independent RR snapshot read proof |
| Full existing F01–F06 | PASS —232tests | Same suite names/counts retained, auth test setup now full schema |
| Final Maven | PASS —274tests | errors0/failures0/skipped0, BUILD SUCCESS04:35:29+07:00 |
| OpenAPI | PASS |59paths exact equality except runtime servers; SHA256 evidence |
| Baseline JDBC vs JPA API | PASS —9scenarios | Exact statuses/normalized JSON equal, actual original JDBC artifact used |
| Minimal latency/query smoke | EXECUTED |25GET measured+5warmup each, ORM metadata update3statements; no performance superiority claim |
| Interactive Swagger UI / Postman / Frontend | NOT RUN | Không thuộc persistence verification, không claim đã chạy |
| Source migration/repair | NOT RUN / NOT AUTHORIZED | Source remainsV5; only disposable target upgraded |

Food fixture: initial total33, sellable24, expired4, blocked5, soon14; after quantity8→3: total28, sellable19, expired4, blocked5, soon9. Product metadata price/name edit giữa hai lần summary không làm thay inventory. S/M update via existing Merch API followed metadata/status writes giữ decimals chính xác; source histórico/snapshot tables không bị sửa.

## 4. Commands và log paths

Baseline: `/tmp/paspm-jpa-pilot/baseline-verify.log`, saved baseline reports `baseline-surefire/` (232 tests). Final `/tmp/paspm-jpa-pilot/final-verify-food.log` (274 tests); command trong [CORE report](../architecture/MIG_JPA_CORE_00_IMPLEMENTATION_REPORT.md). Opt-in target properties path không chứa secret trong command.

Additional logs dưới `/tmp/paspm-jpa-pilot/`:

- `source-before.json`, `source-after.json`, `source-preservation.json`: sanitized source proof.
- `clone-provisioning.log` / `clone-provisioning.json`: isolated copy + upgrade, zero actual rows copied.
- `product-focused-mysql-final.log`: intermediate30tests; final full run bổ sung FK/RR thành33 focused tests, không cộng hai run làm unique-test total.
- `clone-api-tests.log`: initial context failure do missing test avatar directory; `clone-api-retest.log`: fixed8tests pass; final food run có9 clone/safety tests.
- `before-openapi.log`, `after-openapi.log`; `before-product-contract.log`, `after-product-contract.log`.
- `effective-pom-resolved.log` / `effective-pom.xml`: resolved Boot3.5.7-managed dependencies.

Raw logs có thể chứa test URLs/SQL nên không copy wholesale vào Git. Sanitized long-lived evidence trong `docs/testing/evidence/jpa_pilot/`, gồm per-suite result totals, schema preservation booleans, source aggregate-only counts, API response comparisons và OpenAPI hashes. Không password, JWT hoặc customer data.

## 5. Readiness và giới hạn

Approved simulation scope đã PASS; không có FAIL chưa xử lý. Real populated-dataset test không được giả là PASS. Chưa deploy/source-migrate/merge/push; source V5 cần upgrade/backup decision riêng trước khi chạy app mới. Không mở rộng sang Category/User/Utility persistence. Flyway warning MySQL8.4 vs tested8.1 vẫn tồn tại và được ghi rõ; không nâng dependency ngoài JPA starter đã duyệt.

---

## 6. Fresh verification / PR preparation — 2026-10-22, latest run

This section supersedes the historical execution-status statements above for the new verification task. Implementation remains `48bc059`; it was **not implemented again**.

| Status label | Latest result |
|---|---|
| SIMULATED_DATA_TESTED | PASS — fresh full regression and actual Swagger UI |
| REAL_DATA_TESTED | NOT RUN — no genuine populated source copy available |
| REAL_DATA_BLOCKED | BLOCKED — missing genuine catalog data/provenance or approved sanitized backup |
| SWAGGER_UI_TESTED | PASS —18executed UI scenarios, no failures |
| HTTP_API_TESTED | PASS — fresh automated HTTP/API integration + UI requests |
| Source migration/repair/deployment | NOT RUN / NOT AUTHORIZED |

### Source discovery and environment versions

Configured source again inspected **READ ONLY**: MySQL8.4.11, V1–V5 success, Store0/Category0/Product0/InventoryBatch0; F04–F06 tables absent. Source still lacks V8 Product expiry_push_days. Connection/session is read-only and helper statements SELECT-only; no application startup or Flyway execution against the source.

The only accessible non-disposable candidate with Product+Category is the configured empty source. Known automated/disposable schemas were excluded from genuine-source consideration (56 at discovery time; their existence is not evidence of real retail data). Searched project workspace, Downloads and Documents for SQL/dump/backup candidates; the11matching SQL files were the10repo migrations plus historical test V7, not an authorized genuine-data backup. No genuine populated source or verified dump was located within inspected locations.

No staging/production profile was identified in repo configuration, and the source environment tier is undocumented; do not call it production or staging merely from a connection string. Two older temporary local QA configs were probed SELECT-only but unavailable with SQL state42000, so their versions remain unverified. They were not used as test sources. The active guarded disposable target is **V10**.

[Source discovery](evidence/jpa_pilot_review/source-discovery.json), [QA probes](evidence/jpa_pilot_review/configured-qa-probes.json), [source before](evidence/jpa_pilot_review/source-before.json), [source after](evidence/jpa_pilot_review/source-after.json). Source before/after columns/counts/Flyway versions/checksums are identical. Private application.yml and all10migration SHA256 values remain unchanged; no .env/credential/dump copied to Git.

### Current target and data provenance

Target uses local loopback with a dedicated schema-limited credential and explicit disposable marker; actual schema/user/connection values are redacted. It remains the isolated **empty-source copy + SIMULATED food fixtures**, not a genuine populated copy. No real customer/account/OTP/secrets were copied. New UI accounts use example.test and generated test-only credentials stored privately with0600permissions, never in reports.

Fresh post-UI target aggregate counts: **Store42, Category37, Product26, InventoryBatch15**. Snapshot/matrix/sales/mining-request/mining-run/recommendation counts are0. Failed insert identity gaps are normal; Product ID is not a row count. Test fixtures from prior/current runs intentionally remain on this disposable schema. They are not original retail records.

Independent SELECT confirms the UI fixture Product keeps id31, correct Store/category, original SKU, final ACTIVE, edited sellingPrice32000, **S0.8/M0.9 and stock8**. Product created through Swagger has id32 and is read back with the same SKU/metadata. No UI action changes batch quantity/status after initial synthetic setup. Product schema has no timestamp fields; no imaginary createdAt/updatedAt assertion is reported. Nonempty historical-snapshot immutability is verified using regular regression fixtures; target history0 is not equivalent to genuine history coverage.

[Target SQL proof](evidence/jpa_pilot_review/ui-database-proof.json). Record IDs/counts are local-run observations, not fixed API constants.

### Fresh test results

| Test group / gate | PASS | FAIL | BLOCKED | NOT RUN |
|---|---:|---:|---:|---:|
| Existing F01–F06 + deployment regression |232|0|0|0|
| Focused JPA H2 |16|0|0|0|
| Focused JPA MySQL fixtures |17|0|0|0|
| Guarded simulated clone API |5|0|0|0|
| Disposable-target safety |4|0|0|0|
| Actual Swagger UI |18|0|0|0|
| Genuine-data verification gate |0|0|1|0|
| Stale temporary QA environment probes |0|0|2|0|

**Maven total274 PASS, failures0/errors0/skipped0**, BUILD SUCCESS **2026-10-22T05:03:18+07:00**, fresh command:

```text
JAVA_HOME=<JDK17> PASPM_JPA_PILOT_MUTATIONS_ALLOWED=true
PASPM_MINING_SPMF_JAR=<existing-jar> PASPM_MINING_JAVA_EXECUTABLE=<JDK25-java>
mvn -o -Dpaspm.avatar.directory=<temporary-directory>
-Dpaspm.test.mysql=true -Dpaspm.test.jpa-clone=true
-Dpaspm.test.java17-backend=true -Dpaspm.test.java17=<JDK17-java>
-Dpaspm.test.spmf-jar=<existing-jar> -Dpaspm.test.spmf-java=<JDK25-java> clean verify
```

Raw log `/tmp/paspm-jpa-review/verify.log`; [fresh per-suite counts](evidence/jpa_pilot_review/fresh-test-results.json). Original232cases remain present with same suite counts. Coverage by existing feature namespace: F01=25, F02=8, F03=60, F04=25, F05=49, F06=63, deployment=2. These are not an additional test total and include H2/MySQL fixtures as described by each suite. New42=H2JPA16+MySQLJPA17+cloneAPI5+safety4. Shared DataSource/JpaTransactionManager/connection, mixed commit/rollback, refresh, concurrency, RC/RR and F04 REQUIRES_NEW proofs all ran. HUI-Miner/EFIM used the actual configured SPMF JAR with child Java25; Backend stayed Java17.

UI cases are separate from JUnit274 and were genuinely executed using Authorize → Try it out → Execute. Runtime OpenAPI freshly confirms59paths and equality to the original JDBC baseline except server URL. [Swagger report](JPA_PILOT_SWAGGER_VERIFICATION.md), [UI case results](evidence/jpa_pilot_review/SWAGGER_RESULTS.json), [OpenAPI proof](evidence/jpa_pilot_review/OPENAPI_RUNTIME_RESULT.json).

### Readiness and outstanding work

No failed fresh test or actionable code-review defect remains. **Ready for human PR review with REAL_DATA_BLOCKED disclosed**, not unconditional genuine-data/deployment acceptance. Missing genuine data, sourceV5 compatibility and two stale QA configs remain explicit limits. Actual Swagger required scenarios are executed; no required UI scenario remains NOT RUN. Source migrations/repair, remote PR/push/merge/deploy, live Google identity testing and new load benchmarks were not performed.

Local PR description prepared in [PR readiness](../architecture/JPA_PILOT_PR_READINESS.md); [fresh code review](../architecture/JPA_PILOT_CODE_REVIEW.md). Do not start Category migration automatically. First review/integrate the current pilot and obtain a decision accepting or resolving the genuine-data limit; source migration needs a separately approved backup/upgrade plan.

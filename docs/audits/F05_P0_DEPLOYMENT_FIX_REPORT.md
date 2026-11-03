<!-- docs-reorganization:notice -->
> **HISTORICAL — documentation review 2026-10-26.** This P0 execution report is preserved; current acceptance and source-database recovery are separate. See [current navigation](README.md). Original results and business-rule text below are retained; this notice is not a new execution or approval.
<!-- /docs-reorganization:notice -->

# F05 P0 Deployment Fix Report

Ngày kiểm chứng: 2026-10-20, Asia/Ho_Chi_Minh. Branch: Khoi/Fear/F05-high-utility-mining-engine, nền commit 19f94be. Phạm vi: P0-01 Flyway V7 và P0-02 runtime SPMF.

## A. Kết luận

**Hai lỗi P0 đã được sửa trong code và kiểm chứng trên môi trường cô lập.**

- MySQL 8.4.11 đã migrate thành công từ schema trống qua đủ V1–V9, khởi động backend và thực hiện các API F04/F05.
- Backend được clean build, chạy test và khởi động HTTP bằng **Java 17.0.20.1**; SPMF chạy bằng executable **Java 25.0.1 riêng**, lấy qua biến môi trường.
- HUI-Miner và EFIM thật đều trả 8 patterns với utility tương đương tại minUtil 30; backend HTTP lưu 16 patterns vào MySQL và trả comparison=MATCH.
- Lượt cuối: **147 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**, hoàn tất 13:37:58 +07:00.
- Có quy trình đối soát lịch sử V7. **Chưa repair hoặc thay đổi database triển khai nào.** Việc rollout vào database đã áp dụng checksum V7 cũ cần kiểm tra và duyệt riêng cho môi trường đó.
- Windows/Linux và một máy hoàn toàn sạch chưa được thực thi; không coi tài liệu setup là bằng chứng đã cài trên tất cả nền tảng.

## B. Điều tra và nguyên nhân

### P0-01: MySQL V7

Tái hiện bằng TransactionImportMySqlIntegrationTest trên schema mới paspm_p0_test_9acdd14a17a24163af1e4c288b7c9534:

- MySQL 8.4.11, Flyway Community 11.7.2.
- Câu lệnh lỗi: CREATE TABLE import_staging_row, tại row_number INT NOT NULL.
- SQLState 42000, MySQL error 1064.
- History: V1–V6 success=true; V7 success=false.
- Do MySQL DDL không transactional, api_connection, import_batch, sales_transaction và transaction_item đã tồn tại; import_staging_row và các DDL còn lại của V7 chưa hoàn tất.

Không chỉ migration: SQL INSERT/UPDATE của TransactionImportRepository cũng sử dụng tên cột này chưa quote. Cần sửa cả hai để startup và import hoạt động trên MySQL.

CodeGraph được dùng để lần theo TransactionImportRepository, MiningRepository, MiningWorker, SpmfMiningEngine và SpmfRuntime. Các câu SQL còn lại được rà trực tiếp. Không có AGENTS.md trong phạm vi repo/parent được kiểm tra. Không thấy CI/Docker hiện hữu cần cập nhật.

### P0-02: SPMF

Artifact thật ở thư mục spmf_run bên cạnh BE; không thuộc Maven và không được commit/bundled. Đã tính lại hash:

    SPMF_2.67a
    SHA-256 4aa468384693b2016b457ea310a08cc24aa83abd42d3169ad65b9027778dfe6a

Main, AlgoHUIMiner và AlgoEFIM trong JAR có class major 69. Executable Java 17 không chạy được binary này. Trước bản sửa, requireAvailable chỉ kiểm tra đường dẫn/hash và executable tồn tại, chưa kiểm tra Java major/entrypoint nên có thể nhận request rồi mới thất bại ở worker.

JAR giữ nguyên. Phương thức tích hợp ProcessBuilder và lựa chọn HUI-Miner/EFIM giữ nguyên. Nguồn tải được README cục bộ ghi là trang SPMF chính thức; tính xác thực upstream không được suy ra chỉ từ pin nội bộ. Giấy phép SPMF GPLv3 được dẫn trong hướng dẫn setup.

## C. Chiến lược Flyway đã thực hiện

### Sửa tối thiểu

V7 chỉ thêm backtick quanh row_number tại 5 vị trí: khai báo cột, unique constraint, check constraint và hai index. Không đổi tên cột, kiểu dữ liệu hay ý nghĩa constraint.

F04 JDBC INSERT/UPDATE/SELECT/ORDER BY quote cùng identifier; nhãn đọc ResultSet vẫn là row_number. Không đổi nghiệp vụ CSV, trạng thái import hoặc Store scope.

Người dùng duyệt patch và quy trình checksum ngày 2026-10-20 sau khi được cung cấp kết quả thử cô lập. Bộ duyệt tự động ban đầu chặn sửa V7 do thiếu phê duyệt đối soát; patch được áp dụng sau phê duyệt rõ ràng.

### Fresh schema

V1→V9 chạy bình thường với V7 đã sửa; không thêm V10, baselineOnMigrate hoặc startup repair. Test trên schema mới xác nhận schema F04/F05 và thao tác thật.

### Existing schema

Checksum V7:
- Cũ: -1762009830.
- Mới: -1490036030.

FlywayRecoveryTest tạo lịch sử H2 thật bằng V7 nguyên bản trong test resource. Khi chuyển sang file đã sửa, validation bị từ chối vì checksum. Test chỉ thực hiện repair trên chính H2 in-memory đó, xác nhận checksum đổi, staging row còn nguyên và V8/V9 tiếp tục migrate.

Test MySQL riêng bắt đầu từ V7 đã sửa, có dữ liệu import/staging, rồi upgrade V8/V9: chỉ 2 migration chạy, dữ liệu giữ nguyên và validate thành công. Đây không phải bằng chứng đã repair database MySQL triển khai mang checksum cũ.

Bản sao V7 lịch sử chỉ dùng cho regression test, không nằm trong đường migration production.

Option B baseline script đã được đánh giá: Flyway hiện có hỗ trợ baseline migration, nhưng cần lặp lại toàn bộ schema/seed và đi lệch yêu cầu fresh V1→V9. Option A nhỏ hơn và đã được chọn. V10 riêng không giải quyết lỗi trước V7.

Quy trình backup, schema preflight, đối soát, approval, repair, validate và rollback: [FLYWAY_MYSQL_V7_RECOVERY.md](../setup/FLYWAY_MYSQL_V7_RECOVERY.md). Partial-failure MySQL cần kế hoạch riêng; repair không tự hoàn tác DDL đã chạy.

## D. Runtime SPMF sau sửa

SpmfRuntime kiểm tra trước khi MiningService nhận job và trước thực thi:

1. JAR có cấu hình, là file tuyệt đối tồn tại, đọc được và đúng SHA-256 pin.
2. Java là executable tuyệt đối tồn tại.
3. Java thực sự chạy được và báo major >=25.
4. Chạy entrypoint thật SPMF version, yêu cầu banner 2.67a.
5. Thư mục tạm tạo/ghi/xóa được.

Hai property mặc định rỗng, không tự dùng Java 17 của backend:
- paspm.mining.spmf-jar / PASPM_MINING_SPMF_JAR.
- paspm.mining.java-executable / PASPM_MINING_JAVA_EXECUTABLE.

Không cần sửa application.yml cá nhân để dùng cấu hình này. Test HTTP cuối lấy hai đường dẫn từ **biến môi trường thật**, không từ DynamicPropertySource; DynamicPropertySource chỉ tạo database test.

Version probe cache tối đa 30 giây. Mỗi lần admission/execution vẫn hash lại JAR và Java executable, gồm real path trong khóa cache; thay đổi nội dung cùng size/timestamp cũng bị phát hiện. Mỗi probe giới hạn 5 giây/64 KiB log, có kill và cleanup. Mining giữ nguyên heap, timeout, output limits, concurrency/queue và temp policy.

Lỗi readiness trả HTTP503 theo envelope hiện hữu, ví dụ:

~~~json
{"error":"SPMF_JAVA_VERSION_UNSUPPORTED: The approved SPMF JAR requires a Java 25+ child runtime; the backend may remain on Java 17"}
~~~

Các mã: SPMF_JAR_NOT_CONFIGURED, SPMF_JAR_NOT_FOUND, SPMF_JAR_NOT_READABLE, SPMF_JAR_HASH_MISMATCH, SPMF_JAVA_NOT_FOUND, SPMF_JAVA_VERSION_UNSUPPORTED, SPMF_RUNTIME_UNAVAILABLE.

Thông báo không trả đường dẫn máy chủ. Không có probe bắt buộc lúc startup nên F01/F02/F03/F04 vẫn có thể khởi động khi F05 chưa cấu hình. Historical request/run read vẫn hoạt động. Lỗi runtime sau admission được worker ghi nhận là thất bại theo cơ chế hiện hữu.

Hướng dẫn macOS/Linux/Windows, hash/license, môi trường, startup, workflow và xử lý lỗi: [F05_SPMF_RUNTIME_SETUP.md](../setup/F05_SPMF_RUNTIME_SETUP.md).

## E. Kiểm chứng và bằng chứng

Lệnh cuối chạy từ BE, JAVA_HOME chọn JDK17, hai PASPM_MINING_* được export tới JAR và Java25 đã kiểm chứng:

~~~sh
mvn -Dpaspm.avatar.directory=/path/to/test-avatars \
  -Dpaspm.test.mysql=true -Dpaspm.test.java17-backend=true \
  -Dpaspm.test.java17=/path/to/jdk17/bin/java \
  -Dpaspm.test.spmf-jar=/path/to/spmf-2.67a.jar \
  -Dpaspm.test.spmf-java=/path/to/jdk25/bin/java clean verify
~~~

Đã thực thi thực tế bằng đường dẫn runtime của workstation; không dùng các placeholder trên để tạo kết quả.

| Khu vực | Kết quả thực thi |
|---|---|
| Tái hiện V7 trước sửa | FAIL đúng nguyên nhân 1064/42000; history V7 failed, partial DDL được kiểm tra |
| Bản SQL đề xuất trong /tmp | MySQL V1–V9 PASS trước khi sửa repo; F04 context ban đầu còn thiếu cấu hình avatar test, đã bổ sung property test và chạy lại |
| FLY-01 fresh MySQL V1–V9 + startup | PASS; các schema mới, không baseline |
| FLY-02 MySQL V7→V9 có data | PASS; chạy đúng 2 migration, giữ staging row |
| FLY-03 checksum mismatch/repair | PASS trên H2 in-memory có history nguyên bản; không chạy repair deployed MySQL |
| FLY-04 F04 regression MySQL | 6/6 PASS; CSV/import/status/error rows/Store rules |
| FLY-05 F05 regression MySQL | 16/16 PASS; persistence, atomicity, roles, Store FKs, history, runtime503 |
| FLY-06 H2 regression | PASS trong full suite; V1–V9 |
| Backend Java17 | PASS: Corretto17.0.20.1, SpringBoot3.5.7, actual random-port HTTP server |
| Child SPMF Java25 | PASS: Homebrew25.0.1, pinned JAR, both algorithms |
| Runtime failure tests | 8/8 PASS, gồm actual Java17 rejection, missing/wrong/unreadable JAR, missing Java, entrypoint/exit failure, cache changes, probe timeout/cleanup |
| Real algorithms + parser | 5/5 PASS; same input, 8 canonical itemsets/utilities at minUtil30; empty/zero and mining timeout cases |
| Real Java17 HTTP→MySQL→SPMF | 1/1 PASS: F03 snapshot, POST202, polling, MATCH, 8+8 patterns, SQL count16, OpenAPI200, missing token401 |
| OpenAPI/Store isolation | PASS trong F05 suites; endpoint/DTO schema vẫn giữ nguyên |
| Maven clean verify cuối | 147 tests, 0 failures/errors/skips; BUILD SUCCESS, 49.453s, 13:37:58 +07:00 |
| Windows/Linux execution | NOT RUN; hướng dẫn đã review |
| Pristine clean-machine install | NOT RUN; kiểm chứng provisioning tách runtime trên workstation |
| Deployed database repair | NOT RUN; cần inventory/approval riêng |
| Interactive Swagger UI lần này | NOT RUN; HTTP/OpenAPI tự động đã thực thi; không gán kết quả UI mới |

Test-first: MySQL test trước patch thất bại V7; bộ preflight mới trước implementation có 5 failure/6 test đúng hành vi còn thiếu. Sau sửa, focused suite 37 test PASS rồi full Java17/MySQL suite PASS.

JDK17 được tải tạm từ Amazon Corretto theo phê duyệt của người dùng, xác minh SHA256 công bố rồi giải nén /tmp. API Temurin ban đầu trả403; không dùng tải không kiểm chứng để thay thế. Không cài JDK toàn hệ thống hoặc commit binary. File class backend mới là major61; JAR SPMF giữ major69.

Các schema của lượt cuối được giữ lại để xem bằng chứng:
- F04: paspm_p0_test_cd83cc5458464a869f36dccb8106d3bd.
- HTTP: paspm_p0_test_39b16fdfd5e0462a9b8a0d44039aa874.
- F05: paspm_p0_test_0853bb64da0b46fe8bba8303fb4f3554.
- Upgrade: paspm_p0_test_b8c965a9faf94610a29addb125b58d11.

Bằng chứng an toàn, không chứa credential/token:
- [verification-summary.json](evidence/f05-p0/verification-summary.json).
- [verification-events.txt](evidence/f05-p0/verification-events.txt).
- [mysql-readback.txt](evidence/f05-p0/mysql-readback.txt).
- [review.txt](evidence/f05-p0/review.txt).

## F. Danh sách thay đổi

Source/migration:
- src/main/resources/db/migration/V7__f04_transaction_import_pipeline.sql: quote 5 identifier occurrences.
- src/main/java/vn/edu/fpt/paspm/transaction/importing/TransactionImportRepository.java: quote identifier trong SQL.
- src/main/java/vn/edu/fpt/paspm/mining/SpmfRuntime.java: preflight/cache/diagnostics/temp cleanup.
- src/main/java/vn/edu/fpt/paspm/mining/SpmfMiningEngine.java: dùng preflight, yêu cầu Java child explicit; giữ mining command/parser.

Tests:
- transaction/TransactionImportIntegrationTest.java: quote SQL assertion.
- transaction/TransactionImportMySqlIntegrationTest.java: chạy lại F04 suite trên MySQL.
- mining/MiningIntegrationTest.java: regression503/history và test runtime property overrides.
- mining/SpmfMiningEngineTest.java: kỳ vọng lỗi preflight sớm và independent test runtime paths.
- mining/SpmfRuntimeTest.java: 8 failure/readiness/cache/cleanup tests.
- mining/MiningMySqlIntegrationTest.java: chạy F05 suite trên MySQL.
- mining/MiningHttpMySqlTest.java: HTTP Java17 + MySQL + Java25 qua env thật.
- deployment/MySqlTestDatabase.java: tạo schema local riêng; không xóa/reuse.
- deployment/FlywayRecoveryTest.java: upgrade MySQL và checksum/H2 data preservation.
- src/test/resources/db/historical/V7__f04_transaction_import_pipeline.sql: snapshot V7 nguyên bản cho checksum regression.

Documentation:
- Hai setup guide nêu trên, báo cáo này và evidence.
- docs/api/F05_MINING_API.md, docs/frontend_handoff/F05_HANDOFF.md, docs/api/API_CHANGELOG.md: sửa deployment guidance/default/error diagnostic.
- docs/superpowers/plans/2026-10-20-f05-p0-deployment.md: checklist thực hiện.

application.yml đang có thay đổi cá nhân từ trước task được giữ nguyên và không nằm trong bản sửa này. Các tài liệu F03/file tạm chưa tracked sẵn có vẫn giữ nguyên.

## G. Regression và giới hạn

Không thay đổi F03 Weight/Expiry, Utility Snapshot semantics, MiningInputBuilder, scaling/GCD/minUtil, pattern persistence schema, thuật toán, F06 hay FE. Request/response DTO và các endpoint không đổi; chỉ bổ sung nội dung error503 và thay default Java child thành cấu hình bắt buộc.

Superpowers systematic-debugging/TDD/planning/verification và CodeGraph đã được dùng. Review độc lập qua subagent theo requesting-code-review không tìm thấy finding P0–P3; reviewer không chạy test song song. Các test/evidence trên do agent chính chạy.

Code fix và kiểm chứng local đã xong. Rollout database mang history cũ hoặc V7 partial failure vẫn là thao tác vận hành cần backup, kiểm tra schema và phê duyệt riêng theo recovery guide. Không có production data bị thay đổi, không chạy live repair, không commit/push trong task này.

# F03 — Implementation Summary

## Kết quả

**PARTIAL — backend F03 đã triển khai và kiểm thử bằng dữ liệu QA; còn phụ thuộc F04/F05 thật.** Áp dụng quyết định đã xác nhận ngày17/10/2026. Không triển khai Frontend hoặc F05 Mining Engine.

| Task | Kết quả | Trạng thái |
|---|---|---|
| F03-01 | V4 config/scores; V5 immutable snapshot/matrix; constraints/indexes | PARTIAL: FK đến F04/F05 chưa có bảng đích |
| F03-02 | Versioned lambda policy; Store T/config; roles tách biệt | DONE |
| F03-03 | Manual Stock Priority Score, partial validated PATCH | DONE |
| F03-04 | Fixed-date, configurable-T, quantity-weighted expiry calculator | DONE |
| F03-05 | Historical/source-native base utility, missing/negative/overflow checks | DONE calculator; PARTIAL real F04 source |
| F03-06 | Enabled coefficients, W, historical base, precision/overflow | DONE calculation; real provider pending |
| F03-07 | Preview, warnings, paging, hashes | PARTIAL production F04 provider; fixture verified |
| F03-08 | Atomic immutable snapshot capture/read/matrix | PARTIAL external FK/provider; API verified |
| F03-09 | Swagger verification and F05 handoff contract | PARTIAL actual F05 integration not run |
| F03-10 | Manual Seasonal Priority Score, partial validated PATCH | DONE |

## Source changes

19 class/interface files mới trong `src/main/java/vn/edu/fpt/paspm/utility/`: contracts/strict integer binding; settings/configuration controller-service-repository; product score controller-service-repository; expiry/math calculator; TransactionSource với unavailable/explicit QA fixture adapters; product reader; matrix contracts/builder; snapshot controller-service-repository. Giữ mô hình JdbcTemplate và F01 CatalogAccess/StoreScope hiện tại.

`security/SecurityConfig.java` chỉ cập nhật mô tả OpenAPI503. Hai migration: `V4__f03_configuration_and_scores.sql`, `V5__f03_immutable_utility_snapshots.sql`. Ba test classes: UtilityCalculationTest, UtilityIntegrationTest, UtilityFixtureTest. Không sửa migration cũ, không tạo bảng Store mới, không tạo STOCK_MOVEMENT.

## API

11 operations: GET/PUT system lambda-policy; GET/PUT utility config; PUT expiry-window; GET/PATCH Product scores; POST preview; POST snapshots; GET snapshot; GET matrix. Chi tiết body, HTTP statuses và roles trong `F03_API_CONTRACT.md`.

Store Admin chỉ đổi T của Store mình. System Admin đổi bounds/version. Merchandiser đổi lambda, coefficients,S/M và tạo snapshot. T integer>0; initial policy[0,.5]; coefficients enabled sum1; all disabled W1. Historical snapshots giữ nguyên T/policy/lambda/coefficients/scores/date. New preview/create dùng lambda ngoài policy hiện tại trả409; không clamp.

## Verification

`mvn -q verify` exit0: **44 tests,0failure,0error,0skip**. Actual Flyway V1-V5 fresh migrate và repeated validation thành công trên H2 file riêng. **120 actual Swagger UI requests**,11 endpoints;36 nhóm case PASS bằng UI/runtime/automated theo báo cáo. Hai initial setup-token errors được sửa và chạy lại, không tính PASS giả. OpenAPI integerT và dependency503 được kiểm tra sau sửa.

Read [báo cáo chi tiết](F03_SWAGGER_TEST_REPORT.md), [defect/retest](F03_SWAGGER_ISSUES.md), [Maven evidence](evidence/F03_MAVEN_VERIFY.txt), [snapshot response](evidence/F03_SNAPSHOT_1.json), [matrix response](evidence/F03_SNAPSHOT_1_MATRIX.json).

## Remaining blockers

F04 cần cung cấp production TransactionSource và bảng giao dịch/FK. F05 cần bổ sung MINING_REQUEST snapshot FK và tích hợp consumer theo scaled64bit contract. Với profile thường, new preview/snapshot trả503 thay vì sinh dữ liệu giả; snapshot đã lưu vẫn đọc được. Không có production database migration verification hoặc mining run được tuyên bố PASS.

## Documentation

F03_IMPLEMENTATION_PLAN.md; F03_API_CONTRACT.md; F03_SWAGGER_TEST_CASES.md; F03_TASK_UPDATES.md; F03_TECHNICAL_DECISIONS.md; F03_SWAGGER_TEST_REPORT.md; F03_SWAGGER_ISSUES.md; F03_F05_HANDOFF.md; F03_CODEGRAPH_REPORT.md; F03_IMPLEMENTATION_SUMMARY.md; evidence files. Tất cả nằm trong thư mục F03 hiện có. Các backlog/issue Markdown có notice tham chiếu quyết định mới; DOCX/ZIP/ERD gốc được giữ nguyên.

Code nằm trên nhánh Khoi/Fear/F03-dynamic-utility-profiling. Chưa commit/push/merge F03. Các thay đổi local có sẵn ngoài phạm vi được giữ nguyên.

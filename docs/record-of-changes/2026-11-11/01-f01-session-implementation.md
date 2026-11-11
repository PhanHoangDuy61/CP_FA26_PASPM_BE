# 2026-11-11-01 — F01 session compatibility và local startup

| Trường | Giá trị |
|---|---|
| Record ID | `ROC-20261111-001` |
| Date / timezone | 2026-11-11 / Asia/Ho_Chi_Minh |
| Type | Implementation / Security / Database |
| Features | F01 trực tiếp; F02–F06 phụ thuộc xác thực |
| Branch / base HEAD | `dev` / `5b30eb1` |
| Execution status | COMPLETED trong phạm vi ghi nhận; xem giới hạn bên dưới |
| Release status | UNRELEASED — local, chưa commit/merge/deploy |
| Daily summary | [2026-11-11](README.md) |


## Thông tin phiên và nguồn ghi nhận

- Agent: Codex; không gán model/version không có bằng chứng.
- Ngày thực hiện / timezone: 2026-11-11 / Asia/Ho_Chi_Minh.
- Branch nền: `dev`; HEAD nền `5b30eb1`. Đây không phải commit chứa phần sửa local.
- Trạng thái: **COMPLETED trong phạm vi sửa phiên và xác minh local**, còn các giới hạn bên dưới.
- Bản ghi này được tổng hợp lại trong phiên docs kế tiếp từ source, báo cáo và evidence còn lưu; không chạy lại các lệnh được nêu như lịch sử.
- Quyết định người dùng: **“Giữ cơ chế Frontend; bổ sung Backend refresh/CSRF/logout (Recommended)”**.

## Mục tiêu và nguyên nhân

Backend đã có xác thực JWT nhưng response login thiếu `csrfToken` mà Frontend bắt buộc. Backend cũng chưa có refresh/logout trong khi Frontend đã dùng cơ chế phiên cookie. Vì thế credentials hợp lệ vẫn bị báo invalid session credentials. Trước đó startup bị chặn ở LocalAvatarObjectStorage; cấu hình avatar cần được cung cấp trước khi chạy Backend.

## Thay đổi thực hiện

| File / nhóm file | Thay đổi | Lý do |
|---|---|---|
| [AuthController](../../../src/main/java/vn/edu/fpt/paspm/auth/AuthController.java), [AuthService](../../../src/main/java/vn/edu/fpt/paspm/auth/AuthService.java), [Contracts](../../../src/main/java/vn/edu/fpt/paspm/common/Contracts.java) | Modified | HTTP login email/Google tạo family, JWT có sid và Token có csrfToken |
| [RefreshSessionService](../../../src/main/java/vn/edu/fpt/paspm/auth/RefreshSessionService.java) | Added | Rotation, hashed credentials, replay detection, row locks, logout và absolute expiry |
| [SessionController](../../../src/main/java/vn/edu/fpt/paspm/auth/SessionController.java), [SessionHttpSupport](../../../src/main/java/vn/edu/fpt/paspm/auth/SessionHttpSupport.java) | Added | Refresh/logout routes, cookie, CSRF/Origin checks, no-store |
| [SecurityConfig](../../../src/main/java/vn/edu/fpt/paspm/security/SecurityConfig.java) | Modified | Public refresh, kiểm tra family cho access JWT; role gates giữ nguyên |
| [V20](../../../src/main/resources/db/migration/V20__f01_refresh_sessions.sql) | Added | auth_session và auth_refresh_token, FK/indexes; không sửa V1–V19 |
| [SessionHttpIntegrationTest](../../../src/test/java/vn/edu/fpt/paspm/SessionHttpIntegrationTest.java), [SessionMySqlIntegrationTest](../../../src/test/java/vn/edu/fpt/paspm/SessionMySqlIntegrationTest.java), [SessionHttpSupportTest](../../../src/test/java/vn/edu/fpt/paspm/auth/SessionHttpSupportTest.java) | Added | Regression phiên, đồng thời/replay, cookie, CSRF và OpenAPI |
| Migration tests: FlywayRecoveryTest, JpaPilotMigrationMySqlTest, MiningMigrationMySqlTest, MiningSchedulerUpgradeCompatibilityMySqlTest, RecommendationMigrationTest | Modified | Kỳ vọng schema latest V20 và số migration tăng một; không sửa assertions nghiệp vụ để che lỗi |
| application.yml | Cung cấp cấu hình avatar còn thiếu | File đã có sửa local khác; không ghi secret hoặc nhận toàn bộ diff là do agent |
| [.env.example](../../../.env.example) | Modified | Biến cấu hình phiên không chứa secret |
| API/handoff/setup/F01 docs và runtime OpenAPI | Modified | Đồng bộ hợp đồng và hướng dẫn sử dụng; phiên docs 02 bổ sung navigation/feature impact |

Local Backend chạy bằng Java17, SMTP thử nghiệm Mailpit; tài khoản bootstrap admin và Customer demo được thiết lập/xác minh trong đợt local. Không ghi email đăng nhập kèm mật khẩu, token, OTP hoặc dump vào record. Không xem đây là tài khoản mặc định để dùng cho fresh clone hay production.

## API, quyền và database

- Thêm POST `/api/auth/refresh`, `/api/auth/logout`, `/api/auth/logout-all`.
- Login/refresh JSON thêm csrfToken; refresh credential chỉ qua HttpOnly cookie.
- Logout cần Bearer và cookie/CSRF đúng cùng family; logout-all tăng version của chính account.
- Token raw không lưu DB; family expiry tuyệt đối; refresh reuse thu hồi family.
- V20 được thử trên bản sao MySQL 8.4.11 của DB local V19 trước; có backup riêng tư. Sau đó Backend local áp dụng V20 thành công.
- Không repair/reset DB nguồn V5, không rewrite snapshot/transaction/mining history. MySQL recovery tests dùng dữ liệu synthetic và schema/container độc lập.

## Kiểm chứng đã thực thi trong phiên implementation

| Phương pháp | Môi trường | Kết quả / giới hạn |
|---|---|---|
| Red tests trước implementation | H2 / Java17 | 4/4 fail đúng lỗi thiếu csrfToken |
| Targeted green đầu tiên | H2 / Java17 | 4 session + 22 auth = 26 PASS |
| Maven verify cuối, SPMF child Java25 | macOS, Java17.0.21 / Java25.0.1 | 578 discovered, 388 PASS, 190 SKIPPED, 0 failure/error |
| MySQL copy V19→V20 | MySQL 8.4.11 local, bản sao riêng | 10 session tests PASS; DB nguồn vẫn V19 trong bước copy-test |
| Nhóm Docker cuối | Disposable mysql:8.4 | 27 PASS, 0 SKIPPED / failures / errors |
| Chromium thật qua UI hiện có | FE localhost:3000, BE localhost:8080 | Login 200 → me 200 → document reload → refresh 200 → me 200 → logout 200; về login, không còn cookie phiên |
| OpenAPI thực tế | Backend local | 92 operations; Token.csrfToken và ba route mới hiện diện; login/google/refresh public, logout kế thừa Bearer |

190 skip của Maven gồm 23 ca chạy PASS riêng trong nhóm Docker; còn 167 ca không được phiên này chứng nhận. Hai nhóm có test trùng, không cộng thành tổng test độc lập.

Lượt verify đầu lỗi do SPMF dùng nhầm Java17 và kỳ vọng số migration cũ; đã chọn Java25 riêng, cập nhật kỳ vọng latest V20 rồi chạy lại. Lượt MySQL mở rộng đầu thiếu restricted restore provisioner; retest trên disposable Docker với provisioner chuẩn đã PASS. Swagger public-operation inheritance có test fail riêng trước khi thêm SecurityRequirements. Một thử nghiệm instrumentation trình duyệt lỗi môi trường VM Playwright; mở lại và chạy lại thành công. Các failure và retest được giữ trong báo cáo, không đổi kết quả cũ thành PASS.

Lệnh kiểm chứng trọng tâm (dùng SPMF_JAVA của môi trường, không commit đường dẫn JDK riêng):

```sh
mvn -Dtest=SessionHttpIntegrationTest,AuthIntegrationTest,SessionHttpSupportTest test
mvn -Dpaspm.test.spmf-java="$SPMF_JAVA" -Dpaspm.mining.java-executable="$SPMF_JAVA" verify
```

Nguồn chi tiết: [execution report](../../testing/reports/F01_SESSION_VERIFICATION.md), [counts](../../testing/reports/F01_SESSION_TEST_COUNTS.json), [redacted browser evidence](../../testing/reports/F01_SESSION_BROWSER_EVIDENCE.json).

## Ảnh hưởng và giới hạn

F02–F06 tiếp tục dùng role, StoreScope và Bearer hợp lệ; không đổi business rules. Internal scheduler không dùng cookie browser. Customer storefront có thể là dữ liệu demo; đăng nhập thành công không chứng minh public catalog/recommendation/purchase APIs đã có.

NOT RUN: Google thật, HTTPS ingress production, auth nhiều JVM, các nhóm opt-in Linux/multi-container ngoài nhóm được nêu. Chưa có cleanup session và shared refresh limiter; lifetime bảy ngày là mặc định kỹ thuật, chưa phải chính sách retention được PO duyệt độc lập. Không tuyên bố production-ready.

## Git và bàn giao

**Không commit, push, merge hoặc deploy.** Giữ nguyên file ERD đã bị xóa và các diagram/temp file ngoài phạm vi; không stage chúng. Tài liệu và source là working-tree changes. Backend/Frontend local đã được chạy để người dùng xem luồng, nhưng không coi tiến trình localhost là dịch vụ luôn hoạt động ở các phiên sau.

Tiếp theo: [record 02 — đồng bộ documentation](02-f01-documentation-sync.md).

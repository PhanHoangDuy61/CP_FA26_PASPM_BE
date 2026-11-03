# Kết quả kiểm thử tự động — 14/10/2026

## Kiểm tra lại trên Windows

JDK Temurin 17.0.20.1, Maven Wrapper 3.9.9, `./mvnw.cmd -B verify`: **BUILD SUCCESS; 14 tests, 0 failures, 0 errors, 0 skipped**.

Thêm hai test cho Login từ chối pending/inactive/unverified/unknown email, validation; /me trả đúng toàn bộ UserView và store, không dùng userId client gửi, từ chối token thiếu và token đã hết hạn có chữ ký hợp lệ.

MySQL 8.4.9 thật trên data directory mới: Flyway V1 success, 4 role, BCrypt hash, bootstrap verified. Kiểm tra HTTP thật: **14/14 ca đạt**:

| Ca HTTP/MySQL | HTTP thực tế |
|---|---|
| ACTIVE verified Login | 200 |
| /me với JWT Login của System Admin | 200, UserView đúng |
| Sai password; email không tồn tại | 401; 401 |
| PENDING_VERIFICATION; INACTIVE; ACTIVE chưa verified | 401; 401; 401 |
| Thiếu password; email sai định dạng | 400; 400 |
| /me không token; token sai; token hết hạn có chữ ký đúng | 401; 401; 401 |
| Store Admin Login; /me có storeId | 200; 200 |

Swagger UI đã tải danh sách endpoint và Authorize; chưa Execute vì công cụ điều khiển browser lỗi. **Không ghi các ca HTTP trên thành Swagger Pass**. SMTP, Mailpit và Google thật chưa kiểm tra. F01-01 chưa thử DB có STORE sẵn; kiểm tra này chỉ chứng minh migration trên database mới.

Lần repackage khi app chạy từ JAR gặp lỗi khóa file Windows. Sau khi dừng đúng app test, `verify` chạy thành công. Script `scripts/Start-Dev.ps1` kiểm tra JDK/biến môi trường; script đã parse và thử nhánh từ chối JDK không tồn tại.

## Kết quả bàn giao trước phiên kiểm tra này

Java 17.0.20; Maven 3.9.9; Spring Boot 3.5.7. `AuthIntegrationTest`: **12 tests, 0 failures, 0 errors, 0 skipped**.

- Login, /me, anonymous access và JWT không hợp lệ.
- Public registration từ chối trường role; chỉ END_CUSTOMER; OTP dùng một lần.
- Admin invite + OTP activation + initial password.
- Năm lần OTP sai được lưu; challenge bị khóa.
- Resend cooldown và thay mã cũ.
- OTP hết hạn; admin không được bypass email verification để bật ACTIVE.
- Đổi role vô hiệu hóa token cũ.
- INACTIVE vô hiệu hóa token cũ.
- StoreScope chặn truy cập Store khác qua test-only controller.
- SMTP lỗi rollback registration.
- Google chưa cấu hình bị từ chối; Google link cần xác thực PASPM.
- OpenAPI có Bearer schema; password lưu dạng hash.

Đây là test ở HTTP/service/database integration layer với H2 MySQL compatibility và SMTP mock. Controller `/test/store/{id}` chỉ có trong test source, không có trong app thật.

**Chưa kiểm tra:** Flyway trên MySQL 8.4 thật, SMTP thật/Mailpit UI, Google happy path/audience bằng token thật, thao tác Swagger UI thủ công, F02 cross-store Product APIs, performance/concurrency trên nhiều replica, PR review/merge. Không suy diễn các ca này thành Pass.

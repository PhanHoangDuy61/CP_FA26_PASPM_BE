# PASPM Backend — bước triển khai đầu tiên

Backend cho FA26_SE048, dựa trên `PASPM_F01_12_API_Swagger_Tasks_Clean.docx` và `PASPM_ERD_V3.drawio` được cung cấp ngày 14/10/2026. Bản này triển khai F01; React và các module F02–F11 chưa được triển khai. Không có kết quả mining giả.

## Stack

Java 17, Spring Boot 3.5.7, Spring Security, Spring JDBC, MySQL 8.4, Flyway, springdoc Swagger, SMTP. JDBC được chọn để quản lý rõ SQL và migration theo ERD; chưa sử dụng JPA/Hibernate. Hai thuật toán HUI-Miner/EFIM có interface riêng trong `mining/MiningEngine.java`, chưa có adapter thực thi.

## Chạy trên Windows / IntelliJ

1. Cài JDK 17, Docker Desktop. Mở thư mục chứa `pom.xml` trong IntelliJ, chọn JDK 17 cho cả Project và Maven.
2. Copy `.env.example` thành `.env`, điền `DB_PASSWORD` và `MYSQL_ROOT_PASSWORD` bằng giá trị riêng. File `.env` chỉ được Docker Compose đọc tự động.
3. Chạy `docker compose up -d`. MySQL dùng cổng 3306, hộp thư Mailpit ở http://localhost:8025.
4. Trong **Run Configuration → Environment variables** của IntelliJ, cấu hình:

   - `DB_PASSWORD`: trùng giá trị của MySQL.
   - `JWT_SECRET` và `OTP_SECRET`: hai chuỗi ngẫu nhiên khác nhau, mỗi chuỗi ít nhất 32 byte; có thể dùng chuỗi hex ngẫu nhiên 64 ký tự.
   - `BOOTSTRAP_EMAIL`, `BOOTSTRAP_PASSWORD`: tài khoản System Admin khởi tạo. Mật khẩu ít nhất 12 ký tự, có chữ hoa/thường và số, tối đa 72 byte UTF-8.
   - `GOOGLE_CLIENT_ID`: bỏ trống nếu chưa thử Google.

5. Chạy `vn.edu.fpt.paspm.PaspmApplication`. Flyway tự chạy migration. Mở http://localhost:8080/swagger.
6. Sau lần khởi tạo đầu tiên, bỏ biến bootstrap khỏi cấu hình chạy. Source không chứa tài khoản/mật khẩu mặc định.

Nếu không dùng IntelliJ, đặt biến `$env:DB_PASSWORD`, `$env:JWT_SECRET`… trong PowerShell rồi chạy `./mvnw.cmd spring-boot:run`. Maven Wrapper trong gói sẽ tải Maven 3.9.9 ở lần đầu, nên không cần cài Maven riêng. Không dán secret vào tài liệu hay commit.

Có thể dùng script Windows để kiểm tra JDK 17 và đọc file cấu hình local trước khi chạy:

```powershell
./scripts/Start-Dev.ps1 -JdkHome 'DUONG_DAN_JDK_17' -EnvFile './.env'
```

Thay `DUONG_DAN_JDK_17` bằng thư mục có `bin/java.exe` và `bin/javac.exe`. Script đọc `NAME=value` như dữ liệu, không thực thi nội dung file; các biến chỉ áp dụng cho process hiện tại. Điền đầy đủ `DB_PASSWORD`, `JWT_SECRET`, `OTP_SECRET` trước khi chạy. Không đưa file `.env` vào Git. Nếu đã đặt biến trong PowerShell thì bỏ tham số `-EnvFile`. IntelliJ vẫn cần cấu hình biến môi trường riêng.

Trên Windows, dừng backend đang chạy từ `target/*.jar` trước khi `./mvnw.cmd verify`: JVM giữ file JAR có thể khiến bước repackage không đổi tên được file. Nếu chạy `spring-boot:run` trong terminal, dùng Ctrl+C để dừng backend rồi build lại.

MySQL local đã có bảng STORE/F02: **không chạy V1 lên DB đó một cách mù quáng**. Bản này được thiết kế cho DB development trống. Khi tích hợp repository của nhóm, chuyển phần STORE sang migration chủ sở hữu hiện có và viết migration bổ sung F01 riêng; không bật Flyway baseline để bỏ qua khác biệt schema.

## API đã triển khai

| Endpoint | Quyền / hành vi |
|---|---|
| POST `/api/auth/login` | Email/password; ACTIVE + verified mới được cấp JWT |
| GET `/api/auth/me` | JWT hợp lệ; trả user/role/store |
| POST `/api/auth/register` | Công khai; chỉ nhận email, tạo END_CUSTOMER chờ OTP và gửi mã; trả 202 chung |
| POST `/api/auth/otp/request`, `/otp/resend` | Gửi lại OTP cho tài khoản đang chờ; email + purpose SELF_REGISTER hoặc ADMIN_INVITE |
| POST `/api/auth/otp/verify` | Email + OTP; xác minh email. Admin invitation hoàn tất qua `/activate` |
| POST `/api/auth/register/complete` | Email đã xác minh + name/password; hoàn tất và kích hoạt END_CUSTOMER |
| POST `/api/auth/activate` | Email + OTP + password; kích hoạt admin invitation |
| POST `/api/auth/google/register` | Google ID token hợp lệ; tạo END_CUSTOMER chờ OTP gửi tới email Google đã xác minh |
| POST `/api/auth/google` | Đăng nhập Google identity đã liên kết; cần client ID thật |
| POST `/api/auth/google/link` | JWT PASPM hợp lệ + Google ID token; không tự liên kết qua email |
| GET/POST `/api/users` | SYSTEM_ADMIN; list có q/role/status/page/size, POST gửi invitation |
| GET/PUT `/api/users/{id}` | SYSTEM_ADMIN; PUT cập nhật name |
| PATCH `/api/users/{id}/status` | ACTIVE/INACTIVE; không bỏ qua OTP activation |
| PUT `/api/users/{id}/role` | SYSTEM_ADMIN; đổi role/store và thu hồi token cũ |
| GET `/api/roles` | SYSTEM_ADMIN; bốn role đã seed |

### Đăng ký bằng Google/Gmail

Frontend cần lấy Google ID token bằng Google Identity Services với OAuth Client ID trùng `GOOGLE_CLIENT_ID`, sau đó:

1. Gửi `POST /api/auth/google/register` với `{"idToken":"<Google ID token>"}`. API chỉ nhận Google identity có email đã xác minh; tài khoản mới được tạo ở trạng thái chờ OTP.
2. Đọc OTP gửi tới email Google trong Mailpit ở development hoặc hộp thư thật đã cấu hình SMTP.
3. Gửi `POST /api/auth/otp/verify` với `{"email":"<Google email>","otp":"<6 digits>"}`.
4. Gửi `POST /api/auth/google` với cùng Google ID token để nhận JWT PASPM.

Google identity chỉ được liên kết tự động trong lúc tạo tài khoản mới; API không tự liên kết vào tài khoản PASPM đã tồn tại chỉ dựa trên email. Nếu email đã có tài khoản, đăng nhập tài khoản đó rồi dùng `/api/auth/google/link`.

### Đăng ký bằng email trong Swagger

1. Gọi `POST /api/auth/register` với `{"email":"you@example.com"}`. Nếu email đủ điều kiện, backend gửi OTP.
2. Gọi `POST /api/auth/otp/verify` với email và OTP 6 chữ số. Email được xác minh nhưng tài khoản vẫn pending.
3. Gọi `POST /api/auth/register/complete` với email, name và password để kích hoạt tài khoản. Mật khẩu cần ít nhất 12 ký tự, gồm chữ hoa, chữ thường và số.
4. Đăng nhập bằng `POST /api/auth/login`.

Các endpoint trả phản hồi chung cho email đã tồn tại để hạn chế dò tìm tài khoản. OTP gửi qua cấu hình SMTP; Mailpit local có thể xem tại http://localhost:8025.

Swagger có Bearer Authorize và DTO. Các lỗi validation không trả thông tin nội bộ. Duplicate email trong admin creation trả 409. Các endpoint công khai không nhận role/store và từ chối field không khai báo.

## Seed Store để thử nhân viên

F02 chưa có Store CRUD; dùng SQL trên database development để tạo dữ liệu thử:

```sql
INSERT INTO store(name) VALUES ('Demo Store A'), ('Demo Store B');
SELECT store_id, name FROM store;
```

SYSTEM_ADMIN và END_CUSTOMER là tài khoản global (`storeId=null`); STORE_ADMIN/MERCHANDISER phải có Store tồn tại. Admin POST user không nhận mật khẩu; nhân viên đọc OTP trong Mailpit rồi đặt mật khẩu bằng `/activate`.

## Kiểm thử

`./mvnw.cmd test` (Windows) hoặc `./mvnw test` (Linux/macOS) chạy integration tests bằng H2 ở MySQL compatibility mode; SMTP được mock. Các bài test kiểm tra OTP sai/hết hạn/đã dùng, quyền, token revocation, store boundary và rollback khi SMTP lỗi. H2 **không thay thế kiểm thử migration trên MySQL thật**.

Xem `docs/SWAGGER_CHECKLIST.md` để test thủ công theo F01-07/F01-11. Xem `docs/IMPLEMENTATION_STATUS.md` để biết các hạn chế và bước tiếp theo.

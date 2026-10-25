# PASPM Backend — bước triển khai đầu tiên

Backend cho FA26_SE048, dựa trên `PASPM_F01_12_API_Swagger_Tasks_Clean.docx` và `PASPM_ERD_V3.drawio` được cung cấp ngày 14/10/2026. Bản này triển khai F01; React và các module F02–F11 chưa được triển khai. Không có kết quả mining giả.

## Stack

Java 17, Spring Boot 3.5.7, Spring Security, Spring JDBC, MySQL 8.4, Flyway, springdoc Swagger, SMTP. JDBC được chọn để quản lý rõ SQL và migration theo ERD; chưa sử dụng JPA/Hibernate. Hai thuật toán HUI-Miner/EFIM có interface riêng trong `mining/MiningEngine.java`, chưa có adapter thực thi.

## Chạy backend trên Windows

1. Cài JDK 17 và đảm bảo `JAVA_HOME` trỏ đến thư mục JDK.
2. Điền cấu hình local trong `.env` (MySQL, JWT/OTP và SMTP). Script tự đọc file này; không commit hoặc chia sẻ file `.env`.
3. Mở PowerShell tại thư mục backend và chạy:

   ```powershell
   .\scripts\Start-Dev.ps1
   ```

   Script dùng JDK 17 từ `JAVA_HOME`, nạp `.env` và chạy Maven/Spring Boot. Flyway tự chạy migration khi cần. Mở http://localhost:8080/swagger.

Nhấn Ctrl+C trong cửa sổ PowerShell để dừng backend. Maven Wrapper tự tải Maven 3.9.9 ở lần chạy đầu; không cần cài Maven riêng.

Nếu muốn chỉ rõ file `.env` trong thư mục backend:

```powershell
.\scripts\Start-Dev.ps1 -EnvFile '.\.env'
```

Lệnh chạy thông thường vẫn là `.\scripts\Start-Dev.ps1`; script tự tìm `.env` trong thư mục backend. Chỉ truyền `-JdkHome` nếu `JAVA_HOME` chưa trỏ đến JDK 17, và khi đó thay bằng đường dẫn JDK 17 thật trên máy. Script đọc file `NAME=value` như dữ liệu, không thực thi nội dung file; các biến chỉ áp dụng cho process backend. Cần có `DB_PASSWORD`, `JWT_SECRET`, `OTP_SECRET`; mỗi secret phải có ít nhất 32 byte. Nếu chạy bằng IntelliJ, cấu hình biến môi trường trong Run Configuration thay vì dùng script.

Trên Windows, dừng backend đang chạy từ `target/*.jar` trước khi `./mvnw.cmd verify`: JVM giữ file JAR có thể khiến bước repackage không đổi tên được file. Nếu chạy `spring-boot:run` trong terminal, dùng Ctrl+C để dừng backend rồi build lại.

MySQL local đã có bảng STORE/F02: **không chạy V1 lên DB đó một cách mù quáng**. Bản này được thiết kế cho DB development trống. Khi tích hợp repository của nhóm, chuyển phần STORE sang migration chủ sở hữu hiện có và viết migration bổ sung F01 riêng; không bật Flyway baseline để bỏ qua khác biệt schema.

## API đã triển khai

| Endpoint | Quyền / hành vi |
|---|---|
| POST `/api/auth/login` | Email/password; ACTIVE + verified mới được cấp JWT |
| GET `/api/auth/me` | JWT hợp lệ; trả user/role/store |
| POST `/api/auth/register` | Công khai; email mới tạo END_CUSTOMER chờ OTP và trả 202; email đã tồn tại trả 409 |
| POST `/api/auth/otp/request` | Yêu cầu OTP cho tài khoản đang chờ xác minh; email + purpose SELF_REGISTER hoặc ADMIN_INVITE |
| POST `/api/auth/otp/resend` | Gửi mã OTP mới khi mã cũ thất lạc/hết hạn; cooldown được áp dụng và mã cũ bị vô hiệu |
| POST `/api/auth/otp/verify` | Email + OTP; xác minh email. Admin invitation hoàn tất qua `/activate` |
| POST `/api/auth/register/complete` | Email đã xác minh + name/password; hoàn tất và kích hoạt END_CUSTOMER |
| POST `/api/auth/activate` | Email + OTP + password; kích hoạt admin invitation |
| POST `/api/auth/password-reset/request` | Công khai; gửi OTP PASSWORD_RESET, phản hồi không tiết lộ email tồn tại |
| POST `/api/auth/password-reset/confirm` | Email + OTP + mật khẩu mới; dùng một lần, thu hồi JWT cũ |
| POST `/api/auth/password/change` | JWT hợp lệ + mật khẩu hiện tại + mật khẩu mới; chỉ đổi tài khoản đang đăng nhập, bắt đăng nhập lại |
| POST `/api/auth/google/register` | Google ID token hợp lệ; tạo END_CUSTOMER chờ OTP gửi tới email Google đã xác minh |
| POST `/api/auth/google` | Đăng nhập Google identity đã liên kết; cần client ID thật |
| POST `/api/auth/google/link` | JWT PASPM hợp lệ + Google ID token; không tự liên kết qua email |
| GET/POST `/api/users` | SYSTEM_ADMIN; list có q/role/status/page/size, POST gửi invitation |
| GET/PUT `/api/users/{id}` | SYSTEM_ADMIN; PUT cập nhật name |
| PATCH `/api/users/{id}/status` | ACTIVE/INACTIVE; không bỏ qua OTP activation |
| PUT `/api/users/{id}/role` | SYSTEM_ADMIN; đổi role/store và thu hồi token cũ |
| PUT/DELETE `/api/users/me/avatar`; GET `/api/users/{id}/avatar` | JWT hợp lệ; người dùng chỉ cập nhật/xóa/đọc ảnh của chính mình |
| GET `/api/roles` | SYSTEM_ADMIN; bốn role đã seed |

### Ảnh profile và đặt lại mật khẩu

Ảnh profile nhận multipart field `file` dạng JPEG hoặc PNG; backend nhận diện nội dung ảnh thay vì tin MIME type/tên file client, giới hạn 5 MiB, 4096 pixel mỗi chiều và 12 megapixel. Tên object được sinh ngẫu nhiên. Database chỉ lưu object key; file local mặc định ở `${user.home}/.paspm/profile-images` (ngoài source/build). Có thể đặt `PROFILE_IMAGE_DIRECTORY` trong `.env` hoặc environment của process để chọn thư mục persistent riêng.

`UserView` trả `avatarUrl` dạng `/api/users/{userId}/avatar`, hoặc `null` nếu chưa có ảnh. Ảnh được phục vụ riêng cho owner đã xác thực, không có quyền đọc ảnh admin/nhân viên thay mặt user. Production cần gắn thư mục persistent riêng, giới hạn quyền filesystem và sao lưu; nếu có nhiều backend replicas, dùng shared durable volume hoặc đăng ký adapter `AvatarObjectStorage` dùng chung làm Spring `@Primary` bean. Không mount thư mục ảnh vào source hay `target/`.

Luồng quên mật khẩu: `POST /api/auth/password-reset/request` với `{"email":"user@example.com"}` → nhập OTP email → `POST /api/auth/password-reset/confirm` với `{"email":"user@example.com","otp":"123456","password":"NewPassword123!"}`. Hai phản hồi gửi OTP giống nhau cho email tồn tại/không tồn tại/không đủ điều kiện; email không đủ điều kiện (pending, inactive hoặc chỉ đăng nhập Google chưa có password) không nhận thư. Cooldown, hạn dùng, tối đa năm lần thử và giới hạn IP/email tái sử dụng policy OTP hiện tại. SMTP lỗi gửi password-reset cũng giữ phản hồi generic; lỗi được ghi log đã lược bỏ dữ liệu nhạy cảm. Mật khẩu mới theo policy đăng ký (12–72 ký tự, chữ hoa/chữ thường/số; tối đa 72 byte UTF-8).

Đổi mật khẩu chủ động: gửi `POST /api/auth/password/change` cùng bearer JWT và `{"currentPassword":"CurrentPassword123!","newPassword":"NewPassword456!"}`. Backend khóa user record trong transaction, kiểm tra lại JWT `token_version`, xác minh mật khẩu hiện tại và từ chối mật khẩu mới trùng với mật khẩu cũ. Response là `{"message":"Password changed"}`. Token hiện tại và các token cũ đều bị thu hồi; người dùng phải đăng nhập lại bằng mật khẩu mới. Luồng này không dùng OTP và không thay đổi quy trình reset qua OTP.

Đổi mật khẩu tăng `token_version`, nên mọi JWT hiện có bị từ chối ngay ở request tiếp theo; không bổ sung refresh/session store song song.

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

Endpoint đăng ký email phân biệt email mới (202, gửi OTP) với email đã tồn tại (409); phản hồi này cho phép dò tìm email đã đăng ký. OTP gửi qua cấu hình SMTP; Mailpit local có thể xem tại http://localhost:8025.

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

Xem `docs/PASPM_F01_AUTH_SERVICE/SWAGGER_CHECKLIST.md` để test thủ công theo F01-07/F01-11. Xem `docs/PASPM_F01_AUTH_SERVICE/IMPLEMENTATION_STATUS.md` để biết các hạn chế và bước tiếp theo.

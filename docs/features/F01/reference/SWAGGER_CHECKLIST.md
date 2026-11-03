# F01-07 / F01-11 — kiểm tra thủ công

Tất cả các dòng dưới đây khởi đầu **Not Tested**. Automated tests không được ghi thay cho thao tác Swagger và chưa có PR/review/merge.

Ngày 14/10/2026: MySQL/Flyway và 14 ca HTTP Login/Current User đã được kiểm tra thực tế, xem TEST_RESULTS.md. Swagger UI đã tải API và Authorize; chưa Execute do lỗi công cụ điều khiển tab. Vì vậy cột kết quả Swagger bên dưới giữ Not Tested. SMTP/Google thật chưa kiểm tra.

| Ca | Kết quả mong đợi | Kết quả Swagger |
|---|---|---|
| Login bootstrap admin rồi Authorize | 200, /me đúng tài khoản | Not Tested |
| Sai mật khẩu / token sai / không token | 401 | Not Tested |
| POST user staff ở Store A | 201, pending, Mailpit nhận invitation | Not Tested |
| Activate staff bằng OTP và mật khẩu | 200, login được | Not Tested |
| Staff gọi /api/users hoặc /api/roles | 403 | Not Tested |
| Gán role sai / thiếu Store cho staff | 400 | Not Tested |
| Email admin-created bị trùng | 409 | Not Tested |
| Đổi role/status rồi dùng token cũ | 401 | Not Tested |
| Public Register chỉ nhận email; extra field bị từ chối | 202; role luôn END_CUSTOMER | Not Tested |
| Register email → OTP verify → register complete → Login → /me | 202 → 200 → 200 → 200 → 200 | Not Tested |
| Complete trước OTP, OTP sai/hết hạn/đã dùng | 400; account không active | Not Tested |
| Google register → OTP verify → Google login | 202 → 200 → 200; END_CUSTOMER active sau OTP | Not Tested |
| Google đăng ký với email đã có tài khoản | Không tự liên kết identity | Not Tested |
| OTP sai, hết hạn, đã dùng hoặc sai purpose | 400 | Not Tested |
| Resend trong cooldown | 429 | Not Tested |
| Resend sau cooldown; dùng lại OTP cũ | 202; OTP cũ bị 400 | Not Tested |
| Chưa cấu hình Google client ID | 503 | Not Tested |
| Google link bằng ID token thật; Google login | 200; /me lấy role/store từ PASPM | Not Tested |
| Google sai audience/issuer/expiry hoặc chưa liên kết | 401 | Not Tested |
| Store A đọc/sửa Product của Store B | 403 sau khi F02 triển khai | Not Implemented in F01 |

Ghi HTTP status thực tế và Pass/Fail sau thao tác. Che JWT, OTP, mật khẩu, Google ID token và SMTP credentials trong mọi minh chứng.

Đọc OTP ở Mailpit http://localhost:8025, không thêm endpoint trả OTP. Chờ đúng cooldown hoặc chỉnh **database test** để kiểm tra expired/resend; không giảm policy của môi trường sử dụng thật.

# Quyết định triển khai cần nhóm review

Các giá trị dưới đây là baseline development do người triển khai chọn, chưa đại diện cho quyết định production của nhóm.

1. Java 17 + Spring Boot + MySQL; JDBC thay vì JPA để kiểm soát ERD/SQL. F01 làm trước theo tài liệu task mới nhất. Proposal cũ không được dùng để tự đưa ASP.NET trở lại baseline.
2. JWT HS256, issuer PASPM, access token 15 phút, chưa có refresh token; đăng nhập lại khi hết hạn. Role/status/version kiểm tra database mỗi request. Đổi role/store/status tăng token_version, token trước đó bị từ chối ngay. Không có endpoint logout/revoke từng token ở phạm vi task hiện tại.
3. OTP 6 chữ số, 10 phút, cooldown 60 giây, tối đa 5 lần nhập sai/challenge. HMAC-SHA256 với secret ngoài DB; mã cũ bị vô hiệu khi gửi lại. Thêm giới hạn IP/email và khóa row User để serialize verification/resend. Rate limiting in-memory chỉ phù hợp một instance; cần Redis/gateway trước khi chạy nhiều replica.
4. SMTP dùng Mailpit khi development; không trả/log OTP. Gửi email đồng bộ trong transaction để lỗi SMTP rollback User/challenge. Sau SMTP thành công vẫn có khả năng DB commit thất bại; cần transactional outbox cho vận hành ổn định và retry email ở bước sau.
5. Google JWT được kiểm tra signature bằng JWKS chính thức, issuer, audience/client ID, expiry, verified email và subject. Tài khoản Google mới có thể được provision thành END_CUSTOMER pending và identity chỉ được liên kết trong transaction tạo tài khoản; phải xác minh OTP gửi đến email Google trước khi đăng nhập. Email đã có tài khoản không được tự liên kết; người dùng phải đăng nhập PASPM và dùng luồng link đã xác thực. Không nâng role dựa theo Google email.
6. SYSTEM_ADMIN/END_CUSTOMER global; STORE_ADMIN/MERCHANDISER gắn đúng một store. Chưa hỗ trợ một nhân viên thuộc nhiều Store; phù hợp USER.store_id nullable trong ERD hiện tại.
7. SQL dùng `app_user` thay cho USER để tránh tên reserved. Giữ USER–ROLE–STORE như ERD và thêm email_verified_at/token_version/verification_purpose. Thêm verification_challenge, external_identity theo task OTP/Google mới; đây là extension cần cập nhật lại ERD.
8. STORE chỉ tạo một lần ở V1 cho database trống. F02 sử dụng lại bảng này. Không sinh toàn bộ 21 bảng ngay vì các module sau chưa có hợp đồng API đủ cụ thể.
9. Swagger chỉ nên mở ở development. Bản này giữ `/swagger` công khai cho công việc review local; khóa/disable ở deployment production. Không cấu hình CORS React vì tài liệu đợt này không bao gồm frontend.
10. Interface mining chưa tích hợp SPMF. Cần chốt scaling decimal → integer minUtil/item utility, adapter version, support_count (không mặc định xuất từ SPMF), timeout/cancel, output hashes và phạm vi runtime/memory trước khi viết F05.

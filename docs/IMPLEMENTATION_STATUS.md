# Trạng thái bàn giao

| Task | Code | Giới hạn nghiệm thu |
|---|---|---|
| F01-01 | Migration STORE/ROLE/USER + auth extensions; seed 4 role; bootstrap env; Swagger | MySQL thật cần chạy trên máy nhóm |
| F01-02–03 | Login/JWT và /me | Kiểm tra Swagger thủ công còn pending |
| F01-04 | Method RBAC, DB-based revocation, reusable StoreScope | Product/Category endpoints chưa có; store isolation mới test qua fixture |
| F01-05–06 | User list/detail/create/update/status; roles/assignment | PUT chỉ sửa name; email bất biến để không bỏ qua verification |
| F01-07 | Checklist và automated integration tests | Swagger evidence / PR review / merge chưa thực hiện |
| F01-08–09 | HMAC OTP, send/request/resend/verify/activate; SMTP | Mailpit/provider thực tế và outbox chưa nghiệm thu |
| F01-10 | Google verifier + authenticated linking | Happy path cần Google OAuth client/token thật |
| F01-11 | Checklist OTP/Google | Manual verification chưa thực hiện |
| F01-12 | END_CUSTOMER registration + OTP | Mail provider thực tế cần cấu hình |
| F02–F11 | Chưa triển khai | Không có mock endpoints trả thành công |

## Bước tiếp theo

### Đối chiếu source và kiểm tra local ngày 14/10/2026

- Đã đọc code thực tế cùng DOCX task và ERD V3. Login dùng BCrypt, chỉ ACTIVE + email_verified_at mới cấp JWT; /me lấy danh tính từ JWT và chỉ trả UserView.
- Build Windows bằng JDK Temurin 17.0.20.1, Maven Wrapper 3.9.9: `verify` thành công; 14 integration tests, không failure/error/skipped. H2 và SMTP mock chỉ thuộc kiểm thử tự động.
- MySQL portable 8.4.9, data directory mới riêng cho phiên kiểm tra: Flyway V1 áp dụng thành công, 4 role, bootstrap ACTIVE/verified/hash. Không sửa migration hoặc reset DB.
- 14 ca HTTP/MySQL cho F01-02/F01-03 đạt mã mong đợi, gồm System Admin và Store Admin, pending/inactive/unverified, validation, token thiếu/sai/hết hạn. Đây chưa phải kết quả Execute trên Swagger.
- Swagger UI đã hiển thị API/schema/Authorize. Công cụ điều khiển tab lỗi trước Execute; F01-07/F01-11 vẫn chưa nghiệm thu Swagger. SMTP/Mailpit và Google thật vẫn Not Tested.
- F01-01 chưa hoàn tất mọi tiêu chí: bootstrap source chỉ tạo System Admin; Store Admin trong database kiểm tra được tạo bằng fixture riêng. V1 chưa được thử tích hợp vào DB có STORE/F02 sẵn.
- F01-04 chưa nghiệm thu qua Product/Category API thật; F01-05 PUT chỉ sửa name. F01-08–10/F01-12 có implementation nhưng chưa đủ minh chứng nghiệm thu dịch vụ thật.
- ERD chưa có email_verified_at/token_version/verification_purpose/verification_challenge/external_identity; password_hash trong code nullable cho invitation chưa đặt mật khẩu. Các quyết định mở rộng được ghi trong DECISIONS.md, cần cập nhật ERD khi nhóm chốt.
- Flyway có cảnh báo phiên bản MySQL mới hơn phạm vi đã kiểm tra của thư viện hiện tại; migration thực tế vẫn thành công. Chưa tự nâng dependency trong task Login/Current User.
- Thư mục source hiện không có `.git`; chưa có commit/PR/review/merge.

Các bước module sau dưới đây là kế hoạch bàn giao cũ, chưa được triển khai trong phiên kiểm tra F01 này.

1. Nhóm review decisions; chạy MySQL/SMTP/Swagger và xác nhận F01 trên máy Windows.
2. Tạo F02 migration CATEGORY/PRODUCT/INVENTORY theo ERD V3; store-scoped CRUD, unique SKU/store và composite FK chống cross-store.
3. F03/F04: utility snapshot, import CSV, validation/mapping/report, unique source orders và immutable mining input.
4. F05: SPMF HUI-Miner/EFIM adapters, scheduler/job state, cancel/timeout/retry, output parser và benchmark công bằng.
5. Recommendation/layout/campaign sau khi rule engine và cách mô phỏng được nhóm chốt.

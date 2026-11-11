# F01 — Quản lý phiên và tác động tới F02–F06

Ngày đối chiếu: **2026-11-11**, timezone **Asia/Ho_Chi_Minh**. Baseline Git: `dev` tại `5b30eb1`; phần refresh/CSRF/logout hiện là **thay đổi local chưa commit**. Tài liệu này mô tả implementation hiện có, không xác nhận đã merge, triển khai hoặc nghiệm thu production.

## 1. Lý do thay đổi

Frontend hiện có yêu cầu `csrfToken` trong kết quả đăng nhập, khôi phục phiên bằng refresh cookie và gọi logout phía server. Backend trước đó chỉ trả JWT ba trường, khiến Frontend từ chối phiên dù email/mật khẩu hợp lệ. Người dùng đã chọn giữ cơ chế Frontend và bổ sung Backend tương ứng. Không thay đổi implementation Frontend trong đợt sửa này.

Nguồn hợp đồng chi tiết: [F01 API](../../api/F01_AUTH_USER_API.md#session-integration-update--2026-11-11), [Frontend handoff](../../frontend_handoff/F01_HANDOFF.md), [OpenAPI](../../api/openapi.json).

## 2. Hợp đồng phiên hiện tại

| Thao tác | Endpoint | Điều kiện | Kết quả |
|---|---|---|---|
| Đăng nhập email | POST `/api/auth/login` | Login DTO hiện hành; tài khoản ACTIVE, xác minh email; kiểm tra Origin khi có | Token JSON bốn trường và hai cookie |
| Đăng nhập Google | POST `/api/auth/google` | Identity Google liên kết hợp lệ và tài khoản ACTIVE | Cùng hợp đồng phiên; đăng nhập Google thật chưa được kiểm thử trong đợt này |
| Khôi phục / gia hạn | POST `/api/auth/refresh` | Refresh cookie và CSRF cookie/header hợp lệ; không cần Bearer, không có request body | Access token, refresh token và CSRF mới trong cùng session family |
| Đăng xuất phiên hiện tại | POST `/api/auth/logout` | Bearer thuộc cùng phiên, refresh cookie và CSRF | Thu hồi family hiện tại, xóa cookie, 200 |
| Đăng xuất toàn bộ | POST `/api/auth/logout-all` | Cùng điều kiện logout | Thu hồi mọi family của chính tài khoản, tăng token_version, xóa cookie, 200 |

Token JSON gồm `accessToken:string`, `expiresIn:integer` (giây), `tokenType:"Bearer"`, `csrfToken:string`. Refresh credential **không xuất hiện trong JSON**. Client giữ access token trong bộ nhớ và gửi `credentials: 'include'` cho các thao tác phiên; các API nghiệp vụ vẫn nhận `Authorization: Bearer …`.

### Cookie và CSRF

- HTTPS mặc định: `__Host-PASPM-REFRESH` (HttpOnly) và `__Host-PASPM-CSRF` (JavaScript đọc được). Cả hai Secure, SameSite=Strict, Path=/, không Domain.
- HTTP local: tên `PASPM-REFRESH`, `PASPM-CSRF` khi bật cấu hình local rõ ràng. Backend kiểm tra hostname loopback; đây không phải chế độ triển khai production.
- `X-CSRF-Token` phải khớp cookie CSRF và hash phía server. Chỉ tự tạo một cookie/header giống nhau không vượt qua kiểm tra hash.
- Origin phải trùng server hoặc nằm trong allowlist chính xác. Allowlist Origin không tự bật CORS; dùng proxy cùng origin cho local.
- Response credential có `Cache-Control: no-store`, `Pragma: no-cache`. Refresh 401 xóa cookie; CSRF 403 không xoay token và không xóa cookie hợp lệ.
- Thứ tự xử lý refresh: Origin → rate limit → CSRF cookie/header → tra refresh token. Thiếu cả cookie/header trả **403**; token refresh thiếu/không hợp lệ sau bước CSRF trả **401**. API Bearer được bảo vệ vẫn trả 401 khi thiếu xác thực.

## 3. Vòng đời, đồng thời và thu hồi

Mỗi lần login tạo family riêng. Refresh token và CSRF là giá trị ngẫu nhiên 256 bit; DB chỉ lưu SHA-256. Rotation đánh dấu token cũ đã consumed rồi phát token mới trong cùng giao dịch. Thời hạn family là tuyệt đối; refresh không kéo dài thời hạn này.

Các thao tác khóa account trước, rồi session; refresh đọc lại trạng thái sau khi có khóa. Phát lại refresh đã consumed thu hồi cả family và commit việc thu hồi dù trả 401. Hai yêu cầu đồng thời dùng cùng token cho đúng một lần thành công, sau đó family bị thu hồi do phát hiện reuse. Frontend cần phối hợp refresh giữa các tab; cơ chế Frontend hiện tại đã có phần phối hợp này.

Rotation bình thường không lập tức thu hồi access JWT trước đó của cùng family. Logout, reuse, hết hạn family, account không ACTIVE hoặc token_version lệch khiến access JWT có `sid` bị từ chối. Đổi mật khẩu, reset mật khẩu, đổi role/status vẫn dùng cơ chế token_version hiện có. JWT cũ phát hành trước V20 không có `sid` tiếp tục theo TTL/version cũ; logout-all tăng version nên vô hiệu chúng. Logout một phiên yêu cầu JWT có ràng buộc phiên.

## 4. Database và điểm triển khai

| Nguồn | Trách nhiệm |
|---|---|
| [V20](../../../src/main/resources/db/migration/V20__f01_refresh_sessions.sql) | `auth_session`: account, version, expiry, revoked; `auth_refresh_token`: hash, family, csrf_hash, consumed; FK và indexes |
| [RefreshSessionService](../../../src/main/java/vn/edu/fpt/paspm/auth/RefreshSessionService.java) | Transaction, row locks, rotation/reuse và logout |
| [SessionHttpSupport](../../../src/main/java/vn/edu/fpt/paspm/auth/SessionHttpSupport.java) | Cookie, CSRF pair, Origin, cache headers |
| [SessionController](../../../src/main/java/vn/edu/fpt/paspm/auth/SessionController.java) | Refresh, logout, logout-all |
| [AuthController](../../../src/main/java/vn/edu/fpt/paspm/auth/AuthController.java) và [AuthService](../../../src/main/java/vn/edu/fpt/paspm/auth/AuthService.java) | Login email/Google tạo family; JWT gắn `sid` |
| [SecurityConfig](../../../src/main/java/vn/edu/fpt/paspm/security/SecurityConfig.java) | Xác minh account/version và family của JWT; giữ nguyên role gates |

V20 là migration mới; V1–V19 không chỉnh sửa. Family tham chiếu account, không chứa Store ID do client cung cấp. Quyền Store tiếp tục lấy từ account/StoreScope; bảng phiên không thay thế RBAC hoặc Store isolation.

## 5. Tác động tới feature khác

| Feature | Tác động xác thực | Phần nghiệp vụ không thay đổi trong đợt này |
|---|---|---|
| F02 | JWT bị thu hồi/hết hạn không được đọc hay sửa catalog; role gate và StoreScope vẫn áp dụng | Category/Product/Batch/Summary, giá, stock, expiry và persistence |
| F03 | Client khôi phục Bearer rồi thực hiện đúng API của role/Store | Công thức Weight, Product expiryPushDays, snapshot bất biến và dữ liệu lịch sử |
| F04 | Import/list/detail vẫn cần Bearer hợp lệ và quyền Store hiện hành | Định dạng file, validation, idempotency và transaction rollback |
| F05 | API quản trị/đọc tiếp tục kiểm tra role và JWT như trước; worker nội bộ không dùng cookie trình duyệt | Principal nội bộ, lease/fencing, durable dispatch, cấu hình và gate scheduler |
| F06 | Quyền generate/review/config vẫn theo role và Store hiện tại | Rule engine, live eligibility, policy đã lưu và bằng chứng F03/F05 |

`END_CUSTOMER` đăng nhập được không có nghĩa được cấp F02 hoặc F06. UI storefront hiện có thể hiển thị dữ liệu demo; kiểm thử phiên không phải nghiệm thu API mua hàng, catalog công khai hay gợi ý dành cho Customer. Thay đổi phiên không tạo endpoint nghiệp vụ Customer mới.

## 6. Cấu hình local và vận hành

Trong IntelliJ Run Configuration → Environment variables khi dùng HTTP local:

```text
PASPM_SESSION_COOKIE_SECURE=false
PASPM_SESSION_ALLOWED_ORIGINS=http://localhost:3000,http://127.0.0.1:3000
```

`PASPM_SESSION_REFRESH_TTL_SECONDS` mặc định 604800, giới hạn 60–2592000. HTTPS giữ Secure=true. Spring Boot không tự đọc `.env`; tham khảo [setup chung](../../setup/LOCAL_SETUP_COMMON.md#browser-session-integration-working-tree-update-2026-11-11). Không đưa secret vào VITE_* hoặc commit cấu hình máy cá nhân.

Rate limiter vẫn in-memory từng instance (refresh 60/IP/phút). Chưa có cleanup tự động bản ghi consumed/expired. Bảy ngày là mặc định kỹ thuật có thể cấu hình; retention, ingress HTTPS và throttling nhiều replica cần chính sách vận hành riêng.

## 7. Bằng chứng và giới hạn

Đợt implementation trước đã chạy: Java17 Maven verify **578 discovered / 388 PASS / 190 SKIPPED**, zero failure/error; nhóm độc lập MySQL Docker **27 PASS / 0 SKIPPED**. Trong 190 ca skip mặc định, 23 ca được thực thi riêng trong nhóm Docker; 167 ca còn lại không được đợt đó chứng nhận. Không cộng hai lượt như các test hoàn toàn khác nhau.

Chromium thật: login → `/storefront`, reload → refresh 200 và me 200, logout → 200, quay về `/login`, không còn cookie phiên. Đây là bằng chứng local từ đợt implementation; **phiên cập nhật docs này không chạy lại Maven, Swagger Execute hoặc browser**.

- [Báo cáo thực thi](../../testing/reports/F01_SESSION_VERIFICATION.md)
- [Số lượng theo suite](../../testing/reports/F01_SESSION_TEST_COUNTS.json)
- [Bằng chứng browser đã loại credential](../../testing/reports/F01_SESSION_BROWSER_EVIDENCE.json)
- [Record implementation](../../record-of-changes/2026-11-11/01-f01-session-implementation.md)
- [Record đồng bộ tài liệu](../../record-of-changes/2026-11-11/02-f01-documentation-sync.md)

Google thật, HTTPS ingress, auth nhiều JVM, các nhóm opt-in Linux/multi-container ngoài phạm vi không được tuyên bố PASS. Bản local chưa commit, chưa merge và chưa deploy.

## 8. Backlog diagram riêng

Chưa thực hiện trong phiên docs:

- ERD physical/logical: thêm auth_session/auth_refresh_token của V20 theo migration hiện có, hash-only và FK tới app_user; không đổi quan hệ nghiệp vụ của các feature khác.
- Sequence/activity đăng nhập: bốn trường Token, phát cookie, refresh rotation, replay/family revocation, logout và logout-all.
- Architecture/security: phân biệt Bearer cho HTTP business API với cookie/CSRF ở route phiên; giữ execution context nội bộ của scheduler tách khỏi phiên trình duyệt.

Đây là backlog trình bày implementation đã có, không phải quyền sửa diagram hoặc thiết kế lại database trong task này.

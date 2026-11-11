# Record of Changes — 2026-11-11

| Trường | Giá trị |
|---|---|
| Daily ID | `ROC-20261111` |
| Date / timezone | 2026-11-11 / Asia/Ho_Chi_Minh |
| Project | PASPM Backend |
| Branch / base HEAD | `dev` / `5b30eb1` |
| Agent | Codex |
| Feature scope | F01; tài liệu phụ thuộc F02–F06 |
| Release status | **FEATURE_BRANCH** — đã push bản local; merge vào dev chờ reconciliation V20 |
| Record status | COMPLETED — tổng hợp các phiên có bằng chứng bên dưới |

## 1. Summary

Sửa lỗi Backend/Frontend không khớp hợp đồng phiên: thêm refresh/CSRF/logout, migration V20 và xác minh Customer login thực tế. Sau đó đồng bộ feature/API/handoff/setup docs và chuẩn hóa nhật ký thay đổi. Ngày trong bảng là ngày thực hiện có bằng chứng; không gán giờ bắt đầu/kết thúc khi chưa ghi nhận.

## 2. Session records

| ID | Record | Type | Verification |
|---|---|---|---|
| `ROC-20261111-001` | [01 — F01 session implementation](01-f01-session-implementation.md) | Implementation / Security / Database | Local Maven, MySQL và browser đã thực thi; giới hạn ghi trong record |
| `ROC-20261111-002` | [02 — F01 documentation sync](02-f01-documentation-sync.md) | Documentation | Kiểm tra docs/link/hash; không chạy lại runtime |
| `ROC-20261111-003` | [03 — Record organization](03-record-organization.md) | Documentation / Organization | Kiểm tra cấu trúc, ID, link và bảo toàn source |
| `ROC-20261111-004` | [04 — Publication / merge blocker](04-feature-publication-and-merge-blocker.md) | Git integration | Fresh verify 387 PASS / 190 SKIPPED; nhánh đã push; merge chờ quyết định |

## 3. Added

- POST `/api/auth/refresh`, `/api/auth/logout`, `/api/auth/logout-all`.
- Session family, hashed refresh/CSRF credentials, phát hiện reuse và thu hồi phiên phía server.
- Session integration/MySQL/cookie tests; session feature guide và bằng chứng kiểm thử đã loại credential.
- Thư mục record theo ngày, metadata/ID ổn định, mẫu chuẩn và mục lục.

## 4. Changed

- HTTP email/Google login trả Token có `accessToken`, `expiresIn`, `tokenType`, `csrfToken` và phát cookie phiên.
- JWT mới có `sid`; SecurityConfig xác minh family/account/version, giữ nguyên role/Store isolation.
- F01 contract/handoff/setup và dependencies F02–F06; OpenAPI local có 92 operations.
- Các migration test kỳ vọng schema latest V20; không sửa SQL migration đã áp dụng.
- Bản ghi 01/02 chuyển vào thư mục ngày; giữ nội dung lịch sử, cập nhật metadata và đường dẫn liên kết.

## 5. Fixed

- Lỗi Frontend báo invalid session credentials dù tài khoản hợp lệ do thiếu csrfToken và session endpoints.
- Cấu hình avatar còn thiếu làm LocalAvatarObjectStorage không khởi tạo được; file cấu hình vốn có thay đổi local khác, không nhận toàn bộ diff là thay đổi của agent.
- Mô tả OpenAPI của login/google/refresh kế thừa Bearer không đúng; thêm test và khai báo public chính xác.
- Canonical F01 docs thống nhất registration 202 cho email mới / 409 cho email đã tồn tại.

## 6. Security

- Refresh HttpOnly, CSRF cookie/header + stored hash, Origin validation và no-store.
- Secure `__Host-` cookie là mặc định HTTPS; cấu hình HTTP loopback riêng cho local.
- Row locking, rotation/replay revocation, logout theo family và logout-all tăng account token_version.
- Không cấp thêm quyền Customer cho F02/F06; không đưa secret, token/OTP thực hoặc dump vào nhật ký.

## 7. Database

- Added: [V20__f01_refresh_sessions.sql](../../../src/main/resources/db/migration/V20__f01_refresh_sessions.sql).
- Bảng mới: `auth_session`, `auth_refresh_token`; không đổi V1–V19.
- Đã backup V19 local và thử trên bản sao trước khi Backend phát triển migrate lên V20.
- Không repair/reset database nguồn V5 hoặc rewrite snapshot/transaction/mining history. Các recovery test dùng schema/container synthetic độc lập.

## 8. Verification

| Phiên / phương pháp | Kết quả đã ghi nhận | Nguồn |
|---|---|---|
| 01 — Maven verify / Java17, SPMF child Java25 | 578 discovered; **388 PASS / 190 SKIPPED**, 0 failure/error | [Counts](../../testing/reports/F01_SESSION_TEST_COUNTS.json) |
| 01 — Docker MySQL riêng | **27 PASS / 0 SKIPPED**, không lỗi; có test trùng với lượt Maven | [Report](../../testing/reports/F01_SESSION_VERIFICATION.md) |
| 01 — Chromium thật | Login → storefront → reload/refresh → logout; các request auth trả 200, cookie bị xóa sau logout | [Browser evidence](../../testing/reports/F01_SESSION_BROWSER_EVIDENCE.json) |
| 02 — Docs checks | 21 Markdown files, 323 local links; 266 non-doc paths không đổi; OpenAPI kiểm tra tĩnh 92 operations | [Record 02](02-f01-documentation-sync.md) |
| 03 — Record structure checks | Xem kết quả sau sắp xếp trong record 03; không chạy lại Maven/MySQL/browser | [Record 03](03-record-organization.md) |

Không cộng hai lượt thành tổng số ca độc lập. Trong 190 test skip mặc định, 23 ca đã chạy PASS riêng trong nhóm Docker; còn 167 ca không được đợt này chứng nhận. Các lỗi ban đầu và retest giữ trong record 01/report, không đổi failure cũ thành PASS.

## 9. Compatibility and related features

F02–F06 giữ nguyên API nghiệp vụ, role gate và StoreScope; snapshot lịch sử không đổi; internal scheduler không dùng cookie browser. JWT legacy không có sid vẫn theo TTL/token_version cũ; logout-all invalidates qua token_version. Đây là thay đổi hợp đồng HTTP login, nên client phải nhận thêm csrfToken và xử lý cookie/refresh đúng [session guide](../../features/F01/F01_SESSION_MANAGEMENT.md).

## 10. Known limitations / follow-up

- NOT RUN: Google thật, production HTTPS ingress, auth nhiều JVM và các nhóm opt-in Linux/multi-container ngoài phạm vi đã ghi.
- Chưa có cleanup session tự động và shared refresh limiter; TTL bảy ngày là mặc định kỹ thuật, không tự coi là PO-approved retention policy.
- Storefront có thể vẫn dùng dữ liệu demo; xác minh phiên không chứng minh API mua hàng/public recommendation hoàn chỉnh.
- Diagram session/V20 cần task riêng; không sửa Draw.io/ERD trong các phiên docs.
- Không dựng nhật ký cho các phiên cũ hơn khi thiếu bằng chứng.

## 11. Publication update / correction

Đã push feature commit `9d33d88`. Fresh clean verification: 577 discovered / 387 PASS / 190 SKIPPED; số 578/388 của record trước có một stale XML, xem [đính chính](../../testing/reports/F01_SESSION_VERIFICATION.md#publication-verification-and-correction--2026-11-11). Không sửa lịch sử các lượt cũ thành PASS mới. Origin/dev `42b8a9c` có V20 khác; merge đang BLOCKED_PENDING_RECONCILIATION_DECISION.

## 12. Delivery (historical status before publication)

Không commit, push, merge hoặc deploy. Các sửa source local có từ phiên implementation được bảo toàn. Approved requirements, Frontend, diagram và report lịch sử không bị thay đổi nghiệp vụ bởi thao tác chuẩn hóa record.

[Mục lục toàn bộ ngày](../README.md) · [Template](../TEMPLATE.md) · [F01](../../features/F01/README.md) · [API changelog](../../api/API_CHANGELOG.md).

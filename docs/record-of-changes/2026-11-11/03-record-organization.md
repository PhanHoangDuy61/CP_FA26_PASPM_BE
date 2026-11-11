# ROC-20261111-003 — Chuẩn hóa record theo ngày

| Trường | Giá trị |
|---|---|
| Record ID | `ROC-20261111-003` |
| Date / timezone | 2026-11-11 / Asia/Ho_Chi_Minh |
| Agent | Codex |
| Type | Documentation / Organization |
| Features | F01 và navigation hồ sơ F01–F06 |
| Branch / base HEAD | `dev` / `5b30eb1` |
| Execution status | COMPLETED — chuẩn hóa và kiểm tra tài liệu |
| Release status | UNRELEASED — không commit/push/merge/deploy |
| Daily summary | [2026-11-11](README.md) |

## 1. Request and scope

Yêu cầu: “sắp xếp nội dung hôm nay các thay đổi vào trong folder có format chuẩn record of change nhé”. Chỉ chuẩn hóa hồ sơ ngày hiện tại và các link tới record; không thay đổi implementation/evidence hoặc dựng lịch sử phiên chưa có bằng chứng.

## 2. Change details

### Added

Daily README tổng hợp mọi thay đổi đã ghi nhận ngày 2026-11-11, chia Added/Changed/Fixed/Security/Database/Verification/Compatibility/Limitations/Delivery. Record 03 ghi chính thao tác chuẩn hóa này.

### Changed

Chuyển record 01/02 từ thư mục gốc vào thư mục ngày; thêm metadata thống nhất và ID ổn định, giữ nội dung lịch sử. Mục lục gốc chuyển sang tổ chức theo ngày; template dùng chung format metadata và tám phần. Cập nhật inbound/relative links.

### Fixed

Phân biệt daily summary với hồ sơ chi tiết từng phiên; tách rõ các kết quả kiểm thử cũ và kiểm tra tổ chức docs trong phiên này.

### Security

Không ghi credentials, token, CSRF thực, OTP, dump hoặc nội dung .env. Không thay đổi cơ chế xác thực.

### Database

N/A — không truy cập database, không sửa migration/schema/data.

### Documentation

Canonical API contract, feature docs và báo cáo kiểm thử vẫn ở thư mục hiện tại; record chỉ tóm tắt và liên kết. Không nhân bản chúng vào folder nhật ký.

## 3. Files changed

| Path | Action | Nội dung |
|---|---|---|
| [Parent index](../README.md) | Modified | Cấu trúc theo ngày, ID, mục lục |
| [Template](../TEMPLATE.md) | Modified | Metadata và format thống nhất |
| [Daily summary](README.md) | Added | Toàn bộ thay đổi hôm nay, evidence và giới hạn |
| [Record 01](01-f01-session-implementation.md) | Moved / metadata normalized | Implementation; giữ kết quả cũ |
| [Record 02](02-f01-documentation-sync.md) | Moved / metadata normalized | Docs sync; giữ kiểm tra cũ |
| [Record 03](03-record-organization.md) | Added | Phiên sắp xếp hiện tại |
| [F01 README](../../features/F01/README.md), [session guide](../../features/F01/F01_SESSION_MANAGEMENT.md) | Modified links only | Trỏ tới vị trí record mới |

## 4. API and compatibility impact

Không đổi API/DTO/RBAC, source, migration, OpenAPI hoặc dữ liệu lịch sử. Đường dẫn record 01/02 thay đổi; toàn bộ link nội bộ đã được rà soát để chuyển tới folder ngày. Không tạo redirect/duplicate record có thể gây mâu thuẫn.

## 5. Verification executed in this session

Đã thực thi validator cấu trúc/link/hash cục bộ và `git diff --check`:

| Kiểm tra | Kết quả |
|---|---|
| 8 tài liệu Markdown trong phạm vi | PASS |
| 90 liên kết nội bộ tới record/source/evidence | PASS, không thiếu đích |
| Tham chiếu đường dẫn record cũ trong docs | 0 còn sót |
| IDs 001/002/003, daily summary categories | PASS, đủ và không trùng |
| 269 đường dẫn ngoài docs so với đầu phiên | Hash/trạng thái giữ nguyên |
| `git diff --check` | Exit 0 |
| Maven / MySQL / browser / CI | NOT RUN — không cần chạy lại cho thao tác docs-only |

Lệnh validator thực tế: `python3 /private/tmp/paspm-validate-record-layout.py`; script tạm chỉ phục vụ kiểm tra phiên này, không phải dependency của nhật ký.

## 6. Previous evidence referenced

Maven 388 PASS / 190 SKIPPED; Docker 27 PASS / 0 SKIPPED; browser auth 200 ở record 01 và evidence chuẩn. Docs check 323 links/266 non-doc paths ở record 02 là kết quả phiên trước, không phải kết quả tự động được mang sang phiên này.

## 7. Limitations and next actions

Chưa backfill các ngày khác. Không chạy lại Maven/MySQL/browser/CI cho thao tác chỉ sắp xếp tài liệu. Các giới hạn nghiệp vụ/production của F01 vẫn theo record 01; không phát sinh approval mới.

## 8. Delivery

Không commit, push, merge hoặc deploy. Bảo toàn source/config/test và diagram ngoài phạm vi. Daily summary và parent index được cập nhật cho cả ba record.

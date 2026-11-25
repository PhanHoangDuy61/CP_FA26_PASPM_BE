# ROC-YYYYMMDD-NNN — Tên thay đổi

| Trường | Giá trị |
|---|---|
| Record ID | ROC-YYYYMMDD-NNN |
| Date / timezone | YYYY-MM-DD / timezone thực tế |
| Agent | Chỉ ghi thông tin biết được |
| Type | Implementation / Bugfix / Security / Database / Documentation / Organization |
| Features | Các feature thực sự ảnh hưởng |
| Branch / base HEAD | Branch và HEAD nền; không nhận là commit chứa sửa local |
| Execution status | IN_PROGRESS / COMPLETED / PARTIAL / BLOCKED |
| Release status | UNRELEASED / COMMITTED / PR_OPEN / MERGED / DEPLOYED — chỉ khi xác minh |
| Daily summary | [README của ngày](README.md) |

## 1. Request and scope

Yêu cầu người dùng, mục tiêu, nguồn requirement/quyết định và phạm vi được phép. Ghi source có sửa sẵn cần bảo toàn. Không nhận mặc định kỹ thuật là business approval.

## 2. Change details

Chỉ điền các nhóm thực sự có thay đổi; nhóm không dùng ghi N/A.

### Added

### Changed

### Fixed

### Security

### Database

### Documentation

## 3. Files changed

| Path | Added / Modified / Moved / Deleted | Nội dung / lý do |
|---|---|---|
| Link tương đối đúng từ thư mục ngày | Loại thay đổi | Mô tả |

## 4. API and compatibility impact

Endpoint/DTO/validation/RBAC/Store isolation/cookie và dữ liệu lịch sử ảnh hưởng gì? Có yêu cầu migration hoặc thay đổi client nào? Không đổi thì ghi rõ.

## 5. Verification executed in this session

| Command / method | Environment | Actual result | Evidence |
|---|---|---|---|
| Lệnh thực tế hoặc NOT RUN | Môi trường | PASS / FAIL / SKIPPED / BLOCKED / NOT RUN, số lượng | Link tương đối |

## 6. Previous evidence referenced

Nguồn, ngày, commit/schema, số lượng và giới hạn. Không nhận bằng chứng cũ là vừa thực thi. Không cộng các lượt có test trùng thành một tổng độc lập.

## 7. Limitations and next actions

Failure gốc và retest; phần chưa xác minh; quyết định chờ duyệt; diagram backlog và việc người nhận cần làm. Không chứa secrets.

## 8. Delivery

Commit/PR/merge/deploy đã thực hiện và có bằng chứng hoặc NOT PERFORMED. Nêu trạng thái source cuối phiên và các file ngoài phạm vi được giữ nguyên. Cập nhật daily README và mục lục thư mục cha.

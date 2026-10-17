# F03 → F04/F05 Handoff

## Dữ liệu bất biến

F05 nhận snapshotId, gọi GET metadata/matrix bằng quyền và Store scope phù hợp. Snapshot lưu analysis date, T, policy version/bounds, selected lambda, coefficients, scoring/config versions, scores, source/matrix hashes. Không tính lại từ cấu hình hiện tại. Rows có transactionId, productId, algorithmItemId=productId (PRODUCT_ID_V1), quantity, base utility, S/E/M, expiry status, W, weighted utility và TU. Transaction totals được suy từ các row đã đóng băng; pagination không đổi dataset total.

## Số học

Item utility được làm tròn HALF_UP sáu chữ số thập phân trước khi cộng TU và dataset total. Handoff scaled integer = utility ×10^6, phải chuyển chính xác sang signed64bit. Overflow item/TU/dataset bị400. Nếu engine F05 chỉ nhận32bit, cần chốt adapter riêng; không âm thầm scale hoặc clamp. F03 không triển khai F05 algorithms, minUtil hay mining execution.

## Adapter giao dịch F04 còn thiếu

Implement TransactionSource trong module sở hữu dữ liệu thật: load(Store,rangeStart,rangeEnd) trả transactionId,Store,date,eligible và các item productId,quantity>0,giá bán/giá vốn lịch sử hoặc source-native total item utility. Không dùng giá Product hiện tại. Adapter quyết định trạng thái giao dịch đủ điều kiện theo hợp đồng F04, phải xử lý duplicates trước khi cung cấp; F03 reject duplicate Product trong cùng prepared transaction, foreign Store, malformed/negative utility. Range inclusive; analysis date tách khỏi transaction date.

Default provider hiện trả503. UtilityFixtureSource chỉ hoạt động với cả dev và utility-fixture, yêu cầu credential/ngày QA rõ ràng; không phải nguồn production. F04 bổ sung FK utility_matrix_item.transaction_id khi SALES_TRANSACTION tồn tại, kèm kiểm tra dữ liệu/migration riêng. F05 bổ sung MINING_REQUEST.utility_snapshot_id FK trong migration của F05. Không tạo các bảng thuộc module chưa có để giả hoàn tất ERD.

## Giới hạn còn mở

Production eligible-status mapping và adapter dữ liệu thật phụ thuộc F04. Kiểu số consumer32bit nếu có phải được thống nhất với F05. Quy tắc expiry/missing data của F03 đã chốt và kiểm thử; không còn quyết định nghiệp vụ T/lambda/E chưa được chốt trong phạm vi hiện tại.

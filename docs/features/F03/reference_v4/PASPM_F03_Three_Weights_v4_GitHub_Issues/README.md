# PASPM — F03 Utility Profiling: Backend API + Swagger (v4)

**Nguồn:** ERD v3 có INVENTORY_BATCH; cập nhật v4 cho 3 tiêu chí Weight. Bổ sung chỉ tiêu do nhóm đề xuất, không phải tất cả chi tiết đã được chốt trong FA26_SE048.

**Phạm vi:** Migration/Backend/API/Swagger manual verification. Không làm FE, Postman, E2E, không chạy HUI-Miner/EFIM (thuộc F05), không import CSV (thuộc F04), không tính minUtil (thuộc F05).

**Công thức:** W_i = 1 + lambda × (alpha×S_i + beta×E_i + gamma×M_i); weightedUtility = baseUtility × W_i.
- S_i: điểm tồn kho do Merchandiser nhập [0,1]; KHÔNG phải số lượng tồn kho.
- M_i: điểm mùa vụ do Merchandiser nhập [0,1].
- E_i: tính tự động từ INVENTORY_BATCH theo ngày snapshot; đề xuất weighted mean theo remaining_quantity của lô bán được.
- Enabled weights cộng thành 1; tắt tất cả → W=1. Lambda 0–0.5 và T=30 ngày chỉ là đề xuất cần chốt.

**ERD v4:** PRODUCT thêm hai điểm S,M. STORE.utility_config giữ config tổng. INVENTORY_BATCH giữ nguyên. UTILITY_SNAPSHOT và UTILITY_MATRIX_ITEM mới lưu matrix bất biến. MINING_REQUEST tham chiếu utility_snapshot_id. Không thêm PRODUCT_LOT.

**Lưu ý nghiệp vụ:** Không xóa giao dịch lịch sử chỉ vì hôm nay sản phẩm hết hạn/hết hàng. Tình trạng còn được bán phải được truyền đến F06 để lọc recommendation. Nếu metadata bắt buộc bị thiếu, Preview báo warning và Snapshot từ chối khi enableExpiry.

**Thứ tự:** F03-01 → 02 → (03, 04, 05, 10) → 06 → 07 → 08 → 09.

- [F03-01.md](F03-01.md): Utility Schema & Data Contracts
- [F03-02.md](F03-02.md): Weight Configuration APIs
- [F03-03.md](F03-03.md): Manual Stock Priority Score API
- [F03-04.md](F03-04.md): Expiry Score from INVENTORY_BATCH
- [F03-05.md](F03-05.md): Base Utility Calculator
- [F03-06.md](F03-06.md): Weighted Utility & Matrix Builder
- [F03-07.md](F03-07.md): Utility Preview API
- [F03-08.md](F03-08.md): Immutable Snapshot & Matrix APIs
- [F03-09.md](F03-09.md): Swagger Verification & F05 Handoff
- [F03-10.md](F03-10.md): Manual Seasonal Priority Score API
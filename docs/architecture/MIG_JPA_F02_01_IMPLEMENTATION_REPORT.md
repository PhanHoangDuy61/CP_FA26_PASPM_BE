# MIG-JPA-F02-01 — Product Metadata Pilot Implementation Report

Ngày: 2026-10-22, Asia/Ho_Chi_Minh. **APPROVED / IMPLEMENTED / TESTED trên isolated MySQL simulation.** Branch `Khoi/Fear/jpa-product-metadata-pilot`, base commit `3d58a73`; chưa merge/push/deploy. Xem [CORE-00 report](MIG_JPA_CORE_00_IMPLEMENTATION_REPORT.md) cho manager/build/approval.

## Code diff và ownership

| File | Responsibility |
|---|---|
| `catalog/product/persistence/ProductEntity.java` | Package-private @Entity product, IDENTITY INT, metadata field mapping BigDecimal12,2, scalar store/category IDs, @DynamicUpdate |
| `catalog/product/persistence/ProductJpaRepository.java` | Package-private limited Spring Data Repository, scoped finder và scoped status JPQL; không generic findAll/findById/save/delete |
| `catalog/product/persistence/ProductMetadataPersistence.java` | Transactional, translated write adapter; persist/managed update, refresh/flush rõ ràng |
| `catalog/product/ProductRepository.java` | Giữ facade/signatures; write delegate sang JPA; SQL find/search/stock projection/expiryless-batch lookup giữ nguyên |
| `ProductPersistenceIntegrationTest.java` / `ProductPersistenceMySqlIntegrationTest.java` | Mapping, shared transaction, commit/rollback, native refresh, race, FK, RR proof |
| `persistence/PilotCloneTarget.java`, `PilotCloneSafetyTest.java`, `PilotCloneApiIntegrationTest.java` | Target guard và API/food-store tests trên isolated migrated copy |

Entity **không map stock_priority_score hoặc seasonal_priority_score**, không map inventory quantity, không relationship/cascade batch, không @Version. F03 tiếp tục sở hữu S/M; metadata edit không ghi vào inventory/history. Unit, required insert fields, nullable category, trimming, expiryPushDays default0 và price DECIMAL giữ nguyên. Store_id bất biến sau insert. Không expose entity trong HTTP DTO.

Create dùng persist+flush trước JDBC projection. Update dùng scoped managed lookup, refresh để bỏ stale native state, replace metadata và flush; không merge detached DTO. Status dùng column-only JPQL có scope, flush+clear; không ghi đè price từ entity stale. Duplicate/FK constraint errors phát sinh trong translated repository boundary giữ DataIntegrityViolationException và HTTP409 format hiện hành. Foreign category API vẫn404 trước DB write.

## API behavior và schema

API unchanged: GET/POST `/api/products`; GET/PUT `/api/products/{id}`; PATCH `/api/products/{id}/status`. ProductService/ProductController/CatalogContracts không sửa. Category, Inventory Batch, Summary, F03/F04/F05/F06 API không migrate.

Runtime OpenAPI trước/sau: **59 paths**, paths/components/DTO/security metadata giống nhau sau khi bỏ `servers` có cổng runtime khác nhau. Không sửa handoff/API contract. [Hash comparison](../testing/evidence/jpa_pilot/OPENAPI_COMPARISON.json).

9 hành vi được chạy trên original baseline JDBC artifact rồi trên JPA implementation với cùng synthetic contract fixture trên disposable target: create201, get200, update200, status200, duplicate409, foreign404, no-auth401, negative-price400, search200. Responses/statuses giống nhau sau normalize generated IDs. [Actual comparison](../testing/evidence/jpa_pilot/PRODUCT_CONTRACT_COMPARISON.json), [before](../testing/evidence/jpa_pilot/PRODUCT_CONTRACT_BEFORE.json), [after](../testing/evidence/jpa_pilot/PRODUCT_CONTRACT_AFTER.json).

Additional clone tests: cross-store mutations404, Merch cannot write Product403, foreign category404, S/M patch by Merch preserved, batch/detail/summary unchanged after metadata+status updates. Food dataset gồm milk/bread/coffee, date-relative batches, stock33/sellable24/expired4/blocked5/soon14; quantity8→3 cho stock28/sellable19/expired4/blocked5/soon9.

## Transaction / concurrency proof

- Existing managed Product name UPDATE + existing batch quantity JDBC UPDATE cùng commit; read independent connection thấy cả hai.
- Controlled exception sau flush + native UPDATE: independent connection thấy **cả name lẫn quantity gốc**, không chỉ assert exception.
- Native JDBC write được thể hiện stale trong EntityManager rồi em.refresh cập nhật đúng; adapter refresh bảo toàn requested metadata ngay cả khi request bằng stale snapshot cũ.
- Concurrent F03 native score writer và metadata writer, 5 interleavings trên mỗi H2/MySQL: S/M không mất.
- Stale managed Product + independent score/price update + scoped status mutation, 5 interleavings mỗi DB: price/S/M giữ nguyên.
- FK composite Store+Category bị vi phạm trực tiếp qua adapter: translated exception, row count/SKU giữ nguyên; API prevalidation foreign category404 vẫn pass.
- REQUIRES_NEW và RR/RC proofs: xem CORE report và named test cases trong evidence.

## Minimal measurement — không kết luận nhanh hơn

25 GET requests sau5warmup qua MockMvc + real MySQL, same synthetic scenario, hai process riêng: baseline median5.094ms/p95 14.696ms; JPA median2.216ms/p95 3.905ms. Đây chỉ smoke measurement cục bộ, mẫu nhỏ, khác warmup/cache/load; GET vẫn JDBC, **không phải bằng chứng JPA tăng hiệu năng**.

Metadata update mới có3 Hibernate prepared statements được đo (scoped entity SELECT + refresh SELECT + UPDATE), so với original metadata command một JDBC UPDATE. Count Hibernate **không gồm** auth/JDBC validation/projection queries và không phải tổng DB requests của endpoint. Chấp nhận hai SELECT thêm để xử lý identity/cache rõ ràng trong pilot; tối ưu sau chỉ khi có evidence và tests tương đương. Không đổi SQL list/detail để làm đẹp benchmark.

## Kết quả và next step

Final **274/274 PASS**, gồm existing232 + new42; Maven build và fresh/upgrade MySQL migrations pass. Independent reviewer không tìm thấy actionable production/safety defect; các gaps FK/RR/measurement đã bổ sung evidence. CodeGraph sau implementation discover ProductEntity/ProductJpaRepository/ProductMetadataPersistence và ProductRepository→adapter references; graph test-detection warning không thay actual JUnit results.

[Real-source/simulation verification](../testing/JPA_PILOT_REAL_DATA_VERIFICATION.md). Nguồn V5/rỗng giữ nguyên; không deploy hoặc migrate nguồn trong task. Real populated-product coverage **NOT RUN**, simulation alternative đã được người dùng duyệt. Không còn FAIL trong approved simulation scope. Các module ngoài Product vẫn JDBC và chưa được duyệt chuyển đổi.

Rollback CORE + Product adapter/entities đồng bộ về baseline JDBC; không schema rollback/backfill hoặc regeneration snapshot. Giữ branch chưa merge; đề nghị review/tích hợp pilot trước khi xét module khác.

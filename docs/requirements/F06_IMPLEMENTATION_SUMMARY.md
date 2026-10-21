# F06 — Báo cáo triển khai Recommendation Engine v1.1

**Trạng thái: Backend API + Swagger đã hoàn tất và kiểm chứng.** Nhánh `Khoi/Fear/F06-recommendation-engine`, tách từ `dev` tại `3f6faaa`. Policy package được người dùng duyệt ngày 2026-10-20. Chưa push hoặc merge vào dev trong công việc này.

## A. Implementation matrix

Không tìm thấy bộ issue F06-01..11 riêng trong repo; bảng sau là mapping công việc theo các stage của task đã giao, không giả định đã đọc một backlog không có sẵn.

| Task | Trạng thái | File/chức năng chính | Kiểm chứng |
|---|---|---|---|
| F06-01 | PASS | V10, năm bảng F06, ERD conceptual/logical/zoom | MySQL fresh/upgrade, five tables/13 FK |
| F06-02 | PASS | FrozenEvidenceReader/TransactionEvidence | source identity/hash/mapping/count, malformed/foreign source |
| F06-03 | PASS | BUNDLE full-group rule | size2..4 default, max limit, singleton skip |
| F06-04 | PASS | Directional CROSS_SELL | fixture A→B2/3 vs B→A1/2, exact threshold comparison |
| F06-05 | PASS | FBT/BANNER | support count AND ratio; Banner singleton; missing evidence skip |
| F06-06 | PASS | Current eligibility/independent expiry | T0/3/7, day boundaries, expired/blocked/empty/missing/no mining/zero patterns |
| F06-07 | PASS | Ranking, explanation, candidate/source identity | current eligibility order, stable tie, both algorithms provenance, snapshot separation |
| F06-08 | PASS | Merchandiser review + CAS/history | concurrent single winner, rejected final, stock changes before/after approval |
| F06-09 | PASS | Eight HTTP operations + Store policy | roles, Store404/isolation, forged storeId400, config version/validation |
| F06-10 | PASS | Automated/MySQL/Swagger/OpenAPI |210 tests PASS,27 Swagger cases PASS |
| F06-11 | PASS (handoff) | API contract, frontend guide/examples, changelog/OpenAPI | runtime schema and actual examples; Frontend integration NOT RUN |

## B. Kiến trúc thực tế

```text
F01 identity / CatalogAccess / StoreScope
  -> RecommendationController -> RecommendationService
       -> FrozenEvidenceReader -> MiningRepository + persisted frozen_input
                              -> UtilitySnapshotRepository (read only)
       -> RecommendationRules -> TransactionEvidence
       -> ProductEligibilityService -> UtilityProductRepository + ExpiryScoreCalculator
       -> RecommendationRepository (F06 tables only)
  -> RecommendationReviewService -> versioned decision + review history
  -> RecommendationPolicyService -> own-Store versioned F06 config
  -> RecommendationExpiryController -> RecommendationExpiryService
       -> UtilityProductRepository + shared ExpiryScoreCalculator + UtilityTimeZone
```

Expiry flow không phụ thuộc Mining Run, patterns hoặc SPMF. Không thêm dependency Maven. Backend vẫn Java17. F05 giữ child Java25 riêng như bản P0 đã kiểm chứng.

## C. Hợp đồng F03/F05

F06 đọc run COMPLETED trong Store hiện tại, request/snapshot ID, frozen input text/hash/dense mapping/conversion, source/matrix/config hash và pattern_item. Kiểm tra count của run trùng đầy đủ source patterns. Mọi source bị hỏng hoặc không đủ nhất quán trả409, foreign/missing404. Không chạy lại mining, tính minUtil, Weight hay utility.

Membership đến từ immutable frozen input và được đối chiếu immutable utility_matrix_item, không lấy live sales để gán confidence cho kết quả quá khứ. F05 supportCount vẫn null. Các giá trị utility chỉ được đọc, không tính lại.

Fixture AB,AB,A,B,B,C được kiểm tra: supportA3, B4, AB2; ratioAB1/3; confidenceA→B2/3; B→A1/2. JSON hiển thị sáu chữ số; quyết định threshold so sánh count chính xác. Test live F04/Product edits xác nhận source input/config/hash/utility/evidence cũ không đổi.

## D. Rule và ranking

Defaults đã duyệt: Bundle tối đa4; FBT supportCount≥2 AND supportRatio≥0.05; Cross-sell confidence≥0.50; sellable quantity≥1/Product. Store Admin được cập nhật full policy có expectedVersion; chỉ áp dụng vào generation mới. Candidate giữ policy cũ.

BUNDLE dùng full pattern, không tự tạo giá. CROSS_SELL tạo từng anchor có hướng, target là productIds trừ anchor. FBT đòi bằng chứng co-occurrence đủ policy; BANNER là gợi ý duyệt, có thể là singleton, không tạo ảnh/layout. Các rule bị loại có skipped reason.

Rank chỉ khi giới hạn một miningRunId: current eligibility → utility DESC → support DESC → canonical group/type/anchor/identity. History chung theo thời gian/identity DESC, rank=null; không so utility khác snapshot. Không dùng score AI hoặc trọng số business tự đặt.

Dedup cùng snapshot/input/converted threshold/exact utility/policy/type/anchor; giữ mọi run/pattern source tương đương từ HUI-Miner/EFIM. Khác snapshot/policy không gộp; generate lại không mở lại decision đã xử lý.

## E. Independent Expiry Push

T=Product.expiryPushDays; null/0 disabled; âm invalid; không fallback Store window. Shared v1.2 E_i=1 khi T>0 và0<D_min≤T, batch flag dùng riêng D_batch. Chỉ ACTIVE Product và batch ACTIVE, remaining>0, expiryDate>Store-local today. Earliest expiry là FEFO, không phải receivedAt.

New expiry tests5 trên H2 và5 trên MySQL, cộng regression binary calculator, xác nhận boundaries/missing/non-expiring/no eligible/current date/Store404. UI01 đã trả batch D5/T7/qty10 trước khi có Mining Run; test zero-pattern run cũng PASS. Alert/status/warnings/checkedAt không bị che bởi mining hoặc ranking.

## F. Approval workflow

PENDING_REVIEW→APPROVED hoặc REJECTED, Merchandiser của Store hiện tại; reason bắt buộc, expectedVersion, actor/time và old/new-state history. Khóa row + CAS bảo đảm một winner khi hai request review đồng thời. Approval kiểm tra lại tồn bán được; thiếu expiry metadata hoặc không đủ tồn trả409. Không có API reopen.

UI09/UI10 chứng minh APPROVED/version2/history1 persisted; UI11/UI12 chứng minh REJECTED persisted; UI13 chặn reopen; UI18 chặn StoreAdmin; UI19/20 chặn cross-Store. Approved record vẫn giữ review status sau khi hàng đổi nhưng readyForConsumption=false khi không còn eligible. GET detail là check cho downstream; không reserve hàng và phải recheck khi activate/fulfill.

## G. Database và ERD

Thêm duy nhất `V10__f06_recommendations.sql`:
- recommendation_rule_config: cấu hình Store có version riêng, không đụng STORE.utility_config/F03 lambda policy.
- recommendation: candidate, snapshot/Store, type/anchor, identity, utility/evidence/policy/initial eligibility, explanation, review/version.
- recommendation_item: Products thuộc candidate, composite Store FK.
- recommendation_source: đầy đủ source run/pattern/provenance, composite Store FK.
- recommendation_review_history: transition/actor/reason/time/version/eligibility check bất biến.

Unique identity/history version, CHECK type/state/anchor/review fields/nonnegative values và13 FK. ERD mới nhất được cập nhật conceptual, logical annex và F06 logical zoom; F03 zoom và các entity/quan hệ F03/F05 giữ nguyên. XML/column/type/FK checks PASS; rendered visual QA NOT RUN.

Không sửa V1–V9. FlywayRecoveryTest thay expectation số migration latest thành V10; controlled repair trong regression vẫn chỉ là H2 test, không repair database triển khai.

## H. API đã kiểm chứng

| Method | Endpoint | Quyền |
|---|---|---|
| POST | /api/recommendations/generate | Merchandiser |
| GET | /api/recommendations | Merchandiser/Store Admin cùng Store |
| GET | /api/recommendations/{id} | Merchandiser/Store Admin cùng Store |
| POST | /api/recommendations/{id}/approve | Merchandiser |
| POST | /api/recommendations/{id}/reject | Merchandiser |
| GET | /api/recommendations/config | Merchandiser/Store Admin cùng Store |
| PUT | /api/recommendations/config | Store Admin |
| GET | /api/recommendations/expiry-push | Merchandiser/Store Admin cùng Store |

Cả tám operation success200. API input/response types, nullability, examples, validation, error400/401/403/404/409 và precision: [F06_RECOMMENDATION_API.md](../api/F06_RECOMMENDATION_API.md). [OpenAPI actual runtime](../api/openapi.json),59 paths/73 operations gồm8 F06. [React/TypeScript handoff](../frontend_handoff/F06_HANDOFF.md) và actual executed JSON fixtures; developer không cần đọc Java để tích hợp.

## I. Bằng chứng

- [Báo cáo chi tiết](F06_VERIFICATION.md):210 automated tests PASS (63 F06 +147 regression),27 Swagger UI cases PASS.
- [Automated results/all method names](evidence/f06-automated-results.json), [Swagger payload/response/time](evidence/f06-swagger-ui.json), [MySQL readback](evidence/f06-mysql-readback.txt), [ERD check](evidence/f06-erd-schema-check.json), [CodeGraph](evidence/f06-codegraph.json).
- CodeGraph dùng trước và sau triển khai; graph mới phát hiện RecommendationService→FrozenEvidenceReader→MiningRepository, ProductEligibilityService→UtilityProductRepository/ExpiryScoreCalculator và independent expiry controller/service.
- Core review approve; pattern-count defect được fix/retest. Concurrency và OpenAPI schema defects được tái hiện, fix và retest. Không giấu original failures trong báo cáo cuối.

## J. Quyết định còn lại và giới hạn

Không còn ngưỡng business chờ duyệt. Backend+Swagger hoàn tất; Frontend implementation/integration NOT RUN, handoff đã hoàn tất. Không đổi HUI-Miner/EFIM, F03/F05 calculation, F04 pipeline hay Frontend.

Giới hạn10000 candidates/generation và10000 scope để rank, có rollback/409 thay vì cắt bớt im lặng. Chưa có scheduler/reservation/auto-publish/campaign/discount/reopen. Database triển khai mang checksum V7 cũ cần đối soát/backup/approval theo recovery guide riêng; chưa rollout hoặc repair database triển khai trong task này.

## File manifest

Application source mới:
- `src/main/java/vn/edu/fpt/paspm/recommendation/FrozenEvidenceReader.java`
- `src/main/java/vn/edu/fpt/paspm/recommendation/ProductEligibilityService.java`
- `src/main/java/vn/edu/fpt/paspm/recommendation/RecommendationContracts.java`
- `src/main/java/vn/edu/fpt/paspm/recommendation/RecommendationController.java`
- `src/main/java/vn/edu/fpt/paspm/recommendation/RecommendationExpiryController.java`
- `src/main/java/vn/edu/fpt/paspm/recommendation/RecommendationExpiryService.java`
- `src/main/java/vn/edu/fpt/paspm/recommendation/RecommendationPolicyService.java`
- `src/main/java/vn/edu/fpt/paspm/recommendation/RecommendationRepository.java`
- `src/main/java/vn/edu/fpt/paspm/recommendation/RecommendationReviewService.java`
- `src/main/java/vn/edu/fpt/paspm/recommendation/RecommendationRules.java`
- `src/main/java/vn/edu/fpt/paspm/recommendation/RecommendationService.java`
- `src/main/java/vn/edu/fpt/paspm/recommendation/TransactionEvidence.java`

Tests mới:
- `src/test/java/vn/edu/fpt/paspm/recommendation/FrozenEvidenceReaderTest.java`
- `src/test/java/vn/edu/fpt/paspm/recommendation/RecommendationExpiryIntegrationTest.java`
- `src/test/java/vn/edu/fpt/paspm/recommendation/RecommendationExpiryMySqlIntegrationTest.java`
- `src/test/java/vn/edu/fpt/paspm/recommendation/RecommendationIntegrationTest.java`
- `src/test/java/vn/edu/fpt/paspm/recommendation/RecommendationMigrationTest.java`
- `src/test/java/vn/edu/fpt/paspm/recommendation/RecommendationMySqlIntegrationTest.java`
- `src/test/java/vn/edu/fpt/paspm/recommendation/RecommendationRulesTest.java`
- `src/test/java/vn/edu/fpt/paspm/recommendation/TransactionEvidenceTest.java`

Test cũ cập nhật: `src/test/java/vn/edu/fpt/paspm/deployment/FlywayRecoveryTest.java` (latest migration counts only).

Tài liệu: API contract, frontend handoff, API_CHANGELOG/openapi, latest ERD; requirements F06_RECOMMENDATION_RULES_v1_1, F06_IMPLEMENTATION_READINESS, F06_VERIFICATION, báo cáo này; kế hoạch `docs/superpowers/plans/2026-10-20-f06-recommendation.md`; F06_examples và F06 evidence. Cấu hình `application.yml`, file tạm và tài liệu F03 chưa tracked có sẵn không đưa vào thay đổi F06.

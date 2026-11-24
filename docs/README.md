# PASPM Documentation

## Project Overview

PASPM hỗ trợ quản lý sản phẩm, tồn kho, dữ liệu giao dịch, Utility, mining HUI-Miner/EFIM và các gợi ý có bằng chứng. Code của F01–F06 có trong `dev` tại `6601f62`; đây là mô tả phạm vi source, không phải tuyên bố nghiệm thu toàn bộ hệ thống.

Đọc [requirements và quy tắc thẩm quyền](requirements/README.md), [feature index](features/README.md) và [trạng thái tài liệu](DOCUMENTATION_MAP.md#classification-policy). F07–F11 chưa có feature map/đặc tả được track đầy đủ; không tự gán tên từ các work package.

Agent-facing product summaries (glossary, BRD, SRS, architecture): [product documents](product/README.md).

## Getting Started

[Local development](setup/LOCAL_DEVELOPMENT.md) · [setup index](setup/README.md) · [SPMF local](setup/F05_SPMF_RUNTIME_SETUP.md).

## Architecture

[Hybrid JPA + JDBC](architecture/README.md) · [diagrams](diagrams/README.md) · [V7 recovery](setup/FLYWAY_MYSQL_V7_RECOVERY.md) · [V5 historical matrix compatibility](architecture/LEGACY_UTILITY_MATRIX_COMPATIBILITY.md).

Product metadata và Category CRUD dùng JPA; Product catalog/inventory và analytical workloads giữ JDBC. Một transaction manager dùng chung datasource; Flyway của working tree hiện có V1–V21, bao gồm Java migration V11; V20 giữ bản đã có trên dev; V21 bổ sung CSRF binding theo quyết định reconciliation ngày2026-11-12.

## Features

| Feature | Name | Backend Status | API Docs | Requirements | Tests |
|---|---|---|---|---|---|
| [F01](features/F01/README.md) | Authentication/User/RBAC | Local session update: refresh/CSRF/logout, V20; registration 202/409; chưa commit | [API](api/F01_AUTH_USER_API.md) | [Scope](features/F01/README.md#1-purpose-and-scope) | [History](features/F01/README.md#9-tests-and-evidence) |
| [F02](features/F02/README.md) | Product/Category/Inventory | Source implemented; Product/Category JPA integrated | [API](api/F02_PRODUCT_INVENTORY_API.md) | [Reference v3](features/F02/reference_v3/PASPM_F02_Inventory_Batch_API_Swagger_Backlog_v3.md) | [Tests](features/F02/README.md#9-tests-and-evidence) |
| [F03](features/F03/README.md) | Utility/Weight Rules | Weight v2 (`W2_EXPIRY_SEASON_V1`) on branch `Khoi/feat/f05-progress-f03-weight-v2`, not merged; v1.2 legacy authority missing from dev | [API](api/F03_UTILITY_API.md) | [Gap](requirements/README.md#f03-v12-source-gap) | [Tests](features/F03/README.md#9-tests-and-evidence) · [W2 requirements (derived)](requirements/F03_WEIGHT_V2_REQUIREMENTS.md) · [owner decisions 2026-11-15](decisions/OWNER_DECISIONS_F03_F05_2026-11-15.md) |
| [F04](features/F04/README.md) | Transaction Import | File-based import; CSV (sync and async) and XLS/XLSX (sync) available; external ingestion excluded | [API](api/F04_TRANSACTION_API.md) | [Design](features/F04/reference/F04_DESIGN.md) | [History](features/F04/reference/F04_SWAGGER_TEST_REPORT.md) |
| [F05](features/F05/README.md) | EFIM Mining | Core implemented; scheduler off (503); System Admin Store reads gated (D1); local SPMF configuration required; EFIM-aware int32 guard on local feature branch | [API](api/F05_MINING_API.md) | [Scope](features/F05/README.md#1-purpose-and-scope) | [History](testing/F05_FOODMART_SCALABILITY_BENCHMARK.md) |
| [F06](features/F06/README.md) | Recommendation Engine | Backend v1.1 source implemented; downstream activation separate | [API](api/F06_RECOMMENDATION_API.md) | [Rules](requirements/F06_RECOMMENDATION_RULES_v1_1.md) | [History](testing/reports/F06_VERIFICATION.md) |
| [F09](features/F09/README.md) | Storefront MVP | Public read API for ACTIVE Stores by `store.slug`; V29 product display metadata; Related Products deferred | [API](api/F09_STOREFRONT_API.md) | [Scope](features/F09/README.md) | [Tests](api/F09_STOREFRONT_API.md#verification-scope) |

## API Reference

[API index](api/README.md) · [OpenAPI export](api/openapi.json) · [RBAC](api/F01_F02_RBAC_MATRIX.md). **2026-11-25:** with F09 merged the export has 111 paths / 138 operations (see the [API index](api/README.md)); the dated counts that follow are history. The checked-in runtime export was regenerated from the Spring Boot runtime on 2026-11-17 and contained 100 paths / 121 operations; the F04 Excel route (2026-11-19, [API index](api/README.md)) makes it 101 paths / 122 operations. The latest F05 branch changes `RequestView.totalScaledUtility` from int32 to int64 without changing routes; the branch is local and unpublished. Earlier operation counts and session reconciliation details remain dated evidence in the [API changelog](api/API_CHANGELOG.md). Swagger/framework routes are separate. The API Inventory package exists only on a separate local audit branch; see the index for provenance, not a broken link or an implied merge.

## Frontend Integration

**Start with [OPTION_B_BACKEND_API_HANDOFF.md](frontend_handoff/OPTION_B_BACKEND_API_HANDOFF.md)** for F03/F05 (roles, flows, what is open, gated or unavailable). Release status: [F03/F05 Core release readiness](reports/F03_F05_CORE_RELEASE_READINESS_2026-11-02.md).

[Frontend handoff index](frontend_handoff/README.md) links actual contracts and examples. Synthetic response fixtures retain their original dates and identifiers.

## Testing and Verification

[Testing index](testing/README.md) separates local execution, hosted CI, historical Swagger and real-data acceptance. This documentation reorganization does not run Maven/backend/Swagger tests or access any database. No previous PASS is promoted to current verification.

## Historical Documentation

[Archive](archive/README.md) holds original root README, old PR packages and dated delivery records. Original feature requirements remain in `features/F01–F05/reference*`; status and current authority are in their indexes and the [path map](DOCUMENTATION_MAP.md). Frozen evidence/JSON/manifests are not rewritten.

## Known Documentation Gaps

- Final F03 v1.2 source and some referenced examples are absent from tracked dev.
- F01 canonical contract/handoff now agree on 202/409 and the session lifecycle; older reference documents retain their historical wording. Some older F03/F05 handoffs still describe missing F06.
- Latest editable ERD omits V11 archive/routing details; migrations govern physical schema.
- CI/delivery reports contain dated pre-merge statements; current hosted status is not certified here.
- F07–F11 feature definitions and the separate API Inventory merge remain unresolved.

See [review register](requirements/README.md#documentation-review-register), [complete path map](DOCUMENTATION_MAP.md) and [reorganization validation report](audits/DOCS_REORGANIZATION_REPORT.md).

## Record of Changes — Agent sessions

[Nhật ký thay đổi theo phiên](record-of-changes/README.md) · [mẫu ghi nhận](record-of-changes/TEMPLATE.md). Bắt đầu từ bản ghi F01 ngày 2026-11-11; phân biệt thay đổi local, kiểm thử đã chạy, cập nhật docs, trạng thái commit và giới hạn. Agent các phiên sau cần cập nhật record khi bàn giao; không suy diễn các phiên cũ thiếu bằng chứng.

Current F01 reconciliation (2026-11-12): preserves published dev V20/AuthSessionService and adds V21 binding; [current evidence](testing/reports/F01_SESSION_RECONCILIATION_2026-11-12.md). Earlier local-variant details are dated history; no source database repair/deployment was performed.

## F03/F05 merge preparation — 2026-11-16

[Proposed merge plan](release/F03_F05_MERGE_PLAN.md) · [Record 007](record-of-changes/2026-11-16/07-cleanup-and-merge-plan.md). origin/dev is checked in record 007; publication, CI, review, FE coordination and deployment remain gated. No PR created or merge performed.

# PASPM Documentation

## Project Overview

PASPM hỗ trợ quản lý sản phẩm, tồn kho, dữ liệu giao dịch, Utility, mining HUI-Miner/EFIM và các gợi ý có bằng chứng. Code của F01–F06 có trong `dev` tại `6601f62`; đây là mô tả phạm vi source, không phải tuyên bố nghiệm thu toàn bộ hệ thống.

Đọc [requirements và quy tắc thẩm quyền](requirements/README.md), [feature index](features/README.md) và [trạng thái tài liệu](DOCUMENTATION_MAP.md#classification-policy). F07–F11 chưa có feature map/đặc tả được track đầy đủ; không tự gán tên từ các work package.

## Getting Started

[Local development](setup/LOCAL_DEVELOPMENT.md) · [setup index](setup/README.md) · [SPMF local](setup/F05_SPMF_RUNTIME_SETUP.md).

## Architecture

[Hybrid JPA + JDBC](architecture/README.md) · [diagrams](diagrams/README.md) · [V7 recovery](setup/FLYWAY_MYSQL_V7_RECOVERY.md) · [V5 historical matrix compatibility](architecture/LEGACY_UTILITY_MATRIX_COMPATIBILITY.md).

Product metadata và Category CRUD dùng JPA; Product catalog/inventory và analytical workloads giữ JDBC. Một transaction manager dùng chung datasource; Flyway quản lý V1–V11, bao gồm Java migration V11.

## Features

| Feature | Name | Backend Status | API Docs | Requirements | Tests |
|---|---|---|---|---|---|
| [F01](features/F01/README.md) | Authentication/User/RBAC | Source implemented; registration contract needs review | [API](api/F01_AUTH_USER_API.md) | [Scope](features/F01/README.md#requirements-and-authority) | [History](features/F01/README.md#testing-and-evidence) |
| [F02](features/F02/README.md) | Product/Category/Inventory | Source implemented; Product/Category JPA integrated | [API](api/F02_PRODUCT_INVENTORY_API.md) | [Reference v3](features/F02/reference_v3/PASPM_F02_Inventory_Batch_API_Swagger_Backlog_v3.md) | [Tests](features/F02/README.md#testing-and-evidence) |
| [F03](features/F03/README.md) | Utility/Weight Rules | Source implemented; final v1.2 authority missing from dev | [API](api/F03_UTILITY_API.md) | [Gap](requirements/README.md#f03-v12-source-gap) | [Tests](features/F03/README.md#testing-and-evidence) |
| [F04](features/F04/README.md) | Transaction Import | CSV P0 source implemented; broader scope not accepted here | [API](api/F04_TRANSACTION_API.md) | [Design](features/F04/reference/F04_DESIGN.md) | [History](features/F04/reference/F04_SWAGGER_TEST_REPORT.md) |
| [F05](features/F05/README.md) | HUI-Miner/EFIM Mining | Source implemented; local SPMF configuration required | [API](api/F05_MINING_API.md) | [Scope](features/F05/README.md#requirements-and-authority) | [History](features/F05/reference/F05_VERIFICATION.md) |
| [F06](features/F06/README.md) | Recommendation Engine | Backend v1.1 source implemented; downstream activation separate | [API](api/F06_RECOMMENDATION_API.md) | [Rules](requirements/F06_RECOMMENDATION_RULES_v1_1.md) | [History](testing/reports/F06_VERIFICATION.md) |

## API Reference

[API index](api/README.md) · [OpenAPI export](api/openapi.json) · [RBAC](api/F01_F02_RBAC_MATRIX.md). Static mapping at the documented baseline contains 73 business operations. Swagger/framework routes are separate. The API Inventory package exists only on a separate local audit branch; see the index for provenance, not a broken link or an implied merge.

## Frontend Integration

[Frontend handoff index](frontend_handoff/README.md) links actual contracts and examples. Synthetic response fixtures retain their original dates and identifiers.

## Testing and Verification

[Testing index](testing/README.md) separates local execution, hosted CI, historical Swagger and real-data acceptance. This documentation reorganization does not run Maven/backend/Swagger tests or access any database. No previous PASS is promoted to current verification.

## Historical Documentation

[Archive](archive/README.md) holds original root README, old PR packages and dated delivery records. Original feature requirements remain in `features/F01–F05/reference*`; status and current authority are in their indexes and the [path map](DOCUMENTATION_MAP.md). Frozen evidence/JSON/manifests are not rewritten.

## Known Documentation Gaps

- Final F03 v1.2 source and some referenced examples are absent from tracked dev.
- F01 duplicate-email 202/409 statements conflict; several older F03/F05 handoffs still describe missing F06.
- Latest editable ERD omits V11 archive/routing details; migrations govern physical schema.
- CI/delivery reports contain dated pre-merge statements; current hosted status is not certified here.
- F07–F11 feature definitions and the separate API Inventory merge remain unresolved.

See [review register](requirements/README.md#documentation-review-register), [complete path map](DOCUMENTATION_MAP.md) and [reorganization validation report](audits/DOCS_REORGANIZATION_REPORT.md).

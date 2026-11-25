# Record of Changes — Nhật ký thay đổi của agent

Thư mục này ghi lại các thay đổi của agent theo phiên làm việc trong PASPM Backend. Mỗi bản ghi trả lời: vì sao thay đổi, sửa gì, ảnh hưởng feature nào, đã kiểm chứng bằng cách nào và còn việc gì. Đây là lịch sử thực hiện, không thay thế API contract, approved requirements, ADR hay báo cáo kiểm thử.

## Cấu trúc chuẩn

```text
record-of-changes/
├── README.md                         # Mục lục theo ngày và quy ước
├── TEMPLATE.md                       # Mẫu một record
└── YYYY-MM-DD/
    ├── README.md                     # Daily summary: Added/Changed/Fixed/…
    ├── 01-short-topic.md              # Chi tiết một phiên
    └── 02-short-topic.md
```

ID ngày: `ROC-YYYYMMDD`; ID phiên: `ROC-YYYYMMDD-NNN`. File phiên dùng số thứ tự hai chữ số để sắp xếp; ID dùng ba chữ số để dễ tham chiếu. Không dùng version release khi chưa có release; ghi **UNRELEASED** cho thay đổi local.

## Mục lục theo ngày

| Date / timezone | Daily record | Sessions | Scope | Release status |
|---|---|---:|---|---|
| 2026-11-11 / Asia/Ho_Chi_Minh | [ROC-20261111 — thay đổi hôm nay](2026-11-11/README.md) | 4 | F01 implementation, docs F01–F06, record và publication | FEATURE_BRANCH — đã push; merge chờ reconciliation |
| 2026-11-12 / Asia/Ho_Chi_Minh | [ROC-20261112 — dev reconciliation](2026-11-12/README.md) | 1 | Giữ dev V20, tích hợp V21 và security, verification/Git | MERGED_TO_DEV — code7304e29 pushed; không deploy |
| 2026-11-15 / Asia/Ho_Chi_Minh | [ROC-20261115 — F07/F08, F05 Checkpoint 1, F03 Weight v2, docs sync](2026-11-15/README.md) | 5 | Merge F07/F08; mining progress, Merchandiser submit; F03 Weight v2 (V25) and consumers; docs sync | F07/F08 MERGED_TO_DEV; F05 + F03 W2 COMMITTED local (5 commits, not pushed); docs sync 005 committed locally on 2026-11-16 at `369dba4` |
| 2026-11-16 / Asia/Ho_Chi_Minh | [ROC-20261116 — W2 follow-up, EFIM-only, P5/P6, Flyway checksums and F05 scale](2026-11-16/README.md) | 6 | W2 follow-up; cleanup/merge plan; F05 EFIM-only and FoodMart-scale input; P5/P6 V26 (010); Flyway V20/V7 checksum troubleshooting (011) | 007–008 merged via `2a944c5`; 010 pushed at `849af72`; 011 pushed at `f6534a0`; ROC 012 owner-approved for `dev` with known limitations (Git integration pending); synthetic F03→F05→F06 and MySQL group PASS; authentic FoodMart acceptance NOT RUN; 009 unused |
| 2026-11-17 / Asia/Ho_Chi_Minh | [ROC-20261117 — F05 dense acceptance, demo seed and EFIM-aware numeric guard](2026-11-17/README.md) | 3 | Dense and realistic-utility synthetic F04→F03→F05; int32 rejection; Windows fallback test; all 23 multi-JVM cases (013); demo seed for F03 Weight v2 and F06 (014); owner decision DEC-20261117-01, EFIM-aware lossless int32 guard, OpenAPI int64 total (015) | MERGED_TO_DEV (013 by `705e3d7`, 014 by `ac29811`, 015 by `530651b`; status updated 2026-11-19, was LOCAL UNRELEASED); 013: prior verifier 24/24 stages PASS, authentic FoodMart and native Windows NOT RUN; 014: 29 unit tests PASS, seed verified on a disposable stack; 015: complete, authentic FoodMart, native Windows, hosted CI and target Linux container/RSS remain NOT RUN or NOT VERIFIED |
| 2026-11-19 / Asia/Ho_Chi_Minh | [ROC-20261119 — owner decisions, F04 Excel, F05 whole-đồng input, F07/F08 zone caps, agent kit](2026-11-19/README.md) | 5 | Seven DEC files and stale status lines (001); F04 Excel on current dev (002); DEC-20261119-05 whole-đồng SPMF fallback (003); DEC-20261119-06 zone caps, top-K baseline, equal-size simulation, size sensitivity (004); agent kit and product docs (005) | 001 MERGED_TO_DEV (PR #20, `f96a09a`); 002 MERGED_TO_DEV (PR #21, `222e54f`); 003 MERGED_TO_DEV (PR #23, `69b179b`); 004 MERGED_TO_DEV (PR #22, `9b91742`); 005 PR_OPEN pending (branch `Khoi/docs/agent-kit`) |
| 2026-11-20 / Asia/Ho_Chi_Minh | [ROC-20261120 — product documents v0.4; F09 Storefront MVP (branch)](2026-11-20/README.md) | 2 | Product docs v0.4 after PRs #22/#23, `paspm-context` skill, Codex sandbox note in `AGENTS.md` (001); Duy's F09 Storefront MVP branch record (002) | 001 PR_OPEN pending (branch `Khoi/docs/product-sync-20261120`); docs only, Maven NOT RUN |
| 2026-11-21 / Asia/Ho_Chi_Minh | [ROC-20261121 — F09 merge to dev](2026-11-21/README.md) | 1 | Integrate F09 Storefront and F02 display metadata onto current dev; resolve F02 SKU and F07/F08 layout conflicts | MERGED_TO_DEV locally at 68d6f0b; F09 migration renumbered V27 to V29 during F01/F02 integration; 51/51 targeted tests PASS before this integration; MySQL and hosted CI NOT RUN |
| 2026-11-24 / Asia/Ho_Chi_Minh | [ROC-20261124 — F01 Store management and F02 expiry / push day / product delete (backend)](2026-11-24/README.md) | 2 | V27, Store administration and own profile, central INACTIVE guard, Store images on shared MediaStorage, docs and OpenAPI 107/133 (001); V28, F02 expiry boundary and `effectiveStatus`, `MISSING_PUSH_DAY` warning, product soft delete, OpenAPI 107/134 (002) | 001 MERGED_TO_DEV — PR #27 (`3d51f5b`); 002 BRANCH_PUSHED — branch `Khoi/feat/f02-expiry-pushday-product-delete`, PR not opened |
| 2026-11-25 / Asia/Ho_Chi_Minh | [ROC-20261125 — F07 product-level Layout B editor, Maven Wrapper, demo seed / SKU mapping, F09 Storefront merge, priority coverage, F10/F11 contracts, OpenAPI work and SPMF (GPL v3) CI/CD](2026-11-25/README.md) | 9 | Per-product Layout B placements/provenance, snapshot product search, F08 saved-block bundle conversion, real FE editor/campaign/simulation flow, docs and DEC (001); executable Maven Wrapper and setup guides (002); DEC-20261119-07 server-generated SKU, demo seed FoodMart mapping and `JpaPilotMigrationMySqlTest` fix (004); F09 Storefront merged with `dev`: keep dev's V29, V30 drops `store_slug`/`storefront_enabled`, `store.slug` + Store ACTIVE (DEC-20261125-04), soft-delete/expiry rules (005); frozen snapshot priority coverage (006); SPMF notices, license in the image, amd64 build, `SpmfProcessBoundaryTest`, CI skipped-test allowlist and manual full workflow (007); planned F10/F11 contracts (008); OpenAPI and feature status sync (009); F07 OpenAPI annotations (010) | 001 UNRELEASED at its recorded checkpoint; 002 local wrapper verification complete; 004 MERGED_TO_DEV — PR #30 (`2d1280b`); 005 MERGED_TO_DEV — PR #33 (`0ef5a55`; recorded backend group 466/0/0/0, MySQL fresh + upgrade to V30 PASS); 006 MERGED_TO_DEV — PR #32 (`516183d`); 007 BRANCH_PUSHED — branch `Khoi/chore/spmf-ci-artifact`, PR #29 open; the Release `spmf-2.67a` exists; 008 MERGED_TO_DEV — PR #34 (`cded4ab`); 009 MERGED_TO_DEV — PR #35 (`2bcbe69`); 010 UNRELEASED |
| 2026-11-26 / Asia/Ho_Chi_Minh | [ROC-20261126 — Diagrams brought in line with dev; black-box test pack](2026-11-26/README.md) | 9 | 36-page UML pack and ERD V1–V26 landed in `docs/diagrams/`; scheduler wording ported (001); Store management pages and V27 `store` columns (002); F02 V28 (AD10, product soft delete, batch effective status) (003); F09 Storefront by slug, ProductCard, V29 product columns (004); EFIM-only (benchmark pages marked REMOVED) and Weight v2 (AD11) (005); F07/F08 per-product placements, top-K baseline, priority coverage (006); F10/F11 planned markings, SPMF process boundary (007); pack rename, Mermaid ARCHIVED, product.deleted_by FK edges, review fixes (008); 248 black-box test cases (F01–F09; F10/F11 PLANNED), traceability, test plan, PO questions, PO answers in DEC-20261126-02/-03 (009) | 001–008 MERGED_TO_DEV — PR #37 (`fb48d55`); documentation only; 009 UNRELEASED — branch `Khoi/docs/blackbox-test-cases` pushed, no PR; no test case executed |

Bản ghi implementation 01 được tổng hợp từ source và bằng chứng thực thi phiên trước; không phải lượt kiểm thử mới. Chưa có hồ sơ đầy đủ cho các ngày cũ hơn; không suy diễn lịch sử từ tên branch hoặc kết quả chưa xác minh.


## Quy ước cập nhật cho các phiên sau

1. Đọc record gần nhất, yêu cầu hiện tại, Git branch/HEAD/status và tài liệu feature liên quan.
2. Tạo file `YYYY-MM-DD/NN-short-topic.md` từ [TEMPLATE.md](TEMPLATE.md), gán ID `ROC-YYYYMMDD-NNN`; cập nhật README của ngày và mục lục này. Dùng ngày thực tế, timezone và số thứ tự trong ngày. Nếu không biết thời gian/phiên cũ, ghi UNKNOWN thay vì tự đặt ngày.
3. Mở đầu với IN_PROGRESS; cập nhật cùng file khi công việc trong phiên tiến triển. Một phiên dài có thể gom nhiều lượt tiếp tục; không tạo một record cho mỗi lệnh tool.
4. Trước khi bàn giao, ghi file sửa/thêm, endpoint/migration thay đổi, tác động liên feature, kết quả kiểm chứng và các việc chưa hoàn thành. Thêm hàng vào mục lục.
5. Phân biệt rõ thực thi trong phiên này với bằng chứng cũ được tham chiếu; tách local, CI, browser, MySQL, production. Ghi PASS/FAIL/SKIPPED/BLOCKED/NOT RUN đúng thực tế.
6. Ghi commit/PR/merge/deploy **chỉ khi đã thực hiện và xác minh**. HEAD nền không phải commit chứa thay đổi local. Không biến yêu cầu cập nhật record thành quyền commit hoặc deploy.
7. Kết thúc với COMPLETED, PARTIAL hoặc BLOCKED; nêu phạm vi. COMPLETED của một phiên không đồng nghĩa feature production-ready hay Product Owner đã duyệt.
8. Record đã bàn giao giữ làm lịch sử; được chuyển vị trí/chuẩn hóa metadata khi giữ nội dung và cập nhật link. Nếu cần sửa kết luận cũ, thêm mục đính chính có ngày và dẫn record mới. Không xóa failure/giới hạn để đổi thành PASS.

## Nội dung và bảo mật

- Dùng đường dẫn tương đối trong repo và liên kết tới source/API/test report chuẩn; tránh chép lại toàn bộ hợp đồng vào mọi record.
- Không lưu password, JWT, refresh token, CSRF value thực, OTP, cookie header, connection string nhạy cảm, private dump hoặc nội dung `.env`.
- Dữ liệu test phải ghi rõ synthetic/demo; tài liệu hoặc screenshot không tự chứng minh PO approval.
- Không sửa AGENTS.md, requirements gốc, diagram hoặc source chỉ để áp dụng quy ước nhật ký.
- Quy ước này là hướng dẫn trong repository cho các phiên sau; không có tác vụ tự động đọc chat hoặc theo dõi bên ngoài repository được cài đặt.

[Feature index](../features/README.md) · [API changelog](../api/API_CHANGELOG.md) · [Testing](../testing/README.md) · [Documentation index](../README.md).

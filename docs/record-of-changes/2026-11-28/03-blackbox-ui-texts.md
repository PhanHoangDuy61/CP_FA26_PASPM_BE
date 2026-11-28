# ROC-20261128-003 — Black-box cases follow the new UI texts and the storefront placements

| Field | Value |
|---|---|
| Date / timezone | 2026-11-28 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Sonnet 5.5), requested by Nguyễn Minh Khôi |
| Type | Test documentation (black-box pack only; no code, no API change) |
| Features | F01 sign-in, registration, OTP, reset, activation; F09 customer homepage and product page (UI) |
| Branch / base | `Khoi/docs/blackbox-ui-texts` / `origin/dev` @ `dc70776` |
| Decision | None new. Follows the frontend changes of FE PR #22 (clear auth feedback, back links, guest home), PR #23 (featured products and related placements) and PR #24 (Google 503 message) |
| Release | UNRELEASED |
| Daily index | [2026-11-28](README.md) |

## Why

The frontend changes of 2026-11-28 replaced raw backend text by fixed UI messages and changed the customer homepage. Several black-box cases quoted the old behaviour (the API message, or "the UI explains it"), so a tester would have compared the screen with the wrong text.

## What changed (pack regenerated with `outputs/blackbox-tools`; IDs unchanged; 250 → 252 cases)

| Case | Update |
|---|---|
| TC-F01-001 | `Đăng nhập` header button for a guest on /customer-home; none when signed in |
| TC-F01-002, 003 | UI shows `Invalid email or password.` (the API keeps `Invalid credentials or account unavailable`) |
| TC-F01-004 | Messages of the register steps; back links |
| TC-F01-005 | UI `This email is already registered.` (API 409 `Email already exists`) |
| TC-F01-007 | Field errors next to the password and confirmation fields |
| TC-F01-008, 010 | UI `This code could not be verified. Check it or request a new one.`; malformed code `Enter the 6-digit verification code.` |
| TC-F01-009 | Resend 429: `Please wait before requesting another code.` |
| TC-F01-011 | 429: `Too many sign-in attempts. Please wait a few minutes and try again.` |
| TC-F01-012 | 503: `Google sign-in is currently unavailable. Please use your email and password.` |
| TC-F01-017 | Same non-enumerating text for any email; back links |
| TC-F01-019 | English-only /activate, invalid-link text, back links |
| TC-F01-039 | `Your store is inactive. Contact your system administrator for help.` once in the alert area |
| TC-F01-051 | Inline field errors (`This field is required.`, `Please enter a valid email address.`) and one `role=alert` area |
| TC-F09-002, 003 | UI effect of BANNER blocks on the homepage; none for the catalogue fallback |
| **TC-F09-015** (new) | Customer homepage: sign-in button, no category section, stores directory, `Sản phẩm nổi bật`, Store-local categories |
| **TC-F09-016** (new) | Product page: `Thường được mua cùng` (FBT) and `Có thể bạn cũng thích` (CROSS_SELL), availability, versioned cart add |

The two new cases are appended at the end of F09 so no ID moves. `TEST_PLAN.md` says 252 cases; `TRACEABILITY.md` is regenerated (`missing: []`).

## Verification

- `gen.py` and `trace.py` ran on the committed pack first: byte-identical output before any edit; then 252 cases, `missing: []`.
- **Live walk-through of the texts of TC-F09-015/016** (fresh isolated stack: MySQL 3309, Mailpit 1028/8028, backend 8081 from `dev`, Vite 3105 on FE `3b4d413`, throwaway secrets; everything removed afterwards): demo seed and TDP-01, snapshot 2026-10-01..04, mining at `PERCENTAGE 0.12` (182 patterns), F06 generation (17 BUNDLE, 41 CROSS_SELL, 5 FBT), the Merchandiser approved one BUNDLE, one BANNER, one FBT and one CROSS_SELL, campaign created, Layout B READY, decision CHOOSE_B, published, activated by the Store Admin (all through the API). Guest homepage showed `Sản phẩm nổi bật` (one BANNER product) before `Khám phá cửa hàng`; the page of an FBT product showed `Thường được mua cùng` with the other two products, the page of a CROSS_SELL product showed `Có thể bạn cũng thích`; as the End Customer, adding a related product returned `Đã cập nhật giỏ hàng.` and the server cart held that product (version 1). Screenshots: `~/paspm-verification-evidence/2026-11-28-placements/live/` (outside the repository).

## Limitations

- A product appears once per layout, so one product page cannot show both related sections at the same time; TC-F09-016 uses a product of each block.
- The demo seed has no product images (the image seed branch was not available), so the cards show the placeholder icon; image display was checked by the FE browser tests with fixtures.
- The Google 503 text (TC-F01-012) was tested at the unit level through the session layer, not by pressing Google's button.
- The pack still describes the UI for the English auth screens and the Vietnamese customer screens as they are; no decision on one language for the whole app was taken.

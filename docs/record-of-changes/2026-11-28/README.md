# ROC-20261128 — Professional OTP e-mails, their seed check and the black-box texts

2026-11-28 / Asia/Ho_Chi_Minh.

## Record 001 — Professional OTP e-mails (HTML + plain text, purpose-specific subjects)

[Record 001](01-professional-otp-email.md): DEC-20261128-01. Every OTP e-mail (self-registration and Google sign-up verification, admin invitation, password reset) is now a multipart HTML + plain-text e-mail with a purpose-specific subject, the code in large type, the expiry from the configured TTL and a security note; it never shows an enum name. OTP generation, TTL, cooldown, rate limits, API contracts and the 503 behaviour are unchanged. The Mailpit-reading seed scripts, F01 docs and black-box pack (TC-F01-052 added, 250 cases) are updated. Branch `Khoi/fix/professional-otp-email`.

## Record 002 — Seed scripts read the OTP from the message body

[Record 002](02-seed-otp-mail-live-check.md): the live check of ROC-20261128-001 on a fresh isolated stack found that the seed scripts could not read any OTP (the Mailpit list Snippet has no colon and is cut). They now read the plain-text body of the message. Demo seed and TDP-01 each ran twice live: first runs created everything, second runs created nothing, the mining policy stayed `PERCENTAGE 0.12`. Seed scripts only; branch `Khoi/fix/seed-otp-mail-parsing`.

## Record 003 — Black-box cases follow the new UI texts

[Record 003](03-blackbox-ui-texts.md): TC-F01-001/002/003/004/005/007/008/009/010/011/012/017/019/039/051 and TC-F09-002/003 now state the UI texts of FE PR #22–#24; two UI cases appended (TC-F09-015 homepage, TC-F09-016 product-page related sections); 252 cases, IDs unchanged. A live walk-through of the placements (seed, approve, Layout B, publish, activate, storefront, cart) passed on an isolated stack. Docs only; branch `Khoi/docs/blackbox-ui-texts`.

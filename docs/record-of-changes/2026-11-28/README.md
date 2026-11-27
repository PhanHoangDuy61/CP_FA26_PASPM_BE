# ROC-20261128 — Professional OTP e-mails

2026-11-28 / Asia/Ho_Chi_Minh.

## Record 001 — Professional OTP e-mails (HTML + plain text, purpose-specific subjects)

[Record 001](01-professional-otp-email.md): DEC-20261128-01. Every OTP e-mail (self-registration and Google sign-up verification, admin invitation, password reset) is now a multipart HTML + plain-text e-mail with a purpose-specific subject, the code in large type, the expiry from the configured TTL and a security note; it never shows an enum name. OTP generation, TTL, cooldown, rate limits, API contracts and the 503 behaviour are unchanged. The Mailpit-reading seed scripts, F01 docs and black-box pack (TC-F01-052 added, 250 cases) are updated. Branch `Khoi/fix/professional-otp-email`.

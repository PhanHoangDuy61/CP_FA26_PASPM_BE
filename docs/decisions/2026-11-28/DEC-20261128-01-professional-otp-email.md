# DEC-20261128-01 — OTP e-mails are professional multipart e-mails with purpose-specific subjects

| Field | Value |
|---|---|
| Date | 2026-11-28 (Asia/Ho_Chi_Minh) |
| Source | Product Owner decision (Nguyễn Minh Khôi, Team Leader / acting Product Owner), freeze exception given in the task conversation on 2026-11-28: the OTP e-mail was raw text that showed an internal enum name (`Your PASSWORD_RESET code is 679015. Expires in 10 minutes. Do not share this code.`) |
| Status | OWNER_DECISION. Implemented on branch `Khoi/fix/professional-otp-email` (ROC-20261128-001) |
| Number | `-01`: the first decision of 2026-11-28. Checked across `origin/dev` and every `origin/*` branch (no `docs/decisions/2026-11-28` folder existed) |
| Related | F01 OTP rules ([API contract](../../api/F01_AUTH_USER_API.md)), [F01 reference decisions](../../features/F01/reference/DECISIONS.md) item 4 |

## Decision

Every one-time-code e-mail is a **multipart/alternative e-mail**: an HTML part and a plain-text part, in English.

| Purpose (internal, never shown) | When it is sent | Subject |
|---|---|---|
| Self-registration (`SELF_REGISTER`), also the Google sign-up verification | `POST /api/auth/register`, OTP request, resend, Google registration | `Verify your PASPM account` |
| Admin invitation (`ADMIN_INVITE`) | `POST /api/users`, OTP request, resend | `You're invited to PASPM` |
| Password reset (`PASSWORD_RESET`) | `POST /api/auth/password-reset/request` | `Reset your PASPM password` |

Content (both parts): a text-only `PASPM` brand header, a greeting (`Hello <name>,` when the account has a name, `Hello,` otherwise), one purpose line, the 6-digit code in large type (`Your verification code: NNNNNN` in the text part), the expiry (`This code expires in N minutes.`, N from the real `paspm.otp-ttl-seconds` setting), the security note `If you didn't request this, you can ignore this email. Never share this code with anyone.`, and a footer saying the message is automated.

Rules:

- No enum name, ID, status or other internal value in the subject or the body.
- Inline CSS only; no remote image, link, script or tracking pixel.
- Every dynamic value (name, code, expiry) is HTML-escaped in the HTML part; the name is also stripped of control characters and cut at 40 characters.
- The plain-text part keeps the code inside its first 200 characters, because Mailpit's list shows only the start of the text.

## What does not change

OTP generation (6 digits, `SecureRandom`, HMAC-SHA256 hash), TTL (default 600 s, range 60–1800), cooldown, attempt limit, IP and e-mail rate limits, request and response bodies, status codes, and the `503 {"error":"Email delivery unavailable; retry later"}` answer when SMTP fails (mail errors still roll back the challenge; password reset still answers the generic 202). No migration.

## Consequences

- Scripts that read the OTP from Mailpit (`scripts/seed/seed_demo.py`, `seed_test_stores.py`) now find the e-mail by subject and read `Your verification code: NNNNNN`; the old `code is NNNNNN` text no longer exists.
- Black-box steps that named the enum (`ADMIN_INVITE OTP`) name the e-mail subject instead; case TC-F01-052 checks the three e-mails (pack regenerated, no existing ID changed; 250 cases).
- An external reader that parsed `code is NNNNNN` from the e-mail must change to the new line. None is known in this repository.

# ROC-20261128-001 — Professional OTP e-mails (HTML + plain text, purpose-specific subjects)

| Field | Value |
|---|---|
| Date / timezone | 2026-11-28 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Sonnet 5.5), requested by Nguyễn Minh Khôi |
| Type | Fix / UX of an outgoing e-mail (Java, no migration, no API contract change) |
| Features | F01 OTP (self-registration, Google sign-up verification, invitation, password reset) |
| Branch / base | `Khoi/fix/professional-otp-email` / `origin/dev` @ `2871d2b` |
| Decision | [DEC-20261128-01](../../decisions/2026-11-28/DEC-20261128-01-professional-otp-email.md) (freeze exception approved by the PO) |
| Release | UNRELEASED |
| Daily index | [2026-11-28](README.md) |

## Why

`OtpService` sent a `SimpleMailMessage` whose text was `Your <PURPOSE> code is <code>. Expires in <n> minutes. Do not share this code.` The purpose enum (`PASSWORD_RESET`, `SELF_REGISTER`, `ADMIN_INVITE`) was shown to users and there were only two subjects.

## What changed

- **`auth/OtpEmail.java`** (new, pure, no Spring context): `OtpEmail.compose(purpose, name, code, ttlSeconds)` returns subject, plain text and HTML. Three purposes; any other purpose throws `IllegalArgumentException` (before anything is sent). Values reaching the HTML go through `HtmlUtils.htmlEscape`.
- **`auth/OtpService.java`**: `issue(...)` builds a `MimeMessage` with `MimeMessageHelper` (multipart, UTF-8, text then HTML) instead of a `SimpleMailMessage`. A `MessagingException` becomes `MailPreparationException`, a `MailException`, so the existing 503 handler and the generic password-reset answer still apply. Generation, hashing, TTL, cooldown, limits and the order of operations are untouched.
- **Seed scripts** (`seed_demo.py`, `seed_test_stores.py` and the fake in `test_seed_test_stores.py`): find the OTP e-mail by subject and read `Your verification code: NNNNNN`; the old pattern `code is NNNNNN` no longer matches anything.
- **Docs:** F01 API (new "OTP e-mail" paragraph), F01 frontend handoff, F01 README and reference decisions, SRS FR-01.2, STAGING.md (Mailpit reading), seed README, TEST_PLAN (case count and TDP-01 wording), black-box pack regenerated with `outputs/blackbox-tools` (steps that named the enum now name the subject; new case TC-F01-052; 250 cases, no ID changed; `missing: []`).

## Files

| Path | Change |
|---|---|
| `src/main/java/vn/edu/fpt/paspm/auth/OtpEmail.java` | Added |
| `src/main/java/vn/edu/fpt/paspm/auth/OtpService.java` | `SimpleMailMessage` → `MimeMessage` |
| `src/test/java/vn/edu/fpt/paspm/auth/OtpEmailTest.java`, `OtpMailTestSupport.java` | Added |
| `src/test/java/vn/edu/fpt/paspm/AuthIntegrationTest.java` | Reads the OTP from the multipart message; new test `otpMailsArePurposeSpecificMultipartWithoutInternalNames` |
| `.github/scripts/test-groups.py` | `OtpEmailTest` in the `backend` group |
| `scripts/seed/*` | Mailpit reading (see above) |
| `docs/…` | As listed above, plus DEC-20261128-01, this record, the day README and the index row |

## Verification

- **Focused:** `sh ./mvnw -B test -Dtest='OtpEmailTest,AuthIntegrationTest,GoogleRegistrationTest'` (Java 17.0.21): 41 tests, 0 failures, 0 errors.
- **CI backend group:** `sh ./mvnw -B clean verify -Dtest="$(python3 .github/scripts/test-groups.py backend)"`: **505 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS (496 before + 8 `OtpEmailTest` + 1 new `AuthIntegrationTest` test).
- **Seed unit tests:** `python3 -I -m unittest discover -s scripts/seed/tests`: 76 tests OK.
- **Black-box pack:** `gen.py` then `trace.py` of `outputs/blackbox-tools`: 250 cases, `missing: []`; the pack regenerated from the unchanged tool reproduced the committed files byte for byte before the edit.
- **NOT RUN:** the MySQL group and the integration job locally (the PR's CI runs them); the seed scripts against a real Mailpit (checked with the fake only); real SMTP and real mail clients.

No live stack was started (ports 8080 and 3000 were not used); the e-mails were checked at the `MimeMessage` level with the mail sender mocked, and a rendered sample was generated from `OtpEmail`.

## Limitations

- The HTML was checked as text and for forbidden elements, not opened in real mail clients (Gmail, Outlook) or in the Mailpit UI.
- English only (as decided); no localisation.
- A mail client that shows only the plain-text part sees the same information without styling.

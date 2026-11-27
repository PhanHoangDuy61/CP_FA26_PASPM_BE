# ROC-20261128-002 — Seed scripts read the OTP from the message body (live check of PR #54)

| Field | Value |
|---|---|
| Date / timezone | 2026-11-28 / Asia/Ho_Chi_Minh |
| Agent | Claude Code (Claude Sonnet 5.5), requested by Nguyễn Minh Khôi |
| Type | Fix of seed scripts (Python only; no Java, no migration, no API change) |
| Features | Demo seed and TDP-01 (OTP codes read from Mailpit) |
| Branch / base | `Khoi/fix/seed-otp-mail-parsing` / `origin/dev` @ `bb022d1` |
| Decision | None new: follows DEC-20261128-01 (the e-mail text) and [ROC-20261128-001](01-professional-otp-email.md) |
| Release | UNRELEASED |
| Daily index | [2026-11-28](README.md) |

## Why

ROC-20261128-001 changed the OTP e-mails and the seed scripts' Mailpit reading, but tested the scripts only against a fake endpoint (its "NOT RUN" list). The first live run on a fresh stack (backend `bb022d1`, Mailpit v1.27.8) failed at the first invitation: `ERROR: no OTP mail for storeadmin@paspm.example found in Mailpit`.

## Root cause

The scripts matched `verification code:\s*(\d{6})` against the `Snippet` of the Mailpit **search list**. For a multipart message the live Snippet comes from the HTML part, where the label and the code are separate paragraphs, so it reads `… Your verification code 550433 This code expires …` (no colon) and is cut at 200 characters. The fake used a Snippet that contained the colon, so the unit tests passed while the real seed could never read a code. The plain-text part (`Your verification code: 550433`) is exact.

## What changed

- `scripts/seed/seed_demo.py` and `scripts/seed/seed_test_stores.py`: `otp_from_mailpit` keeps choosing the message by subject and age, then reads the code from the **Text** of `GET /api/v1/message/{ID}`.
- `scripts/seed/tests/test_seed_test_stores.py`: the fake now returns the live Snippet shape (no colon, cut) and a message detail with the plain text.
- `scripts/seed/tests/test_otp_mail_parsing.py` (new, 2 tests): `seed_demo.otp_from_mailpit` with shapes copied from the live answer; a message with another purpose's subject is ignored.

## Verification

- **Seed unit tests, test first:** `python3 -I -m unittest discover -s scripts/seed/tests`. Against the old scripts with the live-shaped fakes: **5 errors** (`no OTP mail … found`). With the fix: **78 tests, 0 failures**.
- **Live check** (fresh isolated stack: compose project `paspm_seedcheck`, MySQL 3309, Mailpit 1028/8028, backend jar built from `bb022d1` on 8081, Java 17, throwaway secrets in `/tmp`; the stack and its volume were removed afterwards; nothing else was touched). Logs: `~/paspm-verification-evidence/2026-11-28-otp-email/live/` (outside the repository).

| Step | Result |
|---|---|
| Demo seed, run 1 | exit 0: Store Admin and Merchandiser created and activated through the invitation e-mail, End Customer created through the self-registration e-mail; 1,559 products, 3 imports COMPLETED, mining policy updated to `PERCENTAGE 0.12` (version 1) |
| TDP-01, run 1 | exit 0: 2 Stores and 4 accounts created, 6 created, 0 already present |
| Demo seed, run 2 | exit 0: nothing created (all "already present" / "skipped, already imported"); `mining policy: already PERCENTAGE 0.12 (version 1); nothing changed`; record counts identical to run 1 |
| TDP-01, run 2 | exit 0: `0 created, 6 already present`. A first attempt of run 2 stopped with HTTP 429 `Too many attempts; try later` (the in-memory login limiter after many System Admin sign-ins by the seeds); after restarting the backend the run completed |
| Mining policy after all runs | `GET /api/system/mining/config`: `PERCENTAGE 0.120000`, version 1 |
| Password-reset e-mail | triggered once by hand with `POST /api/auth/password-reset/request` (202) for the demo customer, to see the third template |
| Mailpit UI | screenshots of "Verify your PASPM account", "You're invited to PASPM" and "Reset your PASPM password" saved in the evidence folder |

## Limitations

- The Python seed tests are not run by CI (`.github/workflows` does not call them); they were run locally.
- The HTML was viewed in the Mailpit UI only, not in real mail clients.
- The login limiter can stop the second TDP-01 run right after a long seed; restarting the backend clears the in-memory counters.

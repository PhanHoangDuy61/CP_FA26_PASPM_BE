"""The demo seed reads the OTP from the plain-text body of the Mailpit message (no backend, no network).

Found by the live check of 2026-11-28 (ROC-20261128-003): the list `Snippet` that Mailpit returns is built from the HTML part, has
no colon after "Your verification code" and is cut at 200 characters, so a regex on the Snippet never matched the new e-mails.

Run from the repository root:  python3 -I -m unittest discover -s scripts/seed/tests -v
"""
import os
import sys
import unittest
from pathlib import Path
from unittest import mock

for name, value in {"SEED_SYSADMIN_EMAIL": "sys@x.example", "SEED_SYSADMIN_PASSWORD": "unused", "SEED_ACCOUNT_PASSWORD": "unused",
                    "SEED_STORE_ADMIN_EMAIL": "sa@x.example", "SEED_MERCHANDISER_EMAIL": "me@x.example",
                    "SEED_CUSTOMER_EMAIL": "cu@x.example"}.items():
    os.environ.setdefault(name, value)
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))  # python -I does not add the script directory

import seed_demo  # noqa: E402

# Shapes copied from a live Mailpit v1.27.8 answer for a backend OtpEmail message (ADMIN_INVITE).
SUBJECT = "You're invited to PASPM"
LIVE_SNIPPET = ("PASPM You're invited to PASPM Hello Demo Store Admin, You have been invited to join PASPM. Enter this code to "
                "activate your account. Your verification code 550433 This code expires in 10 minutes. If y...")
LIVE_TEXT = ("PASPM\r\n\r\nYou're invited to PASPM\r\n\r\nHello Demo Store Admin,\r\n\r\nYou have been invited to join PASPM. "
             "Enter this code to activate your account.\r\n\r\nYour verification code: 550433\r\n\r\nThis code expires in 10 minutes.\r\n")


class OtpFromMailpitTest(unittest.TestCase):
    def setUp(self):
        self.original = seed_demo.http
        self.calls = []

    def tearDown(self):
        seed_demo.http = self.original

    def fake(self, messages, text=LIVE_TEXT):
        def http(method, url, *args, **kwargs):
            self.calls.append(url)
            if "/api/v1/message/" in url:
                return 200, {"Text": text}
            return 200, {"messages": messages}
        seed_demo.http = http

    def message(self, subject=SUBJECT, created="2999-01-01T00:00:00Z"):
        return {"ID": "m1", "Subject": subject, "Snippet": LIVE_SNIPPET, "Created": created}

    def test_reads_the_code_from_the_text_body_not_from_the_snippet(self):
        self.fake([self.message()])
        self.assertEqual(seed_demo.otp_from_mailpit("sa@x.example", "ADMIN_INVITE", 0), "550433")
        self.assertTrue(any(url.endswith("/api/v1/message/m1") for url in self.calls))

    def test_other_purpose_subjects_are_ignored(self):
        self.fake([self.message(subject="Verify your PASPM account")], text="Your verification code: 111111")
        with mock.patch.object(seed_demo.time, "sleep"), mock.patch.object(seed_demo.time, "time", side_effect=[0, 0, 1000]):
            with self.assertRaises(SystemExit):  # the 30 s wait ends after one poll that found no matching subject
                seed_demo.otp_from_mailpit("sa@x.example", "ADMIN_INVITE", 0)


if __name__ == "__main__":
    unittest.main()

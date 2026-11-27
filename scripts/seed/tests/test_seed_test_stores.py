"""Unit tests for TDP-01 (seed_test_stores.py): request bodies and idempotency, HTTP layer faked (no backend).

Run from the repository root:  python3 -I -m unittest discover -s scripts/seed/tests -v
"""
import io
import sys
import unittest
from contextlib import redirect_stdout
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))  # python -I does not add the script directory

import seed_test_stores as t  # noqa: E402

API, MAIL = "http://api", "http://mail"
SYSADMIN_PW, ACCOUNT_PW = "sys-secret-Value1", "acct-secret-Value1"


class FakeBackend:
    """Just enough of the API: stores, users, login, OTP request/activate and the Mailpit search."""

    def __init__(self):
        self.stores, self.users, self.calls = [], {}, []  # users: email -> {status, password, store, role}
        self.next_id = 10
        self.otp = "123456"

    def __call__(self, method, url, token=None, body=None, timeout=60):
        self.calls.append((method, url.replace(API, "").replace(MAIL, ""), body))
        path = url.replace(API, "")
        if url.startswith(MAIL):
            if "/api/v1/message/" in url:  # the message detail has the exact plain-text body, like the live Mailpit
                return 200, {"Text": f"PASPM\r\n\r\nYou're invited to PASPM\r\n\r\nYour verification code: {self.otp}\r\n\r\nThis code expires in 10 minutes.\r\n"}
            email = url.split("to%3A")[1]
            email = email.replace("%40", "@")
            u = self.users.get(email)
            # The list Snippet is shaped like the live one (2026-11-28): no colon after the label, cut at 200 characters.
            snippet = "PASPM You're invited to PASPM Hello Demo Store Admin, You have been invited to join PASPM. Enter this code to activate your account. Your verification code 000000 This code expires in 10 minutes. If y..."
            msgs = [{"ID": "m1", "Subject": "You're invited to PASPM", "Snippet": snippet, "Created": "2999-01-01T00:00:00Z"}] if u and u.get("mailed") else []
            return 200, {"messages": msgs}
        if path == "/api/auth/login":
            if body["email"] == "sys@x.example":
                ok = body["password"] == SYSADMIN_PW
            else:
                u = self.users.get(body["email"])
                ok = bool(u and u["status"] == "ACTIVE" and u["password"] == body["password"])
            if not ok:
                raise t.ApiError(401, "no")
            return 200, {"accessToken": "tok"}
        if path.startswith("/api/system/stores?"):
            return 200, {"items": list(self.stores)}
        if path == "/api/system/stores" and method == "POST":
            self.next_id += 1
            self.stores.append({"storeId": self.next_id, "name": body["name"], "slug": body["slug"], "status": "ACTIVE"})
            return 201, {}
        if path.startswith("/api/users?"):
            return 200, [{"email": e, "status": u["status"]} for e, u in self.users.items()]
        if path == "/api/users" and method == "POST":
            self.users[body["email"]] = {"status": "PENDING_VERIFICATION", "password": None, "mailed": True,
                                         "store": body["storeId"], "role": body["roleCode"]}
            return 201, {}
        if path == "/api/auth/otp/request":
            self.users[body["email"]]["mailed"] = True
            return 204, ""
        if path == "/api/auth/activate":
            assert body["otp"] == self.otp
            self.users[body["email"]].update(status="ACTIVE", password=body["password"])
            return 200, {}
        raise AssertionError(f"unexpected call {method} {path}")

    def writes(self):
        return [c for c in self.calls if c[0] == "POST" and c[1] in ("/api/system/stores", "/api/users")]


def make(backend):
    clock = [0.0]

    def now():
        clock[0] += 0.01
        return clock[0] + 1.0e9 * 0  # epoch near 0; the fake mail is dated far in the future

    return t.Seeder(API, MAIL, "sys@x.example", SYSADMIN_PW, ACCOUNT_PW, http_fn=backend,
                    sleep=lambda s: None, now=now, log=lambda m: None, otp_wait=5)


class PlanTest(unittest.TestCase):
    def test_stores_and_accounts_match_the_test_plan(self):
        plan = t.account_plan()
        self.assertEqual([(p["slug"], p["name"]) for p in plan], [("qa-store-b", "QA Store B"), ("qa-store-c", "QA Store C")])
        emails = [(a["role"], a["email"]) for p in plan for a in p["accounts"]]
        self.assertEqual(emails, [
            ("STORE_ADMIN", "qa-storeadmin-b@paspm.example"), ("MERCHANDISER", "qa-merchandiser-b@paspm.example"),
            ("STORE_ADMIN", "qa-storeadmin-c@paspm.example"), ("MERCHANDISER", "qa-merchandiser-c@paspm.example")])


class SeedTest(unittest.TestCase):
    def test_first_run_creates_everything_with_the_right_bodies(self):
        b = FakeBackend()
        report = make(b).run()
        self.assertEqual([s["slug"] for s in b.stores], ["qa-store-b", "qa-store-c"])
        posted = [c for c in b.writes()]
        self.assertEqual(posted[0][2], {"name": "QA Store B", "slug": "qa-store-b"})
        users = [c[2] for c in posted if c[1] == "/api/users"]
        self.assertEqual(len(users), 4)
        by_email = {u["email"]: u for u in users}
        sb = b.stores[0]["storeId"]
        self.assertEqual(by_email["qa-storeadmin-b@paspm.example"],
                         {"name": "QA Store Admin B", "email": "qa-storeadmin-b@paspm.example",
                          "roleCode": "STORE_ADMIN", "storeId": sb})
        self.assertEqual(by_email["qa-merchandiser-c@paspm.example"]["roleCode"], "MERCHANDISER")
        self.assertEqual(by_email["qa-merchandiser-c@paspm.example"]["storeId"], b.stores[1]["storeId"])
        self.assertTrue(all(u["status"] == "ACTIVE" for u in b.users.values()))
        self.assertEqual({s for _, _, s in report}, {"created"})
        self.assertEqual(len(report), 6)

    def test_second_run_creates_nothing(self):
        b = FakeBackend()
        make(b).run()
        b.calls.clear()
        report = make(b).run()
        self.assertEqual(b.writes(), [])
        self.assertNotIn("/api/auth/activate", [c[1] for c in b.calls])
        self.assertEqual({s for _, _, s in report}, {"found"})

    def test_existing_pending_account_gets_a_new_otp_not_a_second_invitation(self):
        b = FakeBackend()
        b.stores.append({"storeId": 5, "name": "QA Store B", "slug": "qa-store-b", "status": "ACTIVE"})
        b.users["qa-storeadmin-b@paspm.example"] = {"status": "PENDING_VERIFICATION", "password": None, "mailed": False}
        make(b).run()
        invites = [c[2]["email"] for c in b.writes() if c[1] == "/api/users"]
        self.assertNotIn("qa-storeadmin-b@paspm.example", invites)
        self.assertIn("/api/auth/otp/request", [c[1] for c in b.calls])
        self.assertEqual(b.users["qa-storeadmin-b@paspm.example"]["status"], "ACTIVE")

    def test_inactive_store_stops_the_run(self):
        b = FakeBackend()
        b.stores.append({"storeId": 5, "name": "QA Store B", "slug": "qa-store-b", "status": "INACTIVE"})
        with self.assertRaises(SystemExit) as ctx:
            make(b).run()
        self.assertIn("INACTIVE", str(ctx.exception))
        self.assertEqual(b.writes(), [])
        self.assertFalse([c for c in b.calls if c[0] == "PATCH"])

    def test_active_account_with_another_password_is_not_touched(self):
        b = FakeBackend()
        b.users["qa-storeadmin-b@paspm.example"] = {"status": "ACTIVE", "password": "something-else"}
        with self.assertRaises(SystemExit):
            make(b).run()
        self.assertNotIn("/api/auth/activate", [c[1] for c in b.calls])

    def test_bad_system_admin_login_stops(self):
        b = FakeBackend()
        s = make(b)
        s._sysadmin = ("sys@x.example", "wrong")
        with self.assertRaises(SystemExit):
            s.run()

    def test_passwords_are_never_printed(self):
        b = FakeBackend()
        out = io.StringIO()
        with redirect_stdout(out):
            print(t.format_report(make(b).run()))
        text = out.getvalue()
        self.assertNotIn(ACCOUNT_PW, text)
        self.assertNotIn(SYSADMIN_PW, text)
        self.assertIn("qa-store-b storeId=", text)
        self.assertIn("qa-merchandiser-c@paspm.example", text)


if __name__ == "__main__":
    unittest.main()

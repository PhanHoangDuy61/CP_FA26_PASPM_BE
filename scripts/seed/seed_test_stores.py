#!/usr/bin/env python3
"""TDP-01: black-box test-data preparation (Store B and Store C with their accounts).

Decided by DEC-20261126-03 (Q-16); described in docs/testing/blackbox/TEST_PLAN.md section 6. Testers must not create
Stores by hand, so running this script is an entry criterion of the test round.

Through the PUBLIC REST API only (no SQL), signed in as the System Admin:
  * Store B "QA Store B" (slug qa-store-b) and Store C "QA Store C" (slug qa-store-c): POST /api/system/stores
  * per Store one Store Admin and one Merchandiser: POST /api/users, then POST /api/auth/activate with the
    ADMIN_INVITE OTP read from Mailpit (the same pattern as seed_demo.py)

Safe to run twice: a Store is found by slug and an account by e-mail before anything is created; the second run
creates nothing. An INACTIVE Store stops the script with a message (it never reactivates a Store).

Passwords come from the environment only (see .env.seed.example) and are never printed or written.
Standard library only (Python 3.9+).
"""
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime

# (slug, name, [(role, e-mail local part, display name)])
STORES = [
    ("qa-store-b", "QA Store B", "b"),
    ("qa-store-c", "QA Store C", "c"),
]
ACCOUNT_DOMAIN = "paspm.example"


def account_plan():
    """The fixed plan: per Store a Store Admin and a Merchandiser (TEST_PLAN section 5: ACC-STA-x, ACC-MER-x)."""
    plan = []
    for slug, name, letter in STORES:
        plan.append({
            "slug": slug, "name": name,
            "accounts": [
                {"role": "STORE_ADMIN", "email": f"qa-storeadmin-{letter}@{ACCOUNT_DOMAIN}",
                 "name": f"QA Store Admin {letter.upper()}"},
                {"role": "MERCHANDISER", "email": f"qa-merchandiser-{letter}@{ACCOUNT_DOMAIN}",
                 "name": f"QA Merchandiser {letter.upper()}"},
            ],
        })
    return plan


class ApiError(Exception):
    def __init__(self, status, body):
        super().__init__(f"HTTP {status}: {str(body)[:300]}")
        self.status, self.body = status, body


def http(method, url, token=None, body=None, timeout=60):
    """One HTTP call; returns (status, parsed JSON or text). Replaced by a fake in the unit tests."""
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    if body is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            text = resp.read().decode()
            return resp.status, (json.loads(text) if text.strip().startswith(("{", "[")) else text)
    except urllib.error.HTTPError as e:
        raise ApiError(e.code, e.read().decode(errors="replace"))


def _epoch(iso):
    try:
        return datetime.fromisoformat(iso.replace("Z", "+00:00")).timestamp()
    except ValueError:
        return 0


class Seeder:
    def __init__(self, api, mailpit, sysadmin_email, sysadmin_password, account_password,
                 http_fn=http, sleep=time.sleep, now=time.time, log=None, otp_wait=30):
        self.api, self.mailpit = api.rstrip("/"), mailpit.rstrip("/")
        self._sysadmin = (sysadmin_email, sysadmin_password)
        self._account_password = account_password
        self.http, self.sleep, self.now = http_fn, sleep, now
        self.log = log or (lambda m: print(m, flush=True))
        self.otp_wait = otp_wait
        self.report = []  # (what, id/e-mail, "created" | "found")

    # ------------------------------------------------------------ API helpers
    def call(self, method, path, token=None, body=None):
        return self.http(method, self.api + path, token=token, body=body)[1]

    def login(self, email, password):
        try:
            return self.call("POST", "/api/auth/login", body={"email": email, "password": password})["accessToken"]
        except ApiError as e:
            if e.status == 401:
                return None
            raise

    def request_otp(self, email, purpose):
        for _ in range(8):
            try:
                self.call("POST", "/api/auth/otp/request", body={"email": email, "purpose": purpose})
                return
            except ApiError as e:
                if e.status != 429:
                    raise
                self.sleep(10)  # OTP cooldown
        raise SystemExit("ERROR: OTP cooldown did not expire")

    def otp_from_mailpit(self, email, purpose_hint, since):
        deadline = self.now() + self.otp_wait
        while self.now() < deadline:
            query = urllib.parse.quote(f"to:{email}")
            res = self.http("GET", f"{self.mailpit}/api/v1/search?query={query}")[1]
            for message in res.get("messages", []):
                snippet = message.get("Snippet", "")
                match = re.search(r"code is (\d{6})", snippet)
                if match and purpose_hint in snippet and _epoch(message.get("Created", "")) >= since - 2:
                    return match.group(1)
            self.sleep(1)
        raise SystemExit(f"ERROR: no OTP mail for {email} found in Mailpit ({self.mailpit})")

    # ------------------------------------------------------------ steps
    def ensure_store(self, token, slug, name):
        def find():
            query = urllib.parse.urlencode({"page": 0, "pageSize": 100, "status": "ALL", "q": slug})
            page = self.call("GET", f"/api/system/stores?{query}", token)
            return [s for s in page["items"] if s["slug"].lower() == slug]

        found = find()
        state = "found"
        if not found:
            self.call("POST", "/api/system/stores", token, {"name": name, "slug": slug})
            found = find()
            state = "created"
        if not found:
            raise SystemExit(f"ERROR: Store {slug} was not created")
        store = found[0]
        if store.get("status", "ACTIVE") != "ACTIVE":
            raise SystemExit(f"ERROR: Store '{slug}' (storeId {store['storeId']}) is INACTIVE; reactivate it first "
                             "(PATCH /api/system/stores/{id}/status). This script never changes a Store status.")
        self.report.append(("store", f"{slug} storeId={store['storeId']}", state))
        return store["storeId"]

    def find_user(self, token, email):
        for u in self.call("GET", "/api/users?q=" + urllib.parse.quote(email) + "&size=100", token):
            if u["email"].lower() == email.lower():
                return u
        return None

    def ensure_account(self, token, account, store_id):
        email, role = account["email"], account["role"]
        if self.login(email, self._account_password):
            self.report.append((role, email, "found"))
            return
        user = self.find_user(token, email)
        started = self.now()
        if user is None:
            self.call("POST", "/api/users", token,
                      {"name": account["name"], "email": email, "roleCode": role, "storeId": store_id})
        elif user["status"] == "PENDING_VERIFICATION":
            self.request_otp(email, "ADMIN_INVITE")  # interrupted earlier run: the first mail may be gone
        else:
            raise SystemExit(f"ERROR: account {email} exists (status {user['status']}) but the configured account "
                             "password does not log in; not touching it")
        otp = self.otp_from_mailpit(email, "ADMIN_INVITE", started)
        self.call("POST", "/api/auth/activate", body={"email": email, "otp": otp, "password": self._account_password})
        if not self.login(email, self._account_password):
            raise SystemExit(f"ERROR: {email} cannot log in after activation")
        self.report.append((role, email, "created"))

    def run(self):
        token = self.login(*self._sysadmin)
        if not token:
            raise SystemExit("ERROR: System Admin login failed (check SEED_SYSADMIN_EMAIL / SEED_SYSADMIN_PASSWORD)")
        for entry in account_plan():
            store_id = self.ensure_store(token, entry["slug"], entry["name"])
            for account in entry["accounts"]:
                self.ensure_account(token, account, store_id)
        return self.report


def format_report(report):
    created = sum(1 for _, _, state in report if state == "created")
    lines = ["TDP-01 test-data preparation", ""]
    lines += [f"  {state:8} {what:13} {ident}" for what, ident, state in report]
    lines += ["", f"{created} created, {len(report) - created} already present."]
    return "\n".join(lines)


def main(environ=os.environ):
    def need(name):
        value = environ.get(name)
        if not value:
            sys.exit(f"ERROR: environment variable {name} is required (see scripts/seed/.env.seed.example)")
        return value

    seeder = Seeder(
        environ.get("SEED_API_URL", "http://127.0.0.1:8080"),
        environ.get("SEED_MAILPIT_URL", "http://127.0.0.1:8025"),
        need("SEED_SYSADMIN_EMAIL"), need("SEED_SYSADMIN_PASSWORD"), need("SEED_TEST_ACCOUNT_PASSWORD"))
    print(format_report(seeder.run()))


if __name__ == "__main__":
    main()

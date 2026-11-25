#!/usr/bin/env python3
"""Repeatable demo seed for an EMPTY PASPM database (Review 2 / staging demo).

Creates through the public REST API, so the business rules apply:
  * one account per role: System Admin (bootstrap account, must already exist), Store Admin,
    Merchandiser, End Customer (OTP codes are read from Mailpit)
  * one category and the FoodMart products (data from FoodMart_Product_Direct_Insert.sql, which is only
    PARSED here; the SQL file itself is not executed) and one inventory batch per product
  * the two transaction CSV files through POST /api/transactions/imports/csv

The ONLY direct SQL is `INSERT INTO store`: the backend has no endpoint that creates a store
(GET /api/system/stores is read-only). The statement is sent through SEED_SQL_COMMAND.

Safe to run twice: every step first looks for the record and skips it when present; an already
imported CSV file (same file name, COMPLETED / COMPLETED_WITH_ERRORS) is not uploaded again.

Secrets come from the environment only (see .env.seed.example); nothing secret is printed.
Standard library only (Python 3.9+).
"""
import json
import os
import re
import shlex
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent


def env(name, default=None, required=False):
    value = os.environ.get(name, default)
    if required and not value:
        sys.exit(f"ERROR: environment variable {name} is required (see scripts/seed/.env.seed.example)")
    return value


API = env("SEED_API_URL", "http://127.0.0.1:8080").rstrip("/")
MAILPIT = env("SEED_MAILPIT_URL", "http://127.0.0.1:8025").rstrip("/")
DATA = Path(env("SEED_DATA_DIR", str(HERE / "data")))
SYSADMIN_EMAIL = env("SEED_SYSADMIN_EMAIL", required=True)
SYSADMIN_PASSWORD = env("SEED_SYSADMIN_PASSWORD", required=True)
ACCOUNT_PASSWORD = env("SEED_ACCOUNT_PASSWORD", required=True)
STORE_NAME = env("SEED_STORE_NAME", "FoodMart Benchmark")
CATEGORY_NAME = env("SEED_CATEGORY_NAME", "FoodMart Imported Items")
BATCH_CODE = env("SEED_BATCH_CODE", "SEED-INITIAL")
BATCH_QTY = int(env("SEED_BATCH_QUANTITY", "1000"))
ACCOUNTS = [
    # role, email variable, default display name
    ("STORE_ADMIN", env("SEED_STORE_ADMIN_EMAIL", required=True), "Demo Store Admin"),
    ("MERCHANDISER", env("SEED_MERCHANDISER_EMAIL", required=True), "Demo Merchandiser"),
]
CUSTOMER_EMAIL = env("SEED_CUSTOMER_EMAIL", required=True)
CUSTOMER_NAME = "Demo End Customer"
# Reads SQL on stdin. Default: the mysql container of the compose project in the repository root.
SQL_COMMAND = env(
    "SEED_SQL_COMMAND",
    "docker compose -f compose.yml exec -T mysql sh -c 'mysql -uroot -p\"$MYSQL_ROOT_PASSWORD\" paspm'",
)
CSV_FILES = ["Transactions_F04_Import_Test_Part1.csv", "Transactions_F04_Import_Test_Part2.csv"]
PRODUCT_SQL = "FoodMart_Product_Direct_Insert.sql"

if not re.fullmatch(r"[A-Za-z0-9 _.\-]{1,150}", STORE_NAME):
    sys.exit("ERROR: SEED_STORE_NAME may contain only letters, digits, space, '_', '.', '-'")

summary = {}


def log(msg):
    print(msg, flush=True)


class ApiError(Exception):
    def __init__(self, status, body):
        super().__init__(f"HTTP {status}: {body[:300]}")
        self.status, self.body = status, body


def http(method, url, token=None, body=None, headers=None, raw=None, timeout=120):
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(url, data=data, method=method)
    if body is not None:
        req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            text = resp.read().decode()
            return resp.status, (json.loads(text) if text.strip().startswith(("{", "[")) else text)
    except urllib.error.HTTPError as e:
        raise ApiError(e.code, e.read().decode(errors="replace"))


def api(method, path, token=None, body=None, **kw):
    return http(method, API + path, token=token, body=body, **kw)[1]


def login(email, password):
    try:
        return api("POST", "/api/auth/login", body={"email": email, "password": password})["accessToken"]
    except ApiError as e:
        if e.status == 401:
            return None
        raise


def sql(statement, quiet=True):
    proc = subprocess.run(SQL_COMMAND, shell=True, input=statement, text=True, capture_output=True, cwd=REPO)
    if proc.returncode != 0:
        sys.exit("ERROR: SEED_SQL_COMMAND failed: " + proc.stderr.strip().splitlines()[-1] if proc.stderr.strip() else "ERROR: SEED_SQL_COMMAND failed")
    return proc.stdout


def wait_for_backend(seconds=120):
    deadline = time.time() + seconds
    while time.time() < deadline:
        try:
            api("POST", "/api/auth/login", body={"email": "probe@invalid.example", "password": "x"})
        except ApiError as e:
            if e.status in (400, 401):
                return
        except Exception:
            pass
        time.sleep(2)
    sys.exit(f"ERROR: backend at {API} did not answer within {seconds}s")


def otp_from_mailpit(email, purpose_hint, since):
    """Newest OTP mail sent to `email` after `since` (epoch seconds)."""
    deadline = time.time() + 30
    while time.time() < deadline:
        q = urllib.parse.quote(f"to:{email}")
        res = http("GET", f"{MAILPIT}/api/v1/search?query={q}")[1]
        for message in res.get("messages", []):
            match = re.search(r"code is (\d{6})", message.get("Snippet", ""))
            created = message.get("Created", "")
            if match and purpose_hint in message.get("Snippet", "") and _epoch(created) >= since - 2:
                return match.group(1)
        time.sleep(1)
    sys.exit(f"ERROR: no OTP mail for {email} found in Mailpit ({MAILPIT})")


def _epoch(iso):
    from datetime import datetime
    try:
        return datetime.fromisoformat(iso.replace("Z", "+00:00")).timestamp()
    except ValueError:
        return 0


def request_otp(email, purpose):
    for _ in range(8):
        try:
            api("POST", "/api/auth/otp/request", body={"email": email, "purpose": purpose})
            return
        except ApiError as e:
            if e.status != 429:
                raise
            time.sleep(10)  # OTP cooldown (default 60 s)
    sys.exit("ERROR: OTP cooldown did not expire")


# ---------------------------------------------------------------- steps
def ensure_store(admin_token):
    def find():
        page = api("GET", "/api/system/stores?page=0&pageSize=100", admin_token)
        return [s for s in page["items"] if s["name"] == STORE_NAME]

    found = find()
    if not found:
        sql(f"INSERT INTO store(name) SELECT '{STORE_NAME}' FROM DUAL "
            f"WHERE NOT EXISTS (SELECT 1 FROM store WHERE name='{STORE_NAME}');")
        found = find()
        summary["store"] = "created (direct SQL: no store endpoint exists)"
    else:
        summary["store"] = "already present"
    if not found:
        sys.exit("ERROR: store was not created")
    if len(found) > 1:
        sys.exit(f"ERROR: several stores are named '{STORE_NAME}'; refusing to guess")
    return found[0]["storeId"]


def find_user(admin_token, email):
    for u in api("GET", "/api/users?q=" + urllib.parse.quote(email) + "&size=100", admin_token):
        if u["email"].lower() == email.lower():
            return u
    return None


def ensure_staff(admin_token, role, email, name, store_id):
    if login(email, ACCOUNT_PASSWORD):
        summary[role] = "already present (login OK)"
        return
    user = find_user(admin_token, email)
    started = time.time()
    if user is None:
        api("POST", "/api/users", admin_token, {"name": name, "email": email, "roleCode": role, "storeId": store_id})
    elif user["status"] == "PENDING_VERIFICATION":
        request_otp(email, "ADMIN_INVITE")
    else:
        sys.exit(f"ERROR: account {email} exists but SEED_ACCOUNT_PASSWORD does not log in")
    otp = otp_from_mailpit(email, "ADMIN_INVITE", started)
    api("POST", "/api/auth/activate", body={"email": email, "otp": otp, "password": ACCOUNT_PASSWORD})
    if not login(email, ACCOUNT_PASSWORD):
        sys.exit(f"ERROR: {email} cannot log in after activation")
    summary[role] = "created and activated through OTP"


def ensure_customer():
    if login(CUSTOMER_EMAIL, ACCOUNT_PASSWORD):
        summary["END_CUSTOMER"] = "already present (login OK)"
        return
    started = time.time()
    try:
        api("POST", "/api/auth/register", body={"email": CUSTOMER_EMAIL})
    except ApiError as e:
        if e.status != 409:
            raise
        request_otp(CUSTOMER_EMAIL, "SELF_REGISTER")  # pending account from an interrupted run
    otp = otp_from_mailpit(CUSTOMER_EMAIL, "SELF_REGISTER", started)
    api("POST", "/api/auth/otp/verify", body={"email": CUSTOMER_EMAIL, "otp": otp})
    api("POST", "/api/auth/register/complete",
        body={"name": CUSTOMER_NAME, "email": CUSTOMER_EMAIL, "password": ACCOUNT_PASSWORD})
    if not login(CUSTOMER_EMAIL, ACCOUNT_PASSWORD):
        sys.exit("ERROR: customer cannot log in after registration")
    summary["END_CUSTOMER"] = "created through self-registration and OTP"


ROW = re.compile(r"^\((\d+),\s*(\d+),\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*([01]),\s*([\d.]+),\s*([\d.]+),\s*'(ACTIVE|INACTIVE)',"
                 r"\s*(NULL|[\d.]+),\s*(NULL|[\d.]+),\s*(\d+)\)[,;]?\s*$")


def read_products():
    products = []
    for line in (DATA / PRODUCT_SQL).read_text(encoding="utf-8").splitlines():
        m = ROW.match(line.strip())
        if m:
            products.append({
                "sku": m.group(3), "name": m.group(4), "unit": m.group(5), "requiresExpiry": m.group(6) == "1",
                "sellingPrice": m.group(7), "costPrice": m.group(8), "status": m.group(9),
                "expiryPushDays": int(m.group(12)),
            })
    if not products:
        sys.exit(f"ERROR: no product rows parsed from {DATA / PRODUCT_SQL}")
    return products


def ensure_catalog(token):
    cats = [c for c in api("GET", "/api/categories", token) if c["name"] == CATEGORY_NAME]
    if cats:
        category_id = cats[0]["id"]
        summary["category"] = "already present"
    else:
        category_id = api("POST", "/api/categories", token, {"name": CATEGORY_NAME, "status": "ACTIVE"})["id"]
        summary["category"] = "created"

    existing, page = {}, 0
    while True:
        res = api("GET", f"/api/products?page={page}&pageSize=100&sortBy=name", token)
        for p in res["content"]:
            existing[p["sku"]] = p
        page += 1
        if page >= res["totalPages"]:
            break

    wanted = read_products()
    created = 0
    for p in wanted:
        if p["sku"] in existing:
            continue
        view = api("POST", "/api/products", token, dict(p, categoryId=category_id))
        existing[p["sku"]] = view
        created += 1
    summary["products"] = f"{created} created, {len(wanted) - created} already present (of {len(wanted)} in the SQL file)"

    batches = 0
    for p in wanted:
        view = existing[p["sku"]]
        if view.get("stockQuantity", 0) > 0:
            continue
        pid = view["id"]
        if any(b["batchCode"] == BATCH_CODE for b in api("GET", f"/api/products/{pid}/batches", token)):
            continue
        api("POST", f"/api/products/{pid}/batches", token, {
            "batchCode": BATCH_CODE, "receivedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "initialQuantity": BATCH_QTY})
        batches += 1
    summary["inventory"] = f"{batches} batches '{BATCH_CODE}' x {BATCH_QTY} created"


def multipart_file(field, path):
    boundary = uuid.uuid4().hex
    head = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{field}\"; filename=\"{path.name}\"\r\n"
            "Content-Type: text/csv\r\n\r\n").encode()
    tail = f"\r\n--{boundary}--\r\n".encode()
    return head + path.read_bytes() + tail, f"multipart/form-data; boundary={boundary}"


def import_csvs(token):
    done = {}
    page = 0
    while True:
        res = api("GET", f"/api/transactions/imports?page={page}&pageSize=100", token)
        for b in res["content"]:
            if b["status"] in ("COMPLETED", "COMPLETED_WITH_ERRORS"):
                done.setdefault(b["filename"], b)
        if (page + 1) * 100 >= res["totalElements"]:
            break
        page += 1
    for name in CSV_FILES:
        if name in done:
            b = done[name]
            summary[f"import {name}"] = f"skipped, already imported (batch {b['importBatchId']}, {b['status']})"
            continue
        raw, ctype = multipart_file("file", DATA / name)
        res = http("POST", API + "/api/transactions/imports/csv", token=token, raw=raw,
                   headers={"Content-Type": ctype}, timeout=600)[1]
        summary[f"import {name}"] = (
            f"batch {res['importBatchId']} {res['status']}: rows total={res['totalCount']} valid={res['validCount']} "
            f"rejected={res['rejectedCount']}; orders total={res['totalOrderCount']} accepted={res['acceptedOrderCount']} "
            f"rejected={res['rejectedOrderCount']} duplicate={res['duplicateOrderCount']} conflict={res['conflictOrderCount']}")


def counts():
    tables = ["store", "app_user", "category", "product", "inventory_batch", "import_batch", "import_staging_row",
              "sales_transaction", "transaction_item"]
    query = " UNION ALL ".join(f"SELECT '{t}', COUNT(*) FROM {t}" for t in tables) + ";"
    out = sql(query)
    result = {}
    for line in out.splitlines():
        parts = line.split()
        if len(parts) == 2 and parts[1].isdigit():
            result[parts[0]] = int(parts[1])
    return result


def main():
    log(f"PASPM demo seed -> {API}")
    wait_for_backend()
    admin = login(SYSADMIN_EMAIL, SYSADMIN_PASSWORD)
    if not admin:
        sys.exit("ERROR: System Admin login failed (the account comes from BOOTSTRAP_EMAIL / BOOTSTRAP_PASSWORD "
                 "when the backend starts on an empty database)")
    store_id = ensure_store(admin)
    for role, email, name in ACCOUNTS:
        ensure_staff(admin, role, email, name, store_id)
    ensure_customer()
    store_admin = login(ACCOUNTS[0][1], ACCOUNT_PASSWORD)
    ensure_catalog(store_admin)
    import_csvs(store_admin)
    log("\n== Seed summary ==")
    for k, v in summary.items():
        log(f"  {k}: {v}")
    log("\n== Record counts (SQL COUNT) ==")
    for k, v in counts().items():
        log(f"  {k}: {v}")


if __name__ == "__main__":
    main()

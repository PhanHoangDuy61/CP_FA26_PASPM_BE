#!/usr/bin/env python3
"""Repeatable demo seed for an EMPTY PASPM database (Review 2 / staging demo).

Creates through the public REST API, so the business rules apply:
  * one account per role: System Admin (bootstrap account, must already exist), Store Admin,
    Merchandiser, End Customer (OTP codes are read from Mailpit)
  * one category and the FoodMart products (data from FoodMart_Product_Direct_Insert.sql, which is only
    PARSED here; the SQL file itself is not executed) and one inventory batch per product.
    The backend generates every SKU (DEC-20261119-07): a product is created as "FoodMart Item NNNN" for the
    dataset code FOODMART-NNNN, its server SKU is read back and written to mapping.csv
  * import-ready copies of the two transaction CSV files (only the sku column is replaced through mapping.csv;
    the originals in scripts/seed/data are never modified), imported through POST /api/transactions/imports/csv.
    mapping.csv and the copies go to SEED_OUTPUT_DIR (default scripts/seed/out/<store slug>/, git-ignored)
  * Weight v2 / F06 demo data (skip with SEED_WEIGHT_V2=0): a deterministic share of the products requires expiry
    and its batches carry expiry dates and push days; four seasons assigned to product groups (Merchandiser);
    the Store weight configuration alpha/beta/lambda/N (Merchandiser). The plan itself is in weight_v2_plan.py.

The Store is created through POST /api/system/stores (DEC-20261123-01). Direct SQL is used only for the final
record counts, sent through SEED_SQL_COMMAND.

Safe to run twice: every step first looks for the record and skips it when present (products by their exact
name "FoodMart Item NNNN", batches by "the product already has a batch", never by batch code); an already
imported CSV file (same file name, COMPLETED / COMPLETED_WITH_ERRORS) is not uploaded again.
Expiry dates cannot be added to a batch that already exists (the batch API has no such field) and a product
cannot be switched to requiresExpiry while one of its batches has no expiry date, so a database seeded before
Weight v2 keeps its undated batches; recreate the database for the full demo (see scripts/seed/README.md).

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
from datetime import datetime
from decimal import Decimal
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
sys.path.insert(0, str(HERE))  # seed.sh runs `python3 -I`, which does not add the script directory itself
import weight_v2_plan as w2  # noqa: E402
import foodmart_mapping as fm  # noqa: E402


def env(name, default=None, required=False):
    value = os.environ.get(name, default)
    if required and not value:
        sys.exit(f"ERROR: environment variable {name} is required (see scripts/seed/.env.seed.example)")
    return value


API = env("SEED_API_URL", "http://127.0.0.1:8080").rstrip("/")
MAILPIT = env("SEED_MAILPIT_URL", "http://127.0.0.1:8025").rstrip("/")
DATA = Path(env("SEED_DATA_DIR", str(HERE / "data")))
OUTPUT_ROOT = Path(env("SEED_OUTPUT_DIR", str(HERE / "out")))  # git-ignored: mapping.csv and import-ready CSV copies
SYSADMIN_EMAIL = env("SEED_SYSADMIN_EMAIL", required=True)
SYSADMIN_PASSWORD = env("SEED_SYSADMIN_PASSWORD", required=True)
ACCOUNT_PASSWORD = env("SEED_ACCOUNT_PASSWORD", required=True)
STORE_NAME = env("SEED_STORE_NAME", "FoodMart Benchmark")
STORE_SLUG = env("SEED_STORE_SLUG", "foodmart-benchmark")
CATEGORY_NAME = env("SEED_CATEGORY_NAME", "FoodMart Imported Items")
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



def store_today():
    """Default SEED_AS_OF: the calendar date in the Store time zone (the backend uses Asia/Ho_Chi_Minh)."""
    try:
        from zoneinfo import ZoneInfo
        return datetime.now(ZoneInfo("Asia/Ho_Chi_Minh")).date()
    except Exception:  # no tz database: fall back to the local date
        return datetime.now().date()


try:
    PLAN = w2.config_from_env(os.environ, store_today())
except w2.PlanConfigError as e:
    sys.exit(f"ERROR: {e}")

if not re.fullmatch(r"[A-Za-z0-9 _.\-]{1,150}", STORE_NAME):
    sys.exit("ERROR: SEED_STORE_NAME may contain only letters, digits, space, '_', '.', '-'")
if not re.fullmatch(r"[a-z0-9]+(-[a-z0-9]+)*", STORE_SLUG) or len(STORE_SLUG) > 50:
    sys.exit("ERROR: SEED_STORE_SLUG must be lowercase letters and digits separated by single '-' (max 50)")

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
        query = urllib.parse.urlencode({"page": 0, "pageSize": 100, "status": "ALL", "q": STORE_NAME})
        page = api("GET", f"/api/system/stores?{query}", admin_token)
        return [s for s in page["items"] if s["name"].strip().lower() == STORE_NAME.strip().lower()]

    found = find()
    if not found:
        api("POST", "/api/system/stores", admin_token, {"name": STORE_NAME, "slug": STORE_SLUG})
        found = find()
        summary["store"] = "created (POST /api/system/stores)"
    else:
        summary["store"] = "already present"
    if not found:
        sys.exit("ERROR: store was not created")
    if len(found) > 1:
        sys.exit(f"ERROR: several stores are named '{STORE_NAME}'; refusing to guess")
    if found[0].get("status", "ACTIVE") != "ACTIVE":
        sys.exit(f"ERROR: store '{STORE_NAME}' is INACTIVE; reactivate it first (PATCH /api/system/stores/{{id}}/status)")
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


ROW = re.compile(r"^\((\d+),\s*(\d+),\s*'([^']*)',\s*'([^']*)',\s*'([^']*)',\s*([01]),\s*([\d.]+),\s*([\d.]+),\s*'(ACTIVE|INACTIVE)'\)[,;]?\s*$")


def read_products():
    products = []
    for line in (DATA / PRODUCT_SQL).read_text(encoding="utf-8").splitlines():
        m = ROW.match(line.strip())
        if m:
            code = m.group(3)
            if fm.code_from_name(m.group(4)) != code:
                sys.exit(f"ERROR: {PRODUCT_SQL}: product name '{m.group(4)}' does not match its code {code}")
            products.append({
                "code": code, "name": m.group(4), "unit": m.group(5), "requiresExpiry": m.group(6) == "1",
                "sellingPrice": m.group(7), "costPrice": m.group(8), "status": m.group(9),
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

    # Existing products by exact name. The FoodMart names are unique, so the name identifies the dataset code; the SKU
    # does not (the backend generates it, DEC-20261119-07; a database seeded before 2026-11-20 keeps FOODMART-NNNN).
    by_name, page = {}, 0
    while True:
        res = api("GET", f"/api/products?page={page}&pageSize=100&sortBy=name", token)
        for p in res["content"]:
            by_name.setdefault(p["name"], []).append(p)
        page += 1
        if page >= res["totalPages"]:
            break

    wanted = read_products()
    mapping, views, created = {}, {}, 0
    for p in wanted:
        code = p["code"]
        found = by_name.get(p["name"], [])
        if len(found) > 1:
            sys.exit(f"ERROR: several products are named '{p['name']}'; refusing to guess")
        if found:
            view = found[0]
        else:
            body = {k: p[k] for k in ("name", "unit", "requiresExpiry", "sellingPrice", "costPrice", "status")}
            body["categoryId"] = category_id
            body["confirmSimilarName"] = True  # every FoodMart name is one character away from another (409 otherwise)
            if PLAN.enabled:  # the SQL file marks every product as non-perishable; Weight v2 flags a deterministic share
                body["requiresExpiry"] = w2.plan_product(PLAN, code).requires_expiry
            view = api("POST", "/api/products", token, body)
            created += 1
        views[code] = view
        mapping[code] = fm.Mapped(code, view["sku"], view["id"], view["name"])
    summary["products"] = f"{created} created, {len(wanted) - created} already present (of {len(wanted)} in the SQL file)"

    batches = dated = with_push = undated_legacy = 0
    for p in wanted:
        code = p["code"]
        view = views[code]
        if view.get("stockQuantity", 0) > 0:
            if (PLAN.enabled and not view.get("requiresExpiry")
                    and w2.plan_product(PLAN, code).requires_expiry):
                undated_legacy += 1  # seeded before Weight v2: cannot be switched to requiresExpiry any more
            continue
        pid = view["id"]
        if api("GET", f"/api/products/{pid}/batches", token):
            continue  # the product already has a batch (batch codes are generated by the backend; never matched here)
        received = f"{PLAN.as_of}T00:00:00Z" if PLAN.enabled else time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        body = {"receivedAt": received, "initialQuantity": BATCH_QTY}
        if view.get("requiresExpiry"):
            plan = w2.plan_product(PLAN, code, force_expiry=True)
            body.update(initialQuantity=plan.quantity, expiryDate=plan.expiry_date.isoformat())
            if plan.push_day:
                body["pushDay"] = plan.push_day.isoformat()
                with_push += 1
            dated += 1
        api("POST", f"/api/products/{pid}/batches", token, body)
        batches += 1
    summary["inventory"] = (f"{batches} batches created ({dated} with expiry date, of which {with_push} with a push day and "
                            f"{dated - with_push} without (MISSING_PUSH_DAY); {batches - dated} undated, x {BATCH_QTY})")
    if PLAN.enabled:
        summary["perishable products"] = (
            f"{sum(1 for v in views.values() if v.get('requiresExpiry'))} of {len(views)} require expiry")
        if undated_legacy:
            summary["WARNING legacy batches"] = (
                f"{undated_legacy} products would be perishable under this seed but already have an undated batch "
                "(seeded before Weight v2). The API cannot add an expiry date to a batch or flag the product while it has "
                "one; recreate the database for the full demo.")
    return mapping


def write_import_files(mapping):
    """mapping.csv plus import-ready copies of the transaction CSVs (only the sku column replaced)."""
    out = OUTPUT_ROOT / STORE_SLUG
    fm.write_mapping(out / "mapping.csv", mapping)
    files = {}
    for name in CSV_FILES:
        rows = fm.rewrite_csv(DATA / name, out / name, mapping)
        files[name] = out / name
        summary[f"import-ready {name}"] = f"{rows} rows -> {out / name}"
    summary["mapping"] = f"{len(mapping)} products -> {out / 'mapping.csv'}"
    return files


def paged(token, path, size=100):
    """All items of an `items`/`page`/`pageSize`/`totalElements` list endpoint."""
    items, page = [], 0
    while True:
        res = api("GET", f"{path}?page={page}&pageSize={size}", token)
        items += res["items"]
        page += 1
        if not res["items"] or page * size >= res["totalElements"]:
            return items


def ensure_seasons(merchandiser, mapping):
    """Create the demo seasons and assign product groups (blocks of FoodMart codes; the data has one category only).

    The order is the FoodMart code from mapping.csv, never the server SKU, so the groups do not depend on insertion order."""
    codes = fm.ordered_codes(mapping)
    present = {s["name"].lower(): s for s in paged(merchandiser, "/api/utility/seasons")}
    created = assigned = 0
    notes = []
    for sp in w2.season_plans(PLAN):
        season = present.get(sp.name.lower())
        if season is None:
            season = api("POST", "/api/utility/seasons", merchandiser, {
                "name": sp.name, "startDate": sp.start.isoformat(), "endDate": sp.end.isoformat(),
                "weight": float(sp.weight)})
            created += 1
        elif (season["startDate"], season["endDate"]) != (sp.start.isoformat(), sp.end.isoformat()):
            notes.append(f"'{sp.name}' kept with its dates {season['startDate']}..{season['endDate']} (SEED_AS_OF differs)")
        sid = season["seasonId"]
        have = {i["productId"] for i in paged(merchandiser, f"/api/utility/seasons/{sid}/products")}
        for index, code in enumerate(codes):
            pid = mapping[code].product_id
            if sp.state in w2.season_states_for(PLAN, index, len(codes)) and pid not in have:
                api("PUT", f"/api/utility/seasons/{sid}/products/{pid}", merchandiser)
                assigned += 1
    summary["seasons"] = f"{created} created, {len(w2.season_plans(PLAN)) - created} already present; {assigned} product assignments added"
    if notes:
        summary["season note"] = "; ".join(notes)


def ensure_weight_config(admin, merchandiser):
    view = api("GET", "/api/utility/config", merchandiser)
    cfg = view["config"]
    wanted = (PLAN.alpha, PLAN.beta, PLAN.lam, PLAN.sales_window_days)
    if (Decimal(str(cfg["alpha"])), Decimal(str(cfg["beta"])), Decimal(str(cfg["lambda"])), cfg["salesWindowDays"]) == wanted:
        summary["weight config"] = "already set (version %s)" % cfg["version"]
        return
    if Decimal(str(view["lambdaPolicy"]["lambdaMax"])) < PLAN.lam:
        api("PUT", "/api/system/utility/lambda-policy", admin, {"lambdaMax": float(PLAN.lam)})
        summary["lambda policy"] = f"lambdaMax raised to {PLAN.lam} by the System Admin (it was lower than lambda)"
    done = api("PUT", "/api/utility/config", merchandiser, {
        "alpha": float(PLAN.alpha), "beta": float(PLAN.beta), "lambda": float(PLAN.lam),
        "salesWindowDays": PLAN.sales_window_days})["config"]
    summary["weight config"] = (f"alpha={done['alpha']} beta={done['beta']} lambda={done['lambda']} "
                                f"salesWindowDays={done['salesWindowDays']} (version {done['version']})")


def multipart_file(field, path):
    boundary = uuid.uuid4().hex
    head = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"{field}\"; filename=\"{path.name}\"\r\n"
            "Content-Type: text/csv\r\n\r\n").encode()
    tail = f"\r\n--{boundary}--\r\n".encode()
    return head + path.read_bytes() + tail, f"multipart/form-data; boundary={boundary}"


def import_csvs(token, files):
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
        raw, ctype = multipart_file("file", files[name])
        res = http("POST", API + "/api/transactions/imports/csv", token=token, raw=raw,
                   headers={"Content-Type": ctype}, timeout=600)[1]
        summary[f"import {name}"] = (
            f"batch {res['importBatchId']} {res['status']}: rows total={res['totalCount']} valid={res['validCount']} "
            f"rejected={res['rejectedCount']}; orders total={res['totalOrderCount']} accepted={res['acceptedOrderCount']} "
            f"rejected={res['rejectedOrderCount']} duplicate={res['duplicateOrderCount']} conflict={res['conflictOrderCount']}")


def counts():
    tables = ["store", "app_user", "category", "product", "inventory_batch", "import_batch", "import_staging_row",
              "sales_transaction", "transaction_item", "season", "product_season"]
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
    log(f"Weight v2: {'on' if PLAN.enabled else 'off'}, SEED_AS_OF={PLAN.as_of}")
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
    mapping = ensure_catalog(store_admin)
    import_csvs(store_admin, write_import_files(mapping))
    if PLAN.enabled:
        merchandiser = login(ACCOUNTS[1][1], ACCOUNT_PASSWORD)
        ensure_seasons(merchandiser, mapping)
        ensure_weight_config(admin, merchandiser)
    else:
        summary["weight v2"] = "skipped (SEED_WEIGHT_V2=0)"
    log("\n== Seed summary ==")
    for k, v in summary.items():
        log(f"  {k}: {v}")
    log("\n== Record counts (SQL COUNT) ==")
    for k, v in counts().items():
        log(f"  {k}: {v}")


if __name__ == "__main__":
    main()

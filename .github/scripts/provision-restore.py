#!/usr/bin/env python3
"""Provision one restricted restore identity on the disposable CI MySQL only."""
import json
import os
import re
import subprocess
import sys


def provision(value):
    if os.environ.get("PASPM_CI_DISPOSABLE_MYSQL") != "true":
        raise ValueError("Disposable CI database opt-in required")
    server = os.environ["PASPM_TEST_MYSQL_SERVER"]
    match = re.fullmatch(r"jdbc:mysql://127\.0\.0\.1:([0-9]{1,5})/", server)
    if not match or not 1 <= int(match[1]) <= 65535:
        raise ValueError("Explicit loopback-only test server required")
    schema, username, password = (value[key] for key in ("schema", "username", "password"))
    if not re.fullmatch(r"paspm_(?:p0|bc)_test_[a-f0-9]{32}", schema):
        raise ValueError("Refusing non-test schema")
    if not re.fullmatch(r"bc_restore_[a-f0-9]{12}", username):
        raise ValueError("Refusing non-test restore identity")
    if not re.fullmatch(r"[A-Za-z0-9_-]{16,128}", password):
        raise ValueError("Invalid generated credential format")
    scope = schema.replace("_", "\\_")
    sql = (f"CREATE USER '{username}'@'%' IDENTIFIED BY '{password}'; "
           f"GRANT ALL PRIVILEGES ON `{scope}`.* TO '{username}'@'%';")
    environment = os.environ.copy()
    environment["MYSQL_PWD"] = environment.get("PASPM_TEST_MYSQL_PASSWORD", "")
    result = subprocess.run(["mysql", "--no-defaults", "--protocol=TCP", "--host=127.0.0.1",
                             "--port=" + match[1], "--user=" + environment["PASPM_TEST_MYSQL_USER"]],
                            input=sql, text=True, capture_output=True, env=environment, timeout=20)
    if result.returncode:
        raise RuntimeError("Restricted restore identity provisioning failed")


if __name__ == "__main__":
    try:
        provision(json.load(sys.stdin))
    except Exception:
        print("Disposable restore provisioning refused; no SQL or credentials are logged.", file=sys.stderr)
        sys.exit(1)

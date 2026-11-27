"""Demo mining policy for the seed: the System Admin minUtil policy `PERCENTAGE 0.12` (pure functions, standard library only).

The mining policy is one global setting of the System Admin (`GET`/`PUT /api/system/mining/config`, versioned: the PUT
carries `expectedVersion`). On the demo data (full range 2026-10-01..04) `PERCENTAGE 0.12` gives 182 patterns and F06
generates 17 BUNDLE, 41 CROSS_SELL and 5 FBT candidates (ROC-20261127-002), so the seed sets it and testers do not have to.

`ensure_mining_policy` reads the current policy and writes a new version only when the mode or the value differ, so a
second run changes nothing. It never touches mining requests, runs or snapshots (they keep their own frozen input).
`call(method, path, token, body)` is the seed's HTTP function: it returns the parsed JSON and raises on an error status.
"""
from decimal import Decimal, InvalidOperation

MODE = "PERCENTAGE"
VALUE = Decimal("0.12")
PATH = "/api/system/mining/config"


class PolicyError(Exception):
    """The policy could not be read or written. Nothing was changed by the failed step."""


def _decimal(value):
    try:
        return Decimal(str(value))
    except (InvalidOperation, ValueError):
        return None


def ensure_mining_policy(call, admin_token):
    """Make the System Admin policy `PERCENTAGE 0.12`; returns one summary line for the seed output."""
    try:
        current = call("GET", PATH, admin_token)
    except Exception as e:  # the seed's ApiError, a network error, ...
        raise PolicyError(f"GET {PATH} failed: {e}") from e
    version = current.get("version")
    if not isinstance(version, int) or isinstance(version, bool) or version < 0:
        raise PolicyError(f"GET {PATH} returned no usable version: {current!r}")
    if current.get("minUtilMode") == MODE and _decimal(current.get("minUtilValue")) == VALUE:
        return f"already {MODE} {VALUE} (version {version}); nothing changed"
    body = {"minUtilMode": MODE, "minUtilValue": float(VALUE), "expectedVersion": version}
    try:
        done = call("PUT", PATH, admin_token, body)
    except Exception as e:
        raise PolicyError(f"PUT {PATH} failed ({e}); the stored policy is unchanged, run the seed again") from e
    was = "none" if current.get("minUtilMode") is None else f"{current.get('minUtilMode')} {current.get('minUtilValue')}"
    return f"updated to {MODE} {VALUE} (was {was}; now version {done.get('version', version + 1)})"

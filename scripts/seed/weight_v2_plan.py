"""Deterministic plan for the Weight v2 / F06 part of the demo seed (pure functions, standard library only).

Nothing here talks to the backend or reads the process environment; `seed_demo.py` feeds it the environment and
applies the plan through the public REST API. Every per-product decision is a hash of (random seed, SKU, purpose),
so a product always gets the same answer whatever the iteration order, whichever products already exist, and however
many times the script runs. All dates are offsets from `SEED_AS_OF`.
"""
import hashlib
from dataclasses import dataclass
from datetime import date, timedelta
from decimal import Decimal, InvalidOperation

STATES = ("ACTIVE", "ACTIVE_OVERLAP", "FUTURE", "ENDED")


class PlanConfigError(ValueError):
    pass


@dataclass(frozen=True)
class PlanConfig:
    enabled: bool
    as_of: date
    seed: str
    expiry_product_pct: float
    soon_pct: float
    mid_pct: float
    push_reached_pct: float
    push_missing_pct: float
    push_lead_min_days: int
    push_lead_max_days: int
    expiry_edge_pct: float
    batch_quantity: int
    qty_min_pct: float
    alpha: Decimal
    beta: Decimal
    lam: Decimal
    sales_window_days: int
    season_active_weight: Decimal
    season_overlap_weight: Decimal
    season_future_weight: Decimal
    season_ended_weight: Decimal


@dataclass(frozen=True)
class ProductPlan:
    sku: str
    requires_expiry: bool
    expiry_date: "date | None"
    push_day: "date | None"
    push_kind: str  # NONE (not perishable), MISSING, REACHED or SET
    quantity: int


@dataclass(frozen=True)
class SeasonPlan:
    state: str
    name: str
    start: date
    end: date
    weight: Decimal
    lo: float  # members are the products whose position i/N lies in [lo, hi)
    hi: float


def _pct(environ, name, default, low=0.0, high=100.0):
    try:
        value = float(environ.get(name, default))
    except ValueError:
        raise PlanConfigError(f"{name} must be a number")
    if not low <= value <= high:
        raise PlanConfigError(f"{name} must be between {low:g} and {high:g}")
    return value


def _int(environ, name, default, low, high):
    try:
        value = int(environ.get(name, default))
    except ValueError:
        raise PlanConfigError(f"{name} must be an integer")
    if not low <= value <= high:
        raise PlanConfigError(f"{name} must be between {low} and {high}")
    return value


def _decimal(environ, name, default, low, high):
    try:
        value = Decimal(str(environ.get(name, default)))
    except InvalidOperation:
        raise PlanConfigError(f"{name} must be a decimal number")
    if not Decimal(low) <= value <= Decimal(high):
        raise PlanConfigError(f"{name} must be between {low} and {high}")
    return value


def config_from_env(environ, today):
    """Build and validate the plan configuration. `today` is the default for SEED_AS_OF (Store calendar date)."""
    try:
        as_of = date.fromisoformat(environ.get("SEED_AS_OF") or today.isoformat())
    except ValueError:
        raise PlanConfigError("SEED_AS_OF must be an ISO date (YYYY-MM-DD)")
    soon, mid = _pct(environ, "SEED_EXPIRY_SOON_PCT", 15), _pct(environ, "SEED_EXPIRY_MID_PCT", 25)
    # Demo push days (PO, 2026-11-25): ~60% set, ~15% already reached, ~25% missing; no `expiry - 7` rule.
    reached, missing = _pct(environ, "SEED_PUSH_REACHED_PCT", 15), _pct(environ, "SEED_PUSH_MISSING_PCT", 25)
    lead_min, lead_max = _int(environ, "SEED_PUSH_LEAD_MIN_DAYS", 3, 0, 365), _int(environ, "SEED_PUSH_LEAD_MAX_DAYS", 21, 0, 365)
    if lead_min > lead_max:
        raise PlanConfigError("SEED_PUSH_LEAD_MIN_DAYS must not exceed SEED_PUSH_LEAD_MAX_DAYS")
    edge = _pct(environ, "SEED_EXPIRY_EDGE_PCT", 3, 0, 50)
    if edge + soon + mid > 100:
        raise PlanConfigError("SEED_EXPIRY_EDGE_PCT + SEED_EXPIRY_SOON_PCT + SEED_EXPIRY_MID_PCT must not exceed 100")
    if reached + missing > 100:
        raise PlanConfigError("SEED_PUSH_REACHED_PCT + SEED_PUSH_MISSING_PCT must not exceed 100")
    alpha = _decimal(environ, "SEED_WEIGHT_ALPHA", "0.6", 0, 1)
    beta = _decimal(environ, "SEED_WEIGHT_BETA", "0.4", 0, 1)
    if alpha + beta != 1:
        raise PlanConfigError("SEED_WEIGHT_ALPHA + SEED_WEIGHT_BETA must equal 1")
    try:
        window = int(environ.get("SEED_WEIGHT_SALES_WINDOW_DAYS", 30))
        quantity = int(environ.get("SEED_BATCH_QUANTITY", 1000))
    except ValueError:
        raise PlanConfigError("SEED_WEIGHT_SALES_WINDOW_DAYS and SEED_BATCH_QUANTITY must be integers")
    if not 1 <= window <= 365:
        raise PlanConfigError("SEED_WEIGHT_SALES_WINDOW_DAYS must be between 1 and 365")
    if quantity < 1:
        raise PlanConfigError("SEED_BATCH_QUANTITY must be positive")
    return PlanConfig(
        enabled=str(environ.get("SEED_WEIGHT_V2", "1")).strip().lower() not in ("0", "false", "no", "off"),
        as_of=as_of, seed=str(environ.get("SEED_RANDOM_SEED", "paspm-demo-weight-v2")),
        expiry_product_pct=_pct(environ, "SEED_EXPIRY_PRODUCT_PCT", 35), soon_pct=soon, mid_pct=mid,
        push_reached_pct=reached, push_missing_pct=missing, push_lead_min_days=lead_min, push_lead_max_days=lead_max,
        expiry_edge_pct=edge, batch_quantity=quantity,
        qty_min_pct=_pct(environ, "SEED_EXPIRY_QTY_MIN_PCT", 2, 0.1, 100),
        alpha=alpha, beta=beta, lam=_decimal(environ, "SEED_WEIGHT_LAMBDA", "0.3", 0, 1000000),
        sales_window_days=window,
        season_active_weight=_decimal(environ, "SEED_SEASON_ACTIVE_WEIGHT", "0.7", 0, 1),
        season_overlap_weight=_decimal(environ, "SEED_SEASON_OVERLAP_WEIGHT", "0.8", 0, 1),
        season_future_weight=_decimal(environ, "SEED_SEASON_FUTURE_WEIGHT", "0.9", 0, 1),
        season_ended_weight=_decimal(environ, "SEED_SEASON_ENDED_WEIGHT", "0.6", 0, 1))


def unit(config, key, purpose):
    """Uniform number in [0, 1) that depends only on (seed, key, purpose)."""
    digest = hashlib.sha256(f"{config.seed}|{key}|{purpose}".encode()).digest()
    return int.from_bytes(digest[:8], "big") / 2 ** 64


def plan_product(config, sku, force_expiry=False):
    """`force_expiry`: the product is already flagged requiresExpiry in the database, so it needs dates whatever the share."""
    if not force_expiry and unit(config, sku, "perishable") >= config.expiry_product_pct / 100:
        return ProductPlan(sku, False, None, None, "NONE", config.batch_quantity)
    u, v = unit(config, sku, "expiry-bucket"), unit(config, sku, "expiry-days")
    edge = config.expiry_edge_pct / 100
    if u < edge:
        days = -1 if v < 0.5 else 0          # expired yesterday / expires today (DEC-20261124-01 boundary demo)
    elif u < edge + config.soon_pct / 100:
        days = int(v * 8)                    # 0..7
    elif u < edge + (config.soon_pct + config.mid_pct) / 100:
        days = 8 + int(v * 23)               # 8..30
    else:
        days = 31 + int(v * 150)             # 31..180
    expiry = config.as_of + timedelta(days=days)
    p, w = unit(config, sku, "push-kind"), unit(config, sku, "push-days")
    if p < config.push_missing_pct / 100:
        push, kind = None, "MISSING"
    elif p < (config.push_missing_pct + config.push_reached_pct) / 100:
        push, kind = min(config.as_of - timedelta(days=int(w * 11)), expiry), "REACHED"   # 0..10 days ago, never after expiry
    else:
        span = config.push_lead_max_days - config.push_lead_min_days + 1
        push, kind = expiry - timedelta(days=config.push_lead_min_days + int(w * span)), "SET"  # spread, no fixed lead
    minimum = max(1, round(config.batch_quantity * config.qty_min_pct / 100))
    # log-uniform between the minimum and the configured quantity, so sell-through differs a lot between products
    quantity = max(minimum, round(config.batch_quantity * (minimum / config.batch_quantity) ** unit(config, sku, "quantity")))
    return ProductPlan(sku, True, expiry, push, kind, min(quantity, config.batch_quantity))


def season_plans(config):
    d = config.as_of
    return [
        SeasonPlan("ACTIVE", "Demo: Year-end Sale", d - timedelta(days=14), d + timedelta(days=45),
                   config.season_active_weight, 0.00, 0.30),
        SeasonPlan("ACTIVE_OVERLAP", "Demo: Flash Sale Week", d - timedelta(days=3), d + timedelta(days=10),
                   config.season_overlap_weight, 0.27, 0.40),
        SeasonPlan("FUTURE", "Demo: T\u1ebft 2027", d + timedelta(days=55), d + timedelta(days=85),
                   config.season_future_weight, 0.40, 0.70),
        SeasonPlan("ENDED", "Demo: Back-to-School (ended)", d - timedelta(days=75), d - timedelta(days=30),
                   config.season_ended_weight, 0.65, 0.85),
    ]


def season_states_for(config, index, total):
    """States of the seasons that product number `index` (0-based, SKU order) of `total` belongs to."""
    position = index / total
    return [s.state for s in season_plans(config) if s.lo <= position < s.hi]

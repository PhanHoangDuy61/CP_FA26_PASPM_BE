"""FoodMart code -> server-generated SKU mapping and import-ready CSV copies (pure functions, standard library only).

Since 2026-11-20 the backend generates every Product SKU (`SKU-` + the 11-digit product ID, DEC-20261119-07), so the
FoodMart codes `FOODMART-NNNN` of the dataset (NNNN = the SPMF FoodMart item ID) can no longer be sent as SKUs.
`seed_demo.py` creates each product with the name `FoodMart Item NNNN`, reads back its SKU, writes `mapping.csv`
and uses `rewrite_csv` to make copies of the transaction files whose `sku` column holds the server SKUs. The original
files are never modified; only the `sku` column of the copy differs, every other byte is kept.
"""
import csv
import re
from dataclasses import dataclass
from pathlib import Path

HEADER = "source_order_id,occurred_at,sku,quantity,unit_price,unit_cost,status"
SKU_COLUMN = 2
_NAME = re.compile(r"^FoodMart Item (\d{4})$")


class MappingError(ValueError):
    pass


@dataclass(frozen=True)
class Mapped:
    code: str         # FOODMART-NNNN (dataset code, stable across databases)
    sku: str          # SKU stored by the backend (SKU-00000000042, or FOODMART-NNNN in a database seeded before 2026-11-20)
    product_id: int
    name: str


def product_name(code):
    return "FoodMart Item " + code.split("-", 1)[1]


def code_from_name(name):
    match = _NAME.match(name or "")
    return f"FOODMART-{match.group(1)}" if match else None


def ordered_codes(mapping):
    """Product order for season groups: the FoodMart code, never the server SKU (which depends on insertion order)."""
    return sorted(mapping)


def write_mapping(path, mapping):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f, lineterminator="\n")
        writer.writerow(["foodmart_code", "sku", "product_id", "name"])
        for code in sorted(mapping):
            m = mapping[code]
            writer.writerow([m.code, m.sku, m.product_id, m.name])


def read_mapping(path):
    with Path(path).open(encoding="utf-8", newline="") as f:
        return {row["foodmart_code"]: Mapped(row["foodmart_code"], row["sku"], int(row["product_id"]), row["name"])
                for row in csv.DictReader(f)}


def rewrite_csv(source, target, mapping):
    """Copy `source` to `target` replacing only the `sku` column by the mapped server SKU. Returns the data row count.

    Fails (and writes nothing) on an unexpected header, a quoted field or an unmapped code."""
    data = Path(source).read_bytes().decode("utf-8")
    lines = data.split("\n")
    if lines[0].rstrip("\r") != HEADER:
        raise MappingError(f"{source}: unexpected header (expected {HEADER})")
    out, rows = [lines[0]], 0
    for number, line in enumerate(lines[1:], start=2):
        body, cr = (line[:-1], "\r") if line.endswith("\r") else (line, "")
        if not body:
            out.append(line)
            continue
        if '"' in body:
            raise MappingError(f"{source}:{number}: quoted fields are not supported")
        fields = body.split(",")
        if len(fields) != 7:
            raise MappingError(f"{source}:{number}: expected 7 fields, found {len(fields)}")
        mapped = mapping.get(fields[SKU_COLUMN])
        if mapped is None:
            raise MappingError(f"{source}:{number}: {fields[SKU_COLUMN]} has no product in mapping.csv")
        fields[SKU_COLUMN] = mapped.sku
        out.append(",".join(fields) + cr)
        rows += 1
    target = Path(target)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes("\n".join(out).encode("utf-8"))
    return rows

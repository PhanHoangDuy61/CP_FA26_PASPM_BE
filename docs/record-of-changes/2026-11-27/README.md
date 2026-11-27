# ROC-20261127 — F09 public Store images; demo seed co-purchase baskets

2026-11-27 / Asia/Ho_Chi_Minh.

## Record 001 — F09 public Store icon and banner URLs

[Record 001](01-f09-store-images.md) adds the nullable `iconUrl` and `bannerUrl` to the public storefront Store object (stores list, home `store`, F10 cart `store`), built by the shared Store image URL builder. Additive; no migration; OpenAPI stays 118 paths / 147 operations. Branch `Khoi/fix/f09-store-images`.

## Record 002 — Demo seed co-purchase baskets

[Record 002](02-demo-seed-copurchase-baskets.md) adds 635 generated co-purchase orders (four disjoint product groups) to the demo seed, so that at `PERCENTAGE 0.12` on the full range F06 generates 17 BUNDLE, 41 CROSS_SELL and 5 FBT candidates (it generated BANNER only before). Seed data only: no Java, no migration, no API change. Measured on a fresh isolated stack; the seed and TDP-01 stay idempotent. Branch `Khoi/fix/demo-seed-copurchase`.

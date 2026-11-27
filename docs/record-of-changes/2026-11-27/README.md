# ROC-20261127 — F09 public Store images; demo seed co-purchase baskets; smoke-test documentation follow-ups; seed mining policy; F05 request list sort memory

2026-11-27 / Asia/Ho_Chi_Minh.

## Record 001 — F09 public Store icon and banner URLs

[Record 001](01-f09-store-images.md) adds the nullable `iconUrl` and `bannerUrl` to the public storefront Store object (stores list, home `store`, F10 cart `store`), built by the shared Store image URL builder. Additive; no migration; OpenAPI stays 118 paths / 147 operations. Branch `Khoi/fix/f09-store-images`.

## Record 002 — Demo seed co-purchase baskets

[Record 002](02-demo-seed-copurchase-baskets.md) adds 635 generated co-purchase orders (four disjoint product groups) to the demo seed, so that at `PERCENTAGE 0.12` on the full range F06 generates 17 BUNDLE, 41 CROSS_SELL and 5 FBT candidates (it generated BANNER only before). Seed data only: no Java, no migration, no API change. Measured on a fresh isolated stack; the seed and TDP-01 stay idempotent. Branch `Khoi/fix/demo-seed-copurchase`.

## Record 003 — Smoke-test documentation follow-ups

[Record 003](03-smoke-test-doc-followups.md): DEC-20261127-01 (the End Customer lands on `/customer-home`, superseding that part of Q-06), the removed benchmark routes documented and tested as 404, known issues KI-02 (cold-start invitation e-mail 503), KI-03 (sign-in rate limit) and KI-04 plus data item DP-10 (the demo Store's full range uses 19,932 of 20,000 matrix rows), SRS §5.4 limit numbers, black-box pack regenerated (249 cases, no ID changed). Documentation only. Branch `Khoi/docs/smoke-followups`.

## Record 004 — Demo seed sets the mining policy

[Record 004](04-demo-seed-mining-policy.md): the demo seed sets the System Admin mining policy to `PERCENTAGE 0.12` through `PUT /api/system/mining/config`, writing a new policy version only when the mode or the value differ (a second run changes nothing); no manual step remains in the test plan. Seed and test data only. Branch `Khoi/fix/seed-demo-mining-policy`.

## Record 005 — F05 request history sort memory

[Record 005](05-f05-request-list-sort-memory.md): `GET /api/mining/requests` returned 500 (MySQL 1038 "Out of sort memory") once a request's frozen input exceeded the default sort buffer; the repository now sorts ids only and loads each row by id. No contract change, no migration. New MySQL test in the CI `mysql` group. Branch `Khoi/fix/f05-history-sort-memory`.

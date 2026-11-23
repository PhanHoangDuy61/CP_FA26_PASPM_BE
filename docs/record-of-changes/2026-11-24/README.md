# ROC-20261124 — F01 Store management (backend)

2026-11-24 / Asia/Ho_Chi_Minh. Branch `Khoi/feat/store-management` from `dev` `6d2a877`. Not committed; waiting for owner approval at Checkpoint 3.

## Record 001 — Store management: profile, status, central guard, branding images

[Record 001](01-f01-store-management.md) implements [DEC-20261123-01](../../decisions/2026-11-23/DEC-20261123-01-store-management.md):
- migration V27;
- System Admin Store administration (UC34) and the own Store profile (UC35);
- the central INACTIVE guard (403 `STORE_INACTIVE`) and 409 for System Admin jobs and user assignment;
- the scheduler skip;
- Store icon/banner on the shared `MediaStorage` (avatars included), with a public image GET for ACTIVE Stores.

OpenAPI goes from 102/123 to 107/133. CI groups: backend and mysql (results in the record).

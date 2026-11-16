# ROC-20261116 — W2 follow-up fixes

2026-11-16 / Asia/Ho_Chi_Minh. UNRELEASED, COMPLETED within local follow-up scope.

- Documentation: approved docs sync committed locally at `369dba4`.
- Fixed and locally verified: clear batch push day, combined warnings, structured ended-season view, coded unsupported-version error.
- [Record 006](06-f03-f05-followup-fixes.md) continues the prior session series using the actual work date.
- No push, merge or deployment. Local fix commit authorized after coverage review; see record 006 and Git log. No push.

Verification: full opt-in **646 PASS / 0 FAIL / 5 SKIP** (651 discovered), JPA supplementary **5/5 PASS**; MySQL group **72/72**. No migration changes. Linux orphan cleanup / CI / Swagger UI / real-data conversion NOT RUN.

## Record 007 — cleanup and merge planning

[Record 007](07-cleanup-and-merge-plan.md) records the owner-approved HEAD amend and metadata cleanup, the fresh origin/dev check and [merge plan](../../release/F03_F05_MERGE_PLAN.md). This docs pass is UNRELEASED/uncommitted; external PR/FE drafts are unsent. Current source evidence remains record 006; no new runtime test.

## Record 011 — known Flyway checksum mismatches

[Record 011](11-flyway-checksum-troubleshooting.md) (first committed as 008): verified the V20 (`89726244` → `1793889682`) and V7 (`-1762009830` → `-1490036030`) checksums with Flyway 11.7.2's own calculator on Git history, added [known checksum mismatches](../../setup/LOCAL_SETUP_TROUBLESHOOTING.md#known-flyway-checksum-mismatches) with a guarded local-only recreate, and updated the setup schema range to V1–V25. Branch `Khoi/docs/flyway-checksum-troubleshooting` from `origin/dev` `3e438f8`; documentation only; no real database touched.

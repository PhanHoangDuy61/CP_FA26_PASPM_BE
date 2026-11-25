# PASPM Backend — instructions for coding agents

PASPM (FA26SE048) backend: Java 17, Spring Boot 3.5, MySQL 8.4 + Flyway, SPMF 2.67a (EFIM) in a Java 25 child process.

## Read first

- Skills:
  - `paspm-context`: project rules, vocabulary, read order;
  - `paspm-backend-workflow`: how to change and test code.
- Product docs:
  - `docs/product/GLOSSARY.md`;
  - `docs/product/SRS.md`;
  - `docs/product/ARCHITECTURE_SDD.md`;
  - `docs/product/BRD_VISION.md`.
- Feature contracts: `docs/api/`, `docs/frontend_handoff/`, `docs/features/`.
- Decisions: `docs/decisions/`. Change log: `docs/record-of-changes/`.
- Source of truth:
  - docs record decisions as of their date; they are not automatically right over code;
  - the latest PO decision (a DEC file, or an explicit PO answer in the session) wins;
  - when code and docs disagree, report the difference and ask the PO; after the decision, update the docs (and code if needed) and record a DEC.
- Reports 1–4 are partly outdated. **TBD** means ask the owner; do not guess.

## Non-negotiable rules

- `dev` changes only through a PR with green CI. Work on a new branch `<member>/<type>/<topic>`. No direct push to `dev`, no force-push. Commit, push or merge only when asked.
- Never edit Flyway V1–V26; add V27+. Never touch a teammate's local database; tests use disposable databases.
- Register every new test class in `.github/scripts/test-groups.py`. CI's backend and mysql jobs only run the classes named there.
- Never fabricate results. Anything not executed is `NOT RUN`. Never print or commit secrets.
- Frozen JSON and historical rows are immutable. New formats get a new version tag.
- Scheduler enable stays HTTP 503.
- SPMF is GPL v3 and runs **only as a separate process** (`java -jar spmf.jar run EFIM …`). Never import an SPMF class, never add SPMF to `pom.xml`, never bundle it into the backend jar (`SpmfProcessBoundaryTest`; DEC-20261125-03, `THIRD_PARTY_NOTICES.md`).
- Same-PR docs: update the API doc, OpenAPI, frontend handoff, record of changes, a DEC (for business rules), and `docs/product/*` whenever a stated fact changes.

## Commands

```bash
./mvnw -B test -Dtest=SomeTest
./mvnw -B clean verify -Dtest="$(python3 .github/scripts/test-groups.py backend)"
```

- The first runs a focused test; the second runs the CI backend group (H2).
- The MySQL group and the full suite need the CI setup; see `.github/workflows/backend-verification.yml` and `docs/setup/BACKEND_CI.md`.
- Local setup: `docs/setup/LOCAL_SETUP_WINDOWS.md`, `LOCAL_SETUP_MACOS.md`, `LOCAL_SETUP_TROUBLESHOOTING.md`.

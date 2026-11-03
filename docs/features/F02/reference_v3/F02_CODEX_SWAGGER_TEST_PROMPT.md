<!-- docs-reorganization:notice -->
> **SUPERSEDED — documentation review 2026-10-26.** This retained prompt contains the original folder/scope instructions and is not current execution policy. See [current navigation](../README.md). Original results and business-rule text below are retained; this notice is not a new execution or approval.
<!-- /docs-reorganization:notice -->

# Codex — Execute F02 Swagger UI Test Cases (PASPM)

Act as a Senior Backend QA Engineer. Your job is to **execute and record** F02 Swagger UI test cases against the actual running PASPM Spring Boot application, not merely create test plans.

## Authoritative scope and folder
Work within the repository root. Read recursively:

`docs/PASPM_F02_Inventory_Batch_Backlog_v3/`

Use, in order:
1. Existing `AGENTS.md` instructions (read-only) and relevant backend configuration.
2. `PASPM_Complete_ERD_F02_Inventory_Batch_v3.drawio`.
3. `PASPM_F02_Inventory_Batch_API_Swagger_Backlog_v3.md` and F02 issue files.
4. `F02_SWAGGER_TEST_CASES.md` (provided test case suite).
5. Actual F02 controllers/DTOs/OpenAPI schema and F01 auth configuration.

If the test-case file is missing, **create it in this F02 docs directory**, using the backlog and ERD; include category, product, inventory batch, inventory summary, roles/store isolation, migration/seed, positive/negative/boundary tests. Do not silently skip missing references or invent confirmed API contracts.

All F02-specific reports, screenshots, evidence files, plans, or updated testcase docs MUST be inside `docs/PASPM_F02_Inventory_Batch_Backlog_v3/` (subfolders under it are allowed). Do not write F02 docs elsewhere. Application source/migrations remain in their normal locations.

## Strict scope
Only Database/startup readiness checks, OpenAPI/Swagger UI API verification, and minimal F02 backend fixes needed for discovered defects. No React/Frontend/Figma changes, no Taste Skill, no Postman, no web-app E2E, no F03 Weight/Seasonality, no STOCK_MOVEMENT, no ingredient/recipe management. Do not change unrelated modules or bypass security.

Use relevant **Superpowers** skills if actually installed; never falsely claim a skill ran. Use **CodeGraph** if configured to locate F02 endpoints/dependencies, but do not block testing when unavailable.

## Execution procedure
1. Check Git working tree, Maven/JDK/runtime profiles, DB connection and Flyway migrations. Use development/test DB only. Do not reset or delete shared/prod data. Ask before destructive operations.
2. Discover the REAL Swagger URL and OpenAPI route from application config; start Spring Boot as needed. Verify OpenAPI lists every approved F02 operation and request/response/security schemas. Do not assume `/swagger-ui/index.html` if the app has customized paths.
3. Obtain valid F01 Store Admin credentials/tokens for Store A and Store B, and a role lacking F02 privileges via the existing dev/test configuration. Never create hardcoded secrets or expose them in docs/screenshots. If credentials are unavailable, mark auth-dependent cases BLOCKED; request only the specific missing input.
4. If an interactive browser/Computer Use/Playwright browser-control integration can access Swagger UI, OPEN Swagger UI, select Authorize, and run the actual **Try it out → Execute** operations. Use its response code/body to evaluate each test. Browser automation here is **only for Swagger UI endpoint testing**, not E2E of the React app.
5. Run P0 successful CRUD flows in dependency order: Category → Product → Inventory Batch → Inventory Summary. Re-read records with GET to verify persistence.
6. Construct isolated test fixtures with IDs from actual responses, with dates relative to the Store's local current date. For the inventory summary fixture, expected values are `total=33, saleable=24, expired=4, blocked=5, expiringSoon=14` for `expiringWithinDays=7`, and `expiringSoon=6` for N=0. After PATCH remaining quantity of 8 to 3, expected `total=28, saleable=19, expired=4, blocked=5, expiringSoon=9`. Use a separate non-perishable fixture with null expiry / remaining 9.
7. Run invalid-input cases: blank category name, duplicate SKU/batchCode, negative prices/quantities, `initialQuantity=0`, `remainingQuantity > initialQuantity`, missing expiry for perishable product, invalid status, negative `expiringWithinDays`, absent IDs. Check that failed writes did not mutate data.
8. Run security cases: 401 without/bad token; 403 role without permission; 404 on cross-store record IDs per approved F02 backlog; cross-store listing isolation; storeId tampering. Do not disable security.
9. Test F02-06 (P1) if implemented; otherwise classify its cases as BLOCKED/NOT IMPLEMENTED, not PASS.
10. For each test, record TC ID, request (redact secrets), actual HTTP code, actual relevant body fields, observed DB state or GET result, PASS/FAIL/BLOCKED, evidence link and test timestamp/timezone.
11. If a test FAILS, investigate and apply only narrowly scoped F02 code fixes when safe, rerun affected tests, and keep original failure/retest evidence. Avoid unrelated refactoring. Run Maven build as a supporting check.
12. Distinguish database/startup checks from actual interactive Swagger execution. **If there is no accessible Swagger UI browser**, you may check OpenAPI separately, but you MUST mark Swagger UI cases BLOCKED/NOT RUN. Do not label curl, direct HTTP scripts or OpenAPI parsing as Swagger UI test passes.

## Reports (inside the F02 docs folder only)
Create/update:
- `F02_SWAGGER_TEST_REPORT.md`: test environment (redacted), per-case outcome, actual vs expected, evidence, bugs, reruns, summary counts.
- `F02_SWAGGER_ISSUES.md` only if defects are found; describe reproduction and affected endpoint/TC IDs.
- `F02_SWAGGER_TEST_CASES.md` only if the approved API contract requires a justified correction; do not delete original coverage.

Optional evidence screenshots belong under `docs/PASPM_F02_Inventory_Batch_Backlog_v3/evidence/`. Redact access tokens, OTP, passwords, private connection strings and any personal data before writing files.

## Completion criteria
- Every planned testcase has a **truthful** result status: PASS / FAIL / BLOCKED / NOT RUN.
- Include totals split by P0/P1 and by F02 task.
- Never claim all tests passed if credentials, DB, UI or endpoint implementations were inaccessible.
- Produce a concise final report: Swagger UI URL (if available), tests executed/passed/failed/blocked, bugs found/fixed, migration/build status, changed files, and exact report path.

**Execute Swagger UI verification now. Do not stop after drafting the plan.**

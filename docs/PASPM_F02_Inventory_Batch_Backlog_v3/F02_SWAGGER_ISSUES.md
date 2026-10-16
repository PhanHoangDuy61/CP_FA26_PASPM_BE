# F02 Swagger Findings

## F02-ISSUE-01 — Foreign category writes disclosed cross-Store ownership

- **Severity:** P1 security / contract behavior.
- **Affected API:** `POST /api/products` and `PUT /api/products/{id}` when `categoryId` belongs to another Store.
- **Observed before fix:** Store A sent a real Store B `categoryId`; API returned HTTP 400 with `Category does not belong to this Store`. The response revealed that the category exists outside the caller's Store. This contradicted the backlog's 404 resource-hiding policy and F02-WRITE-04 expected result.
- **Root cause:** `ProductService.validateRequest` collapsed a category lookup scoped to the current Store into a 400 validation response.
- **Fix:** Return generic HTTP 404 `Category not found` for category IDs that cannot be found in the authenticated Store.
- **Verification:** Added `CatalogIntegrationTest.productWriteHidesCategoryOwnedByAnotherStore`. It failed first with expected 404 / actual 400, passed after the fix, and the same foreign-category request was rerun through Swagger UI after restarting the application and returned 404. No Store ID is accepted from the request body.
- **Status:** RESOLVED.

## F02-ISSUE-02 — Category create test case omitted required status

- **Affected test:** F02-CAT-01.
- **Observed:** `CategoryRequest` requires `name` and `status`; the test case originally sent only `name`, so Swagger returned 400 `Invalid request fields`.
- **Resolution:** Updated the test-case payload to send `status=ACTIVE`, consistent with the DTO/OpenAPI schema. Corrected request returned 201, and GET detail confirmed persistence.
- **Status:** RESOLVED in test documentation; no backend change needed.

## F02-ISSUE-03 — Swagger UI blocks negative lookahead before HTTP execution

- **Affected test:** F02-SUM-11.
- **Observed:** Swagger marks `expiringWithinDays=-1` invalid because the OpenAPI parameter has minimum 0. Clicking Execute did not issue an HTTP request; no server status can be reported for this case.
- **Resolution:** None. The case is accurately marked BLOCKED; no response is claimed. A backend/API-level negative-value test would need a client capable of submitting a value Swagger UI prevents.
- **Status:** OPEN TEST LIMITATION; no implementation defect established.

## F02-ISSUE-04 — Repeat startup against already-migrated persistent database not run

- **Affected test:** F02-DB-02.
- **Observed:** The executed environment used clean in-memory H2, so migration apply/validation was observed, but the same persistent database was not restarted a second time.
- **Resolution:** None. Test is marked NOT RUN; the shared or development database was not used for this check.
- **Status:** OPEN VERIFICATION LIMITATION.

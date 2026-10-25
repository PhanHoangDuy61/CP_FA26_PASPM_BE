# F04 Swagger / API Verification Report

Execution date: 2026-10-19, Asia/Ho_Chi_Minh. Environment: Spring Boot 3.5.7 on Java 24.0.2, port 18080, isolated in-memory H2 database `f04live2`; Flyway applied V1–V7 successfully. Test credentials/secrets were throwaway and are omitted.

## Results

| ID | Test | Expected | Actual | Result | Evidence |
|---|---|---|---|---|---|
| SW-F04-01 | Open `http://localhost:18080/swagger-ui/index.html` | Swagger UI loads and shows transaction routes | UI loaded; all six F04 route operations were visible | PASS | Live Swagger UI DOM, 2026-10-19 09:18–09:22 +07 |
| SW-F04-02 | `GET /api/transactions` in Swagger **Try it out** with no token and defaults | `401` | `401`, response header `WWW-Authenticate: Bearer`; request URL used `page=0&pageSize=20` | PASS | Swagger UI Server response, 2026-10-19 09:18:55 UTC / 09:18:55 +07 |
| SW-F04-03 | Inspect `POST /api/transactions/imports/csv` in Swagger | `multipart/form-data` with a binary `file` field | Swagger showed multipart form and required binary file after the OpenAPI fix | PASS | Live Swagger UI request form, 2026-10-19 09:22 +07 |
| SW-F04-04 | Upload sample CSV through Swagger | `201` and persisted import | Not executed. Edge's browser extension refused `filechooser.setFiles` because “Allow access to file URLs” is disabled; no import request was sent | BLOCKED | Browser upload error, 2026-10-19, Edge extension file URL permission |
| SW-F04-05 | Runtime OpenAPI endpoint and contract check | OpenAPI 3.1 includes all six F04 endpoints and multipart binary upload schema | `GET /v3/api-docs` returned 200; JSON parsed, six F04 routes found, multipart file schema has `type=string, format=binary` | PASS | Saved [`docs/api/openapi.json`](../../../api/openapi.json); assertion output: `OpenAPI 3.1.0 paths 44 F04 routes 6 multipart binary verified` |
| SW-F04-06 | Store Admin/CSV business flow in Swagger | Authenticated upload, per-order results and readback | Not run: isolated H2 runtime has no provisioned Store Admin account or matching Product data; the browser upload control was blocked before request submission | NOT RUN | No credentials or data were added to a shared environment |

The complete authenticated upload/readback flow and F04 endpoint-by-endpoint Swagger testing are therefore **PARTIAL**, not complete. No Postman or frontend flow testing was used.

## Automated verification

Final command `mvn -q -Dpaspm.avatar.directory=/private/tmp/paspm-f04-test-avatars verify` completed successfully. Surefire reports contain 73 tests across 12 suites, with 0 failures, 0 errors and 0 skipped. F04 coverage is 19 passing tests: CSV parser 5, order validator 5, database adapter 2, schema/migration 1 and MockMvc integration 6. The integration suite checks persisted staging rows, all-or-nothing order rejection, idempotency/content conflicts, inventory unchanged, F03 adapter values, authorization and Store isolation. Flyway applied all seven migrations on clean H2 databases.

An OpenAPI regression test first failed because SpringDoc described the upload as JSON. The controller was corrected to declare `multipart/form-data` and use `@RequestPart`; the regression test then passed. `git diff --check` also completed without output.

## Cases not verified interactively

Authenticated CSV upload, validation outcomes returned by the running instance, duplicate and conflicting order retry, batch/error reads, transaction detail persistence, Merchandiser `403`, and cross-Store `404` remain unverified in Swagger UI. These behaviors have automated MockMvc/integration evidence where covered, but that evidence is not a substitute for an interactive Swagger run.

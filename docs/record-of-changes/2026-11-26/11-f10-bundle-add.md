# ROC-20261126-011 — F10 Part 2 whole-bundle add

| Field | Value |
|---|---|
| Date / timezone | 2026-11-26 / Asia/Ho_Chi_Minh |
| Type | Backend implementation |
| Branch / base | `Khoi/feat/f10-bundle-add` / `1da5f03` (F10 Part 1) |
| Release | UNRELEASED |
| Daily index | [2026-11-26](README.md) |

## Scope and behavior

- Implemented the [F10 bundle action](../../api/F10_BUNDLE_CART_API.md) from [DEC-20261125-06](../../decisions/2026-11-25/DEC-20261125-06-f10-f11-mvp-contract.md). [F09 home](../../api/F09_STOREFRONT_API.md) issues an optional encrypted and authenticated key for each BUNDLE block on the currently selected READY Layout B. The key includes the Store, campaign, layout version and block number. Its content is opaque to clients. Instances sharing the JWT signing secret derive the same separate key for bundle tokens.
- `POST /api/storefront/{storeSlug}/cart/bundles` is available to END_CUSTOMER with `If-Match`. It adds at most one unit per component and returns the actual increment for each Product. The customer lock serializes cart versions. The Store row lock coordinates key resolution with campaign replacement and ending.
- One successful write updates the cart and records one `BUNDLE_ADDED` event in the same transaction. An all-zero action commits only a `BUNDLE_ADD_REJECTED` event and responds with 409 and component results. Event origin comes from the full block and the existing F06 live eligibility check for exact, approved candidates of the same Store and snapshot. Invalid/foreign keys and stale cart or layout versions create no event.
- The Part 1 implementation already rejects the whole single-item mutation above the 99-unit limit (DEC-20261126-02, Q-13) or above available sellable stock (DEC-20261126-03, Q-13b). New boundary coverage checks both POST and PATCH, the response code, resulting quantities, unchanged cart version and absence of rejection events.
- No migration changes. Part 2 uses V31 from Part 1. F11 remains planned. Checked 62 local `origin/*` refs, including `origin/dev`, for DEC and ROC numbers. This entry uses 011.

## Verification

Environment: Temurin Java 17.0.21.1 with `JAVA_HOME` pinned, Maven Wrapper in offline mode, disposable H2 databases.

- Before Part 2 implementation, `./mvnw -o -B -Dtest=CartIntegrationTest,StorefrontIntegrationTest test` ran 19 tests. Thirteen passed and the six new assertions failed at missing bundle keys or bundle projection. No errors or skips.
- On the Part 1 production code, `./mvnw -o -B '-Dtest=CartIntegrationTest#singleItemWritesAtSellableStockBoundaryRejectTheEntireExcess+cartCapAppliesAfterMergingQuantities' test` passed both boundary tests, with zero failures, errors or skips.
- The focused F09/F10 and OpenAPI export run passed 21 tests, with zero failures, errors or skips. It covered partial add, all-zero rejection, exact/ambiguous live recommendation matches, stale, foreign and tampered keys, invalid requests, role checks and simultaneous writes with one version. The export used `TransactionExcelImportIntegrationTest#openApiDocumentsBothMultipartImportRoutes` with `-Dpaspm.test.export-openapi=true`.
- The [OpenAPI file](../../api/openapi.json) is semantically equal to the runtime export. It grows from 115 paths / 144 operations to 116 paths / 145 operations. The new operation ID is `addStorefrontCartBundle`. No existing operation changed. All 268 internal references resolve.
- Final `./mvnw -o -B clean verify -Dtest="$(python3 .github/scripts/test-groups.py backend)" -Dpaspm.test.export-openapi=true` passed **487 tests**, with zero failures, errors or skips and exit 0. The group includes the Cart and Storefront integration suites and applies Flyway V1–V31 on H2.
- Checked 197 relative links in the changed documentation, with zero broken links. Three JSON files and three fenced JSON examples parsed. `git diff --check` passed.
- Dedicated MySQL and hosted CI runs were NOT RUN in this checkpoint. F11 is outside scope.

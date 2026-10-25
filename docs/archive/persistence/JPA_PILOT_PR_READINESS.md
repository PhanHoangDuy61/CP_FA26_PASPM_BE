<!-- docs-reorganization:notice -->
> **HISTORICAL — documentation review 2026-10-26.** This branch-specific PR/delivery checkpoint is archived; it does not define current merge status or development authorization. See [current navigation](../../architecture/README.md). Original results and business-rule text below are retained; this notice is not a new execution or approval.
<!-- /docs-reorganization:notice -->

# Product Pilot readiness — current development policy

2026-10-25. Existing Pilot12d348e is integrated on `Khoi/feat/product-jpa-integration` from dev783725c. **LOCAL_VERIFIED / READY_FOR_REVIEW; NOT READY_FOR_MERGE.** Full Java17/MySQL8.4/SPMF clean verify:302 PASS,0failures/errors/skips. Details: [Product integration report](../../architecture/PRODUCT_JPA_INTEGRATION_REPORT.md).

The owner's latest continuation task explicitly allows local Product and dependent Category development while external SPMF CI hosting/human review are unresolved. Category development may start from the verified Product branch; it must remain a stacked PR targeting Product while Product is unmerged. This supersedes earlier development-entry restrictions. Merge/deploy remain unauthorized.

Recovery1 and CI2 are already merged. CI3 remains open/unmerged; no old merge is retroactively approved. Current Product remote CI is BLOCKED_REMOTE_CI, independent human approval REVIEW_PENDING; real data REAL_DATA_BLOCKED. Prior detached299 and original277 results remain historical. Original Pilot branch/private checkout are untouched. No automatic Product/Category merge.

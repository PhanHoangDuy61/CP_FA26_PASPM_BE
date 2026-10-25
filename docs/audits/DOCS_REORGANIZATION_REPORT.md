# PASPM Documentation Reorganization Report

Date: 2026-10-26 — Asia/Ho_Chi_Minh. Baseline: `dev` commit `6601f62d49aa5bc61027f1c7ad418ac8e1e3c3ec`. Scope: documentation structure, navigation, scoped status notices and reproducible documentation validation only.

## Outcome and inventory

All 349 originally tracked files under `docs/` were inspected and inventoried: 128 Markdown, 155 JSON, 55 TXT, three Draw.io files, three PNG, two JPG, one CSV, one DOCX and one ZIP. The original root README is an additional preserved file, outside that 349-file count.

| Measure | Result |
|---|---:|
| Original docs moved with `git mv` | 125 |
| Original docs kept at their existing path | 224 |
| Original docs classified CURRENT | 37 |
| Original docs classified HISTORICAL | 277 |
| Original docs classified SUPERSEDED | 10 |
| Original docs classified NEEDS_REVIEW | 25 |
| Existing Markdown hrefs updated | 70 |
| Previously broken hrefs redirected to an explicit missing-source notice | 6 |
| Duplicate groups preserved, without deduplication | 4 |
| Original Markdown files changed only by href/notice edits | 49 |
| Scoped notices added to original Markdown | 31 |
| Unresolved documentation review groups | 8 |
| Known missing tracked source/example targets | 5 |

The six formerly broken links all pointed to the same absent F03 final specification. They now lead to the [explicit source-gap notice](../requirements/README.md#f03-v12-source-gap), not to an invented replacement. The source remains missing; navigation repair does not resolve business authority.

The [complete documentation map](../DOCUMENTATION_MAP.md) and [PATH_MAP.json](evidence/docs_reorganization/PATH_MAP.json) record all original/final paths, purpose, type, per-file classification, reasoning, authoritative references or limitations, incoming/outgoing references, original SHA-256 and relocation notes. Status is based on content, current source/contracts and independent reviews, not filename/date alone.

## Structure before and after

Before, original feature bundles, a diagrams folder and cross-feature documents were spread across the root of `docs/`:

```text
docs/
├── ERD_Diagram/
├── PASPM_F01_AUTH_SERVICE/
├── PASPM_F02_Inventory_Batch_Backlog_v3/
├── PASPM_F03_ERD_and_Tasks_v4/
├── PASPM_F04_Transaction_Data_Pipeline/
├── PASPM_F05_High_Utility_Mining/
├── api/ architecture/ audits/ frontend_handoff/
├── requirements/ setup/ superpowers/ testing/
```

After:

```text
docs/
├── README.md
├── DOCUMENTATION_MAP.md
├── requirements/README.md
├── features/
│   ├── README.md
│   ├── F01/README.md + reference/
│   ├── F02/README.md + reference_v3/
│   ├── F03/README.md + reference_v4/
│   ├── F04/README.md + reference/
│   ├── F05/README.md + reference/
│   └── F06/README.md + implementation summary
├── api/README.md + existing canonical contracts/OpenAPI
├── architecture/README.md + existing architecture/recovery documents
├── diagrams/README.md + editable ERDs
├── frontend_handoff/README.md + existing handoffs/examples
├── setup/README.md + LOCAL_DEVELOPMENT.md + existing runbooks
├── testing/README.md + reports/ + evidence/
├── audits/README.md + reports + evidence/ + tools/
├── archive/README.md + F01/ + F06/ + persistence/ + review_packages/
└── superpowers/README.md + existing plans/ + specs/
```

No empty F07–F11 folders are created. The main [portal](../README.md) links requirements, getting started, architecture, features, API, frontend integration, testing, archive and known gaps. Each F01–F06 index links canonical contracts and its original requirements/test provenance without copying contracts.

## Relocation decisions

| Original location | Final location / reason |
|---|---|
| Original F01–F05 bundles | `docs/features/F01–F05/reference*`; keep original requirements, plans, issues, ZIP/DOCX/ERD provenance together |
| `docs/ERD_Diagram/` | `docs/diagrams/`; distinguish editable business model from complete current physical schema |
| `docs/requirements/evidence/` | `docs/testing/evidence/feature_verification/`; keep captured results frozen |
| F06 implementation summary | `docs/features/F06/`; implementation navigation |
| F06/retail verification reports | `docs/testing/reports/`; links point to preserved relocated evidence |
| F06 readiness checkpoint | `docs/archive/F06/`; original approval context preserved |
| Four previous PR description packages | `docs/archive/review_packages/`; integrated or historical review stages |
| Three prior JPA readiness/delivery reports | `docs/archive/persistence/`; no stale pending state presented as current |
| Original root README | `docs/archive/F01/ROOT_README_BASELINE.md`; exact bytes preserved, new root README links current portal |

The 224 unmoved docs retain canonical API/handoff paths, architecture/recovery runbooks, existing setup/testing/audit evidence and tool-facing Superpowers paths. Moving every historical file into archive would separate evidence from its feature and weaken provenance; classification/indexes provide the distinction instead.

Reference scans found no Java, test, build script or workflow consumer of relocated documentation paths. No executable path changes were necessary. Historical text and immutable manifests may record original paths: use the old-to-new map. No misleading missing-spec compatibility stub is created. External bookmarks are not verified; the map provides recovery navigation.

## Preservation and duplicates

Actual validator results:

- All **349/349** original docs remain present.
- **300/349** retain identical SHA-256. The other **49** are Markdown with only href changes and/or a delimited notice; after removing notices and normalizing href targets, the original text matches.
- All **221/221** non-Markdown originals retain identical SHA-256, including JSON, CSV, diagrams, ZIP/DOCX and images.
- All **146/146** original evidence files retain identical SHA-256, including evidence Markdown.
- Original root README archive matches its baseline SHA-256.
- All **182/182** protected source/config/test/script/workflow files match baseline SHA-256.
- All four duplicate groups retain their original hash: F02 ERD copies, F03 backlog copies, F01 OTP/register response fixtures, F01/F02 status-request fixtures. No copy is deleted.
- All 65 exact recorded docs paths found in original JSON resolve through the path map or existing paths; no frozen JSON is rewritten.

[Authority review](evidence/docs_reorganization/AUTHORITY_REVIEW.json), [link preflight](evidence/docs_reorganization/LINK_PREFLIGHT.json), [before links](evidence/docs_reorganization/LINKS_BEFORE.json) and [href changes](evidence/docs_reorganization/LINK_CHANGES.json) preserve the reasoning and actual navigation changes. Their baseline paths identify original provenance, not live filesystem targets.

## Current versus historical and unresolved issues

The authority-review evidence records seven source conflicts. Those findings and additional provenance/acceptance gaps are organized into the following eight [DG01–DG08 review items](../requirements/README.md#documentation-review-register):

1. Missing tracked F03 v1.2 final specification, verification companion and weight report. Untracked user copies were not imported.
2. Missing tracked legacy-linear Snapshot/Matrix example JSONs. No historical responses were fabricated.
3. F01 duplicate-registration generic 202 prose versus observed 409 behavior; API/handoff carry NEEDS_REVIEW notices.
4. Mixed older F05/F06 and JPA/CI progression claims. Original executions remain unchanged; current indexes distinguish integrated source from dated reports.
5. Latest editable ERD contains v1.2/F05/F06 despite its v4 name but omits V11 archive/routing details; Flyway governs the physical schema.
6. Full authoritative F07–F11 feature map/SRS is unavailable. Research work packages do not establish feature definitions.
7. API Inventory MD/CSV/gap reports exist only on separate local branch `Khoi/docs/backend-api-inventory-audit`, commit `39a49183d87031009674b5f8c5eb13fcbdd68088`. They were not silently imported into this dev-based branch.
8. Current hosted CI, human review and real-data acceptance were not rerun/certified here. Locally configured SPMF is usable independently; public artifact hosting is not a prerequisite for this task.

Older F02 all-Store-Admin/inclusive expiry-today clauses and F03 Store-T/linear scoring are marked as superseded for new source behavior. Their frozen snapshots/results are preserved. This cleanup does not approve a business-rule change or resolve missing final authority.

## Validation commands and actual results

Run from repository root:

```sh
python3 docs/audits/tools/validate_documentation.py --output docs/audits/evidence/docs_reorganization/VALIDATION.json
git diff --check
git diff --cached --check
```

The [validation evidence](evidence/docs_reorganization/VALIDATION.json) records PASS: **153 Markdown files, 571 internal links, zero broken internal links**, 160 JSON files parsed and 236 local JSON pointers checked. All three Draw.io XML files and both ZIP/DOCX CRC checks pass. It gives full preservation counts and failure details. It checks Markdown links and heading anchors, image/diagram/evidence targets, all original paths, hash preservation, allowed-only historical Markdown edits, duplicates, JSON syntax/local pointers, Draw.io XML and ZIP/DOCX CRC. The API index includes F01–F06 contracts; the unchanged OpenAPI export contains **73 business operations**. It remains a captured export, not a freshly generated Swagger run.

External HTTP URLs: **NOT RUN**. Maven, backend/API/Swagger tests: **NOT RUN**, as no application/build/tooling behavior changes and no executable consumer of the moved paths was found. No database connection, migration or source-data operation was performed. Previous test successes remain scoped historical results.

Validation is considered successful only with zero broken actual internal navigation links and no preservation failures. Missing business sources remain explicit NEEDS_REVIEW findings, even when the link to their gap notice is valid.

## Git delivery and risk

Branch: `Khoi/docs/reorganize-documentation`, based on verified `origin/dev` baseline above. Intended PR title: `docs: reorganize PASPM documentation and improve navigation`, base `dev`. Implementation commit `f39d7a4` was pushed successfully to `origin/Khoi/docs/reorganize-documentation`. The GitHub integration returned **403 — Resource not accessible by integration** when creating a draft PR; no PR was created and no permissions were bypassed. Use this [manual PR creation link](https://github.com/PhanHoangDuy61/CP_FA26_PASPM_BE/compare/dev...Khoi%2Fdocs%2Freorganize-documentation?expand=1) with the prepared [PR description](DOCS_REORGANIZATION_PR.md). The remote dev SHA was checked and remains the baseline above. No automatic merge/deployment.

Risks: external deep links may need the path map; legacy manifests retain old paths intentionally; requirements and observed source can still conflict. Canonical API/handoff/runbook locations stay stable, changes are reversible through Git, and no binary/evidence is rewritten. The original user checkout and unrelated local changes are outside this worktree and remain preserved.

Independent final link/navigation review found no blocking issue; authority review corrected two captured F05 response files that had been mislabeled as current request templates. The map and counts above include that correction.

Next steps: review the eight documented groups, submit authoritative missing F03 materials and separate API audit through their own reviews, and update the ERD under a scoped schema-documentation task. None is silently treated as resolved here.

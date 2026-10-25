# docs: reorganize PASPM documentation and improve navigation

PASPM documentation was split across original feature bundles and shared folders, with a root README still describing F01-only scope. This change adds a central portal and F01–F06 indexes, relocates 125 of 349 original docs into predictable feature/diagram/testing/archive locations, and preserves the original root README byte-for-byte.

Canonical API contracts, handoff examples, architecture/recovery runbooks and Superpowers paths remain stable. Original requirements and reports are retained with scoped status notices; no historical execution is rewritten as current success.

- 70 existing hrefs updated. Six broken F03 authority links now lead to an explicit missing-source notice; the missing specification remains unresolved.
- 349 original files preserved; all 221 non-Markdown originals and 146 evidence files remain byte-identical. All 182 protected source/tooling files remain unchanged.
- Four duplicate groups retained; eight review groups remain visible. Untracked F03 sources and the separate unmerged API Inventory package are not imported.
- Validation: reproducible documentation checker, internal links/anchors, JSON pointers, archive CRC, before/after hashes and Git whitespace checks. See [report](DOCS_REORGANIZATION_REPORT.md) and [actual validation evidence](evidence/docs_reorganization/VALIDATION.json).
- Backend/Maven/Swagger and external HTTP checks are NOT RUN for this documentation-only task. No database was accessed; public SPMF hosting is not required.

Structure now centers on `docs/README.md`, `features/F01–F06`, `api`, `frontend_handoff`, `architecture`, `diagrams`, `requirements`, `setup`, `testing`, `audits`, `archive` and the existing `superpowers` directories. The complete old-to-new path map supports historical references and external bookmarks.

Risk: old external deep links and recorded manifest paths require the path map. Remaining F03/F01 authority conflicts, V11 diagram gaps, F07–F11 definitions and current acceptance/review evidence require separate review. No merge or deployment is included.

# F05 — HUI-Miner and EFIM Mining

## Current scope

8 mapped async request/run/pattern/benchmark operations; actual SPMF child-process adapter exists. Local runtime configuration is distinct from public hosting and from research/production acceptance.

- [Canonical API](../../api/F05_MINING_API.md) · [Frontend handoff](../../frontend_handoff/F05_HANDOFF.md).
- [Architecture](../../architecture/README.md) · [diagrams](../../diagrams/README.md) · [documentation map](../../DOCUMENTATION_MAP.md).

## Requirements and authority

[Original gap/scope](reference/F05_GAP_ANALYSIS.md), [runtime setup](../../setup/F05_SPMF_RUNTIME_SETUP.md), [current contract](../../api/F05_MINING_API.md). Keep threshold/benchmark semantics in the actual contract; no algorithm or scope change here.

## Workflow and implementation

Follow the endpoint/use-case sequence in the canonical contract and handoff; no API tables are duplicated here. Source availability is separate from tested/accepted status. [Review register](../../requirements/README.md#documentation-review-register) records unresolved authority and packaging gaps.

## Testing and evidence

[Historical verification](reference/F05_VERIFICATION.md), [captured evidence](reference/evidence), [P0 fix report](../../audits/F05_P0_DEPLOYMENT_FIX_REPORT.md). Old V7 clean-start failure is a dated result, not a fresh current failure.

## Historical references and known issues

[Original F05→F06 handoff](reference/F05_F06_HANDOFF.md) needs review against implemented F06. [F05 manifest](reference/F05_FILE_MANIFEST.json) is frozen:40 recorded old paths move; use the path map, do not rewrite it.

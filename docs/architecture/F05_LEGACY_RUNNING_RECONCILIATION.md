# F05 legacy RUNNING requests without a lease owner

Status: **REVIEW REQUIRED; no automatic repair is authorized by this document.** A request whose status is RUNNING but whose `lease_owner` is NULL predates worker lease ownership or has incomplete provenance. Its age alone does not prove that the original worker or its SPMF child is dead. The current dispatcher intentionally excludes it.

## Inventory, using a read-only connection

The following MySQL query is for an operator who is already authorized to inspect the target database. It only lists metadata and counts; it does not expose frozen input, credentials or output contents.

```sql
SELECT q.store_id, q.request_id, q.utility_snapshot_id, q.config_version,
       q.status AS request_status, q.lease_owner, q.lease_fence, q.lease_until,
       r.run_id, r.algorithm, r.attempt_no, r.status AS run_status,
       r.itemset_count, COUNT(p.pattern_id) AS persisted_patterns
FROM mining_request q
LEFT JOIN mining_run r ON r.store_id=q.store_id AND r.request_id=q.request_id
LEFT JOIN high_utility_pattern p ON p.store_id=r.store_id AND p.run_id=r.run_id
WHERE q.status='RUNNING' AND q.lease_owner IS NULL
GROUP BY q.store_id,q.request_id,q.utility_snapshot_id,q.config_version,
         q.status,q.lease_owner,q.lease_fence,q.lease_until,
         r.run_id,r.algorithm,r.attempt_no,r.status,r.itemset_count
ORDER BY q.store_id,q.request_id,r.run_id;
```

Do not apply global changes to RUNNING or PENDING requests. Normal owned leases, including other Stores' active jobs, are outside this inventory. Capture the results and an approved backup or equivalent immutable evidence, including snapshot identity, request key, input hash, algorithms, threshold, config version (including NULL) and per-run status/counts. Treat the data as Store-confidential.

## Per-request decision

1. Confirm Store ownership, snapshot existence and immutable provenance. Establish whether any original worker or child can still execute using the deployment supervisor's verified process identity and start time; PID alone is not sufficient. If this cannot be established, **REVIEW REQUIRED** and leave the row unchanged.
2. Inspect each run's status, benchmark fields and pattern/item relationships. A COMPLETED run must retain its exact output and metrics. An interrupted RUNNING/PENDING run with partial or inconsistent persisted data is **REVIEW REQUIRED**; do not delete patterns or fabricate history.
3. An authorized data owner and operator must separately approve any retry/reconciliation procedure after a tested, restorable backup. A retry must retain the original request, Store, snapshot, frozen input and configuration, preserve completed results, and use a fenced lease; the present application has **no approved automated operation** to transition these legacy rows. Do not change a lease owner or status manually merely to make polling pick the row up.
4. Record the evidence, decision, approver, execution time, exact actions and subsequent request/run/pattern counts in the organization's operational record. If source data or process identity cannot be verified, leave the item pending human investigation.

No real database was read or changed to create this procedure. SQL and any future repair must be validated on a disposable copy before production use.

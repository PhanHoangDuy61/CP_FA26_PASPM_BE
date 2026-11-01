-- Existing RUNNING requests retain NULL owner and are not automatically declared stale.
ALTER TABLE mining_request ADD lease_owner VARCHAR(36) NULL;
ALTER TABLE mining_request ADD lease_fence BIGINT NOT NULL DEFAULT 0;
ALTER TABLE mining_request ADD lease_until TIMESTAMP(6) NULL;
CREATE INDEX ix_mining_dispatch ON mining_request(status,lease_until,request_id);

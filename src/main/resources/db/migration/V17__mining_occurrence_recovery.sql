-- Existing occurrences remain unchanged; unowned PROCESSING rows require review.
ALTER TABLE mining_schedule_occurrence ADD lease_owner VARCHAR(36) NULL;
ALTER TABLE mining_schedule_occurrence ADD lease_fence BIGINT NOT NULL DEFAULT 0;
ALTER TABLE mining_schedule_occurrence ADD lease_until TIMESTAMP(6) NULL;
ALTER TABLE mining_schedule_occurrence ADD attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE mining_schedule_occurrence ADD next_retry_at TIMESTAMP(6) NULL;
CREATE INDEX ix_occurrence_recovery ON mining_schedule_occurrence(status,next_retry_at,lease_until,occurrence_id);

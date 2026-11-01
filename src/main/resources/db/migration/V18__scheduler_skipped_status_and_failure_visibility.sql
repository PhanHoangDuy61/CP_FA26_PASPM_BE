-- D6: a missed fire time that a later fire time supersedes is recorded as SKIPPED. It never owns a snapshot, request or run.
-- D7: the final actionable error of an occurrence is stored for authorized administrators.
ALTER TABLE mining_schedule_occurrence DROP CONSTRAINT ck_occurrence_status;
ALTER TABLE mining_schedule_occurrence ADD CONSTRAINT ck_occurrence_status CHECK(status IN ('PENDING','PROCESSING','DISPATCHED','COMPLETED','FAILED','SKIPPED'));
ALTER TABLE mining_schedule_occurrence ADD skip_reason VARCHAR(60) NULL;
ALTER TABLE mining_schedule_occurrence ADD failure_message VARCHAR(500) NULL;
-- Existing rows have no skip reason and none is SKIPPED, so the constraint holds for them.
ALTER TABLE mining_schedule_occurrence ADD CONSTRAINT ck_occurrence_skip_reason CHECK((status='SKIPPED' AND skip_reason IS NOT NULL) OR (status<>'SKIPPED' AND skip_reason IS NULL));

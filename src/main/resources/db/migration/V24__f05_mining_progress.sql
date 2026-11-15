-- Additive progress metadata. Historical status and frozen input are untouched.
ALTER TABLE mining_request ADD COLUMN stage VARCHAR(16) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE mining_request ADD COLUMN stage_started_at TIMESTAMP NULL;
ALTER TABLE mining_request ADD COLUMN current_run_id BIGINT NULL;
ALTER TABLE mining_request ADD CONSTRAINT ck_mining_request_stage CHECK(stage IN ('LEGACY','QUEUED','RUNNING','SAVING','COMPARING','COMPLETED','FAILED'));
ALTER TABLE mining_run ADD CONSTRAINT uq_mining_run_request_store_id UNIQUE(store_id,request_id,run_id);
ALTER TABLE mining_request ADD CONSTRAINT fk_mining_progress_run FOREIGN KEY(store_id,request_id,current_run_id) REFERENCES mining_run(store_id,request_id,run_id);

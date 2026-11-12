ALTER TABLE import_batch ADD idempotency_key VARCHAR(128) NULL;
ALTER TABLE import_batch ADD content_sha256 CHAR(64) NULL;
ALTER TABLE import_batch ADD created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE import_batch ADD updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE import_batch ADD processing_started_at TIMESTAMP NULL;
ALTER TABLE import_batch ADD input_total_count INT NULL;
ALTER TABLE import_batch ADD stage VARCHAR(30) NOT NULL DEFAULT 'LEGACY';
ALTER TABLE import_batch ADD lease_owner VARCHAR(36) NULL;
ALTER TABLE import_batch ADD lease_fence BIGINT NOT NULL DEFAULT 0;
ALTER TABLE import_batch ADD lease_until TIMESTAMP(6) NULL;

UPDATE import_batch SET created_at=started_at,updated_at=COALESCE(finished_at,started_at),
    processing_started_at=started_at,input_total_count=total_count;

ALTER TABLE import_batch DROP CONSTRAINT ck_import_batch_status;
ALTER TABLE import_batch ADD CONSTRAINT ck_import_batch_status
    CHECK (status IN ('RECEIVING','QUEUED','PROCESSING','COMPLETED','COMPLETED_WITH_ERRORS','FAILED'));

ALTER TABLE import_batch ADD CONSTRAINT uq_import_batch_store_idempotency UNIQUE(store_id,idempotency_key);

CREATE INDEX ix_import_batch_async_dispatch ON import_batch(status,lease_until,import_batch_id);

ALTER TABLE import_staging_row ADD result_group INT NULL;
ALTER TABLE import_staging_row ADD result_kind VARCHAR(20) NULL;

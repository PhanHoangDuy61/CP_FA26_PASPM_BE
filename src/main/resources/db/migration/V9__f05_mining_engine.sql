-- Reuse the four mining entities in the approved ERD. Frozen F03 rows are never updated.
ALTER TABLE utility_snapshot ADD CONSTRAINT uq_utility_snapshot_store_id UNIQUE(store_id,snapshot_id);
CREATE TABLE mining_request (
    request_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id INT NOT NULL,
    requested_by INT NOT NULL,
    utility_snapshot_id INT NOT NULL,
    request_key VARCHAR(36) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    algorithms VARCHAR(30) NOT NULL,
    min_util_mode VARCHAR(12) NOT NULL,
    min_util_value DECIMAL(30,6) NOT NULL,
    min_util_spmf INT NOT NULL,
    input_snapshot_hash VARCHAR(64) NOT NULL,
    frozen_input JSON NOT NULL,
    utility_config_snapshot JSON NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    comparison_status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_mining_request_store_key UNIQUE(store_id,request_key),
    CONSTRAINT uq_mining_request_store_id UNIQUE(store_id,request_id),
    CONSTRAINT fk_mining_request_snapshot FOREIGN KEY(store_id,utility_snapshot_id) REFERENCES utility_snapshot(store_id,snapshot_id),
    CONSTRAINT fk_mining_request_actor FOREIGN KEY(requested_by) REFERENCES app_user(user_id),
    CONSTRAINT ck_mining_request_status CHECK(status IN ('PENDING','RUNNING','COMPLETED','FAILED')),
    CONSTRAINT ck_mining_request_comparison CHECK(comparison_status IN ('NOT_REQUESTED','PENDING','MATCH','FAILED','BLOCKED')),
    CONSTRAINT ck_mining_request_mode CHECK(min_util_mode IN ('PERCENTAGE','ABSOLUTE')),
    CONSTRAINT ck_mining_request_threshold CHECK(min_util_value > 0 AND min_util_spmf > 0)
);
CREATE INDEX ix_mining_request_store_created ON mining_request(store_id,created_at,request_id);
CREATE TABLE mining_run (
    run_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id INT NOT NULL,
    request_id BIGINT NOT NULL,
    algorithm VARCHAR(12) NOT NULL,
    attempt_no INT NOT NULL DEFAULT 1,
    library_version VARCHAR(30) NOT NULL,
    conversion_version VARCHAR(60) NOT NULL,
    memory_method VARCHAR(80) NOT NULL,
    runtime_method VARCHAR(150) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    runtime_ms BIGINT NULL,
    memory_mib DOUBLE NULL,
    environment VARCHAR(600) NULL,
    itemset_count INT NULL,
    failure_code VARCHAR(32) NULL,
    failure_message VARCHAR(250) NULL,
    CONSTRAINT uq_mining_run_attempt UNIQUE(request_id,algorithm,attempt_no),
    CONSTRAINT uq_mining_run_store_id UNIQUE(store_id,run_id),
    CONSTRAINT fk_mining_run_request FOREIGN KEY(store_id,request_id) REFERENCES mining_request(store_id,request_id),
    CONSTRAINT ck_mining_run_algorithm CHECK(algorithm IN ('HUI_MINER','EFIM')),
    CONSTRAINT ck_mining_run_status CHECK(status IN ('PENDING','RUNNING','COMPLETED','FAILED','TIMED_OUT')),
    CONSTRAINT ck_mining_run_attempt CHECK(attempt_no > 0),
    CONSTRAINT ck_mining_run_metrics CHECK((runtime_ms IS NULL OR runtime_ms >= 0) AND (memory_mib IS NULL OR memory_mib >= 0) AND (itemset_count IS NULL OR itemset_count >= 0))
);
CREATE INDEX ix_mining_run_request ON mining_run(store_id,request_id,run_id);
CREATE TABLE high_utility_pattern (
    pattern_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id INT NOT NULL,
    run_id BIGINT NOT NULL,
    canonical_key VARCHAR(2816) NOT NULL,
    canonical_hash VARCHAR(64) NOT NULL,
    utility DECIMAL(30,6) NOT NULL,
    utility_spmf INT NOT NULL,
    pattern_size INT NOT NULL,
    support_count INT NULL,
    CONSTRAINT uq_high_utility_pattern_key UNIQUE(run_id,canonical_hash),
    CONSTRAINT uq_high_utility_pattern_store_id UNIQUE(store_id,pattern_id),
    CONSTRAINT fk_pattern_run FOREIGN KEY(store_id,run_id) REFERENCES mining_run(store_id,run_id),
    CONSTRAINT ck_pattern_values CHECK(utility >= 0 AND utility_spmf >= 0 AND pattern_size > 0 AND (support_count IS NULL OR support_count >= 0))
);
CREATE INDEX ix_pattern_run_utility ON high_utility_pattern(store_id,run_id,utility,pattern_id);
CREATE TABLE pattern_item (
    pattern_id BIGINT NOT NULL,
    store_id INT NOT NULL,
    product_id INT NOT NULL,
    algorithm_item_id INT NOT NULL,
    PRIMARY KEY(pattern_id,product_id),
    CONSTRAINT uq_pattern_algorithm_item UNIQUE(pattern_id,algorithm_item_id),
    CONSTRAINT fk_pattern_item_pattern FOREIGN KEY(store_id,pattern_id) REFERENCES high_utility_pattern(store_id,pattern_id),
    CONSTRAINT fk_pattern_item_product FOREIGN KEY(store_id,product_id) REFERENCES product(store_id,product_id),
    CONSTRAINT ck_pattern_algorithm_item CHECK(algorithm_item_id > 0)
);

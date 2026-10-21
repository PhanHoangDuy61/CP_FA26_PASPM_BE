-- F06 owns reviewable business candidates, never executable offers/campaigns.
CREATE TABLE recommendation_rule_config (
    store_id INT PRIMARY KEY,
    config_version BIGINT NOT NULL,
    config_json JSON NOT NULL,
    updated_by INT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rec_config_store FOREIGN KEY(store_id) REFERENCES store(store_id),
    CONSTRAINT fk_rec_config_actor FOREIGN KEY(updated_by) REFERENCES app_user(user_id),
    CONSTRAINT ck_rec_config_version CHECK(config_version > 0)
);
CREATE TABLE recommendation (
    recommendation_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id INT NOT NULL,
    utility_snapshot_id INT NOT NULL,
    identity_hash CHAR(64) NOT NULL,
    canonical_key VARCHAR(2816) NOT NULL,
    type VARCHAR(16) NOT NULL,
    anchor_product_id INT NULL,
    utility DECIMAL(30,6) NOT NULL,
    support_count INT NULL,
    rule_version VARCHAR(80) NOT NULL,
    policy_version BIGINT NOT NULL,
    policy_snapshot JSON NOT NULL,
    evidence JSON NOT NULL,
    generation_eligibility JSON NOT NULL,
    explanation JSON NOT NULL,
    review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW',
    version BIGINT NOT NULL DEFAULT 1,
    created_by INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by INT NULL,
    reviewed_at TIMESTAMP NULL,
    review_reason VARCHAR(1000) NULL,
    CONSTRAINT uq_recommendation_identity UNIQUE(store_id,identity_hash),
    CONSTRAINT uq_recommendation_store_id UNIQUE(store_id,recommendation_id),
    CONSTRAINT fk_rec_snapshot FOREIGN KEY(store_id,utility_snapshot_id) REFERENCES utility_snapshot(store_id,snapshot_id),
    CONSTRAINT fk_rec_anchor FOREIGN KEY(store_id,anchor_product_id) REFERENCES product(store_id,product_id),
    CONSTRAINT fk_rec_creator FOREIGN KEY(created_by) REFERENCES app_user(user_id),
    CONSTRAINT fk_rec_reviewer FOREIGN KEY(reviewed_by) REFERENCES app_user(user_id),
    CONSTRAINT ck_rec_type CHECK(type IN ('BUNDLE','CROSS_SELL','FBT','BANNER')),
    CONSTRAINT ck_rec_anchor_type CHECK((type='CROSS_SELL' AND anchor_product_id IS NOT NULL) OR (type<>'CROSS_SELL' AND anchor_product_id IS NULL)),
    CONSTRAINT ck_rec_utility CHECK(utility>=0 AND (support_count IS NULL OR support_count>=0)),
    CONSTRAINT ck_rec_version CHECK(version>0 AND policy_version>0),
    CONSTRAINT ck_rec_review CHECK(review_status IN ('PENDING_REVIEW','APPROVED','REJECTED')),
    CONSTRAINT ck_rec_review_fields CHECK((review_status='PENDING_REVIEW' AND reviewed_by IS NULL AND reviewed_at IS NULL AND review_reason IS NULL) OR (review_status<>'PENDING_REVIEW' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND review_reason IS NOT NULL))
);
CREATE INDEX ix_rec_store_snapshot ON recommendation(store_id,utility_snapshot_id,recommendation_id);
CREATE INDEX ix_rec_store_review_type ON recommendation(store_id,review_status,type,recommendation_id);
CREATE TABLE recommendation_item (
    recommendation_id BIGINT NOT NULL,
    store_id INT NOT NULL,
    product_id INT NOT NULL,
    PRIMARY KEY(recommendation_id,product_id),
    CONSTRAINT fk_rec_item_rec FOREIGN KEY(store_id,recommendation_id) REFERENCES recommendation(store_id,recommendation_id),
    CONSTRAINT fk_rec_item_product FOREIGN KEY(store_id,product_id) REFERENCES product(store_id,product_id)
);
CREATE TABLE recommendation_source (
    recommendation_id BIGINT NOT NULL,
    store_id INT NOT NULL,
    run_id BIGINT NOT NULL,
    pattern_id BIGINT NOT NULL,
    provenance JSON NOT NULL,
    PRIMARY KEY(recommendation_id,run_id,pattern_id),
    CONSTRAINT fk_rec_source_rec FOREIGN KEY(store_id,recommendation_id) REFERENCES recommendation(store_id,recommendation_id),
    CONSTRAINT fk_rec_source_run FOREIGN KEY(store_id,run_id) REFERENCES mining_run(store_id,run_id),
    CONSTRAINT fk_rec_source_pattern FOREIGN KEY(store_id,pattern_id) REFERENCES high_utility_pattern(store_id,pattern_id)
);
CREATE INDEX ix_rec_source_run ON recommendation_source(store_id,run_id,recommendation_id);
CREATE TABLE recommendation_review_history (
    history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recommendation_id BIGINT NOT NULL,
    store_id INT NOT NULL,
    old_status VARCHAR(20) NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    reviewed_by INT NOT NULL,
    reviewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(1000) NOT NULL,
    resulting_version BIGINT NOT NULL,
    eligibility_check JSON NOT NULL,
    CONSTRAINT uq_rec_history_version UNIQUE(recommendation_id,resulting_version),
    CONSTRAINT fk_rec_history_rec FOREIGN KEY(store_id,recommendation_id) REFERENCES recommendation(store_id,recommendation_id),
    CONSTRAINT fk_rec_history_reviewer FOREIGN KEY(reviewed_by) REFERENCES app_user(user_id),
    CONSTRAINT ck_rec_history_transition CHECK(old_status='PENDING_REVIEW' AND new_status IN ('APPROVED','REJECTED')),
    CONSTRAINT ck_rec_history_version CHECK(resulting_version>1)
);

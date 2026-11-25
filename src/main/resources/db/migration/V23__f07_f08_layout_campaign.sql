-- F07 Layout Simulator and F08 Campaign/A-B workflow.
-- V23 has not been published. The application serializes active-campaign changes
-- by locking the owning store row inside the same transaction.
ALTER TABLE recommendation ADD CONSTRAINT uq_rec_store_snapshot_id
    UNIQUE(store_id, utility_snapshot_id, recommendation_id);

CREATE TABLE campaign (
    campaign_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id INT NOT NULL,
    utility_snapshot_id INT NOT NULL,
    name VARCHAR(150) NOT NULL,
    layout_a_id BIGINT NULL,
    layout_b_id BIGINT NULL,
    selected_layout_id BIGINT NULL,
    decision_report_id BIGINT NULL,
    decision VARCHAR(12) NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 1,
    created_by INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_by INT NULL,
    published_at TIMESTAMP NULL,
    activated_by INT NULL,
    activated_at TIMESTAMP NULL,
    ended_by INT NULL,
    ended_at TIMESTAMP NULL,
    CONSTRAINT uq_campaign_store_id UNIQUE(store_id, campaign_id),
    CONSTRAINT uq_campaign_store_snapshot UNIQUE(store_id, campaign_id, utility_snapshot_id),
    CONSTRAINT fk_campaign_store FOREIGN KEY(store_id) REFERENCES store(store_id),
    CONSTRAINT fk_campaign_snapshot FOREIGN KEY(store_id, utility_snapshot_id)
        REFERENCES utility_snapshot(store_id, snapshot_id),
    CONSTRAINT fk_campaign_creator FOREIGN KEY(created_by) REFERENCES app_user(user_id),
    CONSTRAINT fk_campaign_publisher FOREIGN KEY(published_by) REFERENCES app_user(user_id),
    CONSTRAINT fk_campaign_activator FOREIGN KEY(activated_by) REFERENCES app_user(user_id),
    CONSTRAINT fk_campaign_ender FOREIGN KEY(ended_by) REFERENCES app_user(user_id),
    CONSTRAINT ck_campaign_status CHECK(status IN ('DRAFT','PUBLISHED','ACTIVE','ENDED')),
    CONSTRAINT ck_campaign_decision CHECK(decision IS NULL OR decision IN ('KEEP_A','CHOOSE_B')),
    CONSTRAINT ck_campaign_version CHECK(version > 0),
    CONSTRAINT ck_campaign_layout_pair CHECK(layout_a_id IS NULL OR layout_b_id IS NULL OR layout_a_id <> layout_b_id),
    CONSTRAINT ck_campaign_selected_matches_decision CHECK(
        (decision IS NULL AND selected_layout_id IS NULL) OR
        (decision='KEEP_A' AND selected_layout_id=layout_a_id) OR
        (decision='CHOOSE_B' AND selected_layout_id=layout_b_id)),
    CONSTRAINT ck_campaign_decision_report CHECK(decision IS NOT NULL OR decision_report_id IS NULL)
);

CREATE TABLE storefront_layout (
    layout_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id INT NOT NULL,
    campaign_id BIGINT NOT NULL,
    utility_snapshot_id INT NOT NULL,
    role VARCHAR(1) NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 1,
    name VARCHAR(150) NOT NULL,
    baseline_params JSON NULL,
    created_by INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_layout_store_id UNIQUE(store_id, layout_id),
    CONSTRAINT uq_layout_campaign_role UNIQUE(store_id, campaign_id, role),
    CONSTRAINT uq_layout_campaign_snapshot_role UNIQUE(store_id, campaign_id, utility_snapshot_id, role),
    CONSTRAINT uq_layout_store_snapshot UNIQUE(store_id, layout_id, utility_snapshot_id),
    CONSTRAINT uq_layout_store_campaign_id UNIQUE(store_id, campaign_id, layout_id),
    CONSTRAINT uq_layout_campaign_snapshot_id UNIQUE(store_id, campaign_id, utility_snapshot_id, layout_id),
    CONSTRAINT fk_layout_store FOREIGN KEY(store_id) REFERENCES store(store_id),
    CONSTRAINT fk_layout_campaign_snapshot FOREIGN KEY(store_id, campaign_id, utility_snapshot_id)
        REFERENCES campaign(store_id, campaign_id, utility_snapshot_id),
    CONSTRAINT fk_layout_snapshot FOREIGN KEY(store_id, utility_snapshot_id)
        REFERENCES utility_snapshot(store_id, snapshot_id),
    CONSTRAINT fk_layout_creator FOREIGN KEY(created_by) REFERENCES app_user(user_id),
    CONSTRAINT ck_layout_role CHECK(role IN ('A','B')),
    CONSTRAINT ck_layout_status CHECK(status IN ('DRAFT','READY','ARCHIVED')),
    CONSTRAINT ck_layout_version CHECK(version > 0)
);

ALTER TABLE campaign
    ADD CONSTRAINT fk_campaign_layout_a FOREIGN KEY(store_id, campaign_id, utility_snapshot_id, layout_a_id)
        REFERENCES storefront_layout(store_id, campaign_id, utility_snapshot_id, layout_id);
ALTER TABLE campaign
    ADD CONSTRAINT fk_campaign_layout_b FOREIGN KEY(store_id, campaign_id, utility_snapshot_id, layout_b_id)
        REFERENCES storefront_layout(store_id, campaign_id, utility_snapshot_id, layout_id);
ALTER TABLE campaign
    ADD CONSTRAINT fk_campaign_selected_layout FOREIGN KEY(store_id, campaign_id, utility_snapshot_id, selected_layout_id)
        REFERENCES storefront_layout(store_id, campaign_id, utility_snapshot_id, layout_id);

CREATE TABLE storefront_layout_revision (
    store_id INT NOT NULL,
    layout_id BIGINT NOT NULL,
    version BIGINT NOT NULL,
    placements_json JSON NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    created_by INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(store_id, layout_id, version),
    CONSTRAINT fk_layout_revision_layout FOREIGN KEY(store_id, layout_id)
        REFERENCES storefront_layout(store_id, layout_id),
    CONSTRAINT fk_layout_revision_actor FOREIGN KEY(created_by) REFERENCES app_user(user_id),
    CONSTRAINT ck_layout_revision_version CHECK(version > 0)
);

CREATE TABLE layout_placement (
    placement_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id INT NOT NULL,
    layout_id BIGINT NOT NULL,
    utility_snapshot_id INT NOT NULL,
    recommendation_id BIGINT NULL,
    product_id INT NOT NULL,
    zone VARCHAR(20) NOT NULL,
    block_no INT NOT NULL,
    position INT NOT NULL,
    validated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_layout_placement_product UNIQUE(store_id, layout_id, product_id),
    CONSTRAINT uq_layout_placement_position UNIQUE(store_id, layout_id, zone, position),
    CONSTRAINT fk_placement_layout FOREIGN KEY(store_id, layout_id)
        REFERENCES storefront_layout(store_id, layout_id),
    CONSTRAINT fk_placement_layout_snapshot FOREIGN KEY(store_id, layout_id, utility_snapshot_id)
        REFERENCES storefront_layout(store_id, layout_id, utility_snapshot_id),
    CONSTRAINT fk_placement_recommendation FOREIGN KEY(store_id, utility_snapshot_id, recommendation_id)
        REFERENCES recommendation(store_id, utility_snapshot_id, recommendation_id),
    CONSTRAINT fk_placement_product FOREIGN KEY(store_id, product_id)
        REFERENCES product(store_id, product_id),
    CONSTRAINT ck_placement_zone CHECK(zone IN ('BASELINE','BANNER','BUNDLE','CROSS_SELL','FBT')),
    CONSTRAINT ck_placement_order CHECK(block_no > 0 AND position > 0)
);

CREATE INDEX ix_campaign_store_status ON campaign(store_id, status, campaign_id);
CREATE INDEX ix_layout_store_updated ON storefront_layout(store_id, updated_at, layout_id);
CREATE INDEX ix_placement_layout_block ON layout_placement(store_id, layout_id, zone, block_no, position);

CREATE TABLE comparison_report (
    report_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id INT NOT NULL,
    campaign_id BIGINT NOT NULL,
    test_range_start DATE NOT NULL,
    test_range_end DATE NOT NULL,
    dataset_hash CHAR(64) NOT NULL,
    source_namespace VARCHAR(40) NOT NULL,
    evaluation_count INT NOT NULL,
    algorithm_version VARCHAR(40) NOT NULL,
    assumptions JSON NOT NULL,
    simulated BOOLEAN NOT NULL DEFAULT TRUE,
    created_by INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_report_store_id UNIQUE(store_id, report_id),
    CONSTRAINT uq_report_store_campaign_id UNIQUE(store_id, campaign_id, report_id),
    CONSTRAINT fk_report_campaign FOREIGN KEY(store_id, campaign_id)
        REFERENCES campaign(store_id, campaign_id),
    CONSTRAINT fk_report_creator FOREIGN KEY(created_by) REFERENCES app_user(user_id),
    CONSTRAINT ck_report_range CHECK(test_range_end >= test_range_start),
    CONSTRAINT ck_report_evaluation_count CHECK(evaluation_count >= 0),
    CONSTRAINT ck_report_simulated CHECK(simulated=TRUE)
);

ALTER TABLE campaign
    ADD CONSTRAINT fk_campaign_decision_report FOREIGN KEY(store_id, campaign_id, decision_report_id)
        REFERENCES comparison_report(store_id, campaign_id, report_id);

CREATE TABLE comparison_report_layout (
    report_id BIGINT NOT NULL,
    store_id INT NOT NULL,
    layout_id BIGINT NOT NULL,
    layout_version BIGINT NOT NULL,
    role VARCHAR(1) NOT NULL,
    hit_count INT NOT NULL,
    hit_denominator INT NOT NULL,
    bundle_conversion_count INT NOT NULL,
    bundle_conversion_denominator INT NOT NULL,
    captured_utility DECIMAL(30,6) NULL,
    captured_utility_denominator DECIMAL(30,6) NULL,
    priority_coverage DECIMAL(9,6) NULL,
    priority_coverage_denominator INT NULL,
    PRIMARY KEY(report_id, layout_id),
    CONSTRAINT uq_report_layout_role UNIQUE(store_id, report_id, role),
    CONSTRAINT fk_report_layout_report FOREIGN KEY(store_id, report_id)
        REFERENCES comparison_report(store_id, report_id),
    CONSTRAINT fk_report_layout_revision FOREIGN KEY(store_id, layout_id, layout_version)
        REFERENCES storefront_layout_revision(store_id, layout_id, version),
    CONSTRAINT ck_report_layout_role CHECK(role IN ('A','B')),
    CONSTRAINT ck_report_metrics CHECK(hit_count >= 0 AND hit_denominator >= 0 AND
        bundle_conversion_count >= 0 AND bundle_conversion_denominator >= 0 AND
        hit_count <= hit_denominator AND bundle_conversion_count <= bundle_conversion_denominator),
    CONSTRAINT ck_report_utility CHECK(captured_utility >= 0 AND captured_utility_denominator >= captured_utility)
);

CREATE TABLE comparison_report_transaction (
    store_id INT NOT NULL,
    report_id BIGINT NOT NULL,
    transaction_id INT NOT NULL,
    PRIMARY KEY(report_id, transaction_id),
    CONSTRAINT fk_report_transaction_report FOREIGN KEY(store_id, report_id)
        REFERENCES comparison_report(store_id, report_id),
    CONSTRAINT fk_report_transaction_transaction FOREIGN KEY(store_id, transaction_id)
        REFERENCES sales_transaction(store_id, transaction_id)
);

CREATE TABLE campaign_history (
    history_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id INT NOT NULL,
    campaign_id BIGINT NOT NULL,
    old_status VARCHAR(12) NULL,
    new_status VARCHAR(12) NOT NULL,
    actor_id INT NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    validation_result JSON NULL,
    previous_active_campaign_id BIGINT NULL,
    CONSTRAINT fk_campaign_history_campaign FOREIGN KEY(store_id, campaign_id)
        REFERENCES campaign(store_id, campaign_id),
    CONSTRAINT fk_campaign_history_actor FOREIGN KEY(actor_id) REFERENCES app_user(user_id),
    CONSTRAINT fk_campaign_history_previous FOREIGN KEY(store_id, previous_active_campaign_id)
        REFERENCES campaign(store_id, campaign_id),
    CONSTRAINT ck_campaign_history_status CHECK(new_status IN ('DRAFT','PUBLISHED','ACTIVE','ENDED'))
);

CREATE INDEX ix_campaign_history_lookup ON campaign_history(store_id, campaign_id, occurred_at);

CREATE TABLE utility_snapshot (
    snapshot_id INT AUTO_INCREMENT PRIMARY KEY,
    store_id INT NOT NULL,
    created_by INT NOT NULL,
    range_start DATE NOT NULL,
    range_end DATE NOT NULL,
    as_of_date DATE NOT NULL,
    config_snapshot JSON NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    matrix_hash VARCHAR(64) NOT NULL,
    total_weighted_utility DECIMAL(30,6) NOT NULL,
    rounding_scale INT NOT NULL,
    status VARCHAR(10) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_utility_snapshot_store FOREIGN KEY (store_id) REFERENCES store(store_id),
    CONSTRAINT fk_utility_snapshot_creator FOREIGN KEY (created_by) REFERENCES app_user(user_id),
    CONSTRAINT ck_utility_snapshot_range CHECK (range_end >= range_start),
    CONSTRAINT ck_utility_snapshot_status CHECK (status IN ('READY','FAILED')),
    CONSTRAINT ck_utility_snapshot_total CHECK (total_weighted_utility >= 0),
    CONSTRAINT ck_utility_snapshot_scale CHECK (rounding_scale = 6)
);
CREATE INDEX ix_utility_snapshot_store_created ON utility_snapshot(store_id,created_at,snapshot_id);

CREATE TABLE utility_matrix_item (
    matrix_item_id INT AUTO_INCREMENT PRIMARY KEY,
    snapshot_id INT NOT NULL,
    transaction_id INT NOT NULL,
    product_id INT NOT NULL,
    quantity INT NOT NULL,
    base_utility DECIMAL(30,6) NOT NULL,
    stock_score DECIMAL(24,18) NULL,
    expiry_score DECIMAL(24,18) NULL,
    seasonal_score DECIMAL(24,18) NULL,
    expiry_status VARCHAR(30) NOT NULL,
    weight_value DECIMAL(38,18) NOT NULL,
    weighted_utility DECIMAL(30,6) NOT NULL,
    CONSTRAINT uq_utility_matrix_item UNIQUE(snapshot_id,transaction_id,product_id),
    CONSTRAINT fk_utility_matrix_snapshot FOREIGN KEY(snapshot_id) REFERENCES utility_snapshot(snapshot_id),
    CONSTRAINT fk_utility_matrix_product FOREIGN KEY(product_id) REFERENCES product(product_id),
    CONSTRAINT ck_utility_matrix_quantity CHECK(quantity > 0),
    CONSTRAINT ck_utility_matrix_transaction CHECK(transaction_id > 0),
    CONSTRAINT ck_utility_matrix_base CHECK(base_utility >= 0),
    CONSTRAINT ck_utility_matrix_weight CHECK(weight_value >= 1),
    CONSTRAINT ck_utility_matrix_weighted CHECK(weighted_utility >= 0),
    CONSTRAINT ck_utility_matrix_stock CHECK(stock_score BETWEEN 0 AND 1),
    CONSTRAINT ck_utility_matrix_expiry CHECK(expiry_score BETWEEN 0 AND 1),
    CONSTRAINT ck_utility_matrix_season CHECK(seasonal_score BETWEEN 0 AND 1),
    CONSTRAINT ck_utility_matrix_expiry_status CHECK(expiry_status IN ('AVAILABLE','NOT_APPLICABLE','NO_SELLABLE_BATCH','MISSING_METADATA'))
);
-- F04 owns SALES_TRANSACTION, which does not exist yet. Its migration must add the transaction_id FK.
-- F05 owns MINING_REQUEST, which does not exist yet. Its migration must add utility_snapshot_id FK.

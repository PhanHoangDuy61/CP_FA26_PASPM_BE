-- API_CONNECTION is metadata-only in F04 P0. External API synchronization is P1.
CREATE TABLE api_connection (
    connection_id INT AUTO_INCREMENT PRIMARY KEY,
    store_id INT NOT NULL,
    name VARCHAR(150) NOT NULL,
    base_url VARCHAR(2048) NOT NULL,
    secret_reference VARCHAR(255) NULL,
    status VARCHAR(30) NOT NULL,
    last_synced_at TIMESTAMP NULL,
    CONSTRAINT fk_api_connection_store FOREIGN KEY (store_id) REFERENCES store(store_id)
);

CREATE INDEX ix_api_connection_store_status ON api_connection(store_id, status);

CREATE TABLE import_batch (
    import_batch_id INT AUTO_INCREMENT PRIMARY KEY,
    store_id INT NOT NULL,
    connection_id INT NULL,
    method VARCHAR(20) NOT NULL,
    filename VARCHAR(255) NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP NULL,
    total_count INT NOT NULL DEFAULT 0,
    valid_count INT NOT NULL DEFAULT 0,
    rejected_count INT NOT NULL DEFAULT 0,
    total_order_count INT NOT NULL DEFAULT 0,
    accepted_order_count INT NOT NULL DEFAULT 0,
    rejected_order_count INT NOT NULL DEFAULT 0,
    duplicate_order_count INT NOT NULL DEFAULT 0,
    conflict_order_count INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL,
    error_summary VARCHAR(2000) NULL,
    CONSTRAINT uq_import_batch_store_id UNIQUE (store_id, import_batch_id),
    CONSTRAINT fk_import_batch_store FOREIGN KEY (store_id) REFERENCES store(store_id),
    CONSTRAINT fk_import_batch_connection FOREIGN KEY (connection_id) REFERENCES api_connection(connection_id),
    CONSTRAINT ck_import_batch_method CHECK (method IN ('FILE', 'API')),
    CONSTRAINT ck_import_batch_counts CHECK (
        total_count >= 0 AND valid_count >= 0 AND rejected_count >= 0
        AND total_order_count >= 0 AND accepted_order_count >= 0
        AND rejected_order_count >= 0 AND duplicate_order_count >= 0 AND conflict_order_count >= 0
    ),
    CONSTRAINT ck_import_batch_row_counts CHECK (valid_count + rejected_count <= total_count),
    CONSTRAINT ck_import_batch_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'COMPLETED_WITH_ERRORS', 'FAILED'))
);

CREATE INDEX ix_import_batch_store_started ON import_batch(store_id, started_at, import_batch_id);

CREATE TABLE sales_transaction (
    transaction_id INT AUTO_INCREMENT PRIMARY KEY,
    store_id INT NOT NULL,
    import_batch_id INT NULL,
    source_system VARCHAR(40) NOT NULL,
    source_order_id VARCHAR(150) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    status VARCHAR(30) NOT NULL,
    f03_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    total_amount DECIMAL(24, 2) NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    CONSTRAINT uq_sales_transaction_store_id UNIQUE (store_id, transaction_id),
    CONSTRAINT uq_sales_transaction_idempotency UNIQUE (store_id, source_system, source_order_id),
    CONSTRAINT fk_sales_transaction_store FOREIGN KEY (store_id) REFERENCES store(store_id),
    CONSTRAINT fk_sales_transaction_batch FOREIGN KEY (import_batch_id) REFERENCES import_batch(import_batch_id),
    CONSTRAINT ck_sales_transaction_status CHECK (status IN ('COMPLETED', 'CANCELLED', 'REFUNDED', 'PARTIAL_REFUND')),
    CONSTRAINT ck_sales_transaction_total CHECK (total_amount >= 0)
);

CREATE INDEX ix_sales_transaction_store_occurred ON sales_transaction(store_id, occurred_at, transaction_id);

CREATE TABLE transaction_item (
    transaction_item_id INT AUTO_INCREMENT PRIMARY KEY,
    store_id INT NOT NULL,
    transaction_id INT NOT NULL,
    product_id INT NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    unit_cost DECIMAL(12, 2) NOT NULL,
    CONSTRAINT uq_transaction_item_transaction_product UNIQUE (transaction_id, product_id),
    CONSTRAINT uq_transaction_item_store_id UNIQUE (store_id, transaction_item_id),
    CONSTRAINT fk_transaction_item_transaction FOREIGN KEY (store_id, transaction_id)
        REFERENCES sales_transaction(store_id, transaction_id),
    CONSTRAINT fk_transaction_item_product FOREIGN KEY (store_id, product_id)
        REFERENCES product(store_id, product_id),
    CONSTRAINT ck_transaction_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_transaction_item_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_transaction_item_unit_cost CHECK (unit_cost >= 0)
);

CREATE INDEX ix_transaction_item_transaction ON transaction_item(transaction_id, transaction_item_id);
CREATE INDEX ix_transaction_item_product ON transaction_item(store_id, product_id, transaction_id);

CREATE TABLE import_staging_row (
    staging_row_id INT AUTO_INCREMENT PRIMARY KEY,
    store_id INT NOT NULL,
    import_batch_id INT NOT NULL,
    row_number INT NOT NULL,
    source_order_id VARCHAR(150) NULL,
    raw_data TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_details TEXT NULL,
    product_id INT NULL,
    processed_transaction_id INT NULL,
    transaction_item_id INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    CONSTRAINT uq_import_staging_batch_row UNIQUE (import_batch_id, row_number),
    CONSTRAINT fk_import_staging_batch FOREIGN KEY (store_id, import_batch_id)
        REFERENCES import_batch(store_id, import_batch_id),
    CONSTRAINT fk_import_staging_product FOREIGN KEY (store_id, product_id)
        REFERENCES product(store_id, product_id),
    CONSTRAINT fk_import_staging_transaction FOREIGN KEY (store_id, processed_transaction_id)
        REFERENCES sales_transaction(store_id, transaction_id),
    CONSTRAINT fk_import_staging_item FOREIGN KEY (store_id, transaction_item_id)
        REFERENCES transaction_item(store_id, transaction_item_id),
    CONSTRAINT ck_import_staging_row_number CHECK (row_number > 0),
    CONSTRAINT ck_import_staging_status CHECK (status IN ('PENDING', 'VALID', 'REJECTED', 'PROCESSED'))
);

CREATE INDEX ix_import_staging_batch_status ON import_staging_row(import_batch_id, status, row_number);
CREATE INDEX ix_import_staging_batch_order ON import_staging_row(import_batch_id, source_order_id, row_number);

ALTER TABLE utility_matrix_item
    ADD CONSTRAINT fk_utility_matrix_transaction FOREIGN KEY (transaction_id)
    REFERENCES sales_transaction(transaction_id);

CREATE TABLE category (
    category_id INT AUTO_INCREMENT PRIMARY KEY,
    store_id INT NOT NULL,
    name VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_category_store_id UNIQUE (store_id, category_id),
    CONSTRAINT ck_category_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT fk_category_store FOREIGN KEY (store_id) REFERENCES store(store_id)
);

CREATE INDEX ix_category_store_status ON category(store_id, status, name);

CREATE TABLE product (
    product_id INT AUTO_INCREMENT PRIMARY KEY,
    store_id INT NOT NULL,
    category_id INT NULL,
    sku VARCHAR(80) NOT NULL,
    name VARCHAR(180) NOT NULL,
    unit VARCHAR(40) NOT NULL,
    requires_expiry BOOLEAN NOT NULL DEFAULT FALSE,
    selling_price DECIMAL(12, 2) NOT NULL,
    cost_price DECIMAL(12, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT uq_product_store_sku UNIQUE (store_id, sku),
    CONSTRAINT uq_product_store_id UNIQUE (store_id, product_id),
    CONSTRAINT ck_product_selling_price CHECK (selling_price >= 0),
    CONSTRAINT ck_product_cost_price CHECK (cost_price >= 0),
    CONSTRAINT ck_product_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT fk_product_store FOREIGN KEY (store_id) REFERENCES store(store_id),
    CONSTRAINT fk_product_category_store FOREIGN KEY (store_id, category_id)
        REFERENCES category(store_id, category_id)
);

CREATE INDEX ix_product_store_category ON product(store_id, category_id, status);
CREATE INDEX ix_product_store_name ON product(store_id, name);

CREATE TABLE inventory_batch (
    batch_id INT AUTO_INCREMENT PRIMARY KEY,
    product_id INT NOT NULL,
    batch_code VARCHAR(80) NOT NULL,
    received_at DATETIME NOT NULL,
    expiry_date DATE NULL,
    initial_quantity INT NOT NULL,
    remaining_quantity INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_batch_product_code UNIQUE (product_id, batch_code),
    CONSTRAINT ck_batch_initial_quantity CHECK (initial_quantity > 0),
    CONSTRAINT ck_batch_remaining_quantity CHECK (remaining_quantity >= 0 AND remaining_quantity <= initial_quantity),
    CONSTRAINT ck_batch_status CHECK (status IN ('ACTIVE', 'BLOCKED')),
    CONSTRAINT fk_batch_product FOREIGN KEY (product_id) REFERENCES product(product_id)
);

CREATE INDEX ix_batch_product_status_expiry ON inventory_batch(product_id, status, expiry_date);

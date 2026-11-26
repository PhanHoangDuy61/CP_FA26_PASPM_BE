CREATE TABLE shopping_cart (
    cart_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id INT NOT NULL,
    customer_user_id INT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uq_cart_store_customer UNIQUE (store_id, customer_user_id),
    CONSTRAINT uq_cart_store_id UNIQUE (store_id, cart_id),
    CONSTRAINT fk_cart_store FOREIGN KEY (store_id) REFERENCES store(store_id),
    CONSTRAINT fk_cart_customer FOREIGN KEY (customer_user_id) REFERENCES app_user(user_id),
    CONSTRAINT ck_cart_version CHECK (version >= 0)
);

CREATE TABLE shopping_cart_item (
    item_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id INT NOT NULL,
    cart_id BIGINT NOT NULL,
    product_id INT NOT NULL,
    quantity INT NOT NULL,
    added_from_bundle BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_cart_item_product UNIQUE (store_id, cart_id, product_id),
    CONSTRAINT fk_cart_item_cart FOREIGN KEY (store_id, cart_id) REFERENCES shopping_cart(store_id, cart_id),
    CONSTRAINT fk_cart_item_product FOREIGN KEY (store_id, product_id) REFERENCES product(store_id, product_id),
    CONSTRAINT ck_cart_item_quantity CHECK (quantity BETWEEN 1 AND 99)
);

CREATE TABLE storefront_event (
    event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_id INT NOT NULL,
    cart_id BIGINT NULL,
    event_type VARCHAR(32) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    campaign_id BIGINT NULL,
    layout_id BIGINT NULL,
    layout_version BIGINT NULL,
    block_no INT NULL,
    bundle_origin VARCHAR(8) NULL,
    added_product_units BIGINT NULL,
    failure_code VARCHAR(64) NULL,
    CONSTRAINT fk_storefront_event_store FOREIGN KEY (store_id) REFERENCES store(store_id),
    CONSTRAINT fk_storefront_event_cart FOREIGN KEY (store_id, cart_id) REFERENCES shopping_cart(store_id, cart_id),
    CONSTRAINT fk_storefront_event_campaign FOREIGN KEY (store_id, campaign_id)
        REFERENCES campaign(store_id, campaign_id),
    CONSTRAINT fk_storefront_event_layout FOREIGN KEY (store_id, campaign_id, layout_id)
        REFERENCES storefront_layout(store_id, campaign_id, layout_id),
    CONSTRAINT fk_storefront_event_revision FOREIGN KEY (store_id, layout_id, layout_version)
        REFERENCES storefront_layout_revision(store_id, layout_id, version),
    CONSTRAINT ck_storefront_event_type CHECK (event_type IN (
        'ITEM_ADDED','ITEM_QUANTITY_CHANGED','ITEM_REMOVED','CART_CLEARED','BUNDLE_ADDED','BUNDLE_ADD_REJECTED'
    )),
    CONSTRAINT ck_storefront_event_origin CHECK (bundle_origin IS NULL OR bundle_origin IN ('MINED','MANUAL')),
    CONSTRAINT ck_storefront_event_units CHECK (added_product_units IS NULL OR added_product_units >= 0)
);

CREATE INDEX idx_storefront_event_store_type_time
    ON storefront_event(store_id, event_type, occurred_at, event_id);

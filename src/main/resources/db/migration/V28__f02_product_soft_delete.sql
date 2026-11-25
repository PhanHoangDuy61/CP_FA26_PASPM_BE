-- DEC-20261124-03: product soft delete (F02). The row stays, so every historical reference keeps resolving:
-- transaction_item, import_staging_row, utility_matrix_item, pattern_item, recommendation, layout_placement,
-- product_season and inventory_batch all have RESTRICT foreign keys to product.
-- deleted_at/deleted_by are NULL for every existing product; nothing is backfilled and no existing row changes.
ALTER TABLE product ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE product ADD COLUMN deleted_by INT NULL;
ALTER TABLE product ADD CONSTRAINT fk_product_deleted_by FOREIGN KEY (deleted_by) REFERENCES app_user(user_id);
ALTER TABLE product ADD CONSTRAINT ck_product_deleted_pair CHECK (
    (deleted_at IS NULL AND deleted_by IS NULL) OR (deleted_at IS NOT NULL AND deleted_by IS NOT NULL));
CREATE INDEX ix_product_store_deleted ON product(store_id, deleted_at);

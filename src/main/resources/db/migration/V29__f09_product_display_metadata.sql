-- F09 Storefront MVP (DEC-20261117-03, FR-09.4): optional public display metadata on product.
-- Renumbered from the unmerged branch's V27 (V27 = Store management, V28 = product soft delete on dev).
-- The Store slug is the existing store.slug from V27 (DEC-20261123-01); no storefront slug or visibility column is added.
-- Both columns are nullable; no existing row is changed.
ALTER TABLE product ADD COLUMN image_url VARCHAR(2048) NULL;
ALTER TABLE product ADD COLUMN description TEXT NULL;

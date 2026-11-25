-- F09 Storefront (DEC-20261125-04): drop the columns the old F09 merge on dev added in V29__f09_storefront.
-- V29 stays exactly as merged (it may already be applied); this migration only removes what the runtime no longer uses:
--   store.store_slug          -> the public storeSlug is store.slug from V27 (DEC-20261123-01);
--   store.storefront_enabled  -> a Store is public exactly when store.status = 'ACTIVE'; there is no visibility toggle.
-- product.image_url and product.description are already added by V29 and are kept unchanged.
-- No other row or column changes.
DROP INDEX uq_store_store_slug ON store;
ALTER TABLE store DROP COLUMN store_slug;
ALTER TABLE store DROP COLUMN storefront_enabled;

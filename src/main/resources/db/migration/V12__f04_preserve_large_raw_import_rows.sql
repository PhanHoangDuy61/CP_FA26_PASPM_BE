-- A permitted file can contain a single raw row larger than MySQL TEXT (64 KiB).
-- Preserve original parsed evidence, including JSON escaping expansion, before business validation.
-- Order identity projections and existing historical transaction semantics are unchanged.
ALTER TABLE import_staging_row MODIFY COLUMN raw_data LONGTEXT NOT NULL;

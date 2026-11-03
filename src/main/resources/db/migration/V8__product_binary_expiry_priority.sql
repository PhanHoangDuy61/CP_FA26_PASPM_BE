-- v1.2 is opt-in per Product; legacy Store windows never backfill Product thresholds.
ALTER TABLE product ADD expiry_push_days INT NOT NULL DEFAULT 0;
ALTER TABLE product ADD CONSTRAINT ck_product_expiry_push_days CHECK (expiry_push_days >= 0);

-- Optional evidence for NEW snapshots only. Existing values/JSON/hashes remain untouched.
ALTER TABLE utility_matrix_item ADD expiry_evidence JSON NULL;
ALTER TABLE utility_snapshot ADD validation_warnings JSON NULL;

-- Preserve the published V20 tables and tokens. Old tokens have no trustworthy
-- binding: they require sign-in again; do not fabricate historical CSRF values.
ALTER TABLE auth_refresh_token ADD COLUMN csrf_hash BINARY(32) NULL;

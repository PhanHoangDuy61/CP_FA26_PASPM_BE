-- Session families and hashed single-use refresh credentials. No existing account is changed.
CREATE TABLE auth_session (
    session_id VARCHAR(36) PRIMARY KEY,
    user_id INT NOT NULL,
    token_version INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES app_user(user_id) ON DELETE CASCADE
);
CREATE INDEX ix_auth_session_user ON auth_session(user_id, revoked);
CREATE TABLE auth_refresh_token (
    token_hash CHAR(64) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    csrf_hash CHAR(64) NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_auth_refresh_session FOREIGN KEY (session_id) REFERENCES auth_session(session_id) ON DELETE CASCADE
);
CREATE INDEX ix_auth_refresh_session ON auth_refresh_token(session_id);

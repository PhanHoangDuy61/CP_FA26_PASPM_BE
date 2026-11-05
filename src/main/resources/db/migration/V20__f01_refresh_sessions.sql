CREATE TABLE auth_refresh_session (
    session_id CHAR(36) NOT NULL PRIMARY KEY,
    user_id INT NOT NULL,
    token_version INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    last_used_at TIMESTAMP NOT NULL,
    absolute_expires_at TIMESTAMP NOT NULL,
    idle_expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,
    CONSTRAINT fk_auth_refresh_session_user
        FOREIGN KEY (user_id) REFERENCES app_user(user_id) ON DELETE CASCADE,
    INDEX idx_auth_refresh_session_user (user_id, revoked_at),
    INDEX idx_auth_refresh_session_expiry (absolute_expires_at, revoked_at)
);

CREATE TABLE auth_refresh_token (
    token_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id CHAR(36) NOT NULL,
    token_hash BINARY(32) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL,
    CONSTRAINT uq_auth_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_refresh_token_session
        FOREIGN KEY (session_id) REFERENCES auth_refresh_session(session_id) ON DELETE CASCADE,
    INDEX idx_auth_refresh_token_session (session_id)
);

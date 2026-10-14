-- Shared STORE is owned here exactly once. F02 must reference it, not recreate it.
CREATE TABLE store (
 store_id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(150) NOT NULL,
 utility_config JSON NULL, mining_schedule VARCHAR(200) NULL
);
CREATE TABLE role (
 role_id INT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(40) NOT NULL UNIQUE,
 display_name VARCHAR(100) NOT NULL
);
INSERT INTO role(code, display_name) VALUES
 ('SYSTEM_ADMIN','System Administrator'),('STORE_ADMIN','Store Administrator'),
 ('MERCHANDISER','Merchandiser'),('END_CUSTOMER','End Customer');
CREATE TABLE app_user (
 user_id INT AUTO_INCREMENT PRIMARY KEY, store_id INT NULL, role_id INT NOT NULL,
 email VARCHAR(254) NOT NULL UNIQUE, name VARCHAR(150) NOT NULL,
 password_hash VARCHAR(100) NULL, email_verified_at TIMESTAMP NULL,
 status VARCHAR(30) NOT NULL, token_version INT NOT NULL DEFAULT 0,
 verification_purpose VARCHAR(30) NULL,
 FOREIGN KEY(store_id) REFERENCES store(store_id), FOREIGN KEY(role_id) REFERENCES role(role_id),
 CHECK(status IN ('PENDING_VERIFICATION','ACTIVE','INACTIVE')),
 CHECK(verification_purpose IS NULL OR verification_purpose IN ('SELF_REGISTER','ADMIN_INVITE'))
);
CREATE TABLE verification_challenge (
 user_id INT PRIMARY KEY, purpose VARCHAR(30) NOT NULL,
 code_hash VARCHAR(64) NOT NULL, expires_at TIMESTAMP NOT NULL,
 issued_at TIMESTAMP NOT NULL, attempts INT NOT NULL DEFAULT 0, consumed BOOLEAN NOT NULL DEFAULT FALSE,
 FOREIGN KEY(user_id) REFERENCES app_user(user_id), CHECK(attempts >= 0)
);
CREATE TABLE external_identity (
 identity_id INT AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL,
 provider VARCHAR(30) NOT NULL, provider_subject VARCHAR(255) NOT NULL,
 UNIQUE(provider,provider_subject), UNIQUE(user_id,provider),
 FOREIGN KEY(user_id) REFERENCES app_user(user_id)
);

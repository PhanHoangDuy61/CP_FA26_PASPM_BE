-- A single locked head gives the global policy a version; no algorithm or threshold is invented.
CREATE TABLE mining_config_head (
    config_id INT PRIMARY KEY,
    config_version BIGINT NOT NULL,
    CONSTRAINT ck_mining_config_head_id CHECK (config_id = 1),
    CONSTRAINT ck_mining_config_head_version CHECK (config_version >= 0)
);
INSERT INTO mining_config_head(config_id, config_version) VALUES (1, 0);
CREATE TABLE mining_config_version (
    config_version BIGINT PRIMARY KEY,
    algorithm VARCHAR(12) NOT NULL,
    min_util_mode VARCHAR(12) NOT NULL,
    min_util_value DECIMAL(30,6) NOT NULL,
    changed_by INT NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mining_config_actor FOREIGN KEY(changed_by) REFERENCES app_user(user_id),
    CONSTRAINT ck_mining_config_version CHECK (config_version > 0),
    CONSTRAINT ck_mining_config_algorithm CHECK (algorithm IN ('HUI_MINER','EFIM')),
    CONSTRAINT ck_mining_config_mode CHECK (min_util_mode IN ('PERCENTAGE','ABSOLUTE')),
    CONSTRAINT ck_mining_config_threshold CHECK (min_util_value > 0 AND (min_util_mode <> 'PERCENTAGE' OR min_util_value <= 100))
);
ALTER TABLE mining_request ADD config_version BIGINT NULL;
ALTER TABLE mining_request ADD CONSTRAINT fk_mining_request_config_version FOREIGN KEY(config_version) REFERENCES mining_config_version(config_version);

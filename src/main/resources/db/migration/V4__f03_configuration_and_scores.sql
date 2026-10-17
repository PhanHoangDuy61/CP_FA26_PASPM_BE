ALTER TABLE product ADD stock_priority_score DECIMAL(7,6) NULL;
ALTER TABLE product ADD seasonal_priority_score DECIMAL(7,6) NULL;
ALTER TABLE product ADD CONSTRAINT ck_product_stock_priority CHECK (stock_priority_score BETWEEN 0 AND 1);
ALTER TABLE product ADD CONSTRAINT ck_product_seasonal_priority CHECK (seasonal_priority_score BETWEEN 0 AND 1);

-- No persisted global configuration store exists in V1-V3. One key holds the system policy.
CREATE TABLE system_configuration (
    config_key VARCHAR(100) PRIMARY KEY,
    config_value JSON NOT NULL,
    config_version BIGINT NOT NULL,
    updated_by INT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_system_configuration_version CHECK (config_version > 0),
    CONSTRAINT fk_system_configuration_user FOREIGN KEY (updated_by) REFERENCES app_user(user_id)
);
INSERT INTO system_configuration(config_key,config_value,config_version)
VALUES ('UTILITY_LAMBDA_POLICY','{"lambdaMin":0,"lambdaMax":0.5}',1);

CREATE TABLE app_users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'CUSTOMER', 'DRIVER')),
    PRIMARY KEY (user_id, role)
);
-- Preserve existing profiles. No reusable default password is assigned.
INSERT INTO app_users(username, password_hash, enabled)
SELECT 'legacy-customer-' || id, '!', FALSE FROM customers;
INSERT INTO app_users(username, password_hash, enabled)
SELECT 'legacy-driver-' || id, '!', FALSE FROM drivers;
INSERT INTO user_roles(user_id, role)
SELECT id, 'CUSTOMER' FROM app_users WHERE username LIKE 'legacy-customer-%';
INSERT INTO user_roles(user_id, role)
SELECT id, 'DRIVER' FROM app_users WHERE username LIKE 'legacy-driver-%';
ALTER TABLE customers ADD COLUMN user_id BIGINT;
ALTER TABLE drivers ADD COLUMN user_id BIGINT;
UPDATE customers SET user_id = (SELECT id FROM app_users WHERE username = 'legacy-customer-' || customers.id);
UPDATE drivers SET user_id = (SELECT id FROM app_users WHERE username = 'legacy-driver-' || drivers.id);
ALTER TABLE customers ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE drivers ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE customers ADD CONSTRAINT uq_customer_user UNIQUE(user_id);
ALTER TABLE drivers ADD CONSTRAINT uq_driver_user UNIQUE(user_id);
ALTER TABLE customers ADD CONSTRAINT fk_customer_user FOREIGN KEY(user_id) REFERENCES app_users(id);
ALTER TABLE drivers ADD CONSTRAINT fk_driver_user FOREIGN KEY(user_id) REFERENCES app_users(id);

ALTER TABLE category ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE contact ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE transaction ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE subscription ADD COLUMN deleted_at TIMESTAMP;
ALTER TABLE debt ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX idx_category_deleted ON category (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX idx_contact_deleted ON contact (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX idx_transaction_deleted ON transaction (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX idx_subscription_deleted ON subscription (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX idx_debt_deleted ON debt (deleted_at) WHERE deleted_at IS NOT NULL;

ALTER TABLE category DROP CONSTRAINT category_user_id_name_key;
CREATE UNIQUE INDEX category_user_id_name_uq ON category (user_id, name) WHERE deleted_at IS NULL;

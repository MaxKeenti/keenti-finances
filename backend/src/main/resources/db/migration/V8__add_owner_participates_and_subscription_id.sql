ALTER TABLE subscription ADD COLUMN owner_participates BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE transaction ADD COLUMN subscription_id BIGINT REFERENCES subscription(id);

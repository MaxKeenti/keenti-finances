-- A Payment Record may be settled by a real Transaction. Linking that Transaction
-- to the record marks it PAID and ties the money movement to the billing period.
-- See ADR-0019 (manual per-subscription billing).

ALTER TABLE payment_record ADD COLUMN transaction_id BIGINT REFERENCES transaction(id);

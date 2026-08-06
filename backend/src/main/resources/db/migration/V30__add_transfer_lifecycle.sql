ALTER TABLE financial_account_transfer
    ADD COLUMN deleted_at TIMESTAMP;

CREATE INDEX financial_account_transfer_active_user_date_idx
    ON financial_account_transfer (user_id, transfer_date DESC, created_at DESC)
    WHERE deleted_at IS NULL;

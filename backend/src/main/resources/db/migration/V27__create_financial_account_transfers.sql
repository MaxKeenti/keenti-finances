CREATE TABLE financial_account_transfer (
    id                     BIGSERIAL       PRIMARY KEY,
    user_id                BIGINT          NOT NULL REFERENCES app_user(id),
    source_account_id      BIGINT          NOT NULL REFERENCES financial_account(id),
    destination_account_id BIGINT          NOT NULL REFERENCES financial_account(id),
    amount                 DECIMAL(12,2)   NOT NULL CHECK (amount > 0),
    transfer_date          DATE            NOT NULL,
    notes                  VARCHAR(500),
    created_at             TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT financial_account_transfer_accounts_differ
        CHECK (source_account_id <> destination_account_id)
);

CREATE INDEX financial_account_transfer_user_date_idx
    ON financial_account_transfer (user_id, transfer_date DESC, created_at DESC);

CREATE INDEX financial_account_transfer_source_idx
    ON financial_account_transfer (source_account_id);

CREATE INDEX financial_account_transfer_destination_idx
    ON financial_account_transfer (destination_account_id);

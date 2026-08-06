CREATE TABLE financial_account (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES app_user(id),
    name            VARCHAR(100)    NOT NULL,
    kind            VARCHAR(16)     NOT NULL CHECK (kind IN ('CASH', 'DEBIT', 'CHECKING', 'SAVINGS', 'CREDIT')),
    opening_balance DECIMAL(12,2)   NOT NULL,
    opening_date    DATE            NOT NULL,
    archived        BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX financial_account_user_name_active_uq
    ON financial_account (user_id, LOWER(name))
    WHERE archived = FALSE;

ALTER TABLE app_user
    ADD COLUMN account_tracking_activated_at DATE;

ALTER TABLE transaction
    ADD COLUMN account_id BIGINT REFERENCES financial_account(id);

CREATE INDEX transaction_account_id_idx ON transaction (account_id);

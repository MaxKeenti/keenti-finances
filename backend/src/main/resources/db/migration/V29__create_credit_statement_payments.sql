CREATE TABLE credit_statement_payment (
    id             BIGSERIAL       PRIMARY KEY,
    statement_id   BIGINT          NOT NULL REFERENCES credit_statement(id),
    transfer_id    BIGINT          NOT NULL REFERENCES financial_account_transfer(id),
    amount         DECIMAL(12,2)   NOT NULL CHECK (amount > 0),
    created_at     TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT credit_statement_payment_transfer_statement_uq UNIQUE (statement_id, transfer_id)
);

CREATE INDEX credit_statement_payment_statement_idx
    ON credit_statement_payment (statement_id);

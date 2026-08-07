CREATE TABLE credit_statement_revision (
    id                       BIGSERIAL       PRIMARY KEY,
    statement_id             BIGINT          NOT NULL REFERENCES credit_statement(id),
    due_date                 DATE            NOT NULL,
    official_balance         DECIMAL(12,2)   NOT NULL CHECK (official_balance >= 0),
    official_minimum_payment DECIMAL(12,2)   NOT NULL CHECK (official_minimum_payment >= 0),
    official_avoid_interest  DECIMAL(12,2)   NOT NULL CHECK (official_avoid_interest >= 0),
    official_note            VARCHAR(500),
    estimated_balance        DECIMAL(12,2)   NOT NULL,
    confirmed_at             TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT credit_statement_revision_payments_valid CHECK (
        official_minimum_payment <= official_balance
        AND official_avoid_interest <= official_balance
    )
);

CREATE INDEX credit_statement_revision_statement_idx
    ON credit_statement_revision (statement_id, confirmed_at DESC, id DESC);

CREATE TABLE credit_msi_plan (
    id                     BIGSERIAL       PRIMARY KEY,
    account_id             BIGINT          NOT NULL REFERENCES financial_account(id),
    transaction_id         BIGINT          NOT NULL UNIQUE REFERENCES transaction(id),
    purchase_amount        DECIMAL(12,2)   NOT NULL CHECK (purchase_amount > 0),
    installment_count      SMALLINT        NOT NULL CHECK (installment_count BETWEEN 2 AND 60),
    first_installment_date DATE            NOT NULL,
    cancelled_at           TIMESTAMP,
    created_at             TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX credit_msi_plan_account_active_idx
    ON credit_msi_plan (account_id, first_installment_date)
    WHERE cancelled_at IS NULL;

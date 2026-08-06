CREATE TABLE credit_account_settings (
    account_id            BIGINT        PRIMARY KEY REFERENCES financial_account(id),
    credit_limit          DECIMAL(12,2) NOT NULL CHECK (credit_limit > 0),
    statement_closing_day SMALLINT      NOT NULL CHECK (statement_closing_day BETWEEN 1 AND 31),
    payment_due_day       SMALLINT      NOT NULL CHECK (payment_due_day BETWEEN 1 AND 31),
    created_at            TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMP     NOT NULL DEFAULT NOW()
);

CREATE TABLE credit_statement (
    id                         BIGSERIAL       PRIMARY KEY,
    account_id                 BIGINT          NOT NULL REFERENCES financial_account(id),
    period_start               DATE            NOT NULL,
    period_end                 DATE            NOT NULL,
    due_date                   DATE            NOT NULL,
    estimated_balance          DECIMAL(12,2)   NOT NULL DEFAULT 0,
    official_balance           DECIMAL(12,2),
    official_minimum_payment   DECIMAL(12,2),
    official_avoid_interest    DECIMAL(12,2),
    official_note              VARCHAR(500),
    confirmed_at               TIMESTAMP,
    created_at                 TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT credit_statement_period_valid CHECK (period_end >= period_start),
    CONSTRAINT credit_statement_due_after_period CHECK (due_date >= period_end),
    CONSTRAINT credit_statement_official_amounts_nonnegative CHECK (
        (official_balance IS NULL OR official_balance >= 0)
        AND (official_minimum_payment IS NULL OR official_minimum_payment >= 0)
        AND (official_avoid_interest IS NULL OR official_avoid_interest >= 0)
    )
);

CREATE UNIQUE INDEX credit_statement_account_period_uq
    ON credit_statement (account_id, period_start, period_end);

CREATE INDEX credit_statement_account_due_idx
    ON credit_statement (account_id, due_date);

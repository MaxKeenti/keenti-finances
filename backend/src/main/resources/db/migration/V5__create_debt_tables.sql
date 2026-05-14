CREATE TABLE debt (
    id              BIGSERIAL       PRIMARY KEY,
    contact_id      BIGINT          NOT NULL REFERENCES contact(id),
    description     VARCHAR(500)    NOT NULL,
    total_amount    DECIMAL(12,2)   NOT NULL,
    status          VARCHAR(10)     NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','PAID')),
    created_at      TIMESTAMP       DEFAULT NOW()
);

CREATE TABLE debt_payment (
    id              BIGSERIAL       PRIMARY KEY,
    debt_id         BIGINT          NOT NULL REFERENCES debt(id) ON DELETE CASCADE,
    amount          DECIMAL(12,2)   NOT NULL,
    payment_date    DATE            NOT NULL,
    transaction_id  BIGINT          REFERENCES transaction(id),
    notes           VARCHAR(500),
    created_at      TIMESTAMP       DEFAULT NOW()
);

CREATE TABLE transaction (
    id               BIGSERIAL       PRIMARY KEY,
    amount           DECIMAL(12,2)   NOT NULL,
    direction        VARCHAR(10)     NOT NULL CHECK (direction IN ('INGRESS', 'EGRESS')),
    description      VARCHAR(500),
    transaction_date DATE            NOT NULL,
    category_id      BIGINT          NOT NULL REFERENCES category(id),
    contact_id       BIGINT          REFERENCES contact(id),
    created_at       TIMESTAMP       DEFAULT NOW()
);

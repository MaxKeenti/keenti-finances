ALTER TABLE transaction
    ADD CONSTRAINT transaction_id_user_id_uq UNIQUE (id, user_id);

ALTER TABLE box
    ADD CONSTRAINT box_id_user_id_uq UNIQUE (id, user_id);

CREATE TABLE box_funding (
    id               BIGSERIAL       PRIMARY KEY,
    user_id          BIGINT          NOT NULL REFERENCES app_user(id),
    transaction_id   BIGINT          NOT NULL,
    box_id           BIGINT          NOT NULL,
    amount           DECIMAL(12,2)   NOT NULL CHECK (amount > 0),
    line_order       INTEGER         NOT NULL CHECK (line_order >= 0),
    effective_date   DATE            NOT NULL,
    created_at       TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT box_funding_transaction_user_fk
        FOREIGN KEY (transaction_id, user_id)
        REFERENCES transaction(id, user_id)
        ON DELETE CASCADE,
    CONSTRAINT box_funding_box_user_fk
        FOREIGN KEY (box_id, user_id)
        REFERENCES box(id, user_id),
    CONSTRAINT box_funding_transaction_box_uq
        UNIQUE (transaction_id, box_id),
    CONSTRAINT box_funding_transaction_order_uq
        UNIQUE (transaction_id, line_order)
);

CREATE INDEX box_funding_box_effective_date_idx
    ON box_funding (box_id, effective_date DESC, line_order);

CREATE INDEX box_funding_user_transaction_idx
    ON box_funding (user_id, transaction_id);

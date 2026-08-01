ALTER TABLE category
    ADD CONSTRAINT category_id_user_id_uq UNIQUE (id, user_id);

CREATE TABLE funding_trigger (
    id               BIGSERIAL       PRIMARY KEY,
    user_id          BIGINT          NOT NULL REFERENCES app_user(id),
    box_id           BIGINT          NOT NULL,
    category_id      BIGINT          NOT NULL,
    strategy         VARCHAR(24)     NOT NULL
        CHECK (strategy IN ('PLAN_DERIVED', 'FIXED_AMOUNT', 'PERCENTAGE')),
    fixed_amount     DECIMAL(12,2),
    percentage       DECIMAL(7,4),
    enabled          BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT funding_trigger_box_user_fk
        FOREIGN KEY (box_id, user_id)
        REFERENCES box(id, user_id)
        ON DELETE CASCADE,
    CONSTRAINT funding_trigger_category_user_fk
        FOREIGN KEY (category_id, user_id)
        REFERENCES category(id, user_id)
        ON DELETE CASCADE,
    CONSTRAINT funding_trigger_strategy_value_ck CHECK (
        (strategy = 'PLAN_DERIVED'
            AND fixed_amount IS NULL
            AND percentage IS NULL)
        OR
        (strategy = 'FIXED_AMOUNT'
            AND fixed_amount IS NOT NULL
            AND fixed_amount > 0
            AND percentage IS NULL)
        OR
        (strategy = 'PERCENTAGE'
            AND fixed_amount IS NULL
            AND percentage IS NOT NULL
            AND percentage > 0
            AND percentage <= 100)
    ),
    CONSTRAINT funding_trigger_box_category_uq UNIQUE (box_id, category_id)
);

CREATE INDEX funding_trigger_user_category_enabled_idx
    ON funding_trigger (user_id, category_id, enabled);

CREATE INDEX funding_trigger_box_idx
    ON funding_trigger (box_id, id);

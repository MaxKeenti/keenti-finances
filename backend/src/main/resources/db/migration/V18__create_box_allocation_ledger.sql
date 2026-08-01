CREATE TABLE box (
    id              BIGSERIAL       PRIMARY KEY,
    user_id         BIGINT          NOT NULL REFERENCES app_user(id),
    name            VARCHAR(100)    NOT NULL,
    hue             INTEGER         NOT NULL CHECK (hue >= 0 AND hue < 360),
    icon            VARCHAR(16),
    description     VARCHAR(500),
    display_order   INTEGER         NOT NULL CHECK (display_order >= 0),
    archived        BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX box_active_user_name_uq
    ON box (user_id, LOWER(name))
    WHERE archived = FALSE;

CREATE INDEX box_user_lifecycle_order_idx
    ON box (user_id, archived, display_order, id);

CREATE TABLE box_movement (
    id                   BIGSERIAL       PRIMARY KEY,
    movement_type        VARCHAR(16)     NOT NULL
        CHECK (movement_type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    source_box_id        BIGINT          REFERENCES box(id),
    destination_box_id   BIGINT          REFERENCES box(id),
    amount               DECIMAL(12,2)   NOT NULL CHECK (amount > 0),
    effective_date       DATE            NOT NULL,
    created_at           TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT box_movement_shape_ck CHECK (
        (movement_type = 'DEPOSIT'
            AND source_box_id IS NULL
            AND destination_box_id IS NOT NULL)
        OR
        (movement_type = 'WITHDRAWAL'
            AND source_box_id IS NOT NULL
            AND destination_box_id IS NULL)
        OR
        (movement_type = 'TRANSFER'
            AND source_box_id IS NOT NULL
            AND destination_box_id IS NOT NULL
            AND source_box_id <> destination_box_id)
    )
);

CREATE INDEX box_movement_source_history_idx
    ON box_movement (source_box_id, effective_date, created_at, id)
    WHERE source_box_id IS NOT NULL;

CREATE INDEX box_movement_destination_history_idx
    ON box_movement (destination_box_id, effective_date, created_at, id)
    WHERE destination_box_id IS NOT NULL;

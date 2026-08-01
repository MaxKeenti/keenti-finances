ALTER TABLE box_movement
    ADD COLUMN source_transaction_id BIGINT,
    ADD COLUMN source_transaction_reference BIGINT,
    ADD COLUMN source_transaction_order INTEGER,
    ADD COLUMN source_transaction_changed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE box_movement
    ADD CONSTRAINT box_movement_source_transaction_fk
        FOREIGN KEY (source_transaction_id)
        REFERENCES transaction(id)
        ON DELETE SET NULL,
    ADD CONSTRAINT box_movement_source_transaction_shape_ck CHECK (
        (
            source_transaction_reference IS NULL
            AND source_transaction_id IS NULL
            AND source_transaction_order IS NULL
            AND source_transaction_changed = FALSE
        )
        OR
        (
            source_transaction_reference IS NOT NULL
            AND source_transaction_order IS NOT NULL
            AND source_transaction_order >= 0
            AND movement_type = 'DEPOSIT'
            AND source_box_id IS NULL
            AND (
                source_transaction_id IS NULL
                OR source_transaction_id = source_transaction_reference
            )
        )
    );

CREATE UNIQUE INDEX box_movement_source_transaction_box_uq
    ON box_movement (source_transaction_reference, destination_box_id)
    WHERE source_transaction_reference IS NOT NULL;

CREATE UNIQUE INDEX box_movement_source_transaction_order_uq
    ON box_movement (source_transaction_reference, source_transaction_order)
    WHERE source_transaction_reference IS NOT NULL;

CREATE INDEX box_movement_source_transaction_idx
    ON box_movement (source_transaction_reference)
    WHERE source_transaction_reference IS NOT NULL;

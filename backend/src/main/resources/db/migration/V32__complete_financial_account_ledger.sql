ALTER TABLE app_user
    ADD COLUMN account_tracking_required BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE credit_msi_plan
    ALTER COLUMN transaction_id DROP NOT NULL,
    ADD COLUMN opening_balance_amount DECIMAL(12,2) NOT NULL DEFAULT 0
        CHECK (opening_balance_amount >= 0),
    ADD COLUMN ended_at TIMESTAMP,
    ADD COLUMN end_reason VARCHAR(16)
        CHECK (end_reason IS NULL OR end_reason IN ('COMPLETED', 'CANCELLED'));

ALTER TABLE credit_msi_plan
    ADD CONSTRAINT credit_msi_plan_source_valid CHECK (
        transaction_id IS NOT NULL OR opening_balance_amount > 0
    );

UPDATE credit_msi_plan
SET ended_at = cancelled_at,
    end_reason = 'CANCELLED'
WHERE cancelled_at IS NOT NULL;

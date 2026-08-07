ALTER TABLE financial_account
    ADD COLUMN hue INTEGER NOT NULL DEFAULT 220
    CHECK (hue BETWEEN 0 AND 359);

UPDATE financial_account
SET hue = MOD((id * 137 + 83)::INTEGER, 360);

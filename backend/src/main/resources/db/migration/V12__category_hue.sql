-- Rename and retype category.color (VARCHAR(10), nullable, stringified hue digits) → category.hue (SMALLINT, NOT NULL, 0..359).
-- See ADR-0017.

ALTER TABLE category ADD COLUMN hue SMALLINT;

-- Backfill: parse existing stringified digits; fall back to direction default for nulls/blanks/garbage.
UPDATE category
SET hue = CASE
    WHEN color ~ '^[0-9]+$' AND CAST(color AS INTEGER) BETWEEN 0 AND 359 THEN CAST(color AS SMALLINT)
    WHEN type = 'INGRESS' THEN 100
    WHEN type = 'EGRESS'  THEN 10
    ELSE 220
END;

ALTER TABLE category ALTER COLUMN hue SET NOT NULL;
ALTER TABLE category ADD CONSTRAINT category_hue_range CHECK (hue >= 0 AND hue < 360);

ALTER TABLE category DROP COLUMN color;

-- A Debt is bidirectional: money owed to the User, or money the User owes.
-- The value is the Direction of the Transactions its Debt Payments create —
-- INGRESS when the money comes in, EGRESS when it goes out — so the two
-- concepts never have to be mapped onto each other.
--
-- Every Debt recorded before this migration was money owed to the User, which
-- is exactly what the default backfills.
ALTER TABLE debt
    ADD COLUMN direction VARCHAR(10) NOT NULL DEFAULT 'INGRESS';

ALTER TABLE debt
    ADD CONSTRAINT debt_direction_check CHECK (direction IN ('INGRESS','EGRESS'));

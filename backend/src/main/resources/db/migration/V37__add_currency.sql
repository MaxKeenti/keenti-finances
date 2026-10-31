-- ADR-0024: currency belongs to the Financial Account; totals are per currency.
--
-- Every amount recorded before this migration was MXN, which is exactly what
-- the defaults backfill. The supported set (MXN, EUR, USD, GBP) is enforced in
-- the application, so adding a code later does not need a migration; the
-- database only insists on the ISO 4217 shape.

ALTER TABLE financial_account ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'MXN';
ALTER TABLE financial_account ADD CONSTRAINT financial_account_currency_iso
    CHECK (currency ~ '^[A-Z]{3}$');

-- Copied from the Financial Account when the Transaction is created, so sums
-- per currency need no join and legacy Transactions without an Account stay MXN.
ALTER TABLE transaction ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'MXN';
ALTER TABLE transaction ADD CONSTRAINT transaction_currency_iso
    CHECK (currency ~ '^[A-Z]{3}$');
CREATE INDEX transaction_user_currency_date_idx
    ON transaction (user_id, currency, transaction_date);

ALTER TABLE box ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'MXN';
ALTER TABLE box ADD CONSTRAINT box_currency_iso
    CHECK (currency ~ '^[A-Z]{3}$');

ALTER TABLE debt ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'MXN';
ALTER TABLE debt ADD CONSTRAINT debt_currency_iso
    CHECK (currency ~ '^[A-Z]{3}$');

ALTER TABLE subscription ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'MXN';
ALTER TABLE subscription ADD CONSTRAINT subscription_currency_iso
    CHECK (currency ~ '^[A-Z]{3}$');

-- What arrived in the destination Account, in its currency. Equal to amount
-- for every same-currency Transfer, which is every Transfer recorded so far.
ALTER TABLE financial_account_transfer ADD COLUMN destination_amount DECIMAL(12,2);
UPDATE financial_account_transfer SET destination_amount = amount;
ALTER TABLE financial_account_transfer ALTER COLUMN destination_amount SET NOT NULL;
ALTER TABLE financial_account_transfer ADD CONSTRAINT financial_account_transfer_destination_amount_positive
    CHECK (destination_amount > 0);

-- The default must stay in sync with UserEntity.DEFAULT_PRIMARY_CURRENCY,
-- because Hibernate writes every column on INSERT and so bypasses the SQL DEFAULT.
ALTER TABLE app_user ADD COLUMN primary_currency CHAR(3) NOT NULL DEFAULT 'MXN';
ALTER TABLE app_user ADD CONSTRAINT app_user_primary_currency_iso
    CHECK (primary_currency ~ '^[A-Z]{3}$');

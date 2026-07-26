-- A payment obligation is unique per subscription, billing period, and member.
-- PostgreSQL treats NULL values as distinct in ordinary unique indexes, so
-- personal subscriptions (member_id IS NULL) need a separate partial index.
CREATE UNIQUE INDEX payment_record_shared_period_member_uq
    ON payment_record (subscription_id, billing_date, member_id)
    WHERE member_id IS NOT NULL;

CREATE UNIQUE INDEX payment_record_personal_period_uq
    ON payment_record (subscription_id, billing_date)
    WHERE member_id IS NULL;

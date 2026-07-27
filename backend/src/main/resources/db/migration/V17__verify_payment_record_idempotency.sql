-- V16 established the Payment Record uniqueness indexes before migrations
-- included a diagnostic for pre-existing financial-history duplicates. Keep
-- V16 immutable and make that invariant explicit for every later environment.
DO $$
DECLARE
    duplicate_details TEXT;
BEGIN
    SELECT string_agg(
        format(
            '(subscription_id=%s, billing_date=%s, member_id=%s, records=%s)',
            subscription_id,
            billing_date,
            member_id,
            record_count
        ),
        ', '
    )
    INTO duplicate_details
    FROM (
        SELECT subscription_id, billing_date, member_id, COUNT(*) AS record_count
        FROM payment_record
        WHERE member_id IS NOT NULL
        GROUP BY subscription_id, billing_date, member_id
        HAVING COUNT(*) > 1
        ORDER BY subscription_id, billing_date, member_id
        LIMIT 10
    ) duplicate_shared_records;

    IF duplicate_details IS NOT NULL THEN
        RAISE EXCEPTION
            'Duplicate Shared Subscription Payment Records prevent idempotency enforcement: %',
            duplicate_details;
    END IF;

    SELECT string_agg(
        format(
            '(subscription_id=%s, billing_date=%s, records=%s)',
            subscription_id,
            billing_date,
            record_count
        ),
        ', '
    )
    INTO duplicate_details
    FROM (
        SELECT subscription_id, billing_date, COUNT(*) AS record_count
        FROM payment_record
        WHERE member_id IS NULL
        GROUP BY subscription_id, billing_date
        HAVING COUNT(*) > 1
        ORDER BY subscription_id, billing_date
        LIMIT 10
    ) duplicate_personal_records;

    IF duplicate_details IS NOT NULL THEN
        RAISE EXCEPTION
            'Duplicate Personal Subscription Payment Records prevent idempotency enforcement: %',
            duplicate_details;
    END IF;
END
$$;

-- These are no-ops where V16 is intact and repair the invariant if an
-- environment was manually drifted after marking V16 as applied.
CREATE UNIQUE INDEX IF NOT EXISTS payment_record_shared_period_member_uq
    ON payment_record (subscription_id, billing_date, member_id)
    WHERE member_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS payment_record_personal_period_uq
    ON payment_record (subscription_id, billing_date)
    WHERE member_id IS NULL;

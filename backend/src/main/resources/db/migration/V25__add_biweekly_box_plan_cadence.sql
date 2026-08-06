ALTER TABLE box_plan_revision
    DROP CONSTRAINT box_plan_revision_cadence_check,
    ADD CONSTRAINT box_plan_revision_cadence_check
        CHECK (cadence IN ('DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY')),
    DROP CONSTRAINT box_plan_revision_anchor_ck,
    ADD CONSTRAINT box_plan_revision_anchor_ck CHECK (
        (cadence = 'DAILY'
            AND anchor_weekday IS NULL
            AND anchor_day_of_month IS NULL)
        OR
        (cadence IN ('WEEKLY', 'BIWEEKLY')
            AND anchor_weekday BETWEEN 1 AND 7
            AND anchor_day_of_month IS NULL)
        OR
        (cadence = 'MONTHLY'
            AND anchor_weekday IS NULL
            AND anchor_day_of_month BETWEEN 1 AND 31)
    );

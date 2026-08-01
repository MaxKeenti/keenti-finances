ALTER TABLE app_user
    ADD COLUMN time_zone VARCHAR(64) NOT NULL DEFAULT 'America/Mexico_City';

CREATE TABLE box_plan (
    id                  BIGSERIAL       PRIMARY KEY,
    box_id              BIGINT          NOT NULL REFERENCES box(id),
    plan_type           VARCHAR(24)     NOT NULL
        CHECK (plan_type IN ('SAVING_GOAL', 'SPENDING_BUDGET')),
    status              VARCHAR(24)     NOT NULL
        CHECK (status IN (
            'ACTIVE', 'READY_TO_COMPLETE', 'OVERDUE',
            'COMPLETED', 'ABANDONED', 'ENDED'
        )),
    start_date          DATE            NOT NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    closed_at           TIMESTAMP,
    completion_amount   DECIMAL(12,2),
    CONSTRAINT box_plan_closed_state_ck CHECK (
        (status IN ('ACTIVE', 'READY_TO_COMPLETE', 'OVERDUE') AND closed_at IS NULL)
        OR
        (status IN ('COMPLETED', 'ABANDONED', 'ENDED') AND closed_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX box_plan_one_active_uq
    ON box_plan (box_id)
    WHERE status IN ('ACTIVE', 'READY_TO_COMPLETE', 'OVERDUE');

CREATE INDEX box_plan_box_history_idx
    ON box_plan (box_id, created_at DESC, id DESC);

CREATE TABLE box_plan_revision (
    id                    BIGSERIAL       PRIMARY KEY,
    plan_id               BIGINT          NOT NULL REFERENCES box_plan(id) ON DELETE CASCADE,
    effective_from        DATE            NOT NULL,
    cadence               VARCHAR(12)     NOT NULL
        CHECK (cadence IN ('DAILY', 'WEEKLY', 'MONTHLY')),
    anchor_weekday        SMALLINT,
    anchor_day_of_month   SMALLINT,
    created_at            TIMESTAMP       NOT NULL DEFAULT NOW(),
    superseded_at         TIMESTAMP,
    CONSTRAINT box_plan_revision_anchor_ck CHECK (
        (cadence = 'DAILY'
            AND anchor_weekday IS NULL
            AND anchor_day_of_month IS NULL)
        OR
        (cadence = 'WEEKLY'
            AND anchor_weekday BETWEEN 1 AND 7
            AND anchor_day_of_month IS NULL)
        OR
        (cadence = 'MONTHLY'
            AND anchor_weekday IS NULL
            AND anchor_day_of_month BETWEEN 1 AND 31)
    )
);

CREATE UNIQUE INDEX box_plan_revision_effective_uq
    ON box_plan_revision (plan_id, effective_from)
    WHERE superseded_at IS NULL;

CREATE INDEX box_plan_revision_timeline_idx
    ON box_plan_revision (plan_id, effective_from, created_at, id);

CREATE TABLE saving_goal_revision (
    revision_id          BIGINT          PRIMARY KEY
        REFERENCES box_plan_revision(id) ON DELETE CASCADE,
    target_amount        DECIMAL(12,2)   NOT NULL CHECK (target_amount > 0),
    target_date          DATE            NOT NULL,
    regular_commitment   DECIMAL(12,2)   NOT NULL CHECK (regular_commitment >= 0)
);

CREATE TABLE box_plan_period (
    id                    BIGSERIAL       PRIMARY KEY,
    plan_id               BIGINT          NOT NULL REFERENCES box_plan(id) ON DELETE CASCADE,
    revision_id           BIGINT          NOT NULL REFERENCES box_plan_revision(id),
    period_start          DATE            NOT NULL,
    period_end_exclusive  DATE            NOT NULL,
    opening_balance       DECIMAL(12,2)   NOT NULL CHECK (opening_balance >= 0),
    closing_balance       DECIMAL(12,2)   NOT NULL CHECK (closing_balance >= 0),
    net_progress          DECIMAL(12,2)   NOT NULL,
    evaluated_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT box_plan_period_range_ck CHECK (period_end_exclusive > period_start),
    CONSTRAINT box_plan_period_plan_start_uq UNIQUE (plan_id, period_start)
);

CREATE INDEX box_plan_period_timeline_idx
    ON box_plan_period (plan_id, period_start, id);

CREATE TABLE saving_goal_period (
    period_id             BIGINT          PRIMARY KEY
        REFERENCES box_plan_period(id) ON DELETE CASCADE,
    regular_commitment    DECIMAL(12,2)   NOT NULL CHECK (regular_commitment >= 0),
    opening_arrears       DECIMAL(12,2)   NOT NULL CHECK (opening_arrears >= 0),
    required_amount       DECIMAL(12,2)   NOT NULL CHECK (required_amount >= 0),
    arrears_covered       DECIMAL(12,2)   NOT NULL CHECK (arrears_covered >= 0),
    regular_progress      DECIMAL(12,2)   NOT NULL CHECK (regular_progress >= 0),
    extra_progress        DECIMAL(12,2)   NOT NULL CHECK (extra_progress >= 0),
    shortfall             DECIMAL(12,2)   NOT NULL CHECK (shortfall >= 0),
    status                VARCHAR(12)     NOT NULL CHECK (status IN ('ACHIEVED', 'MISSED'))
);

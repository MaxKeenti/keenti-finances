CREATE TABLE spending_budget_revision (
    revision_id          BIGINT          PRIMARY KEY
        REFERENCES box_plan_revision(id) ON DELETE CASCADE,
    desired_balance      DECIMAL(12,2)   NOT NULL CHECK (desired_balance > 0)
);

CREATE TABLE spending_budget_period (
    period_id             BIGINT          PRIMARY KEY
        REFERENCES box_plan_period(id) ON DELETE CASCADE,
    deposits              DECIMAL(12,2)   NOT NULL CHECK (deposits >= 0),
    withdrawals           DECIMAL(12,2)   NOT NULL CHECK (withdrawals >= 0),
    transfers_in          DECIMAL(12,2)   NOT NULL CHECK (transfers_in >= 0),
    transfers_out         DECIMAL(12,2)   NOT NULL CHECK (transfers_out >= 0),
    funded_spending       DECIMAL(12,2)   NOT NULL CHECK (funded_spending >= 0),
    suggested_top_up      DECIMAL(12,2)   NOT NULL CHECK (suggested_top_up >= 0)
);

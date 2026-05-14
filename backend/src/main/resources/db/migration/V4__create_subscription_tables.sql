CREATE TABLE subscription (
    id                BIGSERIAL       PRIMARY KEY,
    name              VARCHAR(200)    NOT NULL,
    cost              DECIMAL(12,2)   NOT NULL,
    billing_cycle     VARCHAR(10)     NOT NULL CHECK (billing_cycle IN ('MONTHLY','YEARLY')),
    type              VARCHAR(10)     NOT NULL CHECK (type IN ('PERSONAL','SHARED')),
    category_id       BIGINT          REFERENCES category(id),
    next_billing_date DATE            NOT NULL,
    token_uuid        VARCHAR(36)     UNIQUE,
    created_at        TIMESTAMP       DEFAULT NOW()
);

CREATE TABLE subscription_member (
    id              BIGSERIAL       PRIMARY KEY,
    subscription_id BIGINT          NOT NULL REFERENCES subscription(id) ON DELETE CASCADE,
    contact_id      BIGINT          NOT NULL REFERENCES contact(id),
    share_amount    DECIMAL(12,2)   NOT NULL,
    created_at      TIMESTAMP       DEFAULT NOW(),
    UNIQUE(subscription_id, contact_id)
);

CREATE TABLE payment_record (
    id              BIGSERIAL       PRIMARY KEY,
    subscription_id BIGINT          NOT NULL REFERENCES subscription(id) ON DELETE CASCADE,
    member_id       BIGINT          REFERENCES subscription_member(id) ON DELETE CASCADE,
    billing_date    DATE            NOT NULL,
    amount          DECIMAL(12,2)   NOT NULL,
    status          VARCHAR(10)     NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PAID')),
    paid_date       DATE,
    created_at      TIMESTAMP       DEFAULT NOW()
);

CREATE TABLE category (
    id   BIGSERIAL    PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    type VARCHAR(50)  NOT NULL CHECK (type IN ('INGRESS', 'EGRESS', 'BOTH'))
);

CREATE TABLE contact (
    id    BIGSERIAL    PRIMARY KEY,
    name  VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    email VARCHAR(255)
);

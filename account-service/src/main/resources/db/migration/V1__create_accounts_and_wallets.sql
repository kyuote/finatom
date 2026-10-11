CREATE TYPE kyc_status AS ENUM ('PENDING', 'VERIFIED', 'REJECTED');

CREATE TABLE accounts (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(255) NOT NULL UNIQUE,
    kyc_status  kyc_status   NOT NULL DEFAULT 'PENDING',
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE wallets (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id  UUID NOT NULL REFERENCES accounts(id),
    currency    VARCHAR(3) NOT NULL,
    balance     NUMERIC(19,4) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),

    UNIQUE (account_id, currency)
);
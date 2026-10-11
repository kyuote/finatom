CREATE TYPE payment_status AS ENUM
    ('PENDING', 'COMPLETED', 'FAILED', 'REJECTED');

CREATE TYPE entry_type AS ENUM ('DEBIT', 'CREDIT');

CREATE TABLE payments
(
    id              UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    from_wallet_id  UUID           NOT NULL,
    to_wallet_id    UUID           NOT NULL,
    amount          NUMERIC(19, 4) NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    status          payment_status NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(64)    NOT NULL UNIQUE,
    created_at      TIMESTAMP      NOT NULL DEFAULT now()
);

CREATE TABLE ledger
(
    id         UUID PRIMARY KEY        DEFAULT gen_random_uuid(),
    wallet_id  UUID           NOT NULL,
    amount     NUMERIC(19, 4) NOT NULL,
    type       entry_type     NOT NULL,
    payment_id UUID           NOT NULL REFERENCES payments (id),
    created_at TIMESTAMP      NOT NULL DEFAULT now()
);

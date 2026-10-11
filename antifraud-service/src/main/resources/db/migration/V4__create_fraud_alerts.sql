CREATE TYPE alert_reason AS ENUM ('HIGH_AMOUNT', 'HIGH_FREQUENCY');

CREATE TABLE fraud_alerts (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id   UUID        NOT NULL,
    wallet_id    UUID        NOT NULL,
    reason       alert_reason NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE payments (
    id              UUID PRIMARY KEY,
    trip_id         UUID NOT NULL,
    amount          NUMERIC(12,2) NOT NULL,
    status          VARCHAR(30) NOT NULL CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    attempts        INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One payment per trip: a duplicate trip.completed must not charge twice.
ALTER TABLE payments ADD CONSTRAINT uq_payment_trip UNIQUE (trip_id);

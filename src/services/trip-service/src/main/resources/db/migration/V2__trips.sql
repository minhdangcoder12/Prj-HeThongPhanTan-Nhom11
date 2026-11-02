CREATE TABLE trips (
    id              UUID PRIMARY KEY,
    passenger_id    UUID NOT NULL,
    driver_id       UUID,
    status          VARCHAR(30) NOT NULL CHECK (status IN ('REQUESTED', 'MATCHING', 'DRIVER_ASSIGNED', 'ACCEPTED', 'IN_PROGRESS', 'COMPLETED', 'PAYMENT_PENDING', 'PAID', 'PAYMENT_FAILED', 'NO_DRIVER', 'CANCELLED')),
    pickup_lat      DOUBLE PRECISION,
    pickup_lng      DOUBLE PRECISION,
    dropoff_lat     DOUBLE PRECISION,
    dropoff_lng     DOUBLE PRECISION,
    fare            NUMERIC(12,2),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_trips_status ON trips (status);

-- Audit of every transition, shown on System Monitor.
CREATE TABLE trip_status_history (
    id             UUID PRIMARY KEY,
    trip_id        UUID        NOT NULL REFERENCES trips (id),
    from_status    VARCHAR(30),
    to_status      VARCHAR(30) NOT NULL,
    actor          VARCHAR(30) NOT NULL,
    correlation_id UUID,
    changed_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_trip_history_trip ON trip_status_history (trip_id, changed_at);

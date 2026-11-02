CREATE TABLE matching_sagas (
    id              UUID PRIMARY KEY,
    trip_id         UUID NOT NULL,
    driver_id       UUID,
    status          VARCHAR(30) NOT NULL CHECK (status IN ('STARTED', 'CANDIDATE_SELECTED', 'DRIVER_RESERVED', 'TRIP_ASSIGNED', 'COMPLETED', 'COMPENSATING', 'COMPENSATED', 'FAILED')),
    current_step    VARCHAR(255),
    attempts        INT NOT NULL DEFAULT 0,
    last_error      VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE matching_sagas ADD CONSTRAINT uq_saga_trip UNIQUE (trip_id);

-- One row per candidate tried (reserved / conflict / rejected...).
CREATE TABLE matching_attempts (
    id         UUID PRIMARY KEY,
    saga_id    UUID        NOT NULL REFERENCES matching_sagas (id),
    driver_id  UUID        NOT NULL,
    result     VARCHAR(30) NOT NULL,
    reason     VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

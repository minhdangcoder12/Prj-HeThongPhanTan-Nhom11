CREATE TABLE drivers (
    id              UUID PRIMARY KEY,
    user_id         UUID,
    full_name       VARCHAR(255) NOT NULL,
    vehicle_plate   VARCHAR(255),
    status          VARCHAR(30) NOT NULL CHECK (status IN ('OFFLINE', 'AVAILABLE', 'RESERVED', 'ON_TRIP')),
    current_trip_id UUID,
    reserved_until  TIMESTAMPTZ,
    lat             DOUBLE PRECISION,
    lng             DOUBLE PRECISION,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Atomic reservation (R4): UPDATE drivers SET status='RESERVED' ... WHERE id=? AND status='AVAILABLE'
CREATE INDEX idx_drivers_status ON drivers (status);

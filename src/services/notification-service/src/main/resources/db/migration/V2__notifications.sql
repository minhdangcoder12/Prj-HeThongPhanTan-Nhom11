CREATE TABLE notifications (
    id              UUID PRIMARY KEY,
    trip_id         UUID,
    recipient_id    UUID,
    type            VARCHAR(255),
    message         VARCHAR(255),
    status          VARCHAR(30) NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempts        INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

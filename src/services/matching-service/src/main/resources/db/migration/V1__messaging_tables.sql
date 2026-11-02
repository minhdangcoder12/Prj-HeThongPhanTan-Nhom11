-- Transactional Outbox: payload holds the full EventEnvelope JSON; id = eventId.
CREATE TABLE outbox_event (
    id           UUID PRIMARY KEY,
    aggregate_id VARCHAR(64)  NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    payload      JSONB        NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PUBLISHED')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending ON outbox_event (status, created_at);

-- Inbox / idempotent consumer: one row per (event, consumer).
CREATE TABLE inbox_event (
    event_id     UUID         NOT NULL,
    consumer     VARCHAR(100) NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumer)
);

-- Idempotency-Key for retried internal REST commands (reserve, assign, pay...).
CREATE TABLE idempotency_record (
    idempotency_key VARCHAR(200) PRIMARY KEY,
    operation       VARCHAR(100) NOT NULL,
    status_code     INT          NOT NULL,
    response_body   JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

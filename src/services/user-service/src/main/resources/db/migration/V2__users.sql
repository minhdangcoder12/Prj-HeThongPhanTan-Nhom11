CREATE TABLE users (
    id              UUID PRIMARY KEY,
    email           VARCHAR(255) NOT NULL,
    full_name       VARCHAR(255) NOT NULL,
    role            VARCHAR(30) NOT NULL CHECK (role IN ('PASSENGER', 'DRIVER', 'ADMIN')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE users ADD COLUMN password_hash VARCHAR(100) NOT NULL;
ALTER TABLE users ADD CONSTRAINT uq_users_email UNIQUE (email);

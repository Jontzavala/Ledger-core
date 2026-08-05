CREATE TABLE idempotency_keys
(
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    idempotency_key VARCHAR(255) UNIQUE NOT NULL,
    entry_id        BIGINT REFERENCES journal_entries (id),
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT now(),
    request_hash    CHAR(64)            NOT NULL

);
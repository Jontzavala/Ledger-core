-- V1: core double-entry ledger tables (see docs/notes/ledger-schema.md)
-- Balances are derived from postings; no balance column anywhere.
-- Sum-to-zero enforcement is deliberately NOT included here (separate task).

CREATE TABLE accounts (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE journal_entries (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    description TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE postings (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entry_id   BIGINT NOT NULL REFERENCES journal_entries (id),
    account_id BIGINT NOT NULL REFERENCES accounts (id),
    amount     BIGINT NOT NULL
);

# 3. Deferred constraint trigger for balance

## Status

Accepted — 2026-07-28

## Context

Every journal entry's postings must sum to zero, or money has been created or destroyed. The question was where to
enforce it. Validation in LedgerService only covers the path it runs on, so a raw SQL fix, an admin script, or a future
service writing directly would all bypass it.

## Decision

The rule is that every journal entry's postings sum to zero. A CHECK constraint can't express it: a CHECK sees only the
row being written and can't query the entry's other postings. A trigger can, but a per-row trigger firing on every
insert would reject valid work, because a multi-leg entry is legitimately unbalanced between legs. After the first
posting of a three-leg entry, the total is whatever that leg was.

So the check is a deferred constraint trigger: DEFERRABLE INITIALLY DEFERRED, evaluated at commit against the
transaction's final state. Intermediate imbalance is allowed; a transaction that ends unbalanced is rejected.

It fires on INSERT, UPDATE, and DELETE. DELETE matters: without it, removing one leg of a balanced entry would silently
create money. On DELETE there is no NEW row, so the trigger resolves the entry via COALESCE(NEW.entry_id, OLD.entry_id).

## Consequences

- Violations surface at commit rather than at the offending statement, so callers can't attribute the failure to a
  specific write.
- The rule now exists in two places: Java for fast, readable errors, and the trigger as the backstop, so both have to
  stay
  in sync.
- The trigger is row-level, so TRUNCATE and restores or replication running with triggers disabled bypass it. An
  entry with no postings, or a single posting of zero, satisfies sum-to-zero and is considered valid.
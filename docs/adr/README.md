# Architecture Decision Records

An Architecture Decision Record (ADR) captures a single significant technical decision:
the situation that forced a choice, the choice made, and what it costs going forward.
Each record is one file, numbered in the order the decisions were made, and never
rewritten after the fact. If a decision changes, a new ADR supersedes the old one so
the reasoning stays readable as a history rather than a snapshot.

Every ADR here uses four headings: **Status**, **Context**, **Decision**, **Consequences**.

## Records

| # | Title | Decision |
|---|---|---|
| [0001](0001-money-as-bigint-minor-units.md) | Money as BIGINT minor units | Store all monetary amounts as BIGINT in minor units (cents) so the sum-to-zero check is exact integer arithmetic rather than floating point. |
| [0002](0002-Idempotency-via-unique-constraint.md) | Idempotency via unique constraint | Claim the client's Idempotency-Key with `INSERT … ON CONFLICT DO NOTHING` before creating the entry, letting the unique constraint pick the winner atomically and a stored request hash separate honest retries from key reuse. |
| [0003](0003-deferred-constraint-trigger-for-balance.md) | Deferred constraint trigger for balance | Enforce postings-sum-to-zero with a `DEFERRABLE INITIALLY DEFERRED` constraint trigger on INSERT, UPDATE, and DELETE, evaluated at commit so multi-leg entries may be unbalanced between legs. |
| [0004](0004-immutable-posting-entry-id.md) | Immutable posting entry_id | Forbid moving a posting between entries outright with a BEFORE UPDATE trigger that rejects any change to `entry_id`, instead of teaching the balance trigger to validate both entries. |

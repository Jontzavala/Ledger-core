# 2. Idempotency via unique constraint

## Status

Accepted — 2026-08-17

## Context

A client sends a transfer, the response is lost in transit, and the client has no way to know whether it succeeded. If
it retries, we must not create a second transfer. If it doesn't retry, the payment may never have happened. That
ambiguity is what forces the mechanism.

## Decision

Clients send an Idempotency-Key header. The naive approach, check whether the key exists, then create the entry has a
race: two concurrent requests can both see the key as absent and both proceed. That gap between check and write is a
TOCTOU window that application code can't close, because they're two separate round-trips.

Instead the key is claimed with INSERT … ON CONFLICT (idempotency_key) DO NOTHING, so the unique constraint decides the
winner atomically. Using ON CONFLICT rather than catching a constraint violation is deliberate: a violation poisons the
transaction, Postgres aborts the block, Hibernate's session is left inconsistent, and the transaction is marked
rollback-only so catching it and reading the existing row in the same transaction isn't possible. ON CONFLICT returns a
row count instead of raising, and the transaction stays usable.

A count of 1 means this caller claimed the key: create the entry, link it to the key row, return 201. A count of 0 means
the key already existed, and the stored SHA-256 hash of the request payload decides what happens next. A matching hash
is an honest retry: 200 with the original entry, nothing created. A different hash means the key was reused for
different
content, which is a client error: 409. Without the hash, that case would return 200 and the original entry, and the
client would believe a different transfer had succeeded: not duplicated money, but misattributed money.

There is one narrow window: a duplicate that reads the key row before the winner has linked its entry. That returns 409
with a distinct message. In the 50-thread concurrency test it never fired: Postgres blocks the second inserter on the
conflicting tuple until the winner commits, so all 49 duplicates saw a linked entry and replayed cleanly.

The key row is inserted before the entry is created, rather than the other way around. The first plan was entry-first,
since it avoids a nullable foreign key and needs no follow-up update. Tracing the concurrent case changed it: with
entry-first, the losing thread creates an entry, looks up accounts, inserts postings, and runs the balance trigger
before its key insert fails and the whole transaction rolls back. Key-first rejects the duplicate before any of that
work happens. The cost is a nullable entry_id on the key row, which the schema can no longer prove is always set.

The request hash covers the legs in the order the client sent them, not sorted. An honest retry resends a byte-identical
body, so ordering is stable. A retry with reordered legs means the client rebuilt the request rather than replaying it,
and the 409 surfaces that rather than hiding it.

## Consequences

- Clients must generate and send a key. The API is no longer usable without one; that burden moves to the caller, and a
  client that generates keys badly (reusing them, or generating a fresh one per retry) gets wrong behavior.
- Keys accumulate indefinitely. No expiry, so the table only grows. Fine at this scale, and the threshold where it stops
  being fine is real volume.
- The in-flight window returns a 409 that clients have to handle, even though it's a "try again shortly" rather than a
  real conflict.
- The guarantee is scoped to this table. Anything writing journal entries by another path bypasses idempotency entirely.
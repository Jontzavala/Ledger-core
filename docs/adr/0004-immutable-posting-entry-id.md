# 4. Immutable posting entry_id

## Status

Accepted — 2026-07-28

## Context

An edge-case review surfaced a specific hole, and the hole was escapable moving a posting from entry A to B leaves A
unbalanced, and while B's own balance check would usually catch the transfer, you could rebalance B in the same
transaction and slip an imbalance in A past every check, committed.

## Decision

We had two options: teach the trigger to validate both the source and destination entry on every update,
or forbid the operation entirely.
We chose forbidding because moving a posting between entries is meaningless in a real ledger
a posting is a leg of a historical event, so the operation had no legitimate use to protect.
This follows the principle of making illegal states unrepresentable rather than validating against them.
We enforce this with a BEFORE UPDATE trigger that rejects any change to entry_id immediately,
unlike the deferred balance trigger that judges at COMMIT.

## Consequences

We gave up the ability to edit entry_ids after they have been submitted.
So now if a posting lands on the entry_id and it's wrong,
we post a new entry that reverses the mistake and another that records it correctly.
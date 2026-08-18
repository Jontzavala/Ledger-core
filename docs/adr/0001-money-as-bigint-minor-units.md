# 1. Money as BIGINT minor units

## Status

Accepted — 2026-07-28

## Context

This is a ledger; every entry must sum to exactly zero; if amounts can drift by fractions of a cent,
the invariant becomes unverifiable and the books can't be trusted.

## Decision

We store all monetary amounts as BIGINT in minor units cents rather than a floating-point type or NUMERIC.
Binary floating point can't represent decimal cents exactly, 0.1 + 0.2 famously isn't 0.3, and those errors accumulate
Integer math is unambiguous, the balance check is literal integer addition,
and it mirrors how real payment systems store money internally.
NUMERIC would also have been exact,
but integer arithmetic is simpler to reason about and the sum-to-zero check becomes literal integer addition

## Consequences

Every amount in the system is now in cents,
so anything displaying money must divide by 100 the formatting burden moves to the presentation layer.
Multi-currency would need rework, since minor units differ by currency (yen has none).
And BIGINT caps you around 92 quadrillion cents, which is fine forever but is a bound we chose knowingly.
# ledger-core

[![CI](https://github.com/Jontzavala/Ledger-core/actions/workflows/ci.yml/badge.svg)](https://github.com/Jontzavala/Ledger-core/actions/workflows/ci.yml)

> **Status:** Complete through Milestone 2, deliberately closed at a milestone boundary.

## Overview

ledger-core is a double-entry ledger service. A caller posts a journal entry describing a movement of money between
accounts, and the entry's postings must sum to zero — money leaving one account always arrives somewhere else.

Systems that store account balances directly can drift: a bug or a retried request updates one balance and not the
other, and the discrepancy only surfaces later during reconciliation. Here, balances are derived by summing postings,
and the sum-to-zero rule is enforced by the database itself rather than by application code. Money is never created or
destroyed. Duplicate requests are handled by idempotency keys, so a client that retries after a lost response gets the
original entry back instead of a second transfer.

I built it to understand how payment systems move money and where the correctness boundaries actually sit.

## Architecture

Spring Boot 4.1 / Java 21 / Maven, backed by PostgreSQL 16. Schema is managed by
Flyway; JPA runs with `ddl-auto: validate`, so entities are checked against the
migrated schema at startup.

```mermaid
flowchart TB
    client([HTTP client])

    subgraph app["Spring Boot application"]
        direction TB
        controller["<b>LedgerController</b><br/>POST /api/entries"]
        advice["<b>GlobalExceptionHandler</b><br/>IllegalArgumentException<br/>MethodArgumentNotValidException<br/>MissingRequestHeaderException → 400 ApiError<br/>IdempotencyConflictException → 409 ApiError"]
        service["<b>LedgerService</b><br/>postEntry · postEntryIdempotent<br/>@Transactional"]
        hasher["<b>RequestHasher</b><br/>SHA-256 over description + legs"]
        repos["<b>Spring Data JPA repositories</b><br/>Account · JournalEntry · Posting<br/>IdempotencyKey — insertIfAbsent"]
    end

    subgraph db["PostgreSQL 16"]
        direction TB
        tables[("accounts · journal_entries<br/>postings · idempotency_keys")]

        subgraph enforcement["Invariant enforcement — database level"]
            direction TB
            trg1["CONSTRAINT TRIGGER postings_balanced<br/>DEFERRABLE INITIALLY DEFERRED<br/>sum of amount per entry = 0"]
            trg2["TRIGGER postings_entry_id_immutable<br/>BEFORE UPDATE OF entry_id"]
            uniq["UNIQUE idempotency_key<br/>+ ON CONFLICT DO NOTHING"]
        end
    end

    client -->|" JSON + Idempotency-Key "| controller
    controller --> service
    controller -.->|" throws "| advice
    service --> hasher
    service --> repos
    repos -->|" JDBC "| tables
    tables -.->|" every write checked by<br/>RAISE EXCEPTION on violation "| enforcement
    classDef dbLayer fill: #e8f0fe, stroke: #3b6ea5, color: #12243a
    classDef guard fill: #fdecea, stroke: #c0392b, color: #3a1210
    class tables dbLayer
class trg1, trg2, uniq guard
```

The trigger and constraint layer sits below the application: the sum-to-zero
check, the `entry_id` immutability check, and the idempotency-key uniqueness
check are all enforced by PostgreSQL, not by Java.

## Invariants

These guarantees live in PostgreSQL rather than in application code. Java validation only covers the path it runs on a
raw SQL fix, an admin script, or a future service writing directly all bypass it, while a database constraint holds no
matter how the write arrives. LedgerService still validates first, so honest callers get a clear 400 instead of a
constraint violation, but the database is what makes corruption impossible rather than merely unlikely.

| Invariant                                   | Enforced by                  | Mechanism                                                                                                                                                                                                                                                                                                                           |
|---------------------------------------------|------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Postings within a journal entry sum to zero | PostgreSQL                   | Deferred constraint trigger `postings_balanced` (`AFTER INSERT OR UPDATE OR DELETE ON postings`, `DEFERRABLE INITIALLY DEFERRED`, `FOR EACH ROW`) calling `check_postings_balanced()`, which sums `amount` for the entry and raises if the total is non-zero. Deferral means the check runs at commit, after all legs are inserted. |
| A posting's `entry_id` cannot change        | PostgreSQL                   | `BEFORE UPDATE OF entry_id ON postings` trigger `postings_entry_id_immutable` calling `freeze_entry_id()`, which raises when `NEW.entry_id IS DISTINCT FROM OLD.entry_id`.                                                                                                                                                          |
| One journal entry per idempotency key       | PostgreSQL + `LedgerService` | `UNIQUE` constraint on `idempotency_keys.idempotency_key`, claimed via `INSERT … ON CONFLICT (idempotency_key) DO NOTHING` in `IdempotencyKeyRepository.insertIfAbsent`. A return of `1` means this caller claimed the key and creates the entry; `0` means the key existed and the stored entry is replayed.                       |

`LedgerService.postEntry` also validates in Java before touching the database:
at least two legs, and legs summing to zero. Both failures throw
`IllegalArgumentException`.

## API

The ledger API is a single endpoint: `POST /api/entries`. The `Idempotency-Key`
header is required. (Actuator additionally exposes `/actuator/health` and
`/actuator/info`.)

### Request

```json
{
  "description": "August rent",
  "legs": [
    {
      "accountId": 3,
      "amount": -150000
    },
    {
      "accountId": 4,
      "amount": 150000
    }
  ]
}
```

`description` must be non-blank; `legs` must contain at least two entries, each
with a non-null `accountId` and `amount`. Amounts are `BIGINT` and must sum to
zero across the legs.

Accounts must already exist — there is no account-creation endpoint. Insert them
directly:

```bash
docker compose exec postgres psql -U ledger_user -d ledger \
  -c "INSERT INTO accounts (name) VALUES ('alice'), ('bob') RETURNING id, name;"
```

### 201 — entry created

First request with a given key:

```bash
curl -i -X POST localhost:8080/api/entries \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: rent-2026-08-01" \
  -d '{"description":"August rent","legs":[{"accountId":3,"amount":-150000},{"accountId":4,"amount":150000}]}'
```

```http
HTTP/1.1 201
Content-Type: application/json

{"id":2,"description":"August rent"}
```

### 200 — identical retry replays the original entry

Same key, same payload. No new entry is created; the stored one is returned.

```bash
curl -i -X POST localhost:8080/api/entries \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: rent-2026-08-01" \
  -d '{"description":"August rent","legs":[{"accountId":3,"amount":-150000},{"accountId":4,"amount":150000}]}'
```

```http
HTTP/1.1 200
Content-Type: application/json

{"id":2,"description":"August rent"}
```

The `id` is identical to the 201 response.

### 409 — key reused with a different payload

Same key, different legs. The stored SHA-256 request hash does not match.

```bash
curl -i -X POST localhost:8080/api/entries \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: rent-2026-08-01" \
  -d '{"description":"August rent","legs":[{"accountId":3,"amount":-99999},{"accountId":4,"amount":99999}]}'
```

```http
HTTP/1.1 409
Content-Type: application/json

{"code":"IDEMPOTENCY_CONFLICT","message":"Idempotency key rent-2026-08-01 was already used with a different request payload"}
```

`409 IDEMPOTENCY_CONFLICT` is also returned when a request with the same key is
still in flight — the key row exists but its `entry_id` has not been set yet.

### 400 — validation failure

Every 400 returns the same `ApiError` body — `{"code": "INVALID_REQUEST",
"message": …}` — whether the request was rejected by bean validation before the
controller method ran or by `LedgerService` inside it. `GlobalExceptionHandler`
maps `MethodArgumentNotValidException`, `MissingRequestHeaderException`, and
`IllegalArgumentException` to that one shape.

```bash
curl -i -X POST localhost:8080/api/entries \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: unbalanced-demo-1" \
  -d '{"description":"bad","legs":[{"accountId":3,"amount":-1000},{"accountId":4,"amount":999}]}'
```

```http
HTTP/1.1 400
Content-Type: application/json

{"code":"INVALID_REQUEST","message":"Total must equal 0, got -1"}
```

The `message` varies by what was rejected:

| Request problem                  | Rejected by      | `message`                                      |
|----------------------------------|------------------|------------------------------------------------|
| Legs do not sum to zero          | `LedgerService`  | `Total must equal 0, got -1`                   |
| Unknown `accountId`              | `LedgerService`  | `no account 99999`                             |
| Fewer than two legs              | `@Size(min = 2)` | `legs: size must be between 2 and 2147483647`  |
| Blank `description`              | `@NotBlank`      | `description: must not be blank`               |
| Null `amount` in a leg           | `@NotNull`       | `legs[0].amount: must not be null`             |
| Missing `Idempotency-Key` header | `@RequestHeader` | `Required header 'Idempotency-Key' is missing` |

Multiple bean-validation violations are joined with `; ` and sorted by field
name, so the message is deterministic for a given set of violations:

```bash
curl -i -X POST localhost:8080/api/entries \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: invalid-demo-1" \
  -d '{"description":"","legs":[{"accountId":3,"amount":-1000}]}'
```

```http
HTTP/1.1 400
Content-Type: application/json

{"code":"INVALID_REQUEST","message":"description: must not be blank; legs: size must be between 2 and 2147483647"}
```

## Running it

```bash
docker compose up -d      # start Postgres 16 on :5432
./mvnw spring-boot:run    # start the app on :8080
./mvnw verify             # full build + test suite
```

Health check:

```bash
curl localhost:8080/actuator/health
```

Actuator exposes `health` and `info` over HTTP. Database connection settings
default to the `docker-compose.yml` values and can be overridden with
`POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD`.

## Testing

`./mvnw verify` runs four test classes, seven tests. Every class starts a
throwaway Postgres 16 container via Testcontainers (`@ServiceConnection`), so
Docker is the only prerequisite: no local database setup, and the CI workflow
declares no service container — it just runs `./mvnw --batch-mode verify`.

| Test class                   | Verifies                                                                                                                                                                                                             |
|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `LedgerCoreApplicationTests` | The Spring context loads against a real Postgres container, which exercises Flyway migration and `ddl-auto: validate` entity mapping.                                                                                |
| `LedgerServiceTest`          | A balanced entry persists with all its postings and the amounts sum to zero; legs not summing to zero are rejected with `IllegalArgumentException`; fewer than two legs is rejected with `IllegalArgumentException`. |
| `LedgerControllerTest`       | End-to-end through MockMvc: a balanced entry returns `201`; an unbalanced entry returns `400` with `code` = `INVALID_REQUEST`.                                                                                       |
| `IdempotencyConcurrencyTest` | Idempotency under concurrent duplicate requests (below).                                                                                                                                                             |

### Concurrency test

`IdempotencyConcurrencyTest.exactlyOneEntryIsCreatedFor50ConcurrentIdenticalRequests`
submits 50 tasks to a fixed thread pool of 50, each calling
`postEntryIdempotent` with the same idempotency key and the same payload. All 50
threads park on a `CountDownLatch` start gate and are released by a single
`countDown()`, so the calls contend rather than trickling; a second
`CountDownLatch` counts each task down in a `finally` block and the main thread
awaits it with a 60-second timeout. Successful `PostResult`s and thrown
exceptions are collected separately in `CopyOnWriteArrayList`s.

It asserts one row in `journal_entries`, two in `postings`, one in
`idempotency_keys`, and exactly one result with `created() == true`.

Observed on a local run: **50 successful results — 1 created, 49 replayed, 0 failures.**

The test establishes that duplicate requests to a single instance produce exactly one entry. It doesn't prove
correctness in general. Fifty threads share a connection pool that maxes at ten, so contention is real but bounded, and
the winning transaction is short, a few inserts. Two cases are untested: duplicates arriving at different application
instances behind a load balancer, and a slow or stalled winner leaving the blocked duplicates waiting until they time
out or exhaust the pool. The first should hold, since the guarantee is a database constraint rather than an in-process
lock and every instance shares the same database; the second is a real operational concern that this test doesn't
exercise.

## Design decisions

### Money representation

Amounts are signed BIGINT in minor units. -150000 is 150,000 cents, or −$1,500.00. Negative means money leaving an
account, positive means money arriving.

Floating-point types are unusable here. Binary floating point can't represent decimal fractions exactly, so errors
accumulate and a balance check on genuinely balanced entries can fail. NUMERIC would have been exact and is a reasonable
alternative; plenty of ledgers use it. Integers won on simplicity: the sum-to-zero check is literal integer addition,
with no scale or rounding mode to configure, and minor units are how payment systems represent money on the wire.

The cost is that every amount in the system is in cents, so anything displaying money divides by 100. Formatting is a
presentation-layer concern. Multi-currency would need rework, since minor units differ by currency.

### Balance enforcement

The rule is that every journal entry's postings sum to zero. A CHECK constraint can't express it: a CHECK sees only the
row being written and can't query the entry's other postings. A trigger can, but a per-row trigger firing on every
insert would reject valid work, because a multi-leg entry is legitimately unbalanced between legs. After the first
posting of a three-leg entry, the total is whatever that leg was.

So the check is a deferred constraint trigger: DEFERRABLE INITIALLY DEFERRED, evaluated at commit against the
transaction's final state. Intermediate imbalance is allowed; a transaction that ends unbalanced is rejected.

It fires on INSERT, UPDATE, and DELETE. DELETE matters, without it, removing one leg of a balanced entry would silently
create money. On DELETE there is no NEW row, so the trigger resolves the entry via COALESCE(NEW.entry_id, OLD.entry_id).

The trade-off is that violations surface at commit rather than at the offending statement, so callers can't attribute
the failure to a specific write. LedgerService validates in Java first for that reason, and the trigger is the backstop.

### Idempotency

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
is an honest retry 200 with the original entry, nothing created. A different hash means the key was reused for different
content, which is a client error: 409. Without the hash, that case would return 200 and the original entry, and the
client would believe a different transfer had succeeded not duplicated money, but misattributed money.

There is one narrow window: a duplicate that reads the key row before the winner has linked its entry. That returns 409
with a distinct message. In the 50-thread concurrency test it never fired, Postgres blocks the second inserter on the
conflicting tuple until the winner commits, so all 49 duplicates saw a linked entry and replayed cleanly.

## Project structure

```
src/main/java/dev/jonathan/ledgercore/
├── LedgerCoreApplication.java
├── controller/
├── domain/
├── repository/
└── service/

src/main/resources/
├── application.yml
└── db/migration/
```

| Package                  | Contents                                                                                                                                                                                                                                                                                                                                                                                                        |
|--------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `controller`             | `LedgerController` (`POST /api/entries`), the request/response DTOs `CreateEntryRequest` (with nested `LegLine`, Jakarta Validation annotated) and `CreateEntryResponse`, the `ApiError` error body, and `GlobalExceptionHandler` mapping `IllegalArgumentException`, `MethodArgumentNotValidException`, and `MissingRequestHeaderException` → 400 and `IdempotencyConflictException` → 409, all as `ApiError`. |
| `domain`                 | JPA entities mapped to the existing Flyway-managed tables: `Account`, `JournalEntry`, `Posting`, `IdempotencyKey`. All use `@GeneratedValue(strategy = IDENTITY)` against `GENERATED ALWAYS AS IDENTITY` columns.                                                                                                                                                                                               |
| `repository`             | Spring Data JPA repositories: `AccountRepository`, `JournalEntryRepository`, `PostingRepository`, and `IdempotencyKeyRepository` — the last adding `findByIdempotencyKey` and the native `insertIfAbsent` upsert.                                                                                                                                                                                               |
| `service`                | `LedgerService` (`postEntry`, `postEntryIdempotent`, both `@Transactional`), the `LegRequest` and `PostResult` records, `RequestHasher` (SHA-256 hex over description and legs), and `IdempotencyConflictException`.                                                                                                                                                                                            |
| `resources/db/migration` | Flyway SQL migrations, applied automatically at startup.                                                                                                                                                                                                                                                                                                                                                        |

## Schema history

| Migration                                   | What it did                                                                                                                                                                                                                                        |
|---------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `V1__create_ledger_tables.sql`              | Created `accounts`, `journal_entries`, and `postings`. Postings carry `entry_id` and `account_id` foreign keys and a `BIGINT amount`. No balance column anywhere — balances are derived from postings.                                             |
| `V2__enforce_balanced_entries.sql`          | Added the `check_postings_balanced()` plpgsql function and the `postings_balanced` constraint trigger — `AFTER INSERT OR UPDATE OR DELETE`, `DEFERRABLE INITIALLY DEFERRED`, `FOR EACH ROW` — raising when an entry's postings do not sum to zero. |
| `V3__forbid_posting_entry_reassignment.sql` | Added the `freeze_entry_id()` function and the `postings_entry_id_immutable` trigger (`BEFORE UPDATE OF entry_id`), raising when a posting's `entry_id` would change.                                                                              |
| `V4__add_idempotency_keys.sql`              | Created `idempotency_keys` with a `UNIQUE NOT NULL idempotency_key`, a nullable `entry_id` foreign key to `journal_entries`, `created_at`, and a `CHAR(64) request_hash`.                                                                          |
| `V5__change_request_hash_to_varchar.sql`    | Changed `request_hash` from `CHAR(64)` to `VARCHAR(64)`.                                                                                                                                                                                           |

## Deliberately not built

A funding flow. Money has no way into the system. Every entry sums to zero, so with only customer accounts there's no
path to an opening balance. Real ledgers solve this with an external or equity account that goes negative as customer
accounts go positive, keeping the whole system at zero. It's the most significant missing piece.

Idempotency key expiry. Keys persist indefinitely. Stripe expires them after 24 hours, which makes sense at their
volume; at this scale a cleanup job would be machinery serving a problem that doesn't exist. The key has to outlive the
operation it protects, since a retry usually arrives because the first attempt succeeded and the response was lost, so
deleting on completion is the one thing that can't be done.

Webhook delivery, observability, and reconciliation. Planned as later milestones and consciously cut. The ledger and
idempotency work carries the design story; further milestones would have added volume without adding much that a reader
learns something new from.

Known enforcement boundaries. The balance trigger is a row-level trigger, so TRUNCATE bypasses it, and replication or a
restore running with triggers disabled can insert rows without checks. An entry with no postings, or a single posting of
zero, satisfies sum-to-zero and is considered valid. These are documented in
docs/notes/constraint-trigger-limitations.md.

Authentication, a UI, and multi-currency. Out of scope from the start. None of them would change what the project
demonstrates.
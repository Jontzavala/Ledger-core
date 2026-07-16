# ledger-core

[![CI](https://github.com/Jontzavala/Ledger-core/actions/workflows/ci.yml/badge.svg)](https://github.com/Jontzavala/Ledger-core/actions/workflows/ci.yml)

A Spring Boot 4.1 / Java 21 ledger service backed by PostgreSQL.

## Running locally

```bash
docker compose up -d      # start Postgres 16
./mvnw spring-boot:run    # start the app on :8080
```

Health check: `curl localhost:8080/actuator/health`

## Tests

```bash
./mvnw verify
```

Integration tests use [Testcontainers](https://testcontainers.com/) — a throwaway
Postgres 16 container is started automatically; only Docker is required.

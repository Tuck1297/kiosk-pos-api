# Kiosk POS API

[![CI](https://github.com/Tuck1297/kiosk-pos-api/actions/workflows/ci.yml/badge.svg)](https://github.com/Tuck1297/kiosk-pos-api/actions/workflows/ci.yml)

Order and payment API for a theme-park quick-service kiosk, used by a guest mobile app and staff terminals.
Java 21, Spring Boot 4, PostgreSQL 16.

The API is **contract-first**: [`openapi.yaml`](openapi.yaml) is the source of truth. At build time the
OpenAPI Generator turns it into Spring controller interfaces and DTOs, and the controllers in
`web/` implement those interfaces. A change to the contract that the code doesn't honor fails the build.

## Quick start

Requires Java 21, Maven 3.9+ and Docker.

```bash
docker compose up -d                                   # Postgres 16 on localhost:5432
mvn spring-boot:run -Dspring-boot.run.profiles=local  # migrates schema + loads demo data
curl localhost:8080/v1/health
curl localhost:8080/v1/locations
```

Run the tests (starts its own Postgres via Testcontainers):

```bash
mvn verify
```

## Endpoints (v1)

| Method | Path | Who | Status |
|---|---|---|---|
| GET | `/health` | public | ✅ |
| GET | `/locations` | any | ✅ cursor-paged |
| GET | `/locations/{id}` | any | ✅ |
| GET | `/locations/{id}/menu-items` | any | 🚧 |
| POST | `/locations/{id}/menu-items` | staff | 🚧 |
| PATCH | `/menu-items/{id}` | staff | 🚧 |
| POST | `/orders` | any | 🚧 |
| GET | `/orders?locationId=&status=` | any | 🚧 |
| GET | `/orders/{id}` | any | 🚧 |
| PUT | `/orders/{id}/status` | staff | 🚧 |
| POST | `/orders/{id}/cancel` | any | 🚧 |
| POST | `/orders/{id}/payments` | any | 🚧 |
| GET | `/orders/{id}/payments` | any | 🚧 |

🚧 endpoints are already routed from the contract and return `501 Not Implemented` until built.

## Design decisions

- **Money** is `BIGINT` cents everywhere; tax is computed once on the subtotal, rounded half-up.
- **Price snapshots** on `order_line`, so menu changes never alter existing orders.
- **Idempotency keys** on every create or charge. A partial unique index
  (`uq_payment_one_captured_per_order`) backs this up so a double charge is impossible even if that layer fails.
- **Optimistic locking**: `customer_order.version` is exposed as `ETag` and required in `If-Match`.
- **Errors** are RFC 9457 `application/problem+json` with a stable `code` field clients can switch on.
- **Integrity in the database**, not just the service: totals, line math, cash change, status values and
  decline codes are all `CHECK` constraints.

## Project layout

```
openapi.yaml                       API contract (source of truth)
src/main/java/.../kioskpos/
  web/          controllers implementing the generated *Api interfaces (served under /v1)
  location/     JPA entity + repository
  common/       errors (problem+json), pagination cursor
  config/       /v1 path prefix
src/main/resources/db/migration/   Flyway schema (V1__init.sql)
src/main/resources/db/seed/        demo data, local profile only
target/generated-sources/openapi/  generated interfaces + DTOs (not committed)
```

## Roadmap

1. Menu endpoints
2. `POST /orders` with pricing and tax, then the status state machine
3. Payments with a mock card processor, then the idempotency layer
4. JWT auth (`GUEST_APP` / `STAFF` roles)
5. PHP client SDK
6. CI: SBOM (CycloneDX) and open-source license checks

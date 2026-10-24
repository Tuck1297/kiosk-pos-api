# Kiosk POS API

[![CI](https://github.com/Tuck1297/kiosk-pos-api/actions/workflows/ci.yml/badge.svg)](https://github.com/Tuck1297/kiosk-pos-api/actions/workflows/ci.yml)

Order and payment API for a theme-park quick-service kiosk, plus the cashier register that uses it.
Java 21, Spring Boot 4, PostgreSQL 16; register UI in React 19 and Mantine 9.

The API is **contract-first**: [`openapi.yaml`](openapi.yaml) is the source of truth. At build time it is
turned into Spring controller interfaces and DTOs (which the controllers implement) **and** into the
register's TypeScript types. A contract change that either side doesn't honor fails CI.

## Quick start

Requires Java 21, Maven 3.9+, Node 22 and Docker.

```bash
docker compose up -d                                   # Postgres 16 on localhost:5432
mvn spring-boot:run -Dspring-boot.run.profiles=local  # migrates schema + loads demo data

cd web && npm install && npm run dev                  # register on http://localhost:5173
```

The register proxies `/v1` to the API on `:8080`. In the demo card reader, the 4242 card approves
and the 0002 card declines.

Run the API tests (they start their own Postgres via Testcontainers):

```bash
mvn verify
```

## The register (`web/`)

A guided checkout for the cashier at the window: **Items → Review → Payment**.

- **The UI never computes money.** It sends menu item ids and quantities; every price, line total,
  tax amount, total and cash change it shows comes from the API (`POST /orders/quote`,
  `POST /orders`, `POST /orders/{id}/payments`).
- **Cash**: one-tap amounts (Exact, next dollar, next $5/$10/$20, bills); the server checks the
  amount and returns the change due.
- **Card**: a declined card keeps the order open with a clear message; the cashier retries or
  switches to cash.
- **Safe retries**: each attempt carries an `Idempotency-Key`, reused only after a network failure,
  so tapping again can't create a second order or charge twice.

## Endpoints (v1)

| Method | Path | Status |
|---|---|---|
| GET | `/health` | ✅ |
| GET | `/locations`, `/locations/{id}` | ✅ includes the location's tax rate |
| GET | `/locations/{id}/menu-items` | ✅ filter by `available`, `category` |
| POST | `/orders/quote` | ✅ server-side pricing, nothing stored |
| POST | `/orders` | ✅ `Idempotency-Key`; snapshots prices and tax rate |
| GET | `/orders/{id}` | ✅ returns `ETag` |
| POST | `/orders/{id}/payments` | ✅ `Idempotency-Key` + `If-Match`; cash change, mock card |
| GET | `/orders/{id}/payments` | ✅ every attempt, including declines |
| POST | `/locations/{id}/menu-items` | 🚧 |
| PATCH | `/menu-items/{id}` | 🚧 |
| GET | `/orders?locationId=&status=` | 🚧 |
| PUT | `/orders/{id}/status` | 🚧 |
| POST | `/orders/{id}/cancel` | 🚧 |

🚧 endpoints are already routed from the contract and return `501 Not Implemented` until built.

## Design decisions

- **One pricing path.** `PricingService` is the only code that computes prices and tax; the quote and
  order endpoints both call it, so a quote can never disagree with the order it becomes.
- **Tax rates in their own table** (`tax_rate`, in basis points: 650 = 6.50%), shared by every location
  in a jurisdiction. Each order snapshots the rate it was priced with, so changing a rate never alters
  past orders.
- **Money** is integer cents everywhere (`BIGINT` in Postgres, `long` in Java); tax rounds half-up.
- **Price snapshots** on `order_line`, so menu changes never alter existing orders.
- **Idempotency keys** on create and pay, claimed in the same transaction as the work. A concurrent retry
  waits for the first request and then replays its result. A partial unique index
  (`uq_payment_one_captured_per_order`) makes a double charge impossible even if that layer fails.
- **Optimistic locking**: `customer_order.version` is exposed as `ETag` and required in `If-Match`.
- **Errors** are RFC 9457 `application/problem+json` with a stable `code` field clients can switch on.
- **Integrity in the database**, not just the service: totals, line math, cash change, status values and
  decline codes are all `CHECK` constraints.

## Project layout

```
openapi.yaml                       API contract (source of truth)
src/main/java/.../kioskpos/
  web/          controllers implementing the generated *Api interfaces (served under /v1)
  pricing/      PricingService: prices, line totals, tax, totals
  order/        orders + order lines, create with idempotency
  payment/      payments, mock card processor, cash change
  idempotency/  Idempotency-Key store
  tax/          tax rates, TaxCalculator
  location/ menu/  JPA entities + repositories
  common/       errors (problem+json), mappers, pagination cursor
src/main/resources/db/migration/   Flyway schema (V1 init, V2 tax rates, V3 idempotency)
src/main/resources/db/seed/        demo data, local profile only
web/                               cashier register (React + Mantine, Vite)
```

## Roadmap

1. Cancel/void and the kitchen status flow (`PUT /orders/{id}/status`)
2. JWT auth (`GUEST_APP` / `STAFF` roles)
3. Expire old idempotency keys on a schedule
4. PHP client SDK
5. CI: SBOM (CycloneDX) and open-source license checks

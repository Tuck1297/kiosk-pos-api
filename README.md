# Kiosk POS API

[![CI](https://github.com/Tuck1297/kiosk-pos-api/actions/workflows/ci.yml/badge.svg)](https://github.com/Tuck1297/kiosk-pos-api/actions/workflows/ci.yml)

Order and payment API for a theme-park quick-service kiosk, plus the cashier register that uses it.
Java 21, Spring Boot 4, PostgreSQL 16; register UI in React 19 and Mantine 9.

The API is **contract-first**: [`openapi.yaml`](openapi.yaml) is the source of truth. At build time it is
turned into Spring controller interfaces and DTOs (which the controllers implement) **and** into the
register's TypeScript types. A contract change that either side doesn't honor fails CI.

## Quick start

Requires only Docker.

```bash
docker compose up -d --build
```

Open **http://localhost:8080** for the register; the API is at `http://localhost:8080/v1`. This starts
everything: Postgres, PgBouncer, Redis, 3 API replicas and an nginx gateway that serves the register and
load-balances the API. Demo data loads automatically: the Lakeside Snack Stand with a 20-item menu
(entrées, snacks, desserts, drinks; the churro is sold out to show that state), and these staff:

| Employee ID | PIN | Role |
|---|---|---|
| `C2001` | `1111` | Cashier (sign in with this) |
| `M1001` | `2468` | Manager (approves comps) |
| `M1002` | `1357` | Manager |

Sign in as `C2001` / `1111` and the register is ready to take orders.

**Discounts** (one per order, entered on the Review step):

| Enter | What it does |
|---|---|
| Code `PARKFAN10` | 10% off |
| Code `WELCOME5` | $5.00 off orders of $20.00 or more |
| Code `LIMITED50` | 50% off, first 3 orders only |
| Code `SUMMER25` | expired, to show the error |
| Employee ID `C2001`, `M1001`, `M1002` | employee discount, 20% off |

**Cards**: on the Payment step, pick Tap, Insert or Swipe and tap a card in the demo wallet (Visa 4242,
Mastercard 4444 and Amex 0005 approve; the 0002 and 9995 cards decline), or key in a number:
`4242 4242 4242 4242` approves, `4000 0000 0000 0002` declines, `4000 0000 0000 9995` has insufficient
funds; any future expiry and CVC.

```bash
docker compose up -d --scale api=6                 # more API replicas
docker compose --profile loadtest run --rm k6      # 500 simulated registers for 3 minutes
docker compose down -v                             # stop and delete data
```

How it handles 500 registers, and why writes stay on one Postgres primary: [docs/SCALING.md](docs/SCALING.md).

### Developing without containers for the app

Requires Java 21, Maven 3.9+ and Node 22.

```bash
docker compose up -d postgres redis                   # just the infrastructure
mvn spring-boot:run -Dspring-boot.run.profiles=local  # API on :8080, migrates + loads demo data
cd web && npm install && npm run dev                  # register on :5173, proxies /v1 to :8080
mvn verify                                            # tests (start their own Postgres + Redis)
```

## The register (`web/`)

A guided checkout for the cashier at the window: **Sign in → Items → Review → Payment**.

- **Sign-in** with employee ID + PIN. The session is an `HttpOnly`, `SameSite=Strict` cookie the page's
  JavaScript can't read; if it expires mid-shift, the register returns to sign-in.
- **Discounts**: a promo code or an employee discount by employee ID. The server checks the code's
  dates, minimum order and redemption limit (or that the employee ID is active), works out the amount
  and taxes what's left. If an edit drops the order below a code's minimum, the discount comes off and the
  register says why.
- **Comps** with manager approval: comp one item or the whole order, with a reason. A manager enters
  their own ID + PIN on the cashier's register; the server re-prices the order and records who approved
  it. An order comped to $0.00 closes without payment.
- **The UI never computes money.** It sends menu item ids and quantities; every price, line total,
  tax amount, total and cash change it shows comes from the API (`POST /orders/quote`,
  `POST /orders`, `POST /orders/{id}/payments`).
- **Cash**: one-tap amounts (Exact, next dollar, next $5/$10/$20, bills); the server checks the
  amount and returns the change due.
- **Card**: an on-screen reader goes waiting → reading → authorizing → approved/declined, like the
  terminal on the counter. Keyed cards are checked (Luhn, expiry, CVC length, brand) and tokenized in the
  browser, so **a card number never reaches the API**; only a token does, the way a real terminal or a
  processor's hosted fields work. A decline keeps the order open; the cashier tries another card or cash.
- **Safe retries**: each attempt carries an `Idempotency-Key`, reused only after a network failure,
  so tapping again can't create a second order or charge twice.

## Endpoints (v1)

Every endpoint except `/health` and `/auth/login` needs the session cookie, and every change request
needs the `X-Kiosk-Register: 1` header (CSRF protection).

| Method | Path | Status |
|---|---|---|
| GET | `/health` | ✅ public |
| POST | `/auth/login` | ✅ sets the HttpOnly session cookie; 5 wrong PINs lock for 15 min |
| POST | `/auth/logout`, GET `/auth/me` | ✅ |
| GET | `/locations`, `/locations/{id}` | ✅ includes the location's tax rate |
| GET | `/locations/{id}/menu-items` | ✅ filter by `available`, `category` |
| POST | `/orders/quote` | ✅ server-side pricing incl. discount, nothing stored |
| POST | `/orders` | ✅ `Idempotency-Key`; snapshots prices, discount and tax rate |
| GET | `/orders/{id}` | ✅ returns `ETag` |
| POST | `/orders/{id}/comps` | ✅ manager ID + PIN; item or whole order; tax re-computed |
| POST | `/orders/{id}/payments` | ✅ `Idempotency-Key` + `If-Match`; cash change, mock card |
| GET | `/orders/{id}/payments` | ✅ every attempt, including declines |
| POST | `/locations/{id}/menu-items` | 🚧 |
| PATCH | `/menu-items/{id}` | 🚧 |
| GET | `/orders?locationId=&status=` | 🚧 |
| PUT | `/orders/{id}/status` | 🚧 |
| POST | `/orders/{id}/cancel` | 🚧 |

🚧 endpoints are already routed from the contract and return `501 Not Implemented` until built.

## Design decisions

- **Request guards.** Every request body is checked before any controller or service code runs: first
  against the OpenAPI schema (types, required fields, patterns), then by a `RequestGuard` for its type
  (`guard/`) for rules that span fields, such as "card payments need a card token and no cash". Guards
  plug into Spring's binding, so adding one is just adding a bean; failures come back as
  `400 VALIDATION_FAILED` listing every bad field.
- **Stateless cookie sessions.** The `kiosk_session` cookie is signed with HMAC-SHA256 by a key every
  replica shares, so any replica verifies it without a database or Redis lookup. `HttpOnly` keeps it away
  from JavaScript, `SameSite=Strict` plus the required `X-Kiosk-Register` header stop cross-site request
  forgery. Trade-off: a single session can't be revoked before its 12-hour expiry; rotating the key signs
  everyone out.
- **PINs** are bcrypt hashes. The slow comparison runs without holding a row lock, and failed attempts are
  counted with one atomic `UPDATE`, so 500 registers signing in at shift change never queue on a lock and
  parallel wrong guesses are all counted.
- **Discounts before tax, comps after discounts.** Tax is charged on subtotal − discount − comps. The
  order keeps the discount's label and rate as a snapshot, so editing or expiring a code never changes
  past orders. Capped codes count redemptions with one conditional `UPDATE ... WHERE times_redeemed <
  max_redemptions`, so two registers can't both take the last use; uncapped codes write nothing extra,
  keeping the hot path free of a shared counter row.
- **Comps are audit records.** `order_comp` rows are insert-only, with the approving manager, the
  signed-in staff member who asked, reason and amount. The database allows one comp per item and one
  whole-order comp per order, and checks `total = subtotal - discount - comps + tax`.
- **One pricing path.** `PricingService` is the only code that computes prices and tax; the quote and
  order endpoints both call it, so a quote can never disagree with the order it becomes.
- **Cached reads, authoritative writes.** Menu, location and tax snapshots are cached in Redis for
  quotes and menu loads; creating an order re-reads them from Postgres, so a stale cache can't affect a
  charge. If Redis is down, reads fall back to Postgres.
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
  guard/        RequestGuards: cross-field request rules, run before controllers
  auth/         session cookie signing, auth + CSRF filter, current staff
  staff/        cashiers and managers, PIN checks with lockout
  comp/         manager-approved comps
  discount/     promo codes and the employee discount
  pricing/      PricingService: prices, line totals, tax, totals
  order/        orders + order lines, create with idempotency
  payment/      payments, mock card processor, cash change
  idempotency/  Idempotency-Key store
  tax/          tax rates, TaxCalculator
  location/ menu/  JPA entities + repositories
  common/       errors (problem+json), mappers, pagination cursor
src/main/resources/db/migration/   Flyway schema (V1 init … V4 staff + comps, V5 discounts)
src/main/resources/db/seed/        demo data, local profile only
web/                               cashier register (React + Mantine, Vite) + gateway image (nginx)
compose.yaml, Dockerfile           the whole stack in one file
deploy/pgbouncer/                  connection pooler config
load/checkout.js                   k6 load test: 500 registers doing full sales
docs/SCALING.md                    capacity math and scaling design
```

## Roadmap

1. Cancel/void and the kitchen status flow (`PUT /orders/{id}/status`)
2. Comp and void reports per manager (the `ix_comp_approver` index is in place for it)
3. PHP client SDK
4. CI: SBOM (CycloneDX) and open-source license checks
5. Scaling next steps (read replica, Redis HA, partitioning): see [docs/SCALING.md](docs/SCALING.md)

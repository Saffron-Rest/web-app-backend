# Saffron Storefront API

Public-facing Spring Boot service that powers the **`web-app-frontend`** storefront.
Separate from `platform-backend` (operations console) on purpose — different audience,
different security boundary, different database schema.

## What it does

### Public storefront API (anonymous)

| Endpoint                              | Description                                                          |
| ------------------------------------- | -------------------------------------------------------------------- |
| `GET  /api/catalog?mode=instant\|courier\|` | Active products grouped by category (per delivery mode if requested). |
| `GET  /api/catalog/{slug}`            | Product detail by slug.                                              |
| `POST /api/quotes/instant`            | Compare Wolt Drive vs Glovo Courier (Poland on-demand).              |
| `POST /api/quotes/courier`            | Compare DHL Express vs DPD Polska (worldwide / EU).                  |
| `POST /api/orders`                    | Create an order (guest checkout).                                    |
| `GET  /api/orders/{reference}`        | Read an order back by short reference.                               |
| `POST /api/reservations`              | Submit a table reservation request.                                  |
| `GET  /api/uploads/**`                | Serve admin-uploaded product images (public).                        |

### Admin API (JWT bearer token)

All `/api/admin/**` routes require an `Authorization: Bearer <token>` header
obtained via `POST /api/auth/login`. See [Admin section](#admin) below for the
full surface (products CRUD, orders, reservations, customers, users, audit,
dashboard).

## Delivery quote architecture

`QuoteProvider` is a small interface with four concrete adapters:

- `WoltDriveQuoteProvider` — `POST /v1/venues/{venue_id}/delivery-fee`
- `GlovoCourierQuoteProvider` — `POST /v1/laas/parcels/estimate`
- `DhlExpressQuoteProvider` — `POST /rates` (Basic auth)
- `DpdPolskaQuoteProvider` — SOAP (TODO live wiring)

Each adapter behaves like this:

1. If its `*.enabled` flag is `false` **or** credentials are missing → return a
   deterministic distance-based mock quote (`MockQuote`).
2. If credentials are present → call the upstream API with an 8 s timeout. Any
   error or timeout falls back to the same mock and logs a warning, so the
   storefront never breaks.

`QuoteService` fans out to every provider whose `mode()` matches the request, in
parallel via Reactor. The customer always sees both prices in the storefront —
the cheaper one is highlighted but they can pick either.

## Running locally

```bash
# Create the storefront database (separate from the cashflow database):
psql -U saffron -d postgres -c "CREATE DATABASE storefront OWNER saffron;"

# Start the API
cd web-app-backend
mvn spring-boot:run

# (in another shell) start the storefront UI
cd ../web-app-frontend
npm install
npm run dev
# → http://localhost:5174
```

Vite proxies `/api/*` → `http://localhost:3002`, so no CORS during dev.

The seeder inserts a small Azerbaijani menu (płow, dolma, lula kebab, paxlava,
herbata, dżem z pigwy, szafran) on first boot, so the storefront has content out
of the box.

## Wiring real carrier credentials

All adapters are env-keyed. Set these on the running container:

```
# Wolt Drive (Poland — get from your Wolt merchant account manager)
WOLT_DRIVE_ENABLED=true
WOLT_DRIVE_MERCHANT_ID=<venue-id>
WOLT_DRIVE_API_KEY=<bearer-token>

# Glovo Courier / Glovo Express (Poland)
GLOVO_COURIER_ENABLED=true
GLOVO_COURIER_API_KEY=<bearer-token>

# DHL Express (worldwide — from DHL MyDHL API portal)
DHL_EXPRESS_ENABLED=true
DHL_EXPRESS_API_KEY=<basic-user>
DHL_EXPRESS_API_SECRET=<basic-pass>
DHL_EXPRESS_ACCOUNT=<account-number>

# DPD Polska (PL + EU)
DPD_PL_ENABLED=true
DPD_PL_LOGIN=<login>
DPD_PL_PASSWORD=<password>
DPD_PL_FID=<fid>
```

When `*_ENABLED=true` and the required credentials are non-blank, the adapter
flips from `mock` to `live` quotes. You can see which mode each quote came from
in the response (`live: true|false`).

## Pickup venue

```
STOREFRONT_PICKUP_NAME=Saffron Restaurant
STOREFRONT_PICKUP_ADDRESS=ul. Złota 44, 00-001 Warszawa, Poland
STOREFRONT_PICKUP_LAT=52.230000
STOREFRONT_PICKUP_LNG=21.012000
STOREFRONT_PICKUP_PHONE=+48 22 000 0000
```

The lat/lng is the origin for every instant quote — get it from Google Maps
"What's here?" on your actual restaurant address.

## Stripe checkout (next step)

Currently `POST /api/orders` returns an order in `PENDING_PAYMENT`. The next
piece is a `POST /api/orders/{id}/checkout` endpoint that creates a Stripe
Checkout Session and returns its URL. Stripe redirects back to
`STRIPE_SUCCESS_URL` (e.g. `https://shop.saffron.example/order/{ref}?paid=1`),
where a webhook handler flips the order to `PAID`.

## <a id="admin"></a> Admin

Storefront admins are completely separate from the cashflow platform's users.
They live in the `admin_users` table inside the storefront database and
authenticate with JWTs signed by `STOREFRONT_JWT_SECRET`. Roles:

- **ADMIN** — full access including admin user management and the audit log.
- **MANAGER** — orders, reservations, products, customers.
- **STAFF** — read-only access to orders, reservations and customers.

### Bootstrap

On a fresh database the first admin is seeded by `AdminBootstrap`:

```
STOREFRONT_ADMIN_EMAIL=admin@saffron.waw.pl          # default
STOREFRONT_ADMIN_PASSWORD=<long-random-or-empty>     # blank → auto-generated, logged once
STOREFRONT_ADMIN_NAME="Saffron Admin"
```

If `STOREFRONT_ADMIN_PASSWORD` is left blank the app generates a 14-char random
password on first boot and logs it once (look for "INITIAL ADMIN CREATED" in
the container logs). The admin is forced to change the password on first sign-in.

Once at least one active admin exists the bootstrap is a no-op — subsequent
restarts don't recreate or modify the account.

### Endpoints

| Endpoint                                  | Roles                | Notes                                       |
| ----------------------------------------- | -------------------- | ------------------------------------------- |
| `POST /api/auth/login`                    | (public)             | `{ email, password }` → `{ token, user }`   |
| `GET  /api/auth/me`                       | any auth             | Current user, refreshed                     |
| `POST /api/auth/change-password`          | any auth             |                                             |
| `GET  /api/admin/dashboard`               | any auth             | Counts + recent activity                    |
| `GET  /api/admin/products`                | ADMIN, MANAGER       |                                             |
| `POST /api/admin/products`                | ADMIN, MANAGER       |                                             |
| `PATCH /api/admin/products/{id}`          | ADMIN, MANAGER       |                                             |
| `DELETE /api/admin/products/{id}`         | ADMIN, MANAGER       | Soft-delete (preserves order history)       |
| `POST /api/admin/products/{id}/image`     | ADMIN, MANAGER       | `multipart/form-data; file=`                |
| `GET  /api/admin/orders`                  | any auth             | Pagination + status/q filters               |
| `GET  /api/admin/orders/{reference}`      | any auth             |                                             |
| `POST /api/admin/orders/{ref}/status`     | ADMIN, MANAGER       | `{ status, message }`                       |
| `POST /api/admin/orders/{ref}/cancel`     | ADMIN, MANAGER       | `{ reason }`                                |
| `POST /api/admin/orders/{ref}/notes`      | ADMIN, MANAGER       | `{ message }` — appends to the timeline     |
| `GET  /api/admin/reservations`            | any auth             |                                             |
| `POST /api/admin/reservations/{id}/status`| ADMIN, MANAGER       |                                             |
| `DELETE /api/admin/reservations/{id}`     | ADMIN, MANAGER       |                                             |
| `GET  /api/admin/customers`               | any auth             | Aggregated from orders                      |
| `GET  /api/admin/users`                   | ADMIN                |                                             |
| `POST /api/admin/users`                   | ADMIN                | Returns a one-time temporary password       |
| `PATCH /api/admin/users/{id}`             | ADMIN                |                                             |
| `POST /api/admin/users/{id}/reset-password`| ADMIN               | Returns a one-time temporary password       |
| `GET  /api/admin/audit`                   | ADMIN                | Append-only log of admin actions            |

### Image uploads

Product photos are written to `STOREFRONT_UPLOADS_DIR` (default `/data/uploads`
inside the container; bound to the `web-app_uploads` Docker volume in
production). They are served from `/api/uploads/**` — publicly readable, no
auth required, with 7-day cache headers.

### Secrets to set in CI

The web-app-backend GitHub Actions pipeline reads these (all optional except
`STOREFRONT_ADMIN_EMAIL` which has a sane default):

- `STOREFRONT_JWT_SECRET` — leave unset to auto-generate on the VPS on first deploy.
- `STOREFRONT_JWT_EXPIRATION_MS` — defaults to 24 hours.
- `STOREFRONT_ADMIN_EMAIL`, `STOREFRONT_ADMIN_PASSWORD`, `STOREFRONT_ADMIN_NAME`
  — for bootstrapping the first admin on a fresh DB.

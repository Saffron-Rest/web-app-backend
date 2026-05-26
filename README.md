# Saffron Storefront API

Public-facing Spring Boot service that powers the **`web-app-frontend`** storefront.
Separate from `platform-backend` (operations console) on purpose — different audience,
different security boundary, different database schema.

## What it does

| Endpoint                              | Description                                                          |
| ------------------------------------- | -------------------------------------------------------------------- |
| `GET  /api/catalog?mode=instant\|courier\|` | Active products grouped by category (per delivery mode if requested). |
| `GET  /api/catalog/{slug}`            | Product detail by slug.                                              |
| `POST /api/quotes/instant`            | Compare Wolt Drive vs Glovo Courier (Poland on-demand).              |
| `POST /api/quotes/courier`            | Compare DHL Express vs DPD Polska (worldwide / EU).                  |
| `POST /api/orders`                    | Create an order (guest checkout).                                    |
| `GET  /api/orders/{reference}`        | Read an order back by short reference.                               |

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

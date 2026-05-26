#!/usr/bin/env bash
set -euo pipefail
APP_DIR="${VPS_APP_DIR:-/docker/web-app-backend}"
cd "$APP_DIR"

REGISTRY_HOST="${REGISTRY_HOST:-ghcr.io}"
if [[ -z "${REGISTRY_PASSWORD:-}" || -z "${REGISTRY_USER:-}" ]]; then
  echo "ERROR: REGISTRY_USER and REGISTRY_PASSWORD required to pull from GHCR." >&2
  exit 1
fi
echo "$REGISTRY_PASSWORD" | docker login -u "$REGISTRY_USER" --password-stdin "$REGISTRY_HOST"

cat > .env <<EOF
BACKEND_IMAGE=${BACKEND_IMAGE:?BACKEND_IMAGE required}
WEBAPP_DB_USER=${WEBAPP_DB_USER:-storefront}
WEBAPP_DB_PASSWORD=${WEBAPP_DB_PASSWORD:?WEBAPP_DB_PASSWORD required}
WEBAPP_DB_NAME=${WEBAPP_DB_NAME:-storefront}
STOREFRONT_PUBLIC_URL=${STOREFRONT_PUBLIC_URL:-http://shop.saffron.waw.pl}
STOREFRONT_CORS_ORIGINS=${STOREFRONT_CORS_ORIGINS:-*}
WOLT_DRIVE_ENABLED=${WOLT_DRIVE_ENABLED:-false}
WOLT_DRIVE_MERCHANT_ID=${WOLT_DRIVE_MERCHANT_ID:-}
WOLT_DRIVE_API_KEY=${WOLT_DRIVE_API_KEY:-}
GLOVO_COURIER_ENABLED=${GLOVO_COURIER_ENABLED:-false}
GLOVO_COURIER_API_KEY=${GLOVO_COURIER_API_KEY:-}
DHL_EXPRESS_ENABLED=${DHL_EXPRESS_ENABLED:-false}
DHL_EXPRESS_API_KEY=${DHL_EXPRESS_API_KEY:-}
DHL_EXPRESS_API_SECRET=${DHL_EXPRESS_API_SECRET:-}
DHL_EXPRESS_ACCOUNT=${DHL_EXPRESS_ACCOUNT:-}
DPD_PL_ENABLED=${DPD_PL_ENABLED:-false}
DPD_PL_LOGIN=${DPD_PL_LOGIN:-}
DPD_PL_PASSWORD=${DPD_PL_PASSWORD:-}
DPD_PL_FID=${DPD_PL_FID:-}
STRIPE_SECRET_KEY=${STRIPE_SECRET_KEY:-}
STRIPE_PUBLISHABLE_KEY=${STRIPE_PUBLISHABLE_KEY:-}
STRIPE_WEBHOOK_SECRET=${STRIPE_WEBHOOK_SECRET:-}
EOF
chmod 600 .env

docker network inspect saffron_net  >/dev/null 2>&1 || docker network create saffron_net
docker network inspect web-app_db   >/dev/null 2>&1 || docker network create web-app_db

docker compose -f docker-compose.prod.yml --env-file .env pull
docker compose -f docker-compose.prod.yml --env-file .env up -d --remove-orphans

echo "Backend: $(docker ps --filter name=saffron-web-app-backend --format '{{.Names}} {{.Status}}')"

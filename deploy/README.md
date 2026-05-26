# web-app-backend deploy

CI uploads this folder to `/docker/web-app-backend/` on the VPS and runs `deploy.sh`.

| File | Purpose |
|------|---------|
| `docker-compose.prod.yml` | single-service compose for `saffron-web-app-backend` |
| `deploy.sh` | login to GHCR, write `.env`, `docker compose pull && up -d` |

The dedicated Postgres lives in its own stack at `/docker/web-app-postgres/` on the VPS. The backend reaches it via the private `web-app_db` Docker network as host `web-app-postgres`.

The backend is **not** published on any host port — Kong routes `shop.saffron.waw.pl/api` to it over `saffron_net`.

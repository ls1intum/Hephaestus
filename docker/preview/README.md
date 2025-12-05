# Preview Deployment Setup

This directory contains Docker Compose configuration for PR preview deployments on Coolify.

## Database Seeding for PR Previews

Uses the **main (non-PR) postgres data volume** via Docker socket: seed-loader will start a temporary postgres container from that volume (if no base container is running), `pg_dump`, restore into the preview DB via `docker exec` to the preview postgres container, then clean up. No DNS/host resolution needed.

### How It Works

**Key Insight:** Coolify renames PR resources, but the base (non-PR) postgres volume keeps its name. We use Docker socket to start a temporary postgres container from that volume when needed, `pg_dump`, and pipe into the preview DB.

1. The main deployment provides the base postgres volume (no `-pr-*`).
2. seed-loader (in previews) uses Docker socket to either reuse a running base container or spin up a temporary postgres container with the base volume attached, then `pg_dump`.
3. seed-loader pipes the dump into the preview postgres via `psql` using `docker exec -i <preview-postgres-container>`; it prefers `$SERVICE_NAME_POSTGRES` and otherwise resolves to the `postgres-*` container containing the deployment UUID (`$COOLIFY_RESOURCE_UUID`), avoiding cross-PR collisions.
4. Graceful fallback if the base volume is missing or dump fails.
5. DB host is configurable via `SERVICE_NAME_POSTGRES`; defaults to `postgres` with a short DNS retry loop.

### Setup on Server (one-time)

1. Ensure the main deployment exists; seed-loader will start a temporary postgres using the base volume if no base container is running.
2. Nothing to pre-seed: seed-loader live-dumps from the base volume each preview deploy.
3. Docker socket must be available to seed-loader (compose mounts `/var/run/docker.sock:ro`).

### Deployment Flow

1. **postgres container starts** - fresh empty database
2. **seed-loader waits** for postgres to be healthy
3. **seed-loader uses docker socket** to run `pg_dump` from a running base container or a temporary container started with the base volume (no `-pr-*`).
4. **seed-loader restores dump** via `psql` into preview postgres using `docker exec -i <preview-postgres-container>` (prefers `$SERVICE_NAME_POSTGRES`, falls back to matching `postgres-*<UUID>*`).
5. **seed-loader exits** (success or failure doesn't matter).
6. **application-server and intelligence-service wait for seed-loader to finish**.
7. **Rest of stack runs** with seeded data (or empty DB fallback).

### Why This Works

- ✅ Works despite Coolify renaming preview resources (we target the base non-PR volume)
- ✅ No manual seed files or extra volumes required (live dump)
- ✅ Standard `pg_dump` → `psql` flow
- ✅ Graceful fallback if base volume missing or dump fails

### Fallback Behavior

If base volume missing or dump fails:

- seed-loader logs the issue
- Exits cleanly (non-blocking)
- Postgres starts with empty database
- Liquibase creates schema from scratch (~30s)

### Troubleshooting

Check base postgres volume (no `-pr-*`):

```bash
docker volume ls --format '{{.Name}}' | grep postgres-data | grep -v pr-
```

Manual test dump with temp container:

```bash
BASE_VOL=$(docker volume ls --format '{{.Name}}' | grep postgres-data | grep -v pr- | head -1)
TEMP_CTN=temp-seed-test
docker rm -f "$TEMP_CTN" >/dev/null 2>&1 || true
docker run -d --name "$TEMP_CTN" -v "$BASE_VOL":/var/lib/postgresql/data postgres:17-alpine
sleep 5
docker exec "$TEMP_CTN" pg_dump -U hephaestus -d hephaestus --clean --if-exists | head
docker rm -f "$TEMP_CTN"
```

## Files

- `compose.app.yaml` - Main Docker Compose for preview deployments
- `compose.proxy.yaml` - Traefik proxy configuration (shared)

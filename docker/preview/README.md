# Preview Deployment Setup

This directory contains Docker Compose configuration for PR preview deployments on Coolify.

## Database Seeding for PR Previews

Uses the **running main (non-PR) postgres container** via Docker socket to `pg_dump` and restore directly into each preview DB. No separate seed volume or seed file is required.

### How It Works

**Key Insight:** Coolify renames PR resources, but the main (non-PR) postgres container is stable. We exec `pg_dump` inside that container via Docker socket and pipe into the preview DB.

1. The main deployment provides the base postgres container (no `-pr-*`). If it is stopped, seed-loader will start it temporarily, dump, then stop it again.
2. seed-loader (in previews) uses Docker socket to `docker exec <base-container> pg_dump ...`.
3. seed-loader pipes the dump into the preview postgres via `psql`.
4. Graceful fallback if the base container is missing or dump fails.

### Setup on Server (one-time)

1. Ensure the main deployment exists; if its postgres is stopped, seed-loader will start/stop it around the dump.
2. Nothing to pre-seed: seed-loader live-dumps from the base container each preview deploy.
3. Docker socket must be available to seed-loader (compose mounts `/var/run/docker.sock:ro`).

### Deployment Flow

1. **postgres container starts** - fresh empty database
2. **seed-loader waits** for postgres to be healthy
3. **seed-loader uses docker socket** to exec `pg_dump` inside the base postgres container (no `-pr-*`).
4. **seed-loader restores dump** via `psql` into preview postgres.
5. **seed-loader exits** (success or failure doesn't matter).
6. **application-server and intelligence-service wait for seed-loader to finish**.
7. **Rest of stack runs** with seeded data (or empty DB fallback).

### Why This Works

- ✅ Works despite Coolify renaming preview resources (we target the base non-PR container)
- ✅ No manual seed files or extra volumes required (live dump)
- ✅ Standard `pg_dump` → `psql` flow
- ✅ Graceful fallback if base volume missing or dump fails

### Fallback Behavior

If base container missing or dump fails:

- seed-loader logs the issue
- Exits cleanly (non-blocking)
- Postgres starts with empty database
- Liquibase creates schema from scratch (~30s)

### Troubleshooting

Check base postgres container (no `-pr-*`):

```bash
docker ps --format '{{.Names}}' | grep postgres | grep -v pr-
```

Manual test dump:

```bash
BASE_CTN=$(docker ps --format '{{.Names}}' | grep postgres | grep -v pr- | head -1)
docker exec "$BASE_CTN" pg_dump -U hephaestus -d hephaestus --clean --if-exists | head
```

## Files

- `compose.app.yaml` - Main Docker Compose for preview deployments
- `compose.proxy.yaml` - Traefik proxy configuration (shared)

# Preview Deployment Setup

This directory contains Docker Compose configuration for PR preview deployments on Coolify.

## Database Seeding for PR Previews

Uses an **external Docker named volume** (`hephaestus-seed`) that is created once on the host and shared across all deployments (main + previews).

### How It Works

**Key Insight:** Coolify modifies **bind mount paths** but NOT **named volume names**!

1. Create external volume `hephaestus-seed` once on host
2. Populate it with `dump.sql.gz` (done once, manually or via cron)
3. Each preview deployment mounts the same external volume
4. seed-loader reads the dump and restores it
5. Gracefully falls back to empty DB if dump unavailable

### Setup on Server (one-time)

1. **Create the external named volume:**

   ```bash
   docker volume create hephaestus-seed
   ```

2. **Verify it was created:**

   ```bash
   docker volume ls | grep hephaestus-seed
   docker volume inspect hephaestus-seed
   ```

3. **Generate seed dump into the volume:**

   ```bash
   # Find your base postgres container
   docker ps --format '{{.Names}}' | grep postgres
   
   # Get the volume mount point
   VOLUME_PATH=$(docker volume inspect hephaestus-seed --format '{{.Mountpoint}}')
   
   # Dump and compress into the volume
   docker exec <postgres-container> pg_dump -U hephaestus -d hephaestus --clean --if-exists | gzip > "$VOLUME_PATH/dump.sql.gz"
   
   # Verify
   ls -lh "$VOLUME_PATH/dump.sql.gz"
   ```

4. **(Optional) Auto-refresh seed every 6 hours:**

   ```bash
   0 */6 * * * VOLUME_PATH=$(docker volume inspect hephaestus-seed --format '{{.Mountpoint}}') && docker exec $(docker ps -qf "name=postgres" --filter "label=coolify.applicationId=<BASE_APP_ID>") pg_dump -U hephaestus -d hephaestus --clean --if-exists 2>/dev/null | gzip > "$VOLUME_PATH/dump.sql.gz"
   ```

### Deployment Flow

1. **postgres container starts** - fresh empty database
2. **seed-loader waits** for postgres to be healthy
3. **seed-loader mounts external volume** `hephaestus-seed:/seed:ro`
4. **seed-loader restores dump** via psql pipe
5. **seed-loader exits** (success or failure doesn't matter)
6. **application-server and intelligence-service wait for seed-loader to finish**
7. **Rest of stack runs** with seeded data (or empty DB fallback)

### Why This Works

- ✅ **Named volumes are NOT modified by Coolify** (only bind mount paths are)
- ✅ **Persistent across deployments** - main + all previews share one seed
- ✅ **Standard Docker practice** - external volumes are meant for this
- ✅ **No security issues** - standard psql restore, no sockets
- ✅ **Graceful fallback** - works even if volume empty

### Fallback Behavior

If external volume empty OR dump doesn't exist:

- seed-loader logs "No seed dump found"
- Exits cleanly (non-blocking)
- Postgres starts with empty database
- Liquibase creates schema from scratch (~30s)

### Troubleshooting

Check if external volume exists:

```bash
docker volume ls | grep hephaestus-seed
```

Check volume contents:

```bash
VOLUME_PATH=$(docker volume inspect hephaestus-seed --format '{{.Mountpoint}}')
ls -lh "$VOLUME_PATH"
```

Manually recreate dump:

```bash
VOLUME_PATH=$(docker volume inspect hephaestus-seed --format '{{.Mountpoint}}')
docker exec <postgres-container> pg_dump -U hephaestus -d hephaestus --clean --if-exists | gzip > "$VOLUME_PATH/dump.sql.gz"
```

## Files

- `compose.app.yaml` - Main Docker Compose for preview deployments
- `compose.proxy.yaml` - Traefik proxy configuration (shared)

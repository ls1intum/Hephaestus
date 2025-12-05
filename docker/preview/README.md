# Preview Deployment Setup

This directory contains Docker Compose configuration for PR preview deployments on Coolify.

## Database Seeding for PR Previews

Uses a **privileged init container** to create symlinks so Coolify's modified paths point to the shared seed.

### How It Works

**The Problem:** Coolify appends `-pr-{id}` suffix to ALL bind mount paths.

**The Solution:**

1. Init container mounts host root filesystem (`/:/host-root`)
2. Creates symlink: `/data/hephaestus/seed-pr-{id}` → `/data/hephaestus/seed`
3. When postgres tries to read `/data/hephaestus/seed-pr-{id}`, it follows the symlink to the shared seed
4. All PRs share the same seed data

### Setup on Server (one-time)

1. **Create shared seed directory:**

   ```bash
   sudo mkdir -p /data/hephaestus/seed
   sudo chmod 755 /data/hephaestus/seed
   ```

2. **Generate seed from base deployment:**

   ```bash
   # Find your base postgres container
   docker ps --format '{{.Names}}' | grep postgres
   
   # Create the dump (replace <container-name>)
   docker exec <postgres-container> pg_dump -U hephaestus -d hephaestus --clean --if-exists | gzip > /data/hephaestus/seed/dump.sql.gz
   ```

3. **(Optional) Auto-update seed with cron:**

   ```bash
   # Updates seed every 6 hours from base deployment
   0 */6 * * * docker exec $(docker ps -qf "name=postgres" --filter "label=coolify.applicationId=<BASE_APP_ID>") pg_dump -U hephaestus -d hephaestus --clean --if-exists 2>/dev/null | gzip > /data/hephaestus/seed/dump.sql.gz
   ```

### What Happens During Deployment

1. **seed-init container starts:**
   - Mounts `/:/host-root` with write access
   - Extracts PR ID from `COOLIFY_CONTAINER_NAME`
   - Creates symlink: `/data/hephaestus/seed-pr-{id}` → `/data/hephaestus/seed`
   - Exits successfully

2. **postgres container starts:**
   - Tries to mount `/data/hephaestus/seed-pr-{id}`
   - Follows symlink to `/data/hephaestus/seed`
   - Reads `dump.sql.gz` on first boot
   - If seed is missing, Liquibase creates schema from scratch

### Security Note

The init container requires `privileged: true` to create symlinks in the host filesystem. This is necessary because Coolify modifies bind mount paths at deployment time.

### Fallback Behavior

If `/data/hephaestus/seed` doesn't exist or is empty:

- Symlink creation succeeds (dangling symlink is OK)
- Postgres starts with empty database
- Liquibase creates schema from migrations (~30s startup)

### Schema Compatibility

- GitHub's "Require branches to be up to date" ensures PRs have compatible schemas
- Liquibase migrations are additive - PRs can apply on top of develop's schema
- If a PR has conflicting schema changes, seed restore may fail and postgres will start empty

## Files

- `compose.app.yaml` - Main Docker Compose for preview deployments
- `compose.proxy.yaml` - Traefik proxy configuration (shared)

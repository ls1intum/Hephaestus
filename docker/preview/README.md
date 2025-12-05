# Preview Deployment Setup

This directory contains Docker Compose configuration for PR preview deployments on Coolify.

## Database Seeding for PR Previews

PR previews can start with a seeded database from the develop branch. The compose file mounts `/tmp/hephaestus-seed` to PostgreSQL's init directory.

> **Note:** We use `/tmp` because Coolify doesn't add `-pr-{id}` suffix to `/tmp` paths, allowing all previews to share the same seed.

### Setup

1. **Create seed directory on server:**

   ```bash
   sudo mkdir -p /tmp/hephaestus-seed
   ```

2. **Generate seed from base deployment:**

   ```bash
   # Find your base postgres container
   docker ps --format '{{.Names}}' | grep postgres
   
   # Create the dump (replace <container-name> with actual name)
   docker exec <postgres-container> pg_dump -U hephaestus -d hephaestus --clean --if-exists | gzip > /tmp/hephaestus-seed/dump.sql.gz
   ```

3. **(Optional) Auto-update seed with cron:**

   ```bash
   # Add to crontab (updates seed every 6 hours)
   0 */6 * * * docker exec $(docker ps -qf "name=postgres" --filter "label=coolify.applicationId=<BASE_APP_ID>") pg_dump -U hephaestus -d hephaestus --clean --if-exists 2>/dev/null | gzip > /tmp/hephaestus-seed/dump.sql.gz
   ```

### How It Works

1. The compose file mounts `/tmp/hephaestus-seed:/docker-entrypoint-initdb.d:ro`
2. PostgreSQL runs `.sql.gz` files from this directory on **first container start** only
3. **If the seed directory is empty**: postgres starts empty, Liquibase creates schema from scratch
4. **If the directory doesn't exist**: Docker will fail to start the container
5. The `depends_on` + healthcheck ensures app-server waits until postgres is ready

### Schema Compatibility

The seed dump comes from the `develop` branch. PRs must be up-to-date with develop for the seed to work correctly.

- GitHub's "Require branches to be up to date before merging" setting enforces this
- Liquibase migrations are additive - PRs can add new migrations on top of develop's schema
- If a PR has conflicting schema changes, the seed restore may fail and postgres will start empty

## Files

- `compose.app.yaml` - Main Docker Compose for preview deployments
- `compose.proxy.yaml` - Traefik proxy configuration (shared)

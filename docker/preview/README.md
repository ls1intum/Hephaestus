# Preview Deployment Setup

This directory contains Docker Compose configuration for PR preview deployments on Coolify.

## Database Seeding for PR Previews

PR previews can start with a seeded database from the develop branch. The compose file mounts `/data/hephaestus/seed` to PostgreSQL's init directory.

### Setup

1. **Create seed directory on server:**

   ```bash
   sudo mkdir -p /data/hephaestus/seed
   sudo chmod 755 /data/hephaestus/seed
   ```

2. **Generate seed from base deployment:**

   ```bash
   # Find your base postgres container
   docker ps --format '{{.Names}}' | grep postgres
   
   # Create the dump (replace <container-name> with actual name)
   docker exec <postgres-container> pg_dump -U hephaestus -d hephaestus --clean --if-exists | gzip > /data/hephaestus/seed/dump.sql.gz
   ```

3. **(Optional) Auto-update seed with cron:**

   ```bash
   # Add to crontab (updates seed every 6 hours)
   0 */6 * * * docker exec $(docker ps -qf "name=postgres" --filter "label=coolify.applicationId=<BASE_APP_ID>") pg_dump -U hephaestus -d hephaestus --clean --if-exists 2>/dev/null | gzip > /data/hephaestus/seed/dump.sql.gz
   ```

### How It Works

1. The compose file mounts `/data/hephaestus/seed:/docker-entrypoint-initdb.d:ro`
2. **The directory must exist on the host** - create it before first deploy
3. PostgreSQL runs `.sql.gz` files from this directory on **first container start** only
4. **If the seed directory is empty**: postgres starts empty, Liquibase creates schema from scratch
5. **If the directory doesn't exist**: Docker will fail to start the container
6. The `depends_on` + healthcheck ensures app-server waits until postgres is ready

### Schema Compatibility

The seed dump comes from the `develop` branch. PRs must be up-to-date with develop for the seed to work correctly.

- GitHub's "Require branches to be up to date before merging" setting enforces this
- Liquibase migrations are additive - PRs can add new migrations on top of develop's schema
- If a PR has conflicting schema changes, the seed restore may fail and postgres will start empty

## Files

- `compose.app.yaml` - Main Docker Compose for preview deployments
- `compose.proxy.yaml` - Traefik proxy configuration (shared)

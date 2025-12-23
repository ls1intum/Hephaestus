# Scripts

Utility scripts for Hephaestus development. All TypeScript scripts run via `tsx` (no compilation needed).

## Prerequisites

Scripts use dependencies from the root `package.json`. Run `npm install` at the repo root first.

## Available Scripts

### Database Utilities

All database-related commands are accessed via `db-utils.sh` which handles database setup, migrations, and cleanup:

```bash
npm run db:generate-erd-docs                    # Generate Mermaid ERD diagram
npm run db:draft-changelog                       # Generate Liquibase changelog diff
npm run db:generate-models:intelligence-service  # Drizzle schema introspection
```

**ERD Generation Environment Variables:**

| Variable             | Default     | Description                  |
| -------------------- | ----------- | ---------------------------- |
| `POSTGRES_HOST`      | `localhost` | PostgreSQL host              |
| `POSTGRES_PORT`      | `5432`      | PostgreSQL port              |
| `HEPHAESTUS_DB_MODE` | `docker`    | Database mode: docker/local  |

### NATS Webhook Example Extraction

Extract webhook payloads from NATS JetStream for test fixtures:

```bash
npm run nats:extract-examples
# With options:
npm run nats:extract-examples -- --event push --event pull_request:opened
```

**Environment variables:**

| Variable   | Default                 | Description     |
| ---------- | ----------------------- | --------------- |
| `NATS_URL` | `nats://localhost:4222` | NATS server URL |

**Common options:**

| Option                    | Description                         |
| ------------------------- | ----------------------------------- |
| `--nats-server <url>`     | NATS server URL                     |
| `--examples-dir <path>`   | Output directory                    |
| `--event <type[:action]>` | Filter by event type (repeatable)   |
| `--since <iso>`           | Only messages after this timestamp  |
| `--until <iso>`           | Only messages before this timestamp |
| `--dry-run`               | Validate config without extracting  |

## Dependencies

Scripts depend on packages in root `package.json`:

- `tsx` - TypeScript execution
- `commander` - CLI parsing
- `pg` and `@types/pg` - PostgreSQL client with TypeScript types
- `@nats-io/jetstream` and `@nats-io/transport-node` - NATS JetStream client

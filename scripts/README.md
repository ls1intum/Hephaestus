# Scripts

Utility scripts for Hephaestus development. All TypeScript scripts run via `tsx` (no compilation needed).

## Prerequisites

Scripts use dependencies from the root `package.json`. Run `pnpm install` at the repo root first.

## Available Scripts

### Database Utilities

Database documentation commands use `db-utils.sh` and require Docker with the Compose plugin:

```bash
pnpm run db:generate-erd-docs                    # Generate Mermaid ERD diagram
pnpm run db:draft-changelog                       # Generate Liquibase changelog diff
```

**ERD generation environment variables:**

| Variable            | Default      | Description         |
| ------------------- | ------------ | ------------------- |
| `POSTGRES_HOST`     | `localhost`  | PostgreSQL host     |
| `POSTGRES_PORT`     | `5432`       | PostgreSQL port     |
| `POSTGRES_DB`       | `hephaestus` | PostgreSQL database |
| `POSTGRES_USER`     | `root`       | PostgreSQL user     |
| `POSTGRES_PASSWORD` | `root`       | PostgreSQL password |

### Achievement Formatting

Rewrite `server/application/src/main/resources/achievements/achievements.yml` into the property order
`PREFERRED_ORDER` defines. The fields themselves are documented in
[Achievements](../docs/contributor/achievements.mdx).

```bash
pnpm run format:achievements
```

### NATS Webhook Example Extraction

Extract webhook payloads from NATS JetStream for test fixtures:

```bash
pnpm run nats:extract-examples
# With options:
pnpm run nats:extract-examples -- --event push --event pull_request:opened
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

# Scripts

The Node.js version pinned in `package.json#devEngines.runtime` executes these TypeScript utilities
using native type stripping.

Most scripts require `pnpm install` at the repository root. Jean setup performs that installation.

## Available Scripts

### Repository orchestration

Infrastructure tests use `node:test`; Vitest is reserved for the Vite-managed webapp and docs trees.

Substantive developer orchestration under `scripts/` uses typed Node.js entry points:

| Command | Purpose |
| --- | --- |
| `pnpm run check:ports` | Validate configured local ports and report listeners. |
| `pnpm run db:draft-changelog` | Rebuild a disposable database and generate a Liquibase diff. |
| `pnpm run db:generate-erd-docs` | Apply migrations and regenerate the Mermaid ERD. |
| `pnpm run e2e:setup -- <options>` | Configure a local E2E workspace against the selected SCM and model provider. Secrets are environment-only. |
| `pnpm run jean:public-test -- <command>` | Manage the machine-local public test route. |

The database commands require Docker with the Compose plugin.

`jean:public-test` accepts `start`, `stop`, `status`, `smoke`, and `seed-status`. The route is
internet-facing and requires Docker, the machine's Coolify network and Traefik configuration
directory, and `server/.env`.

Jean runs `node "$JEAN_ROOT_PATH/scripts/jean-setup.ts"` from a new worktree to copy machine-local
configuration and install dependencies. It is not a general contributor command.

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

## Retained shell boundaries

The retained shell files run where shell is already part of the runtime:

- `.husky/_/husky.sh` is Husky's generated, minimal Git-hook bootstrap.
- `docker/self-host/setup.sh` bootstraps an operator installation before the repository toolchain is available; its test
  orchestration is typed TypeScript under `scripts/`.
- `webapp/docker/entrypoint.sh` prepares assets in the final nginx image, which does not contain Node.

No substantive shell script is permitted under `scripts/`.

# Webhook Ingest

A Hono-based TypeScript service for ingesting GitHub and GitLab webhooks and publishing events to NATS JetStream.

## Features

- GitHub webhook signature verification (SHA-256 and SHA-1)
- GitLab webhook token verification
- NATS JetStream publishing with retry logic
- Automatic stream creation (`github` and `gitlab` streams)
- Request body size limits (25 MB default, matching GitHub's payload cap)
- Bounded publish retries to stay within provider timeout windows
- Graceful shutdown handling
- Structured JSON logging with request tracing
- Health check endpoint (verifies NATS connectivity)

## NATS Subjects

Events are published to JetStream using these subject patterns:

- **GitHub**: `github.<owner>.<repo>.<event_type>`
- **GitLab**: `gitlab.<namespace>.<project>.<event_type>`

For GitLab events, the full group path (including subgroups) is flattened using `~` as separator.

## Development

### Prerequisites

- Node.js >= 22.22.0 (see `.node-version` at repo root)
- NATS server (use `docker run -d -p 4222:4222 nats:latest -js`)

### Setup

```bash
# Install dependencies (from monorepo root)
npm install

# Copy environment file
cp .env.example .env

# Run in development mode
npm run dev
```

### Scripts

| Script             | Description                       |
| ------------------ | --------------------------------- |
| `npm run dev`      | Start with hot reload (tsx watch) |
| `npm run build`    | Build for production              |
| `npm run start`    | Run production build              |
| `npm run test`     | Run unit tests (Vitest)           |
| `npm run validate` | Run typecheck + lint + test       |
| `npm run check`    | Run Biome check                   |
| `npm run format`   | Format with Biome                 |

## API Endpoints

| Endpoint  | Method | Description                                     |
| --------- | ------ | ----------------------------------------------- |
| `/github` | POST   | GitHub webhook (requires `X-Hub-Signature-256`) |
| `/gitlab` | POST   | GitLab webhook (requires `X-Gitlab-Token`)      |
| `/health` | GET    | Health check (returns 503 if NATS disconnected) |

## Environment Variables

| Variable                           | Required | Default                   | Description                                                                      |
| ---------------------------------- | -------- | ------------------------- | -------------------------------------------------------------------------------- |
| `WEBHOOK_SECRET`                   | **Yes**  | -                         | GitHub webhook secret (HMAC-SHA256)                                              |
| `NATS_URL`                         | No       | `nats://nats-server:4222` | NATS server URL(s), supports TLS (`tls://`) and comma-separated multiple servers |
| `NATS_AUTH_TOKEN`                  | No       | -                         | NATS auth token (if required)                                                    |
| `PORT`                             | No       | `4200`                    | HTTP server port                                                                 |
| `LOG_LEVEL`                        | No       | `info`                    | Pino log level                                                                   |
| `NODE_ENV`                         | No       | `development`             | Environment (development/test/production)                                        |
| `STREAM_MAX_AGE_DAYS`              | No       | `180`                     | Stream retention period                                                          |
| `STREAM_MAX_MSGS`                  | No       | `2000000`                 | Max messages per stream                                                          |
| `MAX_PAYLOAD_SIZE_MB`              | No       | `25`                      | Request body size limit                                                          |
| `NATS_PUBLISH_TIMEOUT_MS`          | No       | `9000`                    | Max publish time before 503                                                      |
| `NATS_PUBLISH_MAX_RETRIES`         | No       | `5`                       | Retry attempts within timeout                                                    |
| `NATS_PUBLISH_RETRY_BASE_DELAY_MS` | No       | `200`                     | Base retry backoff in ms                                                         |

## Docker

The service is deployed as part of the Hephaestus stack via `docker/compose.core.yaml`.

```bash
# Build image
docker build -t webhook-ingest .

# Run standalone (for testing)
docker run -p 4200:4200 \
  -e WEBHOOK_SECRET=your-secret \
  -e NATS_URL=nats://host.docker.internal:4222 \
  webhook-ingest
```

## Architecture

```text
src/
├── index.ts           # Entry point, server lifecycle, graceful shutdown
├── app.ts             # Hono app assembly, middleware, routes
├── env.ts             # Zod environment validation
├── logger.ts          # Shared Pino logger instance
├── crypto/
│   └── verify.ts      # HMAC signature verification (timing-safe)
├── nats/
│   └── client.ts      # JetStream client, stream creation, publish
├── routes/
│   ├── github.ts      # GitHub webhook handler
│   ├── gitlab.ts      # GitLab webhook handler
│   └── health.ts      # Health check (verifies NATS connectivity)
└── utils/
    ├── dedupe.ts       # Dedupe ID helper for JetStream
    └── gitlab-subject.ts  # GitLab subject builder (namespace extraction)
```

## Security

- HMAC-SHA256 signature verification using `crypto.timingSafeEqual()`
- Raw body verification before JSON parsing (prevents signature bypass)
- Request body size limits via Hono `bodyLimit` middleware
- Request timeout protection (15s) against slow loris attacks
- JetStream deduplication via `Nats-Msg-Id` (delivery IDs or hashed payloads)
- Non-root Docker user (`hono:nodejs`) with `dumb-init` for proper signal handling
- All secrets via environment variables (never logged, redacted from Pino output)
- GitLab uses `Idempotency-Key` (17.4+) or `X-Gitlab-Event-UUID` (16.2+) for deduplication
- GitHub uses `X-GitHub-Delivery` for deduplication via `Nats-Msg-Id`
- Docker image uses SHA-pinned base image for supply chain security
- Content-Type validation rejects non-JSON payloads

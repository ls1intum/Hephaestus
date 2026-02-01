# Webhook Ingest Service

High-performance Hono service that receives GitHub/GitLab webhooks and publishes to NATS JetStream.

## Commands

| Task | Command |
|------|---------|
| Dev server | `npm run dev` (port 4200) |
| Build | `npm run build` |
| Tests | `npm run test` |
| Type check | `npm run typecheck` |
| Lint + format | `npm run check` |
| Full validation | `npm run validate` |

## Boundaries

### Always
- Verify signatures on raw bytes BEFORE JSON parsing
- Use `crypto.timingSafeEqual()` for all secret comparisons
- Run `npm run validate` before committing
- Return 503 if NATS publish fails after retries

### Ask First
- Adding new webhook providers
- Changing NATS subject naming
- Modifying retry/timeout logic
- Adding new environment variables

### Never
- Log webhook secrets or tokens
- Parse JSON before signature verification
- Use string comparison for secrets
- Skip signature verification for any provider

## Tech Stack

```
Node.js 22 + TypeScript 5.9
├── Framework: Hono
├── Message Queue: NATS JetStream
├── Validation: Zod
├── Logging: Pino
├── Testing: Vitest
└── Linting: Biome
```

## Project Structure

```
src/
├── index.ts          # Entry point, graceful shutdown
├── app.ts            # Hono app, middleware, routes
├── env.ts            # Zod environment validation
├── crypto/
│   └── verify.ts     # HMAC signature verification (timing-safe)
├── nats/
│   └── client.ts     # JetStream client, publish with retry
├── routes/
│   ├── github.ts     # POST /github
│   ├── gitlab.ts     # POST /gitlab
│   └── health.ts     # GET /health
└── utils/
    ├── dedupe.ts     # Deduplication ID builder
    └── gitlab-subject.ts  # GitLab subject extraction
```

## API Endpoints

| Endpoint | Method | Headers | Purpose |
|----------|--------|---------|---------|
| `/github` | POST | `X-Hub-Signature-256`, `X-GitHub-Event` | GitHub webhooks |
| `/gitlab` | POST | `X-GitLab-Token`, `X-GitLab-Event` | GitLab webhooks |
| `/health` | GET | - | Health check (NATS status) |

## Webhook Processing Flow

### GitHub

1. Read raw body as `Uint8Array`
2. Verify HMAC-SHA256 signature (timing-safe)
3. Handle `ping` event (return `pong`, skip NATS)
4. Parse JSON, extract `org`, `repo`, `action`
5. Build NATS subject: `github.<owner>.<repo>.<event>`
6. Publish with deduplication ID from `X-GitHub-Delivery`

### GitLab

1. Read raw body as `Uint8Array`
2. Verify token (timing-safe comparison)
3. Parse JSON, extract namespace/project
4. Build NATS subject: `gitlab.<namespace>.<project>.<event>`
5. Publish with deduplication ID from `Idempotency-Key` or `X-Gitlab-Event-UUID`

## NATS JetStream

### Streams

| Stream | Subjects |
|--------|----------|
| `github` | `github.>` |
| `gitlab` | `gitlab.>` |

### Subject Naming

- GitHub: `github.<owner>.<repo>.<event_type>`
- GitLab: `gitlab.<namespace>.<project>.<event_type>`

Dots in names replaced with tildes (`~`):
- `ls1intum/Artemis` -> `ls1intum.Artemis`
- `group/subgroup/project` -> `group~subgroup.project`

### Publish Retry

- Exponential backoff with 25% jitter
- Max retries: 5 (configurable)
- Timeout budget: 9s (fits GitHub's 10s limit)
- Returns 503 if all retries fail

## Security (CRITICAL)

### Signature Verification

```typescript
// GitHub: HMAC-SHA256 on raw bytes BEFORE parsing
const signature = request.headers.get("X-Hub-Signature-256");
const isValid = verifyGitHubSignature(rawBody, signature, secret);

// GitLab: Direct token comparison
const token = request.headers.get("X-GitLab-Token");
const isValid = crypto.timingSafeEqual(
  Buffer.from(token),
  Buffer.from(expectedToken)
);
```

### Critical Security Rules

- Verify signature on raw bytes BEFORE JSON parsing
- Always use `crypto.timingSafeEqual()` (prevents timing attacks)
- Never log tokens or secrets
- Minimum 32-character webhook secret enforced

## Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `WEBHOOK_SECRET` | Yes | - | Shared secret (min 32 chars) |
| `NATS_URL` | No | `nats://nats-server:4222` | NATS server URL |
| `PORT` | No | `4200` | HTTP server port |
| `LOG_LEVEL` | No | `info` | Pino log level |
| `STREAM_MAX_AGE_DAYS` | No | `180` | Stream retention |
| `MAX_PAYLOAD_SIZE_MB` | No | `25` | Request body limit |
| `NATS_PUBLISH_TIMEOUT_MS` | No | `9000` | Publish timeout |
| `NATS_PUBLISH_MAX_RETRIES` | No | `5` | Retry attempts |

## Code Style

### Adding New Webhook Provider

1. Create `src/routes/<provider>.ts`:

```typescript
import { Hono } from "hono";
import { verifySignature } from "@/crypto/verify";
import { publishToNats } from "@/nats/client";

export const providerRoutes = new Hono();

providerRoutes.post("/", async (c) => {
  const rawBody = await c.req.arrayBuffer();

  // 1. Verify signature BEFORE parsing
  if (!verifySignature(rawBody, c.req.header("X-Signature"), env.WEBHOOK_SECRET)) {
    return c.json({ error: "Invalid signature" }, 401);
  }

  // 2. Parse and extract subject components
  const payload = JSON.parse(new TextDecoder().decode(rawBody));
  const subject = buildSubject(payload);

  // 3. Publish to NATS
  await publishToNats(subject, payload, dedupeId);

  return c.json({ status: "ok" });
});
```

2. Register in `src/app.ts`
3. Add stream in `src/nats/client.ts`

### Subject Sanitization

```typescript
// Replace dots with tildes (NATS uses dots as separators)
const sanitize = (s: string) => s.replace(/\./g, "~");
const subject = `github.${sanitize(owner)}.${sanitize(repo)}.${event}`;
```

## Testing

### Test Structure

```typescript
describe("GitHub webhook handler", () => {
  it("should return 200 for valid signed request", async () => {
    const payload = { action: "opened", repository: { ... } };
    const signature = createSignature(payload, secret);

    const response = await app.request("/github", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Hub-Signature-256": signature,
        "X-GitHub-Event": "pull_request",
      },
      body: JSON.stringify(payload),
    });

    expect(response.status).toBe(200);
    expect(mockPublish).toHaveBeenCalledWith(
      "github.owner.repo.pull_request",
      expect.any(Object),
      expect.any(Object)
    );
  });
});
```

### Coverage

80% threshold for statements, branches, functions, lines.

## Integration

### Downstream Consumer

Application-server consumes from NATS:
- `NatsConsumerService.java` creates workspace-specific consumers
- Subject filters per workspace
- Durable consumers for at-least-once delivery

### Deployment

```yaml
# docker/compose.core.yaml
webhook-ingest:
  depends_on:
    - nats-server
  environment:
    - WEBHOOK_SECRET=${WEBHOOK_SECRET}
    - NATS_URL=nats://nats-server:4222
```

Exposed via Traefik at `/webhooks`.

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| Parsed JSON before verify | Move verification before `JSON.parse()` |
| Used `===` for secret | Use `crypto.timingSafeEqual()` |
| Logged webhook payload | Remove—may contain secrets |
| NATS timeout | Check `NATS_PUBLISH_TIMEOUT_MS` (default 9s for GitHub's 10s limit) |

# Intelligence Service

TypeScript/Hono microservice providing AI-powered features: mentor chat and bad practice detection.

## Commands

| Task | Command |
|------|---------|
| Dev server | `npm run dev` (port 8000) |
| Build | `npm run build` |
| Tests | `npm run test` |
| Type check | `npm run typecheck` |
| Lint + format | `npm run check` |
| Export OpenAPI | `npm run openapi:export` |
| Sync prompts | `npm run prompts push` |
| Introspect DB | `npm run db:introspect` |

## Boundaries

### Always
- Run `npm run check` before committing
- Use Zod for all external input validation
- Use path alias `@/` for imports
- Add tools to the registry after creating them

### Ask First
- Adding new LLM providers
- Changing prompt structure
- Database schema expectations
- New environment variables

### Never
- Commit API keys or secrets
- Use `any` type (use `unknown` + validation)
- Skip error handling for AI calls
- Expose raw LLM errors to clients

## Tech Stack

```
Node.js 22 + TypeScript 5.9 (strict mode)
├── Framework: Hono (type-safe HTTP)
├── AI: Vercel AI SDK v6 (streamText, generateText, tools)
├── LLM Providers: OpenAI, Azure OpenAI (via registry)
├── Database: PostgreSQL + Drizzle ORM
├── Validation: Zod (request/response schemas)
├── Observability: Langfuse (OpenTelemetry tracing)
├── Testing: Vitest + Testcontainers
└── Linting: Biome
```

## Project Structure

```
src/
├── index.ts              # Entry point, server lifecycle
├── app.ts                # Hono app assembly, routes
├── env.ts                # Zod environment validation
├── detector/             # Bad practice detector
│   ├── bad-practice.prompt.ts
│   ├── detector.handler.ts
│   ├── detector.routes.ts
│   └── detector.schema.ts
├── mentor/               # AI mentor (Heph)
│   ├── chat.prompt.ts    # System prompt
│   ├── chat/             # Chat handling (SSE streaming)
│   ├── documents/        # Document artifacts
│   ├── threads/          # Thread management
│   ├── tools/            # AI tool definitions
│   │   ├── registry.ts
│   │   ├── activity-summary.tool.ts
│   │   ├── pull-requests.tool.ts
│   │   └── ... (10 tools)
│   └── vote/             # Message voting
├── prompts/              # Prompt loader + Langfuse integration
└── shared/
    ├── ai/               # Model registry, error handling
    ├── db/               # Drizzle connection + schema
    └── http/             # Hono utilities, middleware
```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/health` | GET | Health check |
| `/detector` | POST | Bad practice detection |
| `/mentor/chat` | POST | AI chat (SSE streaming) |
| `/mentor/threads/grouped` | GET | List threads by time bucket |
| `/mentor/threads/{id}` | GET | Get thread with messages |
| `/mentor/documents` | GET/POST | Document CRUD |
| `/mentor/documents/{id}` | GET/PUT/DELETE | Document operations |
| `/mentor/messages/{id}/vote` | PUT | Vote on messages |

## AI Tools

The mentor has 10 tools for function calling:

| Tool | Purpose |
|------|---------|
| `getActivitySummary` | Week-over-week metrics (call first) |
| `getPullRequests` | User's PRs with filters |
| `getIssues` | Authored issues |
| `getAssignedWork` | Assigned issues/review requests |
| `getFeedbackReceived` | Review comments received |
| `getReviewsGiven` | Reviews given to others |
| `getSessionHistory` | Previous chat sessions |
| `getDocuments` | User's documents |
| `createDocument` | Create artifact during chat |
| `updateDocument` | Update existing document |

### Adding a Tool

```typescript
// src/mentor/tools/my-feature.tool.ts
import { tool } from "ai";
import { z } from "zod";
import type { ToolContext } from "./context";

export const createMyFeatureTool = (ctx: ToolContext) =>
  tool({
    description: "Brief description for the LLM",
    parameters: z.object({
      param: z.string().describe("Parameter description"),
    }).strict(),
    execute: async ({ param }) => {
      // Access ctx.workspaceId, ctx.userId, ctx.db
      return { result: "data" };
    },
  });
```

Register in `src/mentor/tools/registry.ts`.

## Prompt Management

Prompts follow local-first, sync-to-Langfuse pattern:

```typescript
// src/detector/bad-practice.prompt.ts
export const badPracticePrompt: PromptDefinition = {
  name: "bad-practice-detector",
  type: "text",
  prompt: `You are a PR quality analyzer...`,
  config: { temperature: 0.3 },
};
```

### Workflow

```bash
npm run prompts status  # Check sync status
npm run prompts push    # Push to Langfuse
npm run prompts pull    # Pull from Langfuse
npm run prompts diff    # Show differences
```

At runtime: loads from Langfuse with LRU cache (5 min TTL), falls back to local if unavailable.

## Database

Schema is introspected from application-server's PostgreSQL:

```bash
npm run db:introspect   # Regenerate src/shared/db/schema.ts
```

The service shares the same database as application-server (read-only for activity data, read-write for chat).

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `DATABASE_URL` | Yes | PostgreSQL connection string |
| `MODEL_NAME` | No | Default model (e.g., `openai:gpt-4o-mini`) |
| `DETECTION_MODEL_NAME` | No | Model for bad practice detector |
| `OPENAI_API_KEY` | If OpenAI | OpenAI API key |
| `AZURE_RESOURCE_NAME` | If Azure | Azure resource name |
| `AZURE_API_KEY` | If Azure | Azure API key |
| `LANGFUSE_*` | No | Langfuse credentials |
| `PORT` | No | Server port (default: 8000) |

Model names must be provider-qualified: `openai:gpt-4o-mini`, `azure:gpt-4o`.

## Code Style

### File Naming

- `*.prompt.ts` - Prompt definitions
- `*.tool.ts` - Tool definitions
- `*.handler.ts` - Request handlers
- `*.routes.ts` - Route definitions
- `*.schema.ts` - Zod schemas

### Imports

Use path alias `@/` for all imports:

```typescript
import { db } from "@/shared/db";
import { createMyTool } from "@/mentor/tools/my-feature.tool";
```

### Zod Schemas

Use `strict()` for tool input validation:

```typescript
const inputSchema = z.object({
  workspaceId: z.number(),
  limit: z.number().optional(),
}).strict();
```

## Testing

### Structure

```
test/
├── global-setup.ts       # Testcontainers setup
├── setup.ts              # Test configuration
├── mocks/                # AI SDK mocks, test builders
├── integration/          # Integration tests
└── tools/                # Tool-specific tests
```

### Database Strategy

1. Docker available: Testcontainer (PostgreSQL 17)
2. Docker unavailable: Fall back to local PostgreSQL
3. Explicit local: `HEPHAESTUS_DB_MODE=local`

### Mocking AI SDK

```typescript
import { mockStreamText } from "@test/mocks/ai-sdk-mocks";

vi.mock("ai", () => ({
  streamText: mockStreamText,
}));
```

## Integration with Application Server

Requests flow: `webapp` -> `application-server` (proxy) -> `intelligence-service`

Context passed via headers:
- `X-Workspace-Id`: Workspace database ID
- `X-Workspace-Slug`: URL slug
- `X-User-Id`: User database ID
- `X-User-Login`: Username

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| Used `any` type | Use `unknown` with Zod validation |
| Missing `.strict()` | Add to all Zod schemas for external input |
| Direct provider import | Use model registry in `src/shared/ai/model.ts` |
| Raw error exposed | Wrap in user-friendly message |

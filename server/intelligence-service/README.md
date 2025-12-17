# Intelligence Service — AI-Powered Mentor and Detection API

This service provides the AI-powered mentor chat functionality and bad practice detection for Hephaestus. It uses Hono with Zod OpenAPI for type-safe routing and AI SDK v6 for LLM interactions.

## Features

- **Mentor Chat**: AI-powered conversational assistant with tool calling capabilities
- **Bad Practice Detection**: Automated detection of development anti-patterns
- **Document Artifacts**: Create and update rich documents within chat
- **Database Integration**: Direct access to Hephaestus data via Drizzle ORM

## Run locally

```sh
npm install
npm run dev
```

Then open:

- `http://localhost:8000/docs` — Docs endpoint configuration
- `http://localhost:8000/openapi.json` — OpenAPI v3.0 JSON
- `http://localhost:8000/openapi.yaml` — OpenAPI v3.1 YAML

## Debugging with Verbose Logging

For debugging API issues, enable verbose logging to capture full request/response bodies:

```sh
# Add to .env
VERBOSE_LOGGING=true
VERBOSE_LOG_FILE=logs/verbose.log
```

This will log:
- Full request headers (with sensitive headers redacted)
- Request body (JSON parsed)
- Response body (for non-streaming responses)
- Timing information

**Example output in `logs/verbose.log`:**
```json
{
  "timestamp": "2025-12-16T19:30:00.000Z",
  "type": "REQUEST",
  "requestId": "abc-123",
  "method": "POST",
  "path": "/mentor/chat",
  "headers": { "content-type": "application/json", "authorization": "[REDACTED]" },
  "body": { "id": "thread-1", "message": { "id": "msg-1", "parts": [...] } }
}
────────────────────────────────────────────────────────────────────────────────
{
  "timestamp": "2025-12-16T19:30:00.050Z",
  "type": "RESPONSE",
  "requestId": "abc-123",
  "status": 200,
  "duration": "50ms",
  "body": "[SSE Stream - not captured]"
}
```

> **Warning:** This logs potentially sensitive data. Use only for debugging.

## Export OpenAPI to file

Write `openapi.yaml` to the service root:

```sh
npm run openapi:export
```

This mirrors the Python service’s `export_openapi_specs.py` flow by generating schemas from the same app definition.

## Notes

- The app registers a `Bearer` auth security scheme and a reusable `Error` schema.
- Add new routes via `@hono/zod-openapi` `createRoute()` to keep the spec up-to-date.

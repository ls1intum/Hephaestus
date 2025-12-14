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

## Export OpenAPI to file

Write `openapi.yaml` to the service root:

```sh
npm run openapi:export
```

This mirrors the Python service’s `export_openapi_specs.py` flow by generating schemas from the same app definition.

## Notes

- The app registers a `Bearer` auth security scheme and a reusable `Error` schema.
- Add new routes via `@hono/zod-openapi` `createRoute()` to keep the spec up-to-date.

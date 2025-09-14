# Intelligence Service (Hono) — OpenAPI Docs

This service uses Hono with Zod OpenAPI to define routes and generate OpenAPI specs similar to the Python `intelligence-service`.

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

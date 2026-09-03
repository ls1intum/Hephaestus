---
"hephaestus": minor
---

**Operators:** Worker sandboxes now reach a dedicated sandbox gateway on `SANDBOX_API_PORT` (default `8081`), which serves only the sandbox capabilities and answers every other path with an empty `404`. Worker containers bind their HTTP and management endpoints to loopback, so sandboxes reach them only through the gateway. `SANDBOX_API_REQUESTS_PER_MINUTE` caps each authenticated sandbox and `SANDBOX_API_MAX_REQUEST_BYTES` caps the request bodies it accepts.

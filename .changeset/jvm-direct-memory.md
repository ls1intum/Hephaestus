---
"hephaestus": patch
---

Fixes login and other database operations intermittently failing. The build's
10 MB off-heap direct-memory default sits just below the application server's
steady I/O footprint, so once it filled, PostgreSQL could no longer allocate the
buffer for its connection handshake and the connection pool drained. The server
and worker now get 128 MB of direct memory.

**Operators:** override the default with `APP_MAX_DIRECT_MEMORY` if you need to
tune it (e.g. a larger value if a heavy backfill ever approaches the limit).

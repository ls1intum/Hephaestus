---
"hephaestus": minor
---

The application-server, application-worker and webhook-server containers now have explicit memory
limits, so each JVM sizes its heap for its own container instead of the whole host. Co-located
services no longer oversubscribe host memory and push the box into swap.

**Operators:** defaults are application-server 5 GB, application-worker 3 GB and webhook-server 2 GB,
overridable via `APPLICATION_SERVER_MEM_LIMIT`, `APPLICATION_WORKER_MEM_LIMIT` and
`WEBHOOK_SERVER_MEM_LIMIT`. Keep the sum under the host's RAM; raise them on larger hosts. A host
sized for the old advice will not fit these limits — the single-server install guide states the
floor.

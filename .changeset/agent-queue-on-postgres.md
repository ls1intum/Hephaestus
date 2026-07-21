---
"hephaestus": minor
---

Practice-detection job execution no longer needs NATS — the agent job queue now runs on
PostgreSQL. Smallest self-host deployments that only want practice review can drop a moving part.

**Operators:** replace `AGENT_NATS_ENABLED` with `AGENT_ENABLED` (and drop
`HEPHAESTUS_AGENT_NATS_SERVER`, `AGENT_NATS_MAX_ACK_PENDING`, `AGENT_NATS_FETCH_BATCH_SIZE`) on
the role that executes jobs; optional new tuning is `AGENT_POLL_INTERVAL`,
`AGENT_CLAIM_BATCH_SIZE`, and `AGENT_MAX_RETRIES`. NATS is still required for webhook and sync
ingest.

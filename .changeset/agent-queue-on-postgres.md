---
"hephaestus": minor
---

Practice-review job execution no longer needs NATS — the agent job queue now runs on
PostgreSQL. Smallest self-host deployments that only want practice review can drop a moving part.
The queue now also prunes its own history automatically, so a busy instance no longer accumulates
finished jobs without bound, and it reports its depth and the age of its oldest waiting job as
metrics rather than leaving you to infer them from logs.

**Operators:** replace `AGENT_NATS_ENABLED` with `AGENT_ENABLED` (and drop
`HEPHAESTUS_AGENT_NATS_SERVER`, `AGENT_NATS_MAX_ACK_PENDING`, `AGENT_NATS_FETCH_BATCH_SIZE`) on
every role that submits, executes, or recovers jobs; optional new tuning is `AGENT_POLL_INTERVAL`,
`AGENT_CLAIM_BATCH_SIZE`, `AGENT_MAX_RETRIES`, `AGENT_PAYLOAD_RETENTION` (default `P14D`), and
`AGENT_ROW_RETENTION` (default `P90D`). NATS is still required for webhook and sync ingest.

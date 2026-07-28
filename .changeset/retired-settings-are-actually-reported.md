---
"hephaestus": patch
---

The "this setting is no longer read" warning at startup now actually appears. It was watching for
names no deployment ever used, and the settings it warns about were no longer being handed to the
application at all, so an upgraded instance carrying a stale line in its `.env` started up silently
and left the operator to find it by hand. Any of `AGENT_NATS_ENABLED`, `AGENT_NATS_MAX_ACK_PENDING`,
`AGENT_NATS_FETCH_BATCH_SIZE`, `HEPHAESTUS_WORKER_LLM_BASE_URL`, `HEPHAESTUS_WORKER_LLM_API_KEY` or
`HEPHAESTUS_SANDBOX_LLM_PROXY_ENABLED` still set now names itself once at boot, with what replaced
it. Remove them and the warning goes; they will stop being watched for in a future release.

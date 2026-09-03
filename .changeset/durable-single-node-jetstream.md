---
"hephaestus": patch
---

The single-host Compose topology now persists each webhook and integration-sync event before the
broker acknowledges it, closing the periodic-sync loss window during an unclean shutdown. This
trades some ingest throughput and latency for stronger durability; no operator action is required.

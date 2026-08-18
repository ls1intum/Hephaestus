---
"hephaestus": minor
---

The core NATS port can now be exposed beyond localhost. It stays bound to `127.0.0.1` by default;
set `NATS_BIND_HOST=0.0.0.0` (or a specific interface address) to let other hosts reach the bus — for
example when a separate environment consumes events from this one's JetStream.

Expose it only on a trusted or firewalled network: the bus is unauthenticated, so a public bind puts
its contents within reach of anyone who can route to the host.

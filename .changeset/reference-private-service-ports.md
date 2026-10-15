---
"hephaestus": patch
---

The reference deployment no longer publishes direct host ports for the webapp, application server, or PostgreSQL.
Web traffic uses the reverse proxy, and internal services use the Docker network.

Apply the update during a maintenance window after draining writers.
Compose recreates the affected containers, including PostgreSQL.

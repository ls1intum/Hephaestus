---
"hephaestus": patch
---

Upgrades give the server time to finish database migrations before failed startup probes mark it unhealthy. A ready server is accepted immediately, and the deployment timeout and normal health checks remain in force.

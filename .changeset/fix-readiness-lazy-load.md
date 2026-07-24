---
"hephaestus": patch
---

Fixes practice detection and the Slack mentor reporting themselves as unavailable for workspaces whose model is bound through a workspace-owned (BYO) provider: the readiness check failed while loading the bound model instead of answering, so reviews were skipped and the mentor showed as not ready even though the model was configured and working.

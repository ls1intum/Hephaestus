---
"hephaestus": patch
---

Deployments now make the new release's images available before they stop the running worker. If an image pull fails, the running release, worker, and applied record stay unchanged.

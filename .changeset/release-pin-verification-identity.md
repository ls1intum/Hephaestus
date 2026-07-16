---
"hephaestus": patch
---

Fixes a release deploy that never started: the signature check on the pinned agent image rejected every
valid release, so the application server stayed down.

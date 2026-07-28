---
"hephaestus": patch
---

Fixes the release-image pin check rejecting every valid pinned digest, which stopped the
application server from starting on a fresh deploy that enforces the digest pin.

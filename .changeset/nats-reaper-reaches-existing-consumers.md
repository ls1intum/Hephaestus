---
"hephaestus": patch
---

The consumer-expiry setting now takes effect on deployments that already had consumers, and no longer lengthens the lifetime of the short-lived ones. Setting it previously did nothing unless a consumer happened to be created afterwards, and where consumers were unnamed it extended how long they lingered instead of shortening it. A negative value is now rejected at startup rather than accepted.

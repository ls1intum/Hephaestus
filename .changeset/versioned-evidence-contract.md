---
"hephaestus": minor
---

Practice editors now explain the review lifecycle and let authors choose the exact required or optional evidence, minimum quality, and known limitations. Unavailable or incomplete inputs produce a conservative refusal instead of an unsupported judgment.

**Operators:** before enabling practice review, add each controller-approved `source:audience` grant to `HEPHAESTUS_EVIDENCE_AUTHORIZED_SOURCE_USES`; the default is empty and wildcards are rejected. If Outline is enabled, set the same `HEPHAESTUS_INTEGRATION_OUTLINE_ALLOWED_ORIGINS` value on server, worker, and webhook roles, then restart all three. An empty list blocks Outline connections, sync, webhook collection, evidence projection, and identity linking.

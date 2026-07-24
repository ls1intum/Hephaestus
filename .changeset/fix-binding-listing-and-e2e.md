---
"hephaestus": patch
---

Fixes the workspace AI setup page failing to load when a purpose is bound to a model: listing the bindings returned a server error instead of reporting each purpose's model and readiness.

Also clarifies the message shown when a review is not started — it now names the two causes an operator can act on (practice detection unbound or disabled, or the workspace's monthly LLM budget exhausted) instead of referring to the retired agent-config concept.

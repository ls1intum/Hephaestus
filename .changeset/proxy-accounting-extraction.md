---
"hephaestus": patch
---

Internal cleanup: the LLM proxy's budget check, per-call token accounting, and request metrics now live behind one collaborator instead of being wired individually into the proxy endpoint. No behaviour change.

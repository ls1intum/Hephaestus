---
"hephaestus": patch
---

Approving a GitLab merge request now works. The approval was sent to an endpoint GitLab has never
offered, so every attempt failed; it now goes through GitLab's approval API, and a refusal (for
example, a bot cannot approve a merge request it opened itself) is reported with its reason.

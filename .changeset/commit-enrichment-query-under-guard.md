---
"hephaestus": patch
---

Fixes shared GitHub query fragments being pulled into requests that never asked for them, which
GitHub rejects outright — the whole request fails, so a sync stops rather than degrades. This only
triggered once a second fragment file existed, which it now does: the commit-enrichment request was
the last one assembled entirely in code, invisible to the checks that verify every other request
against GitHub's published schema, and it is now checked like the rest.

---
"hephaestus": patch
---

Fixes adopting a practice from the catalog, creating one, and editing one failing with "an unexpected error occurred". Every write to a practice was rejected as it was saved, so a workspace could not take on the practices it wanted to review, and a workspace that had never recorded a catalog installation no longer received one at start-up. Practices already in a workspace kept working and nothing was lost — the writes were refused rather than half-applied.

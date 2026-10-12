---
"hephaestus": minor
---

The Practice profile overview response no longer includes its top-level `holdingUp` and `reviewedWork` fields after removal of the summary cards. Standing and practice-group responses retain their separate reviewed-work counts.

**Operators:** Update custom API clients that read the removed overview fields before upgrading. The bundled web app is updated with this release.

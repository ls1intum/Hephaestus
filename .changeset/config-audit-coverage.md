---
"hephaestus": patch
---

The configuration-changes audit trail now covers workspace administration, not just AI configuration:
a member's role being granted or changed, features being enabled or disabled, the workspace being
paused or purged, the SCM access token being rotated, and public visibility being toggled are all
recorded with who did it and the before/after — the role change and the enable/disable being the ones
administrators most often need to answer for. Credentials are never stored (a token rotation records
only that a token is set). The database migration widens an existing constraint and applies
automatically, with no action required on upgrade.

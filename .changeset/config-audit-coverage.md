---
"hephaestus": patch
---

The settings-change audit trail now covers workspace administration, not just AI configuration: a
member's role being granted, changed or revoked, a member being hidden or unhidden, features being
enabled or disabled, a practice being activated or deactivated, the workspace being paused or purged,
the SCM access token being rotated, and public visibility being toggled are all recorded with who did
it and the before/after. Credentials are never stored — a token rotation records only that a token was
rotated, and when. Connecting or disconnecting an integration continues to be recorded on the
connection's own history. The database migration widens an existing constraint and applies
automatically, with no action required on upgrade.

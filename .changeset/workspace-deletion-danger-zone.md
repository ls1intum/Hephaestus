---
"hephaestus": minor
---

Workspace owners can now permanently delete a workspace from its Danger Zone after reviewing the
consequences and typing its slug. The flow links to the available personal data export and makes
clear that content already posted to external providers and operational records remain. Deletion removes locally stored integration data and credentials and is blocked while integration sync or
AI work is active.

The workspace status endpoint no longer lets administrators bypass the owner-only deletion check by
setting the status to `PURGED`.

**Operators:** If automation purges workspaces with `PATCH /workspaces/{slug}/status`, switch it to
owner-authenticated `DELETE /workspaces/{slug}`.

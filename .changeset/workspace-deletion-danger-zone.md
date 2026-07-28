---
"hephaestus": minor
---

Workspace owners can now delete a workspace from the UI. Workspace settings gained a Danger Zone
that spells out what deleting erases — memberships, monitored repositories, activity, practice
detections, feedback history, mentor conversations, Slack messages and integration credentials —
and what it does not: comments Hephaestus already posted on GitHub or GitLab stay on the git
provider, and the workspace slug stays reserved permanently. Deleting takes two steps and asks you
to type the workspace slug, and it points at the account data export first, because Hephaestus has
no workspace-level export. Only the workspace owner can delete; administrators are told so rather
than shown a button that fails.

This also closes an authorization hole in the workspace status endpoint. Setting a workspace's
status to `PURGED` through `PATCH /workspaces/{slug}/status` ran the same purge as the
owner-only `DELETE /workspaces/{slug}`, so any workspace administrator could destroy a workspace
they did not own. The status endpoint now accepts only `ACTIVE` and `SUSPENDED` and rejects a purge
with `409`; deletion lives at `DELETE /workspaces/{slug}` alone. Self-hosters whose automation set
the status to `PURGED` must switch to the `DELETE` endpoint and authenticate as the owner.

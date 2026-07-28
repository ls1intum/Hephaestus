---
"hephaestus": patch
---

Fixes a workspace admin page that any member of the workspace could open by visiting its URL
directly. The page's actions were already refused by the server, so it showed only errors rather
than any data, but it should never have been reachable — every workspace admin page now redirects
non-admins away. Two smaller fixes ride along: an administrator whose role is revoked mid-session
no longer keeps the admin UI until they reload, and an instance administrator with no workspaces
yet can once again reach the "Create Workspace" button.

---
"hephaestus": patch
---

On the source-control page, losing access to the connection now closes an open Stop and clears its repository and token forms, and a later outage does not bring them back. When the monitored repositories cannot refresh for a moment, the list and an open Stop stay, with a warning that they may be out of date and a Retry. While the list cannot be read at all, Add waits for it, and a repository path you already typed is kept for when it reads again.

---
"hephaestus": patch
---

Fixes practice dashboards reporting different numbers each time the same page is reloaded, and —
where two workspaces review the same pull request or issue — reporting nothing at all for that piece
of work. Both came from how the most recent review run was picked: the choice was not settled between
two runs recorded at the same instant, and it was not confined to the workspace being looked at, so a
run belonging to another workspace could win and drop that work out of every count on the page.

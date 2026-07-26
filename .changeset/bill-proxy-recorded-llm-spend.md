---
"hephaestus": patch
---

Fixes AI spend being reported as zero for a run whose agent finished without writing a usage report. The tokens the proxy already saw go out are now billed to the workspace's month, so budget caps act on what was really spent.

Also fixes a rare failure when changing a model's price while it already had one, and stops two admins editing AI settings, provider connections, or spending caps at the same time from silently undoing each other's change.

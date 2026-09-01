---
"hephaestus": patch
---

On preview and staging deployments the footer's branch link now opens the branch it names. Builds of
a pull request recorded the merge ref (`1538/merge`) rather than the branch, so the link led nowhere.

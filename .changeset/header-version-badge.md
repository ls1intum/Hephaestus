---
"hephaestus": patch
---

Every deployment is now clearly identifiable. Outside production the header shows
an environment pill (Staging / Preview / Local) instead of a raw commit hash, and
the footer gains a deployment strip — branch, commit (linked to the exact commit),
and how long ago it was deployed. Production is unchanged: the header shows the
release version linking to its notes, and the footer stays clean.

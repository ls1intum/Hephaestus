---
"hephaestus": minor
---

A practice now decides for itself whether a draft is worth reviewing. Previously a single workspace
switch, "Skip drafts", silenced every practice on every draft — including the one whose whole subject
is how work is handed over, so its advice about draft hand-offs could never actually reach a draft.
The switch and its instance-wide default are gone; each practice's occasion says whether it includes
drafts, and today one shipped practice ("Ready and traceable handoff") does.

**Operators:** remove `PRACTICE_REVIEW_SKIP_DRAFTS` from your environment — it is no longer read. If
you had drafts switched off, expect that one practice to start commenting on draft pull requests; no
other practice reviews a draft.

---
"hephaestus": minor
---

A practice now decides for itself whether a draft is worth reviewing. Previously a single workspace
switch, "Skip drafts", silenced every practice on every draft — including the one whose whole subject
is how work is handed over, so its advice about draft hand-offs could never actually reach a draft.
The switch and its instance-wide default are gone; each practice's occasion says whether it includes
drafts. In the shipped catalogue, only "Ready and traceable handoff" opts in.

**Operators:** remove `PRACTICE_REVIEW_SKIP_DRAFTS` from your environment — it is no longer read. If
you had drafts switched off, expect that one practice to start commenting on draft pull requests; no
other practice reviews a draft.

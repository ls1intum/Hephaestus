---
"hephaestus": patch
---

A practice review that skips automated review for insufficient evidence is no longer indistinguishable from one that reviewed the work and found nothing. It now reports "Nothing was assessed" and explains why, instead of showing "no findings" against a completed run.

Fixes a review that could run against a pull request whose diff failed to load. An oversized, corrupt, or unreadable diff was stored as an empty diff, so the review proceeded with no code to examine.

Fixes an upgrade that marked the review results of every existing practice unverifiable on instances created before evidence requirements existed.

Editing a practice's known limitation and retyping the same wording no longer records the practice as having changed its review rules.

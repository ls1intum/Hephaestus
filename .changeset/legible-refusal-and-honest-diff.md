---
"hephaestus": patch
---

A practice review that declines for want of evidence no longer looks like a clean review. It now says "Nothing was assessed" and explains why, instead of reporting "no findings" under a green Completed badge.

Fixes a review that could judge a pull request whose diff failed to load — an oversized, corrupt, or unreadable diff was stored as an empty one, so the review ran against no code at all.

Fixes an upgrade that left every review claim marked unverifiable on instances created before evidence rules existed.

Editing a practice limitation and retyping the same wording no longer marks the practice as having changed its review rules.

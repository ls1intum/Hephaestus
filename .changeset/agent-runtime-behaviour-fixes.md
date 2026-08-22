---
---

No user-facing or operator-facing effect. Type-checking the agent runtime surfaced defects in the
practice-review path that both test suites were green through: a review's result could never be
accepted, so no feedback was ever composed from one; the composer was told no observation was
negative; repeated token usage was billed twice; one malformed precompute hint failed the whole run;
and the diagnostic log always reported zero tool calls. Practice reviews are off by default, so no
instance running the shipped defaults could have observed any of it. The mentor defect found in the
same sweep did reach a release and has its own note.

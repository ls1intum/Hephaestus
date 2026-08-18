---
"hephaestus": patch
---

Every practice-review result can now be traced back to what produced it: the run records the model
and prompt version that ran and a fingerprint of the evidence the review actually saw, so a result
that looks wrong can be told apart from a result produced from different inputs than you assumed.

Every piece of feedback the instance prepares is also recorded as either delivered or withheld, with
the reason it was withheld. Feedback that never left the instance no longer looks the same as
feedback a developer saw and chose not to act on.

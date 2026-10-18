---
"hephaestus": patch
---

When a review runs out of time or stalls while it writes private practice feedback, or its worker stops at that point, Hephaestus now tries again in a new attempt from the review's original inputs instead of dropping that feedback. It tries only after the feedback on the work is accepted and before any private feedback is prepared. Feedback that was already accepted is kept and is not prepared again.

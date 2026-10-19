---
"hephaestus": patch
---

When a review runs out of time or stalls while it writes private practice feedback, or its worker stops before that feedback is written, Hephaestus now tries again in a new attempt from the review's original inputs instead of dropping that feedback. A new attempt composes the feedback on the work only if none was accepted, and private feedback only until any of it is prepared. Feedback that was already accepted is kept and is not prepared again.

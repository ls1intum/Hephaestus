---
"hephaestus": minor
---

Automated code review can now read the rest of the repository at the reviewed commit, not just the changed lines, so feedback about a change accounts for the code it calls into. Practices that review a diff pick this up automatically; a workspace whose repository is not mirrored still gets the review, without the surrounding code.

Reviews also stop making claims their evidence cannot support: the limits an author records on a practice — that a diff cannot show how the code behaves once deployed, for instance — now reach the reviewing model.

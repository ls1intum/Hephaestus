---
"hephaestus": patch
---

Fixes a review that finished normally and then produced none of the feedback written for the
developer themselves. Composing that feedback was skipped whenever the review had been told partway
through to start writing down what it had found — ordinary on any review of real size, so at any
normal time allowance it meant almost every review — and skipped again whenever a review used its
full allowance, because time was still being held back for a retry that could no longer happen.
Composing now goes ahead whenever a review genuinely finished with enough time left to write, and a
review that broke off mid-run still keeps its retry allowance.

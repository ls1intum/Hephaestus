---
"hephaestus": patch
---

When a review has more to say than one comment can hold, the suggestions that survive are now chosen by how much of your change they were actually seen in, rather than by how sure the reviewer said it felt. Previously every observation carried a self-reported confidence score, and that score decided which suggestions made the cut and which strength got acknowledged. Measured across 580 real observations it never once dropped below 90% and was a flat 100% more than half the time — so it was deciding those cuts on noise. It is gone.

Observations are now ordered by severity first, then by how many distinct places in the change the observation is quoted at: a habit running through four files leads a one-off, and problems always precede strengths. The order is stable, so re-reviewing the same work reproduces the same list rather than shuffling it.

The review detail page no longer shows a confidence percentage, because there was never a real measurement behind it. The unmeasured confidence field and previously stored values are removed rather than retained as a misleading signal.

---
"hephaestus": patch
---

Practice reviews show truer facts:

- The work sheet shows no details for work that the provider deleted, and it says why.
- The queue does not count a review that waits a short time before a retry as waiting, because no worker can pick it up yet.
- **Until asked** counts no time for a review from a backfill or a sweep, so these reviews no longer skew the review time.
- Feedback that a review withheld no longer shows the review's posted comment as its own delivery.
